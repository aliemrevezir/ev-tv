package com.aliemrevezir.evtv.cekirdek

/**
 * Yayın adresi süreli imza (st, e) isteyen kanal. liste.json'a imzasız taban
 * adres yazılır; vekil onu tanıyınca [api]'den taze imzalı adresi alır.
 */
interface ImzaliKaynak {
    val api: String

    /** API isteğinde gönderilecek Referer; gerekmiyorsa null. */
    val referer: String? get() = null

    fun tabanMi(adres: String): Boolean

    /** API yanıtındaki imzalı adres; yanıt beklenen biçimde değilse null. */
    fun adresCikar(yanit: String): String?
}

/** İmzanın bittiği an (Unix saniyesi, 'e' parametresi); yoksa null. */
fun imzaBitisi(adres: String): Long? =
    adres.substringAfter('?', "").split('&')
        .firstOrNull { it.startsWith("e=") }
        ?.substringAfter("e=")?.toLongOrNull()
