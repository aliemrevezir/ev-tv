package com.aliemrevezir.evtv

import android.util.Log
import com.aliemrevezir.evtv.cekirdek.ListeKirpici
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * VLC ile kaynak arasında duran 127.0.0.1 sunucusu:
 * - .m3u8 listelerini kırpar (bkz. [ListeKirpici]),
 * - HTTPS segmentlerini kendi TLS'iyle indirip VLC'ye akıtır. VLC'nin TLS'i
 *   stick'in eski sertifika deposuna bağlı; burada [tls] eksik kökleri de
 *   tanır (bkz. [EkKokler]).
 */
class YerelVekil(private val userAgent: String, tlsUret: () -> SSLSocketFactory) {

    // Sistem kök deposunu yüklemek zaman alıyor; ilk istekte, vekil iş parçacığında.
    private val tls by lazy(tlsUret)

    private val sunucu = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    // Liste yenilemesi ile segment indirmeleri aynı anda sürer; VLC'nin açtığı
    // bağlantı sayısı kadar iş parçacığı.
    private val isciler = Executors.newCachedThreadPool()

    init {
        Thread({ dinle() }, "YerelVekil").apply { isDaemon = true }.start()
    }

    /** HLS listesiyse vekil adresini, değilse adresin kendisini verir. */
    fun adres(kaynak: String): String {
        val yol = runCatching { URL(kaynak).path }.getOrNull() ?: return kaynak
        if (!yol.endsWith(".m3u8")) return kaynak
        return "http://127.0.0.1:${sunucu.localPort}/liste.m3u8?u=" + kodla(kaynak)
    }

    /** HTTPS segmentleri vekilden geçer; düz HTTP olduğu gibi kalır. */
    private fun parcaAdresi(kaynak: String): String {
        val url = runCatching { URL(kaynak) }.getOrNull() ?: return kaynak
        if (url.protocol != "https") return kaynak
        // Dosya adı korunur; VLC biçimi tahmin ederken uzantıya da bakıyor.
        val ad = kodla(url.path.substringAfterLast('/').ifEmpty { "parca" })
        return "http://127.0.0.1:${sunucu.localPort}/parca/$ad?u=" + kodla(kaynak)
    }

    fun kapat() {
        sunucu.close()
        isciler.shutdownNow()
    }

    private fun dinle() {
        while (!sunucu.isClosed) {
            val soket = try {
                sunucu.accept()
            } catch (e: IOException) {
                return
            }
            isciler.execute {
                // Kanal değişince VLC bağlantıyı yarıda kapatır; yanıt yazılamaz, sorun değil.
                try {
                    soket.use { yanitla(it) }
                } catch (e: IOException) {
                    Log.d(ETIKET, "Vekil: bağlantı kapandı: $e")
                }
            }
        }
    }

    private fun yanitla(soket: Socket) {
        soket.soTimeout = ZAMAN_ASIMI_MS
        val okuyucu = soket.getInputStream().bufferedReader()
        val yol = okuyucu.readLine()?.split(' ')?.getOrNull(1) ?: return
        val basliklar = generateSequence { okuyucu.readLine() }.takeWhile { it.isNotEmpty() }
        val aralik = basliklar.firstOrNull { it.startsWith("Range:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        val kaynak = URLDecoder.decode(yol.substringAfter("?u=", ""), "UTF-8")
        val cikis = soket.getOutputStream()
        if (yol.startsWith("/parca/")) parca(kaynak, aralik, cikis) else liste(kaynak, cikis)
    }

    private fun liste(kaynak: String, cikis: OutputStream) {
        val (kod, govde) = try {
            "200 OK" to listeIndir(kaynak).toByteArray()
        } catch (e: Exception) {
            Log.w(ETIKET, "Vekil: $kaynak alınamadı: $e")
            "502 Bad Gateway" to ByteArray(0)
        }
        cikis.write(
            ("HTTP/1.1 $kod\r\nContent-Type: application/vnd.apple.mpegurl\r\n" +
                "Content-Length: ${govde.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n")
                .toByteArray(),
        )
        cikis.write(govde)
        cikis.flush()
    }

    private fun listeIndir(kaynak: String): String {
        val baglanti = ac(kaynak)
        try {
            val kod = baglanti.responseCode
            if (kod != HttpURLConnection.HTTP_OK) throw IOException("HTTP $kod")
            val metin = baglanti.inputStream.bufferedReader().use { it.readText() }
            // Yönlendirme olduysa göreli adresler son adrese göre çözülür.
            return ListeKirpici.kirp(metin, baglanti.url.toString(), TUTULAN_SEGMENT, ::adres, ::parcaAdresi)
        } finally {
            baglanti.disconnect()
        }
    }

    /** Segmenti indirirken VLC'ye akıtır; kaynağın durum kodu ve aralık bilgisi aynen iletilir. */
    private fun parca(kaynak: String, aralik: String?, cikis: OutputStream) {
        val baglanti = try {
            ac(kaynak).apply {
                if (aralik != null) setRequestProperty("Range", aralik)
                // Sıkıştırma açılırsa Content-Length akıtılan veriyle tutmaz.
                setRequestProperty("Accept-Encoding", "identity")
                responseCode
            }
        } catch (e: IOException) {
            Log.w(ETIKET, "Vekil: segment $kaynak alınamadı: $e")
            cikis.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        try {
            val kod = baglanti.responseCode
            val baslik = StringBuilder("HTTP/1.1 $kod ${baglanti.responseMessage.orEmpty()}\r\n")
            for (ad in listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges")) {
                baglanti.getHeaderField(ad)?.let { baslik.append("$ad: $it\r\n") }
            }
            baslik.append("Connection: close\r\n\r\n")
            cikis.write(baslik.toString().toByteArray())
            val giris = if (kod < 400) baglanti.inputStream else baglanti.errorStream
            giris?.use { it.copyTo(cikis, TAMPON) }
            cikis.flush()
        } finally {
            baglanti.disconnect()
        }
    }

    private fun ac(kaynak: String): HttpURLConnection {
        val baglanti = URL(kaynak).openConnection() as HttpURLConnection
        if (baglanti is HttpsURLConnection) baglanti.sslSocketFactory = tls
        baglanti.connectTimeout = ZAMAN_ASIMI_MS
        baglanti.readTimeout = ZAMAN_ASIMI_MS
        baglanti.useCaches = false
        baglanti.setRequestProperty("User-Agent", userAgent)
        return baglanti
    }

    private fun kodla(metin: String) = URLEncoder.encode(metin, "UTF-8")

    private companion object {
        const val ETIKET = "EvTV"
        const val ZAMAN_ASIMI_MS = 10_000
        const val TAMPON = 64 * 1024
        // 6 sn'lik segmentlerle ~1 dk; 3 sn'lik önbelleğe fazlasıyla yeter.
        const val TUTULAN_SEGMENT = 10
    }
}
