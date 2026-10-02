package com.aliemrevezir.evtv.cekirdek

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * liste.json'u indirir ve cihazda saklar. Yalnızca ayrıştırılabilen bir liste
 * saklanır; indirme veya ayrıştırma başarısızsa saklanan liste olduğu gibi kalır.
 * Ağ işi yapar, ana iş parçacığında çağrılmamalı.
 */
class KanalListesi(
    private val adres: String,
    private val dosya: File,
    private val zamanAsimiMs: Int = 10_000,
) {

    fun saklanan(): List<Kanal>? {
        if (!dosya.exists()) return null
        return try {
            ListeAyristirici.ayristir(dosya.readText())
        } catch (e: ListeHatasi) {
            null
        }
    }

    /** @throws IOException ağ hatasında, [ListeHatasi] bozuk listede */
    fun indirVeSakla(): List<Kanal> {
        val metin = indir()
        val kanallar = ListeAyristirici.ayristir(metin)
        val gecici = File(dosya.parentFile, dosya.name + ".yeni")
        gecici.writeText(metin)
        if (!gecici.renameTo(dosya)) {
            gecici.delete()
            throw IOException("liste kaydedilemedi")
        }
        return kanallar
    }

    private fun indir(): String {
        val baglanti = URL(adres).openConnection() as HttpURLConnection
        try {
            baglanti.connectTimeout = zamanAsimiMs
            baglanti.readTimeout = zamanAsimiMs
            baglanti.useCaches = false
            val kod = baglanti.responseCode
            if (kod != HttpURLConnection.HTTP_OK) throw IOException("HTTP $kod")
            return baglanti.inputStream.bufferedReader().use { it.readText() }
        } finally {
            baglanti.disconnect()
        }
    }
}
