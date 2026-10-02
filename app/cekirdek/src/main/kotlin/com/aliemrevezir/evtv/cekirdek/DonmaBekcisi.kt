package com.aliemrevezir.evtv.cekirdek

/**
 * Görüntü donmasını yakalar: oynatma sürerken [esikMs] boyunca gösterilen kare
 * sayısı artmazsa donma sayılır. Aynı kanalda [pencereMs] içinde ikinci donmada
 * o kanal, nesne yaşadığı sürece yazılım (işlemci) çözücüye geçirilir.
 *
 * İlk kare gelmeden (kare sayısı 0) donma sayılmaz; açılmayan yayını
 * [AcilisBekcisi] yönetir. Saat dışarıdan verilir; her saniye [ornek] çağrılır.
 */
class DonmaBekcisi(
    private val esikMs: Long = 5_000,
    private val pencereMs: Long = 10 * 60_000,
) {

    enum class Karar { Devam, YenidenBaslat, YazilimCozucuyeGec }

    private var kanalNo = -1
    private var sonKare = 0
    private var sonIlerleme: Long? = null
    private val donmalar = mutableMapOf<Int, MutableList<Long>>()
    private val yazilimCozuculu = mutableSetOf<Int>()

    /** Kanal açıldığında ya da yeniden başlatıldığında çağrılır. */
    fun kanalBasladi(no: Int) {
        kanalNo = no
        sonIlerleme = null
    }

    fun donanimCozucu(no: Int): Boolean = no !in yazilimCozuculu

    fun ornek(zamanMs: Long, kareSayisi: Int, oynuyor: Boolean): Karar {
        val ilerleme = sonIlerleme
        if (!oynuyor || kareSayisi == 0) {
            sonIlerleme = null
            return Karar.Devam
        }
        if (ilerleme == null || kareSayisi != sonKare) {
            sonKare = kareSayisi
            sonIlerleme = zamanMs
            return Karar.Devam
        }
        if (zamanMs - ilerleme < esikMs) return Karar.Devam

        sonIlerleme = null
        val gecmis = donmalar.getOrPut(kanalNo) { mutableListOf() }
        gecmis.removeAll { zamanMs - it > pencereMs }
        gecmis.add(zamanMs)
        if (gecmis.size >= 2 && kanalNo !in yazilimCozuculu) {
            yazilimCozuculu.add(kanalNo)
            return Karar.YazilimCozucuyeGec
        }
        return Karar.YenidenBaslat
    }
}
