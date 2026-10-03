package com.aliemrevezir.evtv.cekirdek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CNN TÜRK'ün yayın adresi ~3 saatlik imza (st, e) taşıyor; gece üretilen
 * listede bayatlar. liste.json'a imzasız [TABAN] yazılır, vekil onu görünce
 * cnnturk.com'un oynatıcısının kullandığı [API]'den taze adresi alır.
 */
object CnnTurk {
    const val TABAN = "https://live.duhnet.tv/S2/HLS_LIVE/cnnturknp/playlist.m3u8"
    const val API = "https://www.cnnturk.com/api/cnnvideo/media?id=62d6814670380e2cdc7c124c&isMobile=false"

    private val json = Json { ignoreUnknownKeys = true }

    fun tabanMi(adres: String): Boolean = adres.substringBefore('?') == TABAN

    /** API yanıtındaki imzalı adres; yanıt beklenen biçimde değilse null. */
    fun adresCikar(yanit: String): String? = runCatching {
        val baglanti = json.parseToJsonElement(yanit).jsonObject["Media"]!!.jsonObject["Link"]!!.jsonObject
        val sunucu = (baglanti["ServiceUrl"] ?: baglanti["DefaultServiceUrl"])!!.jsonPrimitive.content
        val yol = baglanti["SecurePath"]!!.jsonPrimitive.content
        // Sunucu "https://live.duhnet.tv", yol "/S2/..."; bazen arada çift eğik çizgi oluyor.
        sunucu.trimEnd('/') + "/" + yol.trimStart('/')
    }.getOrNull()?.takeIf { it.startsWith("https://") && it.contains(".m3u8") }

    /** İmzanın bittiği an (Unix saniyesi, 'e' parametresi); yoksa null. */
    fun bitis(adres: String): Long? =
        adres.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("e=") }
            ?.substringAfter("e=")?.toLongOrNull()
}
