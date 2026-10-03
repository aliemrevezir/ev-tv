package com.aliemrevezir.evtv.cekirdek

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Ev ağındaki sıcaklık yayınının (`_evtvsicaklik._tcp`, `GET /`) bir okuması.
 * Şimdilik Mac'in batarya sıcaklığı; ileride aynı biçimde yayın yapan sensör.
 */
@Serializable
data class Sicaklik(val sicaklik: Double, val kaynak: String? = null) {

    /** Ekranda gösterilecek metin, ör. "Mac bataryası  30,5 °C". */
    fun metin(): String {
        val deger = String.format(Locale.forLanguageTag("tr"), "%.1f °C", sicaklik)
        return if (kaynak.isNullOrBlank()) deger else "$kaynak  $deger"
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Bozuk ya da hata içeren yanıtta null. */
        fun ayristir(metin: String): Sicaklik? = try {
            json.decodeFromString<Sicaklik>(metin)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
