package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CnnTurkTest {

    private val yanit = """
        {"Id":"62d6814670380e2cdc7c124c","Url":"/canli-yayin","Media":{"Controller":"oms",
         "Link":{"DefaultServiceUrl":"https://live.duhnet.tv","ServiceUrl":"https://live.duhnet.tv",
         "SecurePath":"/S2/HLS_LIVE/cnnturknp/playlist.m3u8?&live=true&app=com.cnnturk&st=abc&e=1791057244",
         "CheckPath":"/status.json"}}}
    """.trimIndent()

    @Test
    fun `api yanitindan imzali adres cikar`() {
        assertEquals(
            "https://live.duhnet.tv/S2/HLS_LIVE/cnnturknp/playlist.m3u8?&live=true&app=com.cnnturk&st=abc&e=1791057244",
            CnnTurk.adresCikar(yanit),
        )
    }

    @Test
    fun `sunucu sonundaki egik cizgi ikilenmez`() {
        val y = """{"Media":{"Link":{"ServiceUrl":"https://h/","SecurePath":"/a/p.m3u8?e=1"}}}"""
        assertEquals("https://h/a/p.m3u8?e=1", CnnTurk.adresCikar(y))
    }

    @Test
    fun `beklenmeyen yanit null verir`() {
        assertNull(CnnTurk.adresCikar("Media bulunamadı"))
        assertNull(CnnTurk.adresCikar("""{"Media":null}"""))
        assertNull(CnnTurk.adresCikar("""{"Media":{"Link":{"ServiceUrl":"https://h"}}}"""))
    }

    @Test
    fun `bitis e parametresinden okunur`() {
        assertEquals(1791057244L, CnnTurk.bitis(CnnTurk.adresCikar(yanit)!!))
        assertNull(CnnTurk.bitis(CnnTurk.TABAN))
    }

    @Test
    fun `taban adres sorgu parametresinden bagimsiz taninir`() {
        assertTrue(CnnTurk.tabanMi(CnnTurk.TABAN))
        assertTrue(CnnTurk.tabanMi(CnnTurk.TABAN + "?x=1"))
        assertFalse(CnnTurk.tabanMi("https://tv-trt1.medya.trt.com.tr/master_720.m3u8"))
    }
}
