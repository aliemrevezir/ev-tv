package com.aliemrevezir.evtv.cekirdek

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress

class KanalListesiTest {

    @get:Rule val klasor = TemporaryFolder()

    private lateinit var sunucu: HttpServer
    private var yanitKodu = 200
    private var yanitGovdesi = ""

    private val gecerliJson = """{"surum":1,"kanallar":[{"no":1,"ad":"TRT 1","adres":"https://t","logo":null}]}"""
    private val ikinciJson = """{"surum":1,"kanallar":[{"no":2,"ad":"ATV","adres":"https://a","logo":null}]}"""

    @Before
    fun basla() {
        sunucu = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        sunucu.createContext("/liste.json") { istek ->
            val govde = yanitGovdesi.toByteArray()
            istek.sendResponseHeaders(yanitKodu, if (govde.isEmpty()) -1 else govde.size.toLong())
            istek.responseBody.use { it.write(govde) }
        }
        sunucu.start()
    }

    @After
    fun bitir() = sunucu.stop(0)

    private fun liste(dosya: File = File(klasor.root, "liste.json")) =
        KanalListesi("http://127.0.0.1:${sunucu.address.port}/liste.json", dosya)

    @Test
    fun `indirilen liste saklanir`() {
        yanitGovdesi = gecerliJson
        val l = liste()
        assertEquals("TRT 1", l.indirVeSakla().single().ad)
        assertEquals("TRT 1", l.saklanan()!!.single().ad)
    }

    @Test
    fun `hic saklanan yoksa null`() {
        assertNull(liste().saklanan())
    }

    @Test
    fun `sunucu hatasinda saklanan liste korunur`() {
        yanitGovdesi = gecerliJson
        val l = liste()
        l.indirVeSakla()
        yanitKodu = 500
        yanitGovdesi = ""
        runCatching { l.indirVeSakla() }.also { assert(it.isFailure) }
        assertEquals("TRT 1", l.saklanan()!!.single().ad)
    }

    @Test
    fun `bozuk liste saklananin ustune yazilmaz`() {
        yanitGovdesi = gecerliJson
        val l = liste()
        l.indirVeSakla()
        yanitGovdesi = "{bozuk"
        runCatching { l.indirVeSakla() }.also { assert(it.exceptionOrNull() is ListeHatasi) }
        assertEquals("TRT 1", l.saklanan()!!.single().ad)
    }

    @Test
    fun `yeni liste eskisinin yerine gecer`() {
        yanitGovdesi = gecerliJson
        val l = liste()
        l.indirVeSakla()
        yanitGovdesi = ikinciJson
        l.indirVeSakla()
        assertEquals("ATV", l.saklanan()!!.single().ad)
    }

    @Test
    fun `saklanan dosya bozuksa null`() {
        val dosya = File(klasor.root, "liste.json").apply { writeText("{bozuk") }
        assertNull(liste(dosya).saklanan())
    }
}
