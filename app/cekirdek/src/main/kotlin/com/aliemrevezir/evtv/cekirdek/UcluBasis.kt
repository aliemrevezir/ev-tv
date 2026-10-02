package com.aliemrevezir.evtv.cekirdek

/**
 * [sureMs] içinde üç kez basılınca tetiklenir; Home tuşuyla eski ana ekrana
 * kaçış için. Saat dışarıdan verilir.
 */
class UcluBasis(private val sureMs: Long = 2_000) {

    private val basislar = ArrayDeque<Long>()

    /** Bu basışla birlikte süre içinde üç basış olduysa true döner ve sayımı sıfırlar. */
    fun basildi(zamanMs: Long): Boolean {
        basislar.addLast(zamanMs)
        while (zamanMs - basislar.first() > sureMs) basislar.removeFirst()
        if (basislar.size < 3) return false
        basislar.clear()
        return true
    }
}
