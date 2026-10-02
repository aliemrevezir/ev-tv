package com.aliemrevezir.evtv.cekirdek

import com.aliemrevezir.evtv.cekirdek.AcilisBekcisi.Karar
import org.junit.Assert.assertEquals
import org.junit.Test

class AcilisBekcisiTest {

    private val bekci = AcilisBekcisi(acilisSuresiMs = 20_000, yenidenDenemeMs = 10_000)

    @Test
    fun `zamaninda oynayan kanal icin bir sey yapmaz`() {
        bekci.denemeBasladi(0)
        assertEquals(Karar.Devam, bekci.ornek(5_000))
        bekci.oynadi()
        assertEquals(Karar.Devam, bekci.ornek(60_000))
    }

    @Test
    fun `sure icinde oynamazsa hata gosterir sonra yeniden dener`() {
        bekci.denemeBasladi(0)
        assertEquals(Karar.Devam, bekci.ornek(19_999))
        assertEquals(Karar.HataGoster, bekci.ornek(20_000))
        assertEquals(Karar.Devam, bekci.ornek(25_000))
        assertEquals(Karar.YenidenDene, bekci.ornek(30_000))
    }

    @Test
    fun `oynatici hata verirse hemen hata gosterir`() {
        bekci.denemeBasladi(0)
        assertEquals(Karar.HataGoster, bekci.hataOldu(2_000))
        assertEquals(Karar.YenidenDene, bekci.ornek(12_000))
    }

    @Test
    fun `oynarken hata olursa da yeniden dener`() {
        bekci.denemeBasladi(0)
        bekci.oynadi()
        assertEquals(Karar.HataGoster, bekci.hataOldu(100_000))
        assertEquals(Karar.YenidenDene, bekci.ornek(110_000))
    }

    @Test
    fun `ayni hata iki kez gosterilmez`() {
        bekci.denemeBasladi(0)
        assertEquals(Karar.HataGoster, bekci.hataOldu(1_000))
        assertEquals(Karar.Devam, bekci.hataOldu(1_500))
    }

    @Test
    fun `durdurulunca hicbir sey yapmaz`() {
        bekci.denemeBasladi(0)
        bekci.durdu()
        assertEquals(Karar.Devam, bekci.ornek(100_000))
        assertEquals(Karar.Devam, bekci.hataOldu(100_000))
    }
}
