import json

import pytest

import uret
from uret import Yanit

MASTER = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
360/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
720/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
https://cdn.example.com/1080/index.m3u8
"""

MEDIA = """#EXTM3U
#EXT-X-TARGETDURATION:6
#EXTINF:6.0,
seg1.ts
"""

BOS_MEDIA = """#EXTM3U
#EXT-X-TARGETDURATION:6
"""


def sahte_indirici(sayfalar):
    """sayfalar: {adres: Yanit veya Exception}"""
    def indir(adres):
        sonuc = sayfalar.get(adres, Yanit(404, "", adres))
        if isinstance(sonuc, Exception):
            raise sonuc
        return sonuc
    return indir


# --- m3u ayrıştırma ---

def test_m3u_ayristir_tvg_id_ekini_atar_ve_sirayi_korur():
    metin = """#EXTM3U
#EXTINF:-1 tvg-id="ATV.tr@SD" tvg-logo="https://logo/atv.png",ATV (1080p)
https://a/1.m3u8
#EXTINF:-1 tvg-id="ATV.tr@SD",ATV (360p)
#EXTVLCOPT:http-user-agent=Foo
https://a/2.m3u8
#EXTINF:-1 tvg-id="TRT1.tr",TRT 1
https://t/1.m3u8
"""
    sonuc = uret.m3u_ayristir(metin)
    assert [k.adres for k in sonuc["ATV.tr"]] == ["https://a/1.m3u8", "https://a/2.m3u8"]
    assert sonuc["ATV.tr"][0].logo == "https://logo/atv.png"
    assert sonuc["ATV.tr"][1].logo is None
    assert [k.adres for k in sonuc["TRT1.tr"]] == ["https://t/1.m3u8"]


def test_m3u_ayristir_tvg_id_olmayan_kaydi_atlar():
    metin = '#EXTM3U\n#EXTINF:-1,Adsiz\nhttps://x/1.m3u8\n'
    assert uret.m3u_ayristir(metin) == {}


# --- master playlist ---

def test_master_mi():
    assert uret.master_mi(MASTER)
    assert not uret.master_mi(MEDIA)


def test_basamaklari_ayristir_goreli_adresi_mutlak_yapar():
    basamaklar = uret.basamaklari_ayristir(MASTER, "https://tv.example.com/live/master.m3u8?t=1")
    assert basamaklar == [
        uret.Basamak(360, 800000, "https://tv.example.com/live/360/index.m3u8"),
        uret.Basamak(720, 2500000, "https://tv.example.com/live/720/index.m3u8"),
        uret.Basamak(1080, 5000000, "https://cdn.example.com/1080/index.m3u8"),
    ]


def test_basamak_sec_720_ve_altindaki_en_yuksegi_secer():
    b = [uret.Basamak(360, 1, "a"), uret.Basamak(720, 2, "b"), uret.Basamak(1080, 3, "c")]
    assert uret.basamak_sec(b).adres == "b"


def test_basamak_sec_720_yoksa_altindaki_en_yuksek():
    b = [uret.Basamak(1080, 3, "c"), uret.Basamak(480, 2, "b"), uret.Basamak(360, 1, "a")]
    assert uret.basamak_sec(b).adres == "b"


def test_basamak_sec_hepsi_720_ustundeyse_en_dusugu():
    b = [uret.Basamak(1440, 9, "d"), uret.Basamak(1080, 5, "c")]
    assert uret.basamak_sec(b).adres == "c"


def test_basamak_sec_ayni_yukseklikte_yuksek_bant_genisligi():
    b = [uret.Basamak(720, 1, "dusuk"), uret.Basamak(720, 2, "yuksek")]
    assert uret.basamak_sec(b).adres == "yuksek"


def test_basamak_sec_cozunurluk_yoksa_ilk_basamak():
    b = [uret.Basamak(None, 2, "ilk"), uret.Basamak(None, 1, "ikinci")]
    assert uret.basamak_sec(b).adres == "ilk"


def test_basamak_sec_cozunurlugu_olmayanlari_yok_sayar():
    b = [uret.Basamak(None, 9, "ses"), uret.Basamak(720, 1, "video")]
    assert uret.basamak_sec(b).adres == "video"


def test_segment_var_mi():
    assert uret.segment_var_mi(MEDIA)
    assert not uret.segment_var_mi(BOS_MEDIA)


# --- tek adres işleme ---

def test_adres_dene_master_720_secer():
    indir = sahte_indirici({
        "https://tv/master.m3u8": Yanit(200, MASTER, "https://tv/master.m3u8"),
        "https://tv/720/index.m3u8": Yanit(200, MEDIA, "https://tv/720/index.m3u8"),
    })
    assert uret.adres_dene("https://tv/master.m3u8", indir) == ("ok", "https://tv/720/index.m3u8")


def test_adres_dene_yonlendirmede_son_adrese_gore_cozer():
    indir = sahte_indirici({
        "https://tv/master.m3u8": Yanit(200, MASTER, "https://edge/x/master.m3u8"),
        "https://edge/x/720/index.m3u8": Yanit(200, MEDIA, "https://edge/x/720/index.m3u8"),
    })
    assert uret.adres_dene("https://tv/master.m3u8", indir) == ("ok", "https://edge/x/720/index.m3u8")


def test_adres_dene_media_playlist_dogrudan_ok():
    indir = sahte_indirici({"https://tv/a.m3u8": Yanit(200, MEDIA, "https://tv/a.m3u8")})
    assert uret.adres_dene("https://tv/a.m3u8", indir) == ("ok", "https://tv/a.m3u8")


@pytest.mark.parametrize("kod", [403, 451])
def test_adres_dene_kaynak_bolge_kisitli(kod):
    indir = sahte_indirici({"https://tv/m.m3u8": Yanit(kod, "", "https://tv/m.m3u8")})
    assert uret.adres_dene("https://tv/m.m3u8", indir) == ("dogrulanamadi", "https://tv/m.m3u8")


def test_adres_dene_basamak_bolge_kisitli_ise_secilen_basamak_yazilir():
    indir = sahte_indirici({
        "https://tv/master.m3u8": Yanit(200, MASTER, "https://tv/master.m3u8"),
        "https://tv/720/index.m3u8": Yanit(403, "", "https://tv/720/index.m3u8"),
    })
    assert uret.adres_dene("https://tv/master.m3u8", indir) == ("dogrulanamadi", "https://tv/720/index.m3u8")


def test_adres_dene_bos_media_hata():
    indir = sahte_indirici({"https://tv/a.m3u8": Yanit(200, BOS_MEDIA, "https://tv/a.m3u8")})
    assert uret.adres_dene("https://tv/a.m3u8", indir) == ("hata", None)


def test_adres_dene_ag_hatasi():
    indir = sahte_indirici({"https://tv/a.m3u8": ConnectionError("zaman aşımı")})
    assert uret.adres_dene("https://tv/a.m3u8", indir) == ("hata", None)


def test_adres_dene_404():
    assert uret.adres_dene("https://tv/yok.m3u8", sahte_indirici({})) == ("hata", None)


# --- kanal işleme ---

def aday(adres, logo=None):
    return uret.Aday(adres, logo)


def test_kanal_isle_ilk_calisan_adayi_kullanir():
    indir = sahte_indirici({"https://b/m.m3u8": Yanit(200, MEDIA, "https://b/m.m3u8")})
    kayit = {"ad": "ATV", "iptv_org": "ATV.tr"}
    adaylar = {"ATV.tr": [aday("https://a/m.m3u8"), aday("https://b/m.m3u8", "https://logo")]}
    sonuc = uret.kanal_isle(1, kayit, adaylar, indir, None)
    assert sonuc == {"no": 1, "ad": "ATV", "adres": "https://b/m.m3u8",
                     "logo": "https://logo", "durum": "ok"}


def test_kanal_isle_ok_dogrulanamadiya_ustun_gelir():
    indir = sahte_indirici({
        "https://a/m.m3u8": Yanit(403, "", "https://a/m.m3u8"),
        "https://b/m.m3u8": Yanit(200, MEDIA, "https://b/m.m3u8"),
    })
    kayit = {"ad": "ATV", "iptv_org": "ATV.tr"}
    adaylar = {"ATV.tr": [aday("https://a/m.m3u8"), aday("https://b/m.m3u8")]}
    assert uret.kanal_isle(1, kayit, adaylar, indir, None)["adres"] == "https://b/m.m3u8"


def test_kanal_isle_hic_ok_yoksa_ilk_dogrulanamadi():
    indir = sahte_indirici({"https://b/m.m3u8": Yanit(451, "", "https://b/m.m3u8")})
    kayit = {"ad": "TRT 2", "iptv_org": "TRT2.tr"}
    adaylar = {"TRT2.tr": [aday("https://a/m.m3u8"), aday("https://b/m.m3u8")]}
    sonuc = uret.kanal_isle(1, kayit, adaylar, indir, None)
    assert (sonuc["adres"], sonuc["durum"]) == ("https://b/m.m3u8", "dogrulanamadi")


def test_kanal_isle_adres_filtresi():
    indir = sahte_indirici({
        "https://ucuncu.uz/m.m3u8": Yanit(200, MEDIA, "https://ucuncu.uz/m.m3u8"),
        "https://tv.trt.com.tr/m.m3u8": Yanit(200, MEDIA, "https://tv.trt.com.tr/m.m3u8"),
    })
    kayit = {"ad": "TRT Müzik", "iptv_org": "TRTMuzik.tr", "adres_filtresi": "trt.com.tr"}
    adaylar = {"TRTMuzik.tr": [aday("https://ucuncu.uz/m.m3u8"), aday("https://tv.trt.com.tr/m.m3u8")]}
    assert uret.kanal_isle(1, kayit, adaylar, indir, None)["adres"] == "https://tv.trt.com.tr/m.m3u8"


def test_kanal_isle_dogrudan_adres_ve_yaml_logosu_oncelikli():
    indir = sahte_indirici({"https://x/m.m3u8": Yanit(200, MEDIA, "https://x/m.m3u8")})
    kayit = {"ad": "Özel", "adres": "https://x/m.m3u8", "logo": "https://yaml-logo"}
    sonuc = uret.kanal_isle(3, kayit, {}, indir, None)
    assert sonuc == {"no": 3, "ad": "Özel", "adres": "https://x/m.m3u8",
                     "logo": "https://yaml-logo", "durum": "ok"}


def test_kanal_isle_hata_ve_eski_adres_varsa_eski():
    eski = {"no": 2, "ad": "ATV", "adres": "https://eski/m.m3u8", "logo": None, "durum": "ok"}
    kayit = {"ad": "ATV", "iptv_org": "ATV.tr"}
    adaylar = {"ATV.tr": [aday("https://a/m.m3u8")]}
    sonuc = uret.kanal_isle(2, kayit, adaylar, sahte_indirici({}), eski)
    assert (sonuc["adres"], sonuc["durum"]) == ("https://eski/m.m3u8", "eski")


def test_kanal_isle_hata_ve_eski_adres_yoksa_none():
    kayit = {"ad": "ATV", "iptv_org": "ATV.tr"}
    adaylar = {"ATV.tr": [aday("https://a/m.m3u8")]}
    assert uret.kanal_isle(2, kayit, adaylar, sahte_indirici({}), None) is None


def test_kanal_isle_iptv_orgda_yoksa_eskiye_duser():
    eski = {"no": 2, "ad": "ATV", "adres": "https://eski/m.m3u8", "logo": None, "durum": "ok"}
    sonuc = uret.kanal_isle(2, {"ad": "ATV", "iptv_org": "ATV.tr"}, {}, sahte_indirici({}), eski)
    assert sonuc["durum"] == "eski"


# --- yaml doğrulama ---

@pytest.mark.parametrize("kayit", [
    {"ad": "X"},
    {"ad": "X", "iptv_org": "X.tr", "adres": "https://x"},
    {"iptv_org": "X.tr"},
])
def test_kanallari_dogrula_hatali(kayit):
    with pytest.raises(ValueError):
        uret.kanallari_dogrula([kayit])


def test_kanallari_dogrula_ayni_ad_iki_kez():
    with pytest.raises(ValueError):
        uret.kanallari_dogrula([{"ad": "X", "iptv_org": "X.tr"}, {"ad": "X", "adres": "https://x"}])


# --- liste üretimi ---

def test_liste_uret_dusen_kanal_numaralari_kaydirmaz():
    indir = sahte_indirici({
        "https://a/m.m3u8": Yanit(200, MEDIA, "https://a/m.m3u8"),
        "https://c/m.m3u8": Yanit(200, MEDIA, "https://c/m.m3u8"),
    })
    kanallar = [
        {"ad": "A", "adres": "https://a/m.m3u8"},
        {"ad": "B", "adres": "https://b/m.m3u8"},
        {"ad": "C", "adres": "https://c/m.m3u8"},
    ]
    sonuc = uret.liste_uret(kanallar, {}, indir, None, "2026-10-03T00:00:00Z")
    assert sonuc["surum"] == 1
    assert sonuc["uretildi"] == "2026-10-03T00:00:00Z"
    assert [(k["no"], k["ad"]) for k in sonuc["kanallar"]] == [(1, "A"), (3, "C")]


def test_liste_uret_eski_kaydi_ada_gore_bulur():
    eski_liste = {"kanallar": [{"no": 9, "ad": "B", "adres": "https://eski-b", "logo": None, "durum": "ok"}]}
    kanallar = [{"ad": "A", "adres": "https://a/m.m3u8"}, {"ad": "B", "adres": "https://b/m.m3u8"}]
    sonuc = uret.liste_uret(kanallar, {}, sahte_indirici({}), eski_liste, "t")
    assert sonuc["kanallar"] == [{"no": 2, "ad": "B", "adres": "https://eski-b", "logo": None, "durum": "eski"}]


def test_kaydet_kanallar_degismediyse_dosyaya_dokunmaz(tmp_path):
    yol = tmp_path / "liste.json"
    kanallar = [{"no": 1, "ad": "A", "adres": "x", "logo": None, "durum": "ok"}]
    yol.write_text(json.dumps({"surum": 1, "uretildi": "eski-zaman", "kanallar": kanallar}))
    assert not uret.kaydet(yol, {"surum": 1, "uretildi": "yeni-zaman", "kanallar": kanallar})
    assert json.loads(yol.read_text())["uretildi"] == "eski-zaman"


def test_kaydet_kanallar_degistiyse_yazar(tmp_path):
    yol = tmp_path / "liste.json"
    yeni = {"surum": 1, "uretildi": "t", "kanallar": [{"no": 1, "ad": "Ç", "adres": "x", "logo": None, "durum": "ok"}]}
    assert uret.kaydet(yol, yeni)
    assert json.loads(yol.read_text(encoding="utf-8")) == yeni
    assert "Ç" in yol.read_text(encoding="utf-8")
