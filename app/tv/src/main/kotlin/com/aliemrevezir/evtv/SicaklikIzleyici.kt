package com.aliemrevezir.evtv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aliemrevezir.evtv.cekirdek.Sicaklik
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Ev ağındaki sıcaklık yayınını (`_evtvsicaklik._tcp`) mDNS ile bulur ve
 * [ARALIK_SN] saniyede bir okur. Sonuç ana iş parçacığında [dinleyici]'ye
 * gelir; yayın yoksa ya da üst üste okunamıyorsa null gelir (gösterge gizlenir).
 */
class SicaklikIzleyici(context: Context, private val dinleyici: (Sicaklik?) -> Unit) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val anaIs = Handler(Looper.getMainLooper())
    private var isci: ScheduledExecutorService? = null
    private var kesif: NsdManager.DiscoveryListener? = null

    @Volatile private var adres: String? = null
    private var basarisiz = 0

    fun basla() {
        if (isci != null) return
        isci = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({ oku() }, 2, ARALIK_SN, TimeUnit.SECONDS)
        }
        kesif = KesifDinleyici().also {
            nsd.discoverServices(SERVIS_TURU, NsdManager.PROTOCOL_DNS_SD, it)
        }
    }

    fun durdur() {
        kesif?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        kesif = null
        isci?.shutdownNow()
        isci = null
    }

    private fun oku() {
        val hedef = adres
        val sonuc = hedef?.let { runCatching { indir(it) }.getOrNull() }
        if (sonuc != null) {
            basarisiz = 0
        } else if (++basarisiz < KABUL_EDILEN_HATA && hedef != null) {
            return  // tek seferlik aksaklıkta göstergeyi titretme
        }
        anaIs.post { dinleyici(sonuc) }
    }

    private fun indir(hedef: String): Sicaklik? {
        val baglanti = URL(hedef).openConnection() as HttpURLConnection
        try {
            baglanti.connectTimeout = ZAMAN_ASIMI_MS
            baglanti.readTimeout = ZAMAN_ASIMI_MS
            if (baglanti.responseCode != HttpURLConnection.HTTP_OK) return null
            return Sicaklik.ayristir(baglanti.inputStream.bufferedReader().use { it.readText() })
        } finally {
            baglanti.disconnect()
        }
    }

    private inner class KesifDinleyici : NsdManager.DiscoveryListener {
        override fun onServiceFound(bilgi: NsdServiceInfo) {
            nsd.resolveService(bilgi, object : NsdManager.ResolveListener {
                override fun onServiceResolved(cozulen: NsdServiceInfo) {
                    val yeni = "http://${cozulen.host.hostAddress}:${cozulen.port}/"
                    if (yeni != adres) Log.i(ETIKET, "Sıcaklık yayını: $yeni")
                    adres = yeni
                    isci?.execute { oku() }
                }

                override fun onResolveFailed(b: NsdServiceInfo, hata: Int) {
                    Log.w(ETIKET, "Sıcaklık yayını çözülemedi: $hata")
                }
            })
        }

        override fun onServiceLost(bilgi: NsdServiceInfo) {
            Log.i(ETIKET, "Sıcaklık yayını kayboldu")
            adres = null
        }

        override fun onDiscoveryStarted(tur: String) = Unit
        override fun onDiscoveryStopped(tur: String) = Unit
        override fun onStartDiscoveryFailed(tur: String, hata: Int) {
            Log.w(ETIKET, "Sıcaklık keşfi başlamadı: $hata")
        }
        override fun onStopDiscoveryFailed(tur: String, hata: Int) = Unit
    }

    private companion object {
        const val ETIKET = "EvTV"
        const val SERVIS_TURU = "_evtvsicaklik._tcp"
        const val ARALIK_SN = 60L
        const val ZAMAN_ASIMI_MS = 5_000
        const val KABUL_EDILEN_HATA = 3
    }
}
