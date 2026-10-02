package com.aliemrevezir.evtv.cekirdek

import com.aliemrevezir.evtv.cekirdek.DonmaBekcisi.Karar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonmaBekcisiTest {

    private val bekci = DonmaBekcisi()

    /** [baslangic, bitis] arasında her saniye aynı kare sayısıyla örnekler; son kararı döner. */
    private fun bekle(baslangic: Long, bitis: Long, kare: Int, oynuyor: Boolean = true): Karar {
        var karar: Karar = Karar.Devam
        var t = baslangic
        while (t <= bitis) {
            karar = bekci.ornek(t, kare, oynuyor)
            if (karar != Karar.Devam) return karar
            t += 1000
        }
        return karar
    }

    @Test
    fun `kare ilerledikce bir sey yapmaz`() {
        bekci.kanalBasladi(1)
        for (s in 0..60) assertEquals(Karar.Devam, bekci.ornek(s * 1000L, s * 25, true))
    }

    @Test
    fun `bes saniye kare artmazsa yeniden baslat`() {
        bekci.kanalBasladi(1)
        bekci.ornek(0, 100, true)
        assertEquals(Karar.Devam, bekle(1000, 4999, 100))
        assertEquals(Karar.YenidenBaslat, bekci.ornek(5000, 100, true))
    }

    @Test
    fun `oynamiyorken donma sayilmaz`() {
        bekci.kanalBasladi(1)
        assertEquals(Karar.Devam, bekle(0, 60_000, 100, oynuyor = false))
    }

    @Test
    fun `oynamaya baslayinca sayac sifirdan baslar`() {
        bekci.kanalBasladi(1)
        bekle(0, 30_000, 0, oynuyor = false)
        assertEquals(Karar.Devam, bekle(31_000, 35_000, 0))
        assertEquals(Karar.YenidenBaslat, bekci.ornek(36_000, 0, true))
    }

    @Test
    fun `kare sayisi sifirlanirsa ilerleme sayilir`() {
        bekci.kanalBasladi(1)
        bekci.ornek(0, 500, true)
        bekci.ornek(4000, 500, true)
        assertEquals(Karar.Devam, bekci.ornek(4500, 3, true))
        assertEquals(Karar.Devam, bekci.ornek(9000, 3, true))
        assertEquals(Karar.YenidenBaslat, bekci.ornek(9500, 3, true))
    }

    @Test
    fun `on dakika icinde ikinci donmada yazilim cozucuye gec`() {
        bekci.kanalBasladi(1)
        assertTrue(bekci.donanimCozucu(1))
        assertEquals(Karar.YenidenBaslat, bekle(0, 10_000, 7))
        bekci.kanalBasladi(1)
        assertEquals(Karar.YazilimCozucuyeGec, bekle(300_000, 310_000, 9))
        assertFalse(bekci.donanimCozucu(1))
    }

    @Test
    fun `on dakikadan sonraki donma ilk donma sayilir`() {
        bekci.kanalBasladi(1)
        assertEquals(Karar.YenidenBaslat, bekle(0, 10_000, 7))
        bekci.kanalBasladi(1)
        assertEquals(Karar.YenidenBaslat, bekle(700_000, 710_000, 9))
        assertTrue(bekci.donanimCozucu(1))
    }

    @Test
    fun `yazilim cozucudeyken donma yeniden baslatir`() {
        bekci.kanalBasladi(1)
        bekle(0, 10_000, 7)
        bekci.kanalBasladi(1)
        bekle(20_000, 30_000, 7)
        bekci.kanalBasladi(1)
        assertEquals(Karar.YenidenBaslat, bekle(40_000, 50_000, 7))
        assertFalse(bekci.donanimCozucu(1))
    }

    @Test
    fun `donmalar kanal basina sayilir`() {
        bekci.kanalBasladi(1)
        bekle(0, 10_000, 7)
        bekci.kanalBasladi(2)
        assertEquals(Karar.YenidenBaslat, bekle(20_000, 30_000, 7))
        assertTrue(bekci.donanimCozucu(1))
        assertTrue(bekci.donanimCozucu(2))
    }

    @Test
    fun `kanal degisince bekleme sifirlanir`() {
        bekci.kanalBasladi(1)
        bekci.ornek(0, 100, true)
        bekci.ornek(4000, 100, true)
        bekci.kanalBasladi(2)
        assertEquals(Karar.Devam, bekci.ornek(5000, 100, true))
        assertEquals(Karar.Devam, bekci.ornek(9000, 100, true))
        assertEquals(Karar.YenidenBaslat, bekci.ornek(10_000, 100, true))
    }
}
