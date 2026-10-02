package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanalSeciciTest {

    private class BellekSaklayici(var no: Int? = null) : SonKanalSaklayici {
        override fun oku() = no
        override fun yaz(no: Int) { this.no = no }
    }

    private fun kanal(no: Int, adres: String = "https://$no") = Kanal(no, "K$no", adres, null)

    private val liste = listOf(kanal(1), kanal(3), kanal(4))

    @Test
    fun `ilk acilista ilk kanal`() {
        assertEquals(1, KanalSecici(liste, BellekSaklayici()).gecerli.no)
    }

    @Test
    fun `son kanal hatirlanir`() {
        assertEquals(3, KanalSecici(liste, BellekSaklayici(3)).gecerli.no)
    }

    @Test
    fun `hatirlanan kanal listede yoksa ilk kanal`() {
        assertEquals(1, KanalSecici(liste, BellekSaklayici(2)).gecerli.no)
    }

    @Test
    fun `sonraki ve onceki bosluklari atlar ve basa doner`() {
        val saklayici = BellekSaklayici()
        val secici = KanalSecici(liste, saklayici)
        assertEquals(3, secici.sonraki().no)
        assertEquals(4, secici.sonraki().no)
        assertEquals(1, secici.sonraki().no)
        assertEquals(4, secici.onceki().no)
        assertEquals(4, saklayici.no)
    }

    @Test
    fun `numaraya gore sec`() {
        val saklayici = BellekSaklayici()
        val secici = KanalSecici(liste, saklayici)
        assertEquals(4, secici.sec(4)?.no)
        assertEquals(4, saklayici.no)
        assertNull(secici.sec(2))
        assertEquals(4, secici.gecerli.no)
    }

    @Test
    fun `liste guncellenince ayni adresse oynatma degismez`() {
        val secici = KanalSecici(liste, BellekSaklayici(3))
        assertFalse(secici.listeyiGuncelle(listOf(kanal(1), kanal(3), kanal(5))))
        assertEquals(3, secici.gecerli.no)
        assertEquals(5, secici.sonraki().no)
    }

    @Test
    fun `liste guncellenince gecerli kanalin adresi degistiyse true`() {
        val secici = KanalSecici(liste, BellekSaklayici(3))
        assertTrue(secici.listeyiGuncelle(listOf(kanal(1), kanal(3, "https://yeni"))))
        assertEquals("https://yeni", secici.gecerli.adres)
    }

    @Test
    fun `liste guncellenince gecerli kanal dustuyse ilk kanala gecer`() {
        val saklayici = BellekSaklayici(3)
        val secici = KanalSecici(liste, saklayici)
        assertTrue(secici.listeyiGuncelle(listOf(kanal(1), kanal(4))))
        assertEquals(1, secici.gecerli.no)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bos liste kabul edilmez`() {
        KanalSecici(emptyList(), BellekSaklayici())
    }
}
