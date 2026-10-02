package com.aliemrevezir.evtv

import android.content.Context
import com.aliemrevezir.evtv.cekirdek.SonKanalSaklayici

class TercihSaklayici(context: Context) : SonKanalSaklayici {

    private val tercihler = context.getSharedPreferences("evtv", Context.MODE_PRIVATE)

    override fun oku(): Int? = tercihler.getInt(ANAHTAR, -1).takeIf { it >= 0 }

    override fun yaz(no: Int) {
        tercihler.edit().putInt(ANAHTAR, no).apply()
    }

    private companion object {
        const val ANAHTAR = "son_kanal"
    }
}
