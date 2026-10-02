package com.aliemrevezir.evtv

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * libVLC sarmalayıcı. Olaylar ana iş parçacığında gelir; komutlar sırayla
 * arka plandaki tek bir iş parçacığında çalışır, çağıran hiç beklemez.
 */
class Oynatici(context: Context, private val goruntu: VLCVideoLayout, private val dinleyici: Dinleyici) {

    interface Dinleyici {
        fun goruntuGeldi()
        fun hataOldu()
    }

    // Yazı işleyici (freetype) ilk görüntüde sistem fontlarını tarıyor; stick'te bu
    // 20 sn'yi aşıyor ve görüntü hiç gelmiyor. Altyazı kullanmıyoruz, kapalı.
    private val libVlc = LibVLC(
        context,
        arrayListOf("--text-renderer=none", "--http-reconnect", "--network-caching=$ONBELLEK_MS"),
    )
    private val vekil = context.applicationContext.let { uygulama ->
        YerelVekil(USER_AGENT) { EkKokler.soketFabrikasi(uygulama) }
    }

    private val oynatici = MediaPlayer(libVlc).apply {
        setEventListener { olay ->
            // Son komut henüz işlenmediyse olay eski akıştandır.
            if (isleyenNesil != nesil.get()) return@setEventListener
            when (olay.type) {
                MediaPlayer.Event.Vout -> if (olay.voutCount > 0) dinleyici.goruntuGeldi()
                MediaPlayer.Event.EncounteredError,
                MediaPlayer.Event.EndReached -> dinleyici.hataOldu()
            }
        }
    }

    // setMedia/stop eski akışın kapanmasını bekler; akış takılınca dakikalarca
    // sürebiliyor. Ana iş parçacığı kilitlenmesin diye tüm VLC çağrıları burada.
    private val isci = Executors.newSingleThreadExecutor { Thread(it, "Oynatici") }

    // Her oynat/durdur bir sonraki nesli başlatır; sırada bekleyen eski oynatmalar
    // atlanır, eski medyadan gelen örnekler yok sayılır.
    private val nesil = AtomicInteger()
    @Volatile private var isleyenNesil = 0
    private val ornekSirada = AtomicBoolean(false)
    @Volatile private var sonKare = 0
    @Volatile private var sonOynuyor = false

    /** @param donanimCozucu false ise görüntü işlemcide çözülür (MediaCodec kapalı). */
    fun oynat(adres: String, donanimCozucu: Boolean) {
        gorunumuBagla()
        val n = yeniNesil()
        isci.execute {
            if (n != nesil.get()) return@execute
            val medya = Media(libVlc, Uri.parse(vekil.adres(adres)))
            medya.setHWDecoderEnabled(donanimCozucu, false)
            medya.addOption(":http-user-agent=$USER_AGENT")
            medya.addOption(":network-caching=$ONBELLEK_MS")
            oynatici.media = medya
            medya.release()
            isleyenNesil = n
            oynatici.play()
        }
    }

    /**
     * [kareSayisi] ve [oynuyor] değerlerini arka planda tazeler; sonuç bir sonraki
     * çağrıda görünür. Önceki örnek bitmeden yenisi sıraya girmez.
     */
    fun ornekle() {
        if (!ornekSirada.compareAndSet(false, true)) return
        isci.execute {
            val n = nesil.get()
            val kare = kareOku()
            val oynuyor = oynatici.isPlaying
            if (n == nesil.get()) {
                sonKare = kare
                sonOynuyor = oynuyor
            }
            ornekSirada.set(false)
        }
    }

    fun oynuyor(): Boolean = sonOynuyor

    /** O ana kadar gösterilen kare sayısı; medya değişince sıfırdan başlar. */
    fun kareSayisi(): Int = sonKare

    /** Etkinlik görünmez olunca çağrılır; dönüşte [oynat] görünümü yeniden bağlar. */
    fun durdur() {
        yeniNesil()
        gorunumuAyir()
        isci.execute { oynatici.stop() }
    }

    fun birak() {
        yeniNesil()
        oynatici.setEventListener(null)
        gorunumuAyir()
        isci.execute {
            oynatici.stop()
            oynatici.release()
            libVlc.release()
            vekil.kapat()
        }
        isci.shutdown()
    }

    // Yüzey etkinlik durunca yok olur; bağlı kalırsa dönüşte VLC görüntü
    // çıkışı kuramıyor ve ekran siyah kalıyor. Görünümlere dokunduğu için
    // ikisi de ana iş parçacığında çalışır.
    private var bagli = false

    private fun gorunumuBagla() {
        if (bagli) return
        oynatici.attachViews(goruntu, null, false, false)
        bagli = true
    }

    private fun gorunumuAyir() {
        if (!bagli) return
        oynatici.detachViews()
        bagli = false
    }

    private fun yeniNesil(): Int {
        sonKare = 0
        sonOynuyor = false
        return nesil.incrementAndGet()
    }

    private fun kareOku(): Int {
        val medya = oynatici.media ?: return 0
        return try {
            medya.stats?.displayedPictures ?: 0
        } finally {
            medya.release()
        }
    }

    private companion object {
        const val ONBELLEK_MS = 3000
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }
}
