package com.aliemrevezir.evtv.cekirdek

data class Kanal(
    val no: Int,
    val ad: String,
    val adres: String,
    val logo: String?,
    /** Paneldeki başlık; aynı grubun kanalları listede art arda gelir. */
    val grup: String? = null,
)
