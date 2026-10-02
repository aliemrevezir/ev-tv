package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeAyristiriciTest {

    @Test
    fun `liste json kanallara ayrilir ve numaraya gore siralanir`() {
        val json = """
            {
              "surum": 1,
              "uretildi": "2026-10-03T00:00:00Z",
              "kanallar": [
                {"no": 3, "ad": "Kanal D", "adres": "https://d/720.m3u8", "logo": null, "durum": "ok"},
                {"no": 1, "ad": "TRT 1", "adres": "https://t/720.m3u8", "logo": "https://logo", "durum": "dogrulanamadi"}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(
                Kanal(1, "TRT 1", "https://t/720.m3u8", "https://logo"),
                Kanal(3, "Kanal D", "https://d/720.m3u8", null),
            ),
            ListeAyristirici.ayristir(json),
        )
    }

    @Test
    fun `bilinmeyen alanlar ve eksik logo sorun cikarmaz`() {
        val json = """{"surum": 2, "yeni": true, "kanallar": [{"no": 1, "ad": "A", "adres": "https://a", "baska": 5}]}"""
        assertEquals(listOf(Kanal(1, "A", "https://a", null)), ListeAyristirici.ayristir(json))
    }

    @Test(expected = ListeHatasi::class)
    fun `bozuk json hata verir`() {
        ListeAyristirici.ayristir("{bozuk")
    }

    @Test(expected = ListeHatasi::class)
    fun `bos kanal listesi hata verir`() {
        ListeAyristirici.ayristir("""{"surum": 1, "kanallar": []}""")
    }
}
