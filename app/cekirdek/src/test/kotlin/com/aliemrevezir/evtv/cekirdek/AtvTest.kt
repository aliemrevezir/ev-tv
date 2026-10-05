package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtvTest {

    @Test
    fun `api adresi tabani kodlanmadan tasir`() {
        assertEquals(
            "https://securevideotoken.tmgrup.com.tr/webtv/secure?3&url=" + Atv.TABAN,
            Atv.api,
        )
        assertEquals("https://www.atv.com.tr/", Atv.referer)
    }

    @Test
    fun `api yanitindan imzali adres cikar`() {
        val yanit = """{"Success":true,"Url":"https://trkvz-live.ercdn.net/atvhd/atvhd_720p.m3u8?st=x3P&e=1791270825","AlternateUrl":"","Time":41600}"""
        assertEquals("https://trkvz-live.ercdn.net/atvhd/atvhd_720p.m3u8?st=x3P&e=1791270825", Atv.adresCikar(yanit))
        assertEquals(1791270825L, imzaBitisi(Atv.adresCikar(yanit)!!))
    }

    @Test
    fun `basarisiz yanit null verir`() {
        assertNull(Atv.adresCikar("""{"status":false,"message":"1 01 01 1970"}"""))
        assertNull(Atv.adresCikar("""{"Success":false,"Url":"https://x/a.m3u8"}"""))
        assertNull(Atv.adresCikar("bozuk"))
    }

    @Test
    fun `taban adres taninir`() {
        assertTrue(Atv.tabanMi(Atv.TABAN))
        assertTrue(Atv.tabanMi(Atv.TABAN + "?st=a&e=1"))
        assertFalse(Atv.tabanMi(CnnTurk.TABAN))
        assertFalse(CnnTurk.tabanMi(Atv.TABAN))
    }
}
