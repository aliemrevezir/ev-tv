package com.aliemrevezir.evtv.cekirdek

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ListeHatasi(mesaj: String, sebep: Throwable? = null) : Exception(mesaj, sebep)

/** liste.json → numaraya göre sıralı kanal listesi. */
object ListeAyristirici {

    @Serializable
    private data class Liste(val kanallar: List<KanalJson>)

    @Serializable
    private data class KanalJson(
        val no: Int,
        val ad: String,
        val adres: String,
        val logo: String? = null,
        val grup: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun ayristir(metin: String): List<Kanal> {
        val liste = try {
            json.decodeFromString<Liste>(metin)
        } catch (e: SerializationException) {
            throw ListeHatasi("liste.json okunamadı", e)
        } catch (e: IllegalArgumentException) {
            throw ListeHatasi("liste.json okunamadı", e)
        }
        if (liste.kanallar.isEmpty()) throw ListeHatasi("liste.json'da kanal yok")
        return liste.kanallar
            .map { Kanal(it.no, it.ad, it.adres, it.logo, it.grup) }
            .sortedBy { it.no }
    }
}
