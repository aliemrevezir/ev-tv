# ev-tv

Xiaomi Mi TV Stick için donmayan canlı TV: sabit kaliteli (≤720p) kanal
listesi + libVLC tabanlı Android TV uygulaması.
Tasarım: `docs/superpowers/specs/2026-10-02-ev-tv-design.md`.

## Yapı

- `liste/` — `kanallar.yaml` seçkisinden `liste.json` üreten betik. Her gece
  03:00 TSİ'de Actions'ta çalışır; ayrıntılı günlük `liste/rapor.txt`'de.
- `app/cekirdek` — Android'e bağımlı olmayan mantık (liste, kanal seçici,
  donma/açılış bekçileri). `cd app && ./gradlew -p cekirdek test`
- `app/tv` — Android TV uygulaması. APK'yı `APK` workflow'u derler.

## Kanal eklemek / çıkarmak

`liste/kanallar.yaml`'ı düzenleyip `main`'e push'la; liste birkaç dakika
içinde yeniden üretilir. Uygulama yeni listeyi bir sonraki açılışta alır.
Sırayı değiştirmek kanal numaralarını değiştirir.

## İmza anahtarı (bir kez)

Anahtar yoksa APK her derlemede farklı bir debug anahtarıyla imzalanır ve
güncelleme kurulurken önce eski sürümü kaldırmak gerekir. Mac'te bir kez:

```sh
SIFRE=$(openssl rand -base64 24)
keytool -genkeypair -keystore evtv.jks -storepass "$SIFRE" -keypass "$SIFRE" \
  -alias evtv -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Ev TV"
base64 -i evtv.jks | gh secret set EVTV_IMZA_ANAHTARI -R aliemrevezir/ev-tv
printf '%s' "$SIFRE" | gh secret set EVTV_IMZA_SIFRESI -R aliemrevezir/ev-tv
gh workflow run APK -R aliemrevezir/ev-tv
```

`evtv.jks`'i ve şifreyi parola yöneticisinde sakla (depoya koyma).

## APK'yı indirmek

```sh
gh run download -R aliemrevezir/ev-tv -n ev-tv-<çalışma no>   # en son: gh run list -w APK
```

Üç APK çıkar: `tv-armeabi-v7a-release.apk`, `tv-arm64-v8a-release.apk`,
`tv-x86_64-release.apk` (Intel Mac emülatörü).

## Mac'te emülatör testi

```sh
brew install --cask temurin@17 android-commandlinetools
sdkmanager "platform-tools" "emulator" "system-images;android-34;android-tv;arm64-v8a"
avdmanager create avd -n tv -k "system-images;android-34;android-tv;arm64-v8a" -d tv_1080p
emulator -avd tv &
adb install -r tv-arm64-v8a-release.apk      # Intel Mac: x86_64 imajı + x86_64 APK
adb logcat -s EvTV
```

Kontrol listesi: açılışta TRT 1 oynuyor · Yukarı/Aşağı kanal değiştiriyor ve
sol üstte numara/ad 3 sn görünüyor · OK liste panelini açıyor, panelden kanal
seçiliyor · Geri paneli kapatıyor, panel kapalıyken iki kez Geri çıkıyor ·
uygulama kapatılıp açılınca son kanal açılıyor · internet kesilip açılınca
saklanan liste kullanılıyor. Emülatör Amlogic çözücüyü taklit edemez; donma
testi yalnızca stick'te yapılabilir.

## Stick'e kurulum (TV boşken)

```sh
adb connect 192.168.1.101
adb shell getprop ro.product.cpu.abilist     # hangi APK'nın kurulacağını söyler
adb install -r tv-armeabi-v7a-release.apk    # ya da arm64-v8a
adb logcat -s EvTV                           # "Kare sayısı", "Donma", "İkinci donma" satırları
```

`Kare sayısı` 30 sn'de bir yazılır; oynarken artmıyorsa donma bekçisi bu
cihazda kare sayısını okuyamıyor demektir (bildir).

## Sıcaklık göstergesi

Ev TV, ev ağında `_evtvsicaklik._tcp` adıyla duyurulan bir yayını mDNS ile
bulur, dakikada bir `GET /` ile `{"sicaklik": 30.4, "kaynak": "..."}` okur ve
sağ üst köşede gösterir; yayın yoksa gösterge gizlenir. Şimdilik kaynak Mac'in
batarya sıcaklığı (`mac/sicaklik_yayini.py`, oda sıcaklığının birkaç derece
üstü). Mac açılınca otomatik başlatmak için `mac/com.aliemrevezir.evtv.sicaklik.plist`
içindeki kurulum adımları. İleride ESP32 + DHT22 aynı adla ve aynı JSON'la
yayın yapınca uygulamada değişiklik gerekmez.
