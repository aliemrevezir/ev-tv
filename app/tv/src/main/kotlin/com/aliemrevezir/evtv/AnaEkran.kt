package com.aliemrevezir.evtv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.aliemrevezir.evtv.cekirdek.AcilisBekcisi
import com.aliemrevezir.evtv.cekirdek.DonmaBekcisi
import com.aliemrevezir.evtv.cekirdek.Kanal
import com.aliemrevezir.evtv.cekirdek.KanalListesi
import com.aliemrevezir.evtv.cekirdek.KanalSecici
import com.aliemrevezir.evtv.cekirdek.UcluBasis
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnaEkran : Activity(), Oynatici.Dinleyici {

    private val anaIs = Handler(Looper.getMainLooper())
    private lateinit var arkaPlan: ExecutorService

    private lateinit var oynatici: Oynatici
    private lateinit var kanalListesi: KanalListesi
    private lateinit var logolar: LogoYukleyici
    private var secici: KanalSecici? = null
    private val donmaBekcisi = DonmaBekcisi()
    private val acilisBekcisi = AcilisBekcisi()

    private lateinit var kanalBilgisi: LinearLayout
    private lateinit var logo: ImageView
    private lateinit var kanalAdi: TextView
    private lateinit var mesaj: TextView
    private lateinit var panel: View
    private lateinit var listeGorunumu: ListView
    private lateinit var listeUyarlayici: KanalUyarlayici

    private val homeSayaci = UcluBasis()

    private var calisiyor = false
    private var sonGunluk = 0L

    private val kanalBilgisiniGizle = Runnable { kanalBilgisi.visibility = View.GONE }
    private val listeyiYenidenDene = Runnable { listeyiTazele() }
    private val tik = object : Runnable {
        override fun run() {
            bekcileriCalistir()
            anaIs.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.ana_ekran)

        kanalBilgisi = findViewById(R.id.kanal_bilgisi)
        logo = findViewById(R.id.logo)
        kanalAdi = findViewById(R.id.kanal_adi)
        mesaj = findViewById(R.id.mesaj)
        panel = findViewById(R.id.panel)
        listeGorunumu = findViewById(R.id.kanal_listesi)

        arkaPlan = Executors.newSingleThreadExecutor()
        oynatici = Oynatici(this, findViewById<VLCVideoLayout>(R.id.video), this)
        kanalListesi = KanalListesi(LISTE_ADRESI, File(filesDir, "liste.json"))
        logolar = LogoYukleyici(resources.getDimensionPixelSize(R.dimen.logo_yuksekligi))

        listeUyarlayici = KanalUyarlayici()
        listeGorunumu.adapter = listeUyarlayici
        listeGorunumu.setOnItemClickListener { _, _, konum, _ ->
            panelKapat()
            val s = secici ?: return@setOnItemClickListener
            val kanal = listeUyarlayici.getItem(konum) ?: return@setOnItemClickListener
            if (kanal.no != s.gecerli.no) {
                s.sec(kanal.no)
                oynat(yeniKanal = true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        calisiyor = true
        anaIs.post(tik)
        if (secici != null) {
            oynat(yeniKanal = true)
            listeyiTazele()
        } else {
            arkaPlan.execute {
                val saklanan = kanalListesi.saklanan()
                anaIs.post {
                    if (!calisiyor) return@post
                    if (saklanan != null) listeHazir(saklanan)
                    listeyiTazele()
                }
            }
        }
    }

    override fun onStop() {
        calisiyor = false
        anaIs.removeCallbacksAndMessages(null)
        acilisBekcisi.durdu()
        oynatici.durdur()
        super.onStop()
    }

    override fun onDestroy() {
        oynatici.birak()
        logolar.kapat()
        arkaPlan.shutdownNow()
        super.onDestroy()
    }

    // --- liste ---

    private fun listeyiTazele() {
        anaIs.removeCallbacks(listeyiYenidenDene)
        arkaPlan.execute {
            val sonuc = runCatching { kanalListesi.indirVeSakla() }
            anaIs.post {
                if (!calisiyor) return@post
                sonuc.onSuccess { listeHazir(it) }
                sonuc.onFailure { hata ->
                    Log.w(ETIKET, "Liste indirilemedi: $hata")
                    if (secici == null) {
                        mesajGoster(R.string.internet_bekleniyor)
                        anaIs.postDelayed(listeyiYenidenDene, YENIDEN_DENEME_MS)
                    }
                }
            }
        }
    }

    private fun listeHazir(kanallar: List<Kanal>) {
        val s = secici
        if (s == null) {
            secici = KanalSecici(kanallar, TercihSaklayici(this))
            listeUyarlayici.yenile()
            oynat(yeniKanal = true)
        } else {
            val degisti = s.listeyiGuncelle(kanallar)
            listeUyarlayici.yenile()
            if (degisti) oynat(yeniKanal = true)
        }
    }

    // --- oynatma ---

    private fun oynat(yeniKanal: Boolean) {
        val kanal = secici?.gecerli ?: return
        val donanim = donmaBekcisi.donanimCozucu(kanal.no)
        Log.i(ETIKET, "Oynat ${kanal.no} ${kanal.ad} donanım=$donanim ${kanal.adres}")
        if (yeniKanal) {
            mesajGizle()
            kanalBilgisiGoster(kanal)
        }
        donmaBekcisi.kanalBasladi(kanal.no)
        acilisBekcisi.denemeBasladi(SystemClock.elapsedRealtime())
        oynatici.oynat(kanal.adres, donanim)
    }

    override fun goruntuGeldi() {
        acilisBekcisi.oynadi()
        mesajGizle()
    }

    override fun hataOldu() {
        if (acilisBekcisi.hataOldu(SystemClock.elapsedRealtime()) == AcilisBekcisi.Karar.HataGoster) {
            kanalAcilmiyor()
        }
    }

    private fun bekcileriCalistir() {
        if (secici == null) return
        val simdi = SystemClock.elapsedRealtime()
        val kare = oynatici.kareSayisi()
        val oynuyor = oynatici.oynuyor()
        if (kare > 0 && oynuyor) goruntuGeldi()

        when (acilisBekcisi.ornek(simdi)) {
            AcilisBekcisi.Karar.HataGoster -> kanalAcilmiyor()
            AcilisBekcisi.Karar.YenidenDene -> {
                oynat(yeniKanal = false)
                return
            }
            AcilisBekcisi.Karar.Devam -> Unit
        }

        if (simdi - sonGunluk >= GUNLUK_ARALIGI_MS) {
            sonGunluk = simdi
            Log.i(ETIKET, "Kare sayısı $kare, oynuyor=$oynuyor")
        }
        when (donmaBekcisi.ornek(simdi, kare, oynuyor)) {
            DonmaBekcisi.Karar.YenidenBaslat -> {
                Log.w(ETIKET, "Donma: yeniden başlatılıyor")
                oynat(yeniKanal = false)
            }
            DonmaBekcisi.Karar.YazilimCozucuyeGec -> {
                Log.w(ETIKET, "İkinci donma: bu kanal işlemcide çözülecek")
                oynat(yeniKanal = false)
            }
            DonmaBekcisi.Karar.Devam -> Unit
        }
    }

    private fun kanalAcilmiyor() {
        Log.w(ETIKET, "Kanal açılmıyor: ${secici?.gecerli?.ad}")
        mesajGoster(R.string.acilmiyor)
        // Adres gün içinde değişmiş olabilir; yeni liste farklı adres getirirse hemen oynar.
        listeyiTazele()
    }

    // --- kumanda ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (panel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                panelKapat()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> kanalDegistir { it.sonraki() }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> kanalDegistir { it.onceki() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU -> panelAc()
            // Ev TV ana ekran: Geri ile çıkılırsa boş ekran kalır, yutulur.
            KeyEvent.KEYCODE_BACK -> Unit
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // Home tuşu uygulamaya tuş olarak gelmez; ana ekran olduğumuz için yeni intent gelir.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return
        panelKapat()
        if (homeSayaci.basildi(SystemClock.elapsedRealtime())) eskiAnaEkraniAc()
    }

    private fun eskiAnaEkraniAc() {
        Log.i(ETIKET, "Home x3: eski ana ekran açılıyor")
        val niyet = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(ESKI_ANA_EKRAN_PAKETI, ESKI_ANA_EKRAN_ETKINLIGI))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(niyet)
        } catch (e: ActivityNotFoundException) {
            Log.w(ETIKET, "Eski ana ekran yok, ayarlar açılıyor: $e")
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun kanalDegistir(adim: (KanalSecici) -> Kanal) {
        val s = secici ?: return
        adim(s)
        oynat(yeniKanal = true)
    }

    private fun panelAc() {
        val s = secici ?: return
        val konum = s.kanallar.indexOfFirst { it.no == s.gecerli.no }
        panel.visibility = View.VISIBLE
        listeGorunumu.setItemChecked(konum, true)
        listeGorunumu.setSelection(konum)
        listeGorunumu.requestFocus()
    }

    private fun panelKapat() {
        panel.visibility = View.GONE
    }

    // --- ekran ---

    private fun kanalBilgisiGoster(kanal: Kanal) {
        kanalAdi.text = getString(R.string.kanal_bilgisi, kanal.no, kanal.ad)
        logolar.yukle(kanal.logo, logo)
        kanalBilgisi.visibility = View.VISIBLE
        anaIs.removeCallbacks(kanalBilgisiniGizle)
        anaIs.postDelayed(kanalBilgisiniGizle, KANAL_BILGISI_MS)
    }

    private fun mesajGoster(metin: Int) {
        mesaj.setText(metin)
        mesaj.visibility = View.VISIBLE
    }

    private fun mesajGizle() {
        mesaj.visibility = View.GONE
    }

    private inner class KanalUyarlayici : ArrayAdapter<Kanal>(this, R.layout.kanal_ogesi) {

        fun yenile() {
            clear()
            addAll(secici?.kanallar.orEmpty())
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val gorunum = (convertView ?: layoutInflater.inflate(R.layout.kanal_ogesi, parent, false)) as TextView
            val kanal = getItem(position)!!
            gorunum.text = getString(R.string.kanal_bilgisi, kanal.no, kanal.ad)
            return gorunum
        }
    }

    private companion object {
        const val ETIKET = "EvTV"
        const val LISTE_ADRESI = "https://raw.githubusercontent.com/aliemrevezir/ev-tv/main/liste.json"
        const val YENIDEN_DENEME_MS = 10_000L
        const val KANAL_BILGISI_MS = 3_000L
        const val ESKI_ANA_EKRAN_PAKETI = "com.google.android.tvlauncher"
        const val ESKI_ANA_EKRAN_ETKINLIGI = "com.google.android.tvlauncher.MainActivity"
        const val GUNLUK_ARALIGI_MS = 30_000L
    }
}
