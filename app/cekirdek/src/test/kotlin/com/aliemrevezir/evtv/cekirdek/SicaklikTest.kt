package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SicaklikTest {

    @Test
    fun `yayin json'u ayristirilir ve turkce bicimlenir`() {
        val s = Sicaklik.ayristir("""{"sicaklik": 30.47, "kaynak": "Mac bataryası"}""")
        assertEquals(Sicaklik(30.47, "Mac bataryası"), s)
        assertEquals("Mac bataryası  30,5 °C", s!!.metin())
    }

    @Test
    fun `kaynak yoksa yalnizca deger yazilir, bilinmeyen alanlar sorun degil`() {
        val s = Sicaklik.ayristir("""{"sicaklik": 22, "nem": 41, "yeni": true}""")
        assertEquals("22,0 °C", s!!.metin())
    }

    @Test
    fun `bozuk ya da hatali yanit null doner`() {
        assertNull(Sicaklik.ayristir("{bozuk"))
        assertNull(Sicaklik.ayristir("""{"hata": "okunamadı"}"""))
    }
}
