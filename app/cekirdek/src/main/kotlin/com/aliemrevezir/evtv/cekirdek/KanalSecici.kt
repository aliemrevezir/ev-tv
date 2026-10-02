package com.aliemrevezir.evtv.cekirdek

interface SonKanalSaklayici {
    fun oku(): Int?
    fun yaz(no: Int)
}

/** Geçerli kanalı tutar; sonraki/önceki kanal listenin sonunda başa döner. */
class KanalSecici(kanallar: List<Kanal>, private val saklayici: SonKanalSaklayici) {

    var kanallar: List<Kanal> = kanallar.sortedBy { it.no }
        private set

    private var indeks: Int

    init {
        require(kanallar.isNotEmpty()) { "kanal listesi boş" }
        indeks = this.kanallar.indexOfFirst { it.no == saklayici.oku() }.coerceAtLeast(0)
    }

    val gecerli: Kanal get() = kanallar[indeks]

    fun sonraki(): Kanal = git((indeks + 1) % kanallar.size)

    fun onceki(): Kanal = git((indeks - 1 + kanallar.size) % kanallar.size)

    fun sec(no: Int): Kanal? {
        val i = kanallar.indexOfFirst { it.no == no }
        return if (i < 0) null else git(i)
    }

    /**
     * Yeni listeye geçer. Geçerli kanal yeni listede yoksa ilk kanala geçer.
     * @return oynatılan yayının değişip değişmediği (adres ya da kanal)
     */
    fun listeyiGuncelle(yeni: List<Kanal>): Boolean {
        require(yeni.isNotEmpty()) { "kanal listesi boş" }
        val onceki = gecerli
        kanallar = yeni.sortedBy { it.no }
        indeks = kanallar.indexOfFirst { it.no == onceki.no }.coerceAtLeast(0)
        return gecerli != onceki
    }

    private fun git(i: Int): Kanal {
        indeks = i
        saklayici.yaz(gecerli.no)
        return gecerli
    }
}
