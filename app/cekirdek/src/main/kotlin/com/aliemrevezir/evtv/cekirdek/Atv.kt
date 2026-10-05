package com.aliemrevezir.evtv.cekirdek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ATV'nin resmi yayını ~11 saatlik, istemci IP'sine bağlı imza istiyor; gece
 * GitHub'da üretilen imza evde geçmez. atv.com.tr'nin oynatıcısı imzayı
 * tmgrup'un token servisinden alıyor (Referer'sız istek reddediliyor).
 * Taban doğrudan 720p alt listesi (1536x864); ana liste 1080p'ye çıkabiliyor.
 */
object Atv : ImzaliKaynak {
    const val TABAN = "https://trkvz-live.ercdn.net/atvhd/atvhd_720p.m3u8"
    override val api = "https://securevideotoken.tmgrup.com.tr/webtv/secure?3&url=$TABAN"
    override val referer = "https://www.atv.com.tr/"

    private val json = Json { ignoreUnknownKeys = true }

    override fun tabanMi(adres: String): Boolean = adres.substringBefore('?') == TABAN

    override fun adresCikar(yanit: String): String? = runCatching {
        val nesne = json.parseToJsonElement(yanit).jsonObject
        if (nesne["Success"]?.jsonPrimitive?.booleanOrNull != true) return null
        nesne["Url"]!!.jsonPrimitive.content
    }.getOrNull()?.takeIf { it.startsWith("https://") && it.contains(".m3u8") }
}
