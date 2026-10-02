package com.aliemrevezir.evtv.cekirdek

/**
 * Kanal açılmazsa ya da oynatıcı hata verirse "açılmıyor" mesajını ve
 * [yenidenDenemeMs] sonra yeniden denemeyi yönetir. Saat dışarıdan verilir.
 */
class AcilisBekcisi(
    private val acilisSuresiMs: Long = 20_000,
    private val yenidenDenemeMs: Long = 10_000,
) {

    enum class Karar { Devam, HataGoster, YenidenDene }

    private sealed interface Durum {
        data class Aciliyor(val baslangic: Long) : Durum
        data object Oynuyor : Durum
        data class Hatali(val zaman: Long) : Durum
        data object Durdu : Durum
    }

    private var durum: Durum = Durum.Durdu

    fun denemeBasladi(zamanMs: Long) {
        durum = Durum.Aciliyor(zamanMs)
    }

    fun oynadi() {
        if (durum is Durum.Aciliyor) durum = Durum.Oynuyor
    }

    fun durdu() {
        durum = Durum.Durdu
    }

    fun hataOldu(zamanMs: Long): Karar {
        if (durum is Durum.Hatali || durum is Durum.Durdu) return Karar.Devam
        durum = Durum.Hatali(zamanMs)
        return Karar.HataGoster
    }

    fun ornek(zamanMs: Long): Karar = when (val d = durum) {
        is Durum.Aciliyor ->
            if (zamanMs - d.baslangic >= acilisSuresiMs) hataOldu(zamanMs) else Karar.Devam
        is Durum.Hatali ->
            if (zamanMs - d.zaman >= yenidenDenemeMs) {
                durum = Durum.Durdu
                Karar.YenidenDene
            } else {
                Karar.Devam
            }
        else -> Karar.Devam
    }
}
