package com.aliemrevezir.evtv

import android.util.Log
import com.aliemrevezir.evtv.cekirdek.ListeKirpici
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * VLC'ye çalma listelerini kırpılmış olarak veren 127.0.0.1 sunucusu
 * (bkz. [ListeKirpici]). Yalnızca .m3u8 listeleri buradan geçer; segmentleri
 * VLC kaynaktan kendisi indirir.
 */
class YerelVekil(private val userAgent: String) {

    private val sunucu = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val isciler = Executors.newFixedThreadPool(2)

    init {
        Thread({ dinle() }, "YerelVekil").apply { isDaemon = true }.start()
    }

    /** HLS listesiyse vekil adresini, değilse adresin kendisini verir. */
    fun adres(kaynak: String): String {
        val yol = runCatching { URL(kaynak).path }.getOrNull() ?: return kaynak
        if (!yol.endsWith(".m3u8")) return kaynak
        return "http://127.0.0.1:${sunucu.localPort}/liste.m3u8?u=" + URLEncoder.encode(kaynak, "UTF-8")
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
        val istek = soket.getInputStream().bufferedReader().readLine() ?: return
        val cikis = soket.getOutputStream()
        val kaynak = istek.split(' ').getOrNull(1)
            ?.substringAfter("?u=", "")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            .orEmpty()
        val (kod, govde) = try {
            "200 OK" to liste(kaynak).toByteArray()
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

    private fun liste(kaynak: String): String {
        val baglanti = URL(kaynak).openConnection() as HttpURLConnection
        try {
            baglanti.connectTimeout = ZAMAN_ASIMI_MS
            baglanti.readTimeout = ZAMAN_ASIMI_MS
            baglanti.useCaches = false
            baglanti.setRequestProperty("User-Agent", userAgent)
            val kod = baglanti.responseCode
            if (kod != HttpURLConnection.HTTP_OK) throw IOException("HTTP $kod")
            val metin = baglanti.inputStream.bufferedReader().use { it.readText() }
            // Yönlendirme olduysa göreli adresler son adrese göre çözülür.
            return ListeKirpici.kirp(metin, baglanti.url.toString(), TUTULAN_SEGMENT, ::adres)
        } finally {
            baglanti.disconnect()
        }
    }

    private companion object {
        const val ETIKET = "EvTV"
        const val ZAMAN_ASIMI_MS = 10_000
        // 6 sn'lik segmentlerle ~1 dk; 3 sn'lik önbelleğe fazlasıyla yeter.
        const val TUTULAN_SEGMENT = 10
    }
}
