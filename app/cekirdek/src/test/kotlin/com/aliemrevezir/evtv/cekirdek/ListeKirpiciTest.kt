package com.aliemrevezir.evtv.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeKirpiciTest {

    private val taban = "https://cdn.ornek/kanal/master_720.m3u8?t=1"
    private val vekil: (String) -> String = { "http://vekil/$it" }

    private fun canli(adet: Int, bas: Int = 100) = buildString {
        append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:7\n#EXT-X-MEDIA-SEQUENCE:$bas\n")
        repeat(adet) { append("#EXTINF:6.0,\nparca_${bas + it}.ts\n") }
    }

    @Test
    fun `uzun canli liste son segmentlere kirpilir ve sira numarasi kayar`() {
        val sonuc = ListeKirpici.kirp(canli(14_400), taban, tut = 3, vekil)
        assertEquals(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:7
            #EXT-X-MEDIA-SEQUENCE:14497
            #EXTINF:6.0,
            https://cdn.ornek/kanal/parca_14497.ts
            #EXTINF:6.0,
            https://cdn.ornek/kanal/parca_14498.ts
            #EXTINF:6.0,
            https://cdn.ornek/kanal/parca_14499.ts

            """.trimIndent(),
            sonuc,
        )
    }

    @Test
    fun `kisa liste kirpilmaz, yalnizca adresler mutlak olur`() {
        val sonuc = ListeKirpici.kirp(canli(2), taban, tut = 3, vekil)
        assertEquals(canli(2).replace("parca_", "https://cdn.ornek/kanal/parca_"), sonuc)
    }

    @Test
    fun `bitmis liste kirpilmaz`() {
        val metin = canli(5) + "#EXT-X-ENDLIST\n"
        val sonuc = ListeKirpici.kirp(metin, taban, tut = 3, vekil)
        assertEquals(metin.replace("parca_", "https://cdn.ornek/kanal/parca_"), sonuc)
    }

    @Test
    fun `atilan kesintiler kesinti sirasina eklenir, son anahtar korunur, EVENT turu kalkar`() {
        val metin = """
            #EXTM3U
            #EXT-X-PLAYLIST-TYPE:EVENT
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-DISCONTINUITY-SEQUENCE:2
            #EXT-X-KEY:METHOD=AES-128,URI="anahtar1"
            #EXTINF:6,
            a.ts
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=AES-128,URI="anahtar2"
            #EXTINF:6,
            b.ts
            #EXTINF:6,
            c.ts

        """.trimIndent()
        assertEquals(
            """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:2
            #EXT-X-DISCONTINUITY-SEQUENCE:3
            #EXT-X-KEY:METHOD=AES-128,URI="https://cdn.ornek/kanal/anahtar2"
            #EXTINF:6,
            https://cdn.ornek/kanal/c.ts

            """.trimIndent(),
            ListeKirpici.kirp(metin, taban, tut = 1, vekil),
        )
    }

    @Test
    fun `segment anahtar ve baslangic bolumu adresleri parca donusumunden gecer`() {
        val metin = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-MAP:URI="baslangic.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="anahtar"
            #EXTINF:6,
            a.ts

        """.trimIndent()
        assertEquals(
            """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-MAP:URI="P(https://cdn.ornek/kanal/baslangic.mp4)"
            #EXT-X-KEY:METHOD=AES-128,URI="P(https://cdn.ornek/kanal/anahtar)"
            #EXTINF:6,
            P(https://cdn.ornek/kanal/a.ts)

            """.trimIndent(),
            ListeKirpici.kirp(metin, taban, tut = 3, vekil) { "P($it)" },
        )
    }

    @Test
    fun `ana listedeki alt listeler vekile yonlendirilir`() {
        val metin = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ses",URI="ses/tr.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=1280x720
            master_720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=500
            https://baska.cdn/360.m3u8

        """.trimIndent()
        assertEquals(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ses",URI="http://vekil/https://cdn.ornek/kanal/ses/tr.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=1280x720
            http://vekil/https://cdn.ornek/kanal/master_720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=500
            http://vekil/https://baska.cdn/360.m3u8

            """.trimIndent(),
            ListeKirpici.kirp(metin, taban, tut = 3, vekil),
        )
    }
}
