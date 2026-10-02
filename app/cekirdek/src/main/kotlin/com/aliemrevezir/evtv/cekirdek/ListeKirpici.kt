package com.aliemrevezir.evtv.cekirdek

import java.net.URI

/**
 * HLS çalma listesini stick'in kaldırabileceği boyuta indirir. TRT 1 gibi
 * kanallar 24 saatlik geri sarma penceresi yayınlıyor (14.400 segment, ~1 MB);
 * stick'teki libVLC bunu her 6 sn'de ayrıştırırken tıkanıp hiç oynatamıyor.
 *
 * Canlı listede yalnızca son [tut] segment kalır; tüm adresler mutlak yapılır
 * (liste artık yerel vekilden geliyor). Ana listedeki alt listeler [vekil]
 * üzerinden yönlendirilir ki onlar da kırpılsın.
 */
object ListeKirpici {

    private val LISTE_DUZEYI = listOf(
        "#EXTM3U", "#EXT-X-VERSION", "#EXT-X-TARGETDURATION", "#EXT-X-MEDIA-SEQUENCE",
        "#EXT-X-DISCONTINUITY-SEQUENCE", "#EXT-X-PLAYLIST-TYPE", "#EXT-X-INDEPENDENT-SEGMENTS",
        "#EXT-X-START", "#EXT-X-ALLOW-CACHE", "#EXT-X-SERVER-CONTROL", "#EXT-X-PART-INF",
    )
    private val URI_ALANI = Regex("URI=\"([^\"]*)\"")

    private class Segment(val satirlar: List<String>)

    fun kirp(metin: String, taban: String, tut: Int, vekil: (String) -> String): String {
        val tabanUri = URI(taban)
        val satirlar = metin.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
        val sonuc = if (satirlar.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            anaListe(satirlar, tabanUri, vekil)
        } else {
            medyaListesi(satirlar, tabanUri, tut)
        }
        return sonuc.joinToString("\n", postfix = "\n")
    }

    private fun anaListe(satirlar: List<String>, taban: URI, vekil: (String) -> String) =
        satirlar.map { satir ->
            when {
                !satir.startsWith("#") -> vekil(mutlak(taban, satir))
                satir.startsWith("#EXT-X-MEDIA:") -> uriAlanlari(satir) { vekil(mutlak(taban, it)) }
                else -> satir
            }
        }

    private fun medyaListesi(satirlar: List<String>, taban: URI, tut: Int): List<String> {
        val baslik = satirlar.takeWhile { s -> LISTE_DUZEYI.any { s.startsWith(it) } }.toMutableList()
        val govde = satirlar.drop(baslik.size)
        val son = govde.filter { it.startsWith("#EXT-X-ENDLIST") }

        val segmentler = mutableListOf<Segment>()
        var birikim = mutableListOf<String>()
        for (satir in govde) {
            if (satir.startsWith("#EXT-X-ENDLIST")) continue
            birikim.add(satir)
            if (!satir.startsWith("#")) {
                segmentler.add(Segment(birikim))
                birikim = mutableListOf()
            }
        }

        val atilacak = if (son.isEmpty()) (segmentler.size - tut).coerceAtLeast(0) else 0
        var kalanlar = segmentler.drop(atilacak)
        if (atilacak > 0) {
            val atilan = segmentler.take(atilacak).flatMap { it.satirlar }
            val kesinti = atilan.count { it == "#EXT-X-DISCONTINUITY" }
            sayiyiArtir(baslik, "#EXT-X-MEDIA-SEQUENCE:", atilacak)
            sayiyiArtir(baslik, "#EXT-X-DISCONTINUITY-SEQUENCE:", kesinti)
            // EVENT türü segment atılmasına izin vermez.
            baslik.removeAll { it.startsWith("#EXT-X-PLAYLIST-TYPE") }
            // Anahtar ve başlangıç bölümü sonraki segmentlere de geçerli; ilk kalan segmente taşınır.
            val ilk = kalanlar.first().satirlar
            val tasinan = listOf("#EXT-X-KEY", "#EXT-X-MAP").mapNotNull { etiket ->
                if (ilk.any { it.startsWith(etiket) }) null else atilan.lastOrNull { it.startsWith(etiket) }
            }
            kalanlar = listOf(Segment(tasinan + ilk)) + kalanlar.drop(1)
        }

        val segmentSatirlari = kalanlar.flatMap { it.satirlar }.map { satir ->
            when {
                !satir.startsWith("#") -> mutlak(taban, satir)
                satir.startsWith("#EXT-X-KEY") || satir.startsWith("#EXT-X-MAP") ->
                    uriAlanlari(satir) { mutlak(taban, it) }
                else -> satir
            }
        }
        return baslik + segmentSatirlari + son
    }

    private fun sayiyiArtir(baslik: MutableList<String>, etiket: String, artis: Int) {
        val i = baslik.indexOfFirst { it.startsWith(etiket) }
        if (i >= 0) {
            baslik[i] = etiket + (baslik[i].removePrefix(etiket).trim().toLong() + artis)
        } else if (artis > 0) {
            baslik.add(etiket + artis)
        }
    }

    private fun uriAlanlari(satir: String, donustur: (String) -> String) =
        URI_ALANI.replace(satir) { "URI=\"" + donustur(it.groupValues[1]) + "\"" }

    private fun mutlak(taban: URI, adres: String) = taban.resolve(adres).toString()
}
