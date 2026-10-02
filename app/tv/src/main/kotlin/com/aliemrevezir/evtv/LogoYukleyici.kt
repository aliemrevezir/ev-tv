package com.aliemrevezir.evtv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Kanal logolarını küçültülmüş olarak indirir ve bellekte tutar. */
class LogoYukleyici(private val hedefYukseklikPx: Int) {

    private val onbellek = LruCache<String, Bitmap>(30)
    private val isci = Executors.newSingleThreadExecutor()
    private val anaIs = Handler(Looper.getMainLooper())

    fun yukle(adres: String?, hedef: ImageView) {
        hedef.tag = adres
        hedef.setImageDrawable(null)
        hedef.visibility = if (adres == null) View.GONE else View.VISIBLE
        if (adres == null) return
        onbellek.get(adres)?.let {
            hedef.setImageBitmap(it)
            return
        }
        isci.execute {
            val resim = runCatching { indir(adres) }.getOrNull() ?: return@execute
            anaIs.post {
                onbellek.put(adres, resim)
                if (hedef.tag == adres) hedef.setImageBitmap(resim)
            }
        }
    }

    fun kapat() = isci.shutdownNow()

    private fun indir(adres: String): Bitmap? {
        val baglanti = URL(adres).openConnection() as HttpURLConnection
        val veri = try {
            baglanti.connectTimeout = 10_000
            baglanti.readTimeout = 10_000
            if (baglanti.responseCode != HttpURLConnection.HTTP_OK) return null
            baglanti.inputStream.use { it.readBytes() }
        } finally {
            baglanti.disconnect()
        }
        val boyut = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(veri, 0, veri.size, boyut)
        var oran = 1
        while (boyut.outHeight / (oran * 2) >= hedefYukseklikPx) oran *= 2
        val secenek = BitmapFactory.Options().apply { inSampleSize = oran }
        return BitmapFactory.decodeByteArray(veri, 0, veri.size, secenek)
    }
}
