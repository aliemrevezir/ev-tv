"""kanallar.yaml + iptv-org Türkiye listesi → liste.json

Her kanal için sabit kaliteli (≤720p) bir HLS adresi bulur ve doğrular.
Ayrıntılar: docs/superpowers/specs/2026-10-02-ev-tv-design.md §3.
"""
import json
import logging
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, NamedTuple, Optional
from urllib.parse import urljoin

import requests
import yaml

KOK = Path(__file__).resolve().parent.parent
KANALLAR_YAML = KOK / "liste" / "kanallar.yaml"
LISTE_JSON = KOK / "liste.json"
RAPOR = KOK / "liste" / "rapor.txt"

IPTV_ORG_KAYNAKLARI = [
    "https://iptv-org.github.io/iptv/countries/tr.m3u",
    "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/tr.m3u",
]
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)
ZAMAN_ASIMI = 10
HEDEF_YUKSEKLIK = 720
BOLGE_KISITI_KODLARI = {403, 451}

log = logging.getLogger("uret")


class Yanit(NamedTuple):
    durum_kodu: int
    metin: str
    adres: str  # yönlendirmelerden sonraki son adres


class Aday(NamedTuple):
    adres: str
    logo: Optional[str]


class Basamak(NamedTuple):
    yukseklik: Optional[int]
    bant: int
    adres: str


Indirici = Callable[[str], Yanit]


# --- ayrıştırma ---

def _nitelik(satir: str, ad: str) -> Optional[str]:
    m = re.search(rf'{ad}="([^"]*)"', satir)
    return m.group(1) if m else None


def m3u_ayristir(metin: str) -> dict[str, list[Aday]]:
    """iptv-org m3u → {tvg-id (ek olmadan): [Aday, ...]} (dosyadaki sırayla)."""
    sonuc: dict[str, list[Aday]] = {}
    tvg_id = logo = None
    for satir in metin.splitlines():
        satir = satir.strip()
        if satir.startswith("#EXTINF"):
            tvg_id = _nitelik(satir, "tvg-id")
            logo = _nitelik(satir, "tvg-logo") or None
        elif satir and not satir.startswith("#"):
            if tvg_id:
                sonuc.setdefault(tvg_id.split("@")[0], []).append(Aday(satir, logo))
            tvg_id = logo = None
    return sonuc


def master_mi(metin: str) -> bool:
    return "#EXT-X-STREAM-INF" in metin


def basamaklari_ayristir(metin: str, taban_adres: str) -> list[Basamak]:
    basamaklar = []
    bekleyen = None
    for satir in metin.splitlines():
        satir = satir.strip()
        if satir.startswith("#EXT-X-STREAM-INF"):
            cozunurluk = re.search(r"RESOLUTION=\d+x(\d+)", satir)
            bant = re.search(r"[:,]BANDWIDTH=(\d+)", satir)
            bekleyen = (int(cozunurluk.group(1)) if cozunurluk else None,
                        int(bant.group(1)) if bant else 0)
        elif satir and not satir.startswith("#") and bekleyen:
            basamaklar.append(Basamak(bekleyen[0], bekleyen[1], urljoin(taban_adres, satir)))
            bekleyen = None
    return basamaklar


def basamak_sec(basamaklar: list[Basamak]) -> Basamak:
    """≤720 içinde en yüksek; yoksa en düşük. Çözünürlüğü olmayanlar (genelde
    yalnızca ses) yok sayılır; hiçbirinde çözünürlük yoksa ilk basamak."""
    bilinen = [b for b in basamaklar if b.yukseklik is not None]
    if not bilinen:
        return basamaklar[0]
    uygun = [b for b in bilinen if b.yukseklik <= HEDEF_YUKSEKLIK]
    if uygun:
        return max(uygun, key=lambda b: (b.yukseklik, b.bant))
    return min(bilinen, key=lambda b: (b.yukseklik, -b.bant))


def segment_var_mi(metin: str) -> bool:
    return "#EXTINF" in metin


# --- doğrulama ---

def _indir(adres: str, indir: Indirici) -> Optional[Yanit]:
    try:
        return indir(adres)
    except Exception as e:  # ağ hatası, zaman aşımı, bozuk yanıt
        log.info("  %s: %s", adres, type(e).__name__)
        return None


def adres_dene(adres: str, indir: Indirici) -> tuple[str, Optional[str]]:
    """→ ("ok", adres) | ("dogrulanamadi", adres) | ("hata", None)"""
    yanit = _indir(adres, indir)
    if yanit is None:
        return "hata", None
    if yanit.durum_kodu in BOLGE_KISITI_KODLARI:
        return "dogrulanamadi", adres
    if yanit.durum_kodu != 200:
        log.info("  %s: HTTP %s", adres, yanit.durum_kodu)
        return "hata", None

    if master_mi(yanit.metin):
        basamaklar = basamaklari_ayristir(yanit.metin, yanit.adres)
        if not basamaklar:
            log.info("  %s: master'da basamak yok", adres)
            return "hata", None
        secilen = basamak_sec(basamaklar).adres
        yanit = _indir(secilen, indir)
        if yanit is None:
            return "hata", None
        if yanit.durum_kodu in BOLGE_KISITI_KODLARI:
            return "dogrulanamadi", secilen
        if yanit.durum_kodu != 200:
            log.info("  %s: HTTP %s", secilen, yanit.durum_kodu)
            return "hata", None
        adres = secilen

    if not segment_var_mi(yanit.metin):
        log.info("  %s: segment yok", adres)
        return "hata", None
    return "ok", adres


def kanal_isle(no: int, kayit: dict, iptv_org: dict[str, list[Aday]],
               indir: Indirici, eski: Optional[dict]) -> Optional[dict]:
    if "adres" in kayit:
        adaylar = [Aday(kayit["adres"], None)]
    else:
        adaylar = iptv_org.get(kayit["iptv_org"], [])
        filtre = kayit.get("adres_filtresi")
        if filtre:
            adaylar = [a for a in adaylar if filtre in a.adres]
        if not adaylar:
            log.warning("%s: iptv-org'da aday adres yok (%s)", kayit["ad"], kayit["iptv_org"])

    ilk_logo = next((a.logo for a in adaylar if a.logo), None)
    dogrulanamadi = None
    for aday in adaylar:
        durum, adres = adres_dene(aday.adres, indir)
        if adres:
            log.info("  %s: %s", durum, adres)
        if durum == "ok":
            return _kanal(no, kayit, adres, aday.logo or ilk_logo, "ok")
        if durum == "dogrulanamadi" and dogrulanamadi is None:
            dogrulanamadi = (adres, aday.logo or ilk_logo)

    if dogrulanamadi:
        return _kanal(no, kayit, dogrulanamadi[0], dogrulanamadi[1], "dogrulanamadi")
    if eski and eski.get("adres"):
        return _kanal(no, kayit, eski["adres"], eski.get("logo") or ilk_logo, "eski")
    log.warning("%s: çalışan adres yok, listeye alınmadı", kayit["ad"])
    return None


def _kanal(no: int, kayit: dict, adres: str, logo: Optional[str], durum: str) -> dict:
    return {"no": no, "ad": kayit["ad"], "adres": adres,
            "logo": kayit.get("logo") or logo, "durum": durum}


def kanallari_dogrula(kanallar: list[dict]) -> None:
    adlar = set()
    for i, k in enumerate(kanallar, 1):
        if not isinstance(k.get("ad"), str) or not k["ad"]:
            raise ValueError(f"{i}. kayıtta 'ad' metin değil (sayıysa tırnak içine alın)")
        if ("iptv_org" in k) == ("adres" in k):
            raise ValueError(f"{k['ad']}: 'iptv_org' ve 'adres' alanlarından tam olarak biri olmalı")
        if k["ad"] in adlar:
            raise ValueError(f"{k['ad']}: aynı ad iki kez kullanılmış")
        adlar.add(k["ad"])


def liste_uret(kanallar: list[dict], iptv_org: dict[str, list[Aday]], indir: Indirici,
               eski_liste: Optional[dict], uretildi: str) -> dict:
    kanallari_dogrula(kanallar)
    eskiler = {k["ad"]: k for k in (eski_liste or {}).get("kanallar", [])}
    sonuc = []
    for no, kayit in enumerate(kanallar, 1):
        log.info("%d. %s", no, kayit["ad"])
        kanal = kanal_isle(no, kayit, iptv_org, indir, eskiler.get(kayit["ad"]))
        if kanal:
            sonuc.append(kanal)
    return {"surum": 1, "uretildi": uretildi, "kanallar": sonuc}


def kaydet(yol: Path, liste: dict) -> bool:
    """Kanallar değiştiyse yazar. Yalnızca 'uretildi' değiştiyse dokunmaz
    (her gece boş commit olmasın)."""
    if yol.exists():
        eski = json.loads(yol.read_text(encoding="utf-8"))
        if eski.get("kanallar") == liste["kanallar"]:
            return False
    yol.write_text(json.dumps(liste, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return True


# --- gerçek ağ ---

def http_indirici() -> Indirici:
    oturum = requests.Session()
    oturum.headers["User-Agent"] = USER_AGENT

    def indir(adres: str) -> Yanit:
        r = oturum.get(adres, timeout=ZAMAN_ASIMI)
        return Yanit(r.status_code, r.text if r.status_code == 200 else "", r.url)
    return indir


def adaylari_birlestir(listeler: list[dict[str, list[Aday]]]) -> dict[str, list[Aday]]:
    """Aynı adres bir kez; sıra korunur; logosuz kayda diğer kaynaktaki logo eklenir."""
    sonuc: dict[str, list[Aday]] = {}
    for liste in listeler:
        for tvg_id, adaylar in liste.items():
            mevcut = sonuc.setdefault(tvg_id, [])
            for aday in adaylar:
                i = next((i for i, m in enumerate(mevcut) if m.adres == aday.adres), None)
                if i is None:
                    mevcut.append(aday)
                elif not mevcut[i].logo and aday.logo:
                    mevcut[i] = aday
    return sonuc


def iptv_org_indir(indir: Indirici) -> dict[str, list[Aday]]:
    listeler = []
    for kaynak in IPTV_ORG_KAYNAKLARI:
        yanit = _indir(kaynak, indir)
        if yanit and yanit.durum_kodu == 200:
            log.info("iptv-org listesi: %s", kaynak)
            listeler.append(m3u_ayristir(yanit.metin))
        else:
            log.warning("iptv-org listesi indirilemedi: %s", kaynak)
    if not listeler:
        raise RuntimeError("iptv-org listesi indirilemedi")
    return adaylari_birlestir(listeler)


class _Toplayici(logging.Handler):
    def __init__(self):
        super().__init__()
        self.satirlar: list[str] = []

    def emit(self, kayit):
        self.satirlar.append(self.format(kayit))


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    toplayici = _Toplayici()
    logging.getLogger().addHandler(toplayici)
    kanallar = yaml.safe_load(KANALLAR_YAML.read_text(encoding="utf-8"))
    indir = http_indirici()
    eski = json.loads(LISTE_JSON.read_text(encoding="utf-8")) if LISTE_JSON.exists() else None
    uretildi = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    liste = liste_uret(kanallar, iptv_org_indir(indir), indir, eski, uretildi)

    sayac: dict[str, int] = {}
    for k in liste["kanallar"]:
        sayac[k["durum"]] = sayac.get(k["durum"], 0) + 1
    log.info("Toplam %d/%d kanal: %s", len(liste["kanallar"]), len(kanallar), sayac)
    if not liste["kanallar"]:
        log.error("Hiç kanal yok, liste.json yazılmadı")
        return 1
    log.info("liste.json %s", "güncellendi" if kaydet(LISTE_JSON, liste) else "değişmedi")
    RAPOR.write_text(f"Üretildi: {uretildi}\n\n" + "\n".join(toplayici.satirlar) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
