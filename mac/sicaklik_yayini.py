"""Mac'in batarya sıcaklığını ev ağında Ev TV'ye yayınlar.

Gerçek oda sıcaklığı sensörü (ESP32 + DHT22) gelene kadar geçici kaynak.
Batarya, Mac boştayken odanın birkaç derece üstünde seyreder; bu yüzden Ev TV
değeri "Mac bataryası" etiketiyle gösterir.

Ev TV, servisi mDNS'te `_evtvsicaklik._tcp` adıyla bulur ve `GET /` ile
şu JSON'u okur: {"sicaklik": 30.47, "kaynak": "Mac bataryası"}.
ESP32 de aynı adla ve aynı JSON'la yayın yaparsa Ev TV'de değişiklik gerekmez.

Çalıştırma: python3 mac/sicaklik_yayini.py
"""
import json
import re
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8765
SERVIS_TURU = "_evtvsicaklik._tcp"
KAYNAK = "Mac bataryası"


def batarya_sicakligi() -> float:
    """ioreg 'Temperature' değeri santigrat derecenin yüzde biri cinsinden."""
    cikti = subprocess.run(
        ["ioreg", "-rn", "AppleSmartBattery"], capture_output=True, text=True, check=True
    ).stdout
    m = re.search(r'"Temperature" = (\d+)', cikti)
    if not m:
        raise RuntimeError("batarya sıcaklığı okunamadı")
    return int(m.group(1)) / 100


class Yanitlayici(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            govde = json.dumps(
                {"sicaklik": round(batarya_sicakligi(), 1), "kaynak": KAYNAK}, ensure_ascii=False
            ).encode()
            self.send_response(200)
        except Exception as e:
            govde = json.dumps({"hata": str(e)}, ensure_ascii=False).encode()
            self.send_response(500)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(govde)))
        self.end_headers()
        self.wfile.write(govde)

    def log_message(self, *args):
        pass


def main() -> int:
    batarya_sicakligi()  # okunamıyorsa hemen hata versin
    sunucu = ThreadingHTTPServer(("0.0.0.0", PORT), Yanitlayici)
    # dns-sd açık kaldıkça servis ağda duyurulur.
    duyuru = subprocess.Popen(
        ["dns-sd", "-R", "Ev TV sıcaklık", SERVIS_TURU, "local", str(PORT)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    print(f"Yayında: http://0.0.0.0:{PORT}/ ({SERVIS_TURU})", flush=True)
    try:
        sunucu.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        duyuru.terminate()
        sunucu.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
