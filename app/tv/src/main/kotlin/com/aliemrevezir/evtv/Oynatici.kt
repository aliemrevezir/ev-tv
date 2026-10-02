package com.aliemrevezir.evtv

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** libVLC sarmalayıcı. Olaylar ana iş parçacığında gelir. */
class Oynatici(context: Context, goruntu: VLCVideoLayout, private val dinleyici: Dinleyici) {

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
    private val vekil = YerelVekil(USER_AGENT)

    private val oynatici = MediaPlayer(libVlc).apply {
        attachViews(goruntu, null, false, false)
        setEventListener { olay ->
            when (olay.type) {
                MediaPlayer.Event.Vout -> if (olay.voutCount > 0) dinleyici.goruntuGeldi()
                MediaPlayer.Event.EncounteredError,
                MediaPlayer.Event.EndReached -> dinleyici.hataOldu()
            }
        }
    }

    /** @param donanimCozucu false ise görüntü işlemcide çözülür (MediaCodec kapalı). */
    fun oynat(adres: String, donanimCozucu: Boolean) {
        val medya = Media(libVlc, Uri.parse(vekil.adres(adres)))
        medya.setHWDecoderEnabled(donanimCozucu, false)
        medya.addOption(":http-user-agent=$USER_AGENT")
        medya.addOption(":network-caching=$ONBELLEK_MS")
        oynatici.media = medya
        medya.release()
        oynatici.play()
    }

    fun oynuyor(): Boolean = oynatici.isPlaying

    /** O ana kadar gösterilen kare sayısı; medya değişince sıfırdan başlar. */
    fun kareSayisi(): Int {
        val medya = oynatici.media ?: return 0
        return try {
            medya.stats?.displayedPictures ?: 0
        } finally {
            medya.release()
        }
    }

    fun durdur() = oynatici.stop()

    fun birak() {
        oynatici.setEventListener(null)
        oynatici.stop()
        oynatici.detachViews()
        oynatici.release()
        libVlc.release()
        vekil.kapat()
    }

    private companion object {
        const val ONBELLEK_MS = 3000
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }
}
