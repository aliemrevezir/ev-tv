package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UcluBasisTest {

    private val sayac = UcluBasis(sureMs = 2_000)

    @Test
    fun `sure icinde ucuncu basista tetiklenir`() {
        assertFalse(sayac.basildi(0))
        assertFalse(sayac.basildi(500))
        assertTrue(sayac.basildi(1_900))
    }

    @Test
    fun `sure asilirsa sayim eski basislari unutur`() {
        assertFalse(sayac.basildi(0))
        assertFalse(sayac.basildi(1_000))
        assertFalse(sayac.basildi(2_500))   // ilk basış 2 sn'den eski
        assertTrue(sayac.basildi(2_900))    // 1_000, 2_500, 2_900
    }

    @Test
    fun `tetiklendikten sonra sifirdan sayar`() {
        sayac.basildi(0)
        sayac.basildi(100)
        assertTrue(sayac.basildi(200))
        assertFalse(sayac.basildi(300))
        assertFalse(sayac.basildi(400))
        assertTrue(sayac.basildi(500))
    }

    @Test
    fun `tek tuk basislar tetiklemez`() {
        assertFalse(sayac.basildi(0))
        assertFalse(sayac.basildi(5_000))
        assertFalse(sayac.basildi(10_000))
    }
}
