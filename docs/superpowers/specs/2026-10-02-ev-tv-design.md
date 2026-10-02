# ev-tv — Tasarım

Tarih: 2026-10-02
Durum: Onaylandı

## 1. Amaç

Xiaomi Mi TV Stick'te (model MiTV-AESP0, Android 10, 1 GB RAM, Amlogic
çip) canlı ulusal TV kanallarını **donmadan** izletecek, bize ait küçük bir
uygulama ve kanal listesi.

**Neden:** Televizo ile izlerken görüntü donuyor, ses akmaya devam ediyor;
düzeltmek için kanalı kapatıp açmak gerekiyor. 2026-10-02 incelemesinde
sebep bulundu: uyarlamalı (master) HLS yayınlarda kalite basamağı her
değiştiğinde stick'in Amlogic çözücüsü (`OMX.amlogic.avc.decoder.awesome2`)
yeniden kuruluyor ve bu sırada hata veriyor (`setPortMode ...
DynamicANWBuffer failed`). Ağ temiz (modeme ve TRT'ye %0 kayıp). TRT 1
sabit 720p adresiyle açıldığında 90 sn boyunca donma görülmedi.

**Kullanıcı:** Esas olarak kullanıcının annesi. Uygulama çok basit
olmalı; ayar menüsü olmamalı.

**Başarı ölçütü:** Bir akşam boyunca (birkaç saat) kanal izlenirken
görüntü donmaz; donsa bile kullanıcı bir şey yapmadan birkaç saniye
içinde kendiliğinden düzelir.

### Kapsam dışı

- Kayıttan izleme, arşiv, program rehberi (EPG).
- Birden fazla cihaz/kullanıcı profili, giriş/hesap.
- Mağazada yayınlama. Uygulama yalnızca bu stick'e ADB ile kurulur.
- Ücretli veya şifreli yayınlar. Listede yalnızca herkese açık, ücretsiz
  yayınlar bulunur.

## 2. Genel yapı

Tek bir herkese açık GitHub deposu: `aliemrevezir/ev-tv`.

```
ev-tv/
├── liste/                     # Kanal listesi üretimi (Python)
│   ├── kanallar.yaml          # Elle tutulan seçki: ad, sıra, logo, kaynak
│   ├── uret.py                # kanallar.yaml + iptv-org → liste.json
│   └── test_uret.py
├── liste.json                 # Üretilen liste (uygulama bunu indirir)
├── .github/workflows/liste.yml  # Her gece uret.py'yi çalıştırır, commit'ler
└── app/                       # Android TV uygulaması (Kotlin + libVLC)
```

**Veri akışı:**

```
kanallar.yaml ──(GitHub Actions, her gece)──► liste.json
                                                  │
           uygulama açılışta indirir, cihazda saklar
                                                  ▼
                              libVLC sabit kaliteli adresi oynatır
```

Mac'teki tv-dashboard sunucusuna ve Televizo'ya bağımlılık yoktur.
Televizo yedek olarak stick'te kalır. tv-dashboard'a eklenen `/tv.m3u`
ucu bu proje çalışır hale gelince kaldırılabilir; bu tasarımın parçası
değildir.

## 3. Kanal listesi

### 3.1 `kanallar.yaml`

Elle tutulan, sıralı seçki. Sıra, uygulamadaki kanal numarasını belirler
(ilk kanal = 1).

```yaml
- ad: TRT 1
  iptv_org: TRT1.tr          # iptv-org tvg-id öneki (@SD gibi ek yok sayılır)
  logo: https://...           # isteğe bağlı; yoksa iptv-org'daki logo
- ad: ATV
  iptv_org: ATV.tr
- ad: Özel Kanal
  adres: https://.../index.m3u8   # isteğe bağlı: iptv-org yerine doğrudan adres
```

Her kayıtta `iptv_org` ya da `adres` alanından **tam olarak biri**
bulunur. İlk seçki yaklaşık 20-30 ulusal kanaldan oluşur (TRT 1, ATV,
Show TV, Star TV, Kanal D, NOW, TV8, Kanal 7, TRT Haber vb.). Seçki,
iptv-org Türkiye listesinde çalışan kanallar arasından yapılır.

### 3.2 `uret.py`

Girdi: `kanallar.yaml` ve
`https://iptv-org.github.io/iptv/countries/tr.m3u`.

Her kanal için:

1. Kaynak adresi bulur (`adres` alanı ya da iptv-org'daki eşleşen
   `tvg-id`). Aynı `tvg-id` için birden fazla kayıt varsa listedeki ilk
   çalışan kayıt kullanılır.
2. Adresi indirir. Dosya **master playlist** ise (`#EXT-X-STREAM-INF`
   içeriyorsa) kalite basamaklarından birini seçer:
   - yüksekliği 720 ve altında olanlar arasından en yükseği;
   - 720 ve altı yoksa en düşük çözünürlüklü olanı.
   Göreli adresleri master'ın adresine göre mutlak adrese çevirir.
3. Seçilen adresin (media playlist) indirilebildiğini ve içinde en az bir
   segment olduğunu doğrular.
4. Sonuca göre:
   - Başarılı → kanalı `durum: "ok"` ile yazar.
   - Sunucu HTTP 403 veya 451 döndü (büyük olasılıkla bölge kısıtı,
     bkz. §7) → master'dan basamak seçilemediyse kaynak adresi olduğu
     gibi, `durum: "dogrulanamadi"` ile yazar.
   - Başka bir hata, önceki `liste.json`'da bu kanalın bir adresi var →
     o adresi korur, `durum: "eski"` ile yazar.
   - Başka bir hata, önceki adres yok → kanalı listeye almaz, günlüğe
     yazar.

   `durum` alanı yalnızca bilgi içindir; uygulama her durumda kanalı
   oynatmayı dener.

İstek başına zaman aşımı 10 sn. User-Agent sabit, sıradan bir tarayıcı
değeri.

### 3.3 `liste.json`

```json
{
  "surum": 1,
  "uretildi": "2026-10-03T03:00:00Z",
  "kanallar": [
    {
      "no": 1,
      "ad": "TRT 1",
      "adres": "https://tv-trt1.medya.trt.com.tr/master_720.m3u8",
      "logo": "https://...",
      "durum": "ok"
    }
  ]
}
```

`no` alanı `kanallar.yaml` sırasından gelir ve listeden düşen kanallar
yüzünden kaymaz: bir kanal bu gece çalışmasa bile diğer kanalların
numarası değişmez.

### 3.4 GitHub Actions

`.github/workflows/liste.yml`: her gece 03:00 TSİ'de (00:00 UTC) ve elle
tetiklemeyle çalışır. `uret.py`'yi çalıştırır; `liste.json` değiştiyse
commit'ler ve push'lar. Uygulama listeyi şu adresten indirir:

```
https://raw.githubusercontent.com/aliemrevezir/ev-tv/main/liste.json
```

## 4. Uygulama

### 4.1 Teknik temel

- Kotlin, tek Activity. Arayüz Android View'larla (Compose kullanılmaz;
  1 GB RAM'li cihazda daha hafif).
- Oynatıcı: libVLC (`org.videolan.android:libvlc-all`).
- `minSdk` 21, hedef cihaz Android 10 (API 29). Stick'in ABI'si
  ilk kurulumda `ro.product.cpu.abilist` ile doğrulanır; APK yalnızca
  gereken ABI ile (muhtemelen `armeabi-v7a`) üretilir, boyut küçük kalsın
  diye.
- Android TV başlatıcısında görünür (`LEANBACK_LAUNCHER`, banner).
- Paket adı: `com.aliemrevezir.evtv`.

### 4.2 Bileşenler

| Bileşen | Görev | Bağımlılık |
|---|---|---|
| `KanalListesi` | `liste.json`'u indirir, ayrıştırır, cihazda saklar; çevrimdışıyken saklananı döner | ağ, dosya |
| `KanalSecici` | Geçerli kanal, sonraki/önceki, numaraya göre bul; son kanalı hatırlar | `SharedPreferences` |
| `Oynatici` | libVLC'yi sarar: adres + çözücü kipiyle oynat, durdur, olayları yayınla | libVLC |
| `DonmaBekcisi` | Kare ilerlemesini izler; donmayı ve tekrarları sayar, karar verir | yalnızca saat ve olaylar (saf mantık) |
| `AnaEkran` (Activity) | Kumanda tuşları, kanal bilgisi kutusu, kanal listesi paneli | hepsi |

`KanalSecici` ve `DonmaBekcisi` Android'e bağımlı olmayan saf Kotlin
sınıflarıdır; birim testleri JVM'de çalışır.

### 4.3 Davranış

- **Açılış:** Liste yüklenir (önce cihazdaki kayıt, arka planda
  GitHub'dan tazelenir). En son izlenen kanal hemen oynar; ilk açılışta
  1 numaralı kanal (TRT 1).
- **Kumanda:**
  - Yukarı / Aşağı: sonraki / önceki kanal (listenin sonunda başa döner).
  - OK: kanal listesi paneli açılır. Panelde Yukarı/Aşağı ile gezilir,
    OK ile kanal açılır.
  - Geri: panel açıksa kapatır. Panel kapalıyken birinci basışta
    "Çıkmak için tekrar basın" yazar; 3 sn içinde ikinci basış çıkar.
- **Kanal bilgisi:** Kanal değişince sol üstte 3 sn boyunca numara, ad
  ve logo görünür.
- **Ekran:** Oynatma sırasında ekran uykuya geçmez
  (`FLAG_KEEP_SCREEN_ON`).
- **Ayar menüsü yoktur.**

### 4.4 Donma ve hata yönetimi

**Donma bekçisi:**

- Bekçi her saniye libVLC'den gösterilen kare sayısını okur.
- Oynatma "oynuyor" durumundayken **5 sn** boyunca kare sayısı artmazsa
  bunu donma sayar.
- Donma olunca yayın yeniden başlatılır (aynı adres, aynı çözücü kipi).
- Aynı kanalda **10 dakika** içinde **2.** donma olursa o kanal, uygulama
  açık kaldığı sürece işlemcide çözme kipine geçer (libVLC'de donanım
  çözücü kapalı).

**Çözücü kipi:**

- Varsayılan: donanım çözücü (Amlogic, MediaCodec).
- Yedek: işlemcide çözme. 720p işlemcide çözmek stick'i zorlayabilir; bu
  yüzden yalnızca sorunlu kanalda ve gerektiğinde kullanılır.
- Kip seçimi kanal başına bellekte tutulur, kalıcı değildir.

**Kanal açılmazsa:** Ekranın ortasında "Bu kanal şu an açılmıyor"
yazar; uygulama 10 sn'de bir yeniden dener. Kullanıcı bu sırada kanal
değiştirebilir.

**Liste indirilemezse:** Cihazda saklanan son liste kullanılır. Hiç
liste yoksa (ilk açılış ve internet yok) ekranda "İnternet bağlantısı
bekleniyor" yazar ve 10 sn'de bir yeniden dener.

## 5. Test

- **Liste betiği:** `pytest` ile, ağa çıkmadan: master/media playlist
  ayrıştırma, 720p seçimi, göreli adres çözme, başarısız kanalın eski
  adresle korunması, `no` alanının kaymaması. Ayrıca betik bir kez gerçek
  iptv-org listesiyle çalıştırılır ve çıkan adreslerin açıldığı
  doğrulanır.
- **Uygulama mantığı:** JVM birim testleri: `KanalSecici` (sonraki,
  önceki, başa dönme, son kanalı hatırlama), `DonmaBekcisi` (5 sn
  eşiği, 10 dk içinde ikinci donmada kip değişimi, oynamıyorken
  saymama), `liste.json` ayrıştırma.
- **Uçtan uca:** Mac'te Android TV emülatöründe: uygulama açılıyor,
  kanal oynuyor, Yukarı/Aşağı ve liste paneli çalışıyor, son kanal
  hatırlanıyor, internet kesilince saklanan liste kullanılıyor.
- **Sınır:** Emülatör Amlogic çözücüyü taklit edemez. Asıl donma
  sorununun çözüldüğü ancak stick'te, TV boşken, kullanıcıyla birlikte
  doğrulanır. Geliştirme sırasında stick'e (192.168.1.101) **bağlanılmaz**.

## 6. Kurulum gereksinimleri (Mac)

- JDK 17 (Homebrew, `temurin@17`).
- Android SDK komut satırı araçları, platform API 34, build-tools,
  Android TV sistem imajı (arm64) ve emülatör.
- Python 3 ve `pyyaml` (liste betiği için).
- GitHub: `gh` hesabı (aliemrevezir) zaten bağlı. Depo herkese açık
  oluşturulur.

## 7. Açık riskler

- **İşlemcide çözme performansı:** 720p işlemcide çözmek bu stick'te
  takılabilir. Bu durumda yedek kip 480p/360p basamağı seçecek şekilde
  genişletilebilir. İlk sürümde yapılmaz, stick'te ölçüldükten sonra
  karar verilir.
- **Yayın adreslerinin değişmesi:** Bazı kanallar adreslerini sık
  değiştirir veya süreli anahtar (token) kullanır. Bu kanallar her gece
  yenilenir; yine de gün içinde bozulabilirler. Bu kanallar seçkiye
  alınmaz ya da listede kalıp açılmazlarsa "açılmıyor" mesajı gösterilir.
- **Bölge kısıtı:** GitHub Actions makineleri ABD'de çalışır; Türkiye'ye
  özel (geo-blocked) yayınlar orada doğrulanamaz. Bu kanallar
  §3.2'deki kurala göre `durum: "dogrulanamadi"` ile listeye alınır ve
  uygulama yine dener. Böyle bir kanal master adresiyle kalırsa kalite
  geçişi riski o kanalda sürer; ilk gerçek liste üretiminde hangi
  kanalların bu durumda olduğu görülür ve gerekirse onlar için `adres`
  alanıyla sabit basamak elle yazılır.
