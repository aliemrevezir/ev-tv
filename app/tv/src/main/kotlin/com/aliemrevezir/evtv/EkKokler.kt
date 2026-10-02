package com.aliemrevezir.evtv

import android.content.Context
import android.util.Log
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Stick'in Android 10 sertifika deposunda olmayan, güncel tarayıcıların
 * güvendiği kökler. Doğrulama kapatılmaz: önce sistem kökleri, olmazsa
 * yalnızca bu kökler denenir.
 *
 * - Sectigo Public Server Authentication Root R46 (SHA-256 7B:B6:47:A6:…:5A:06):
 *   canli.tgrthaber.com'un sunucularından biri bu köke bağlanan zinciri,
 *   USERTrust çapraz imzası olmadan gönderiyor; VLC ve Java ikisi de reddediyordu.
 */
object EkKokler {

    fun soketFabrikasi(context: Context): SSLSocketFactory {
        val sertifikalar = CertificateFactory.getInstance("X.509")
        val depo = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        context.resources.openRawResource(R.raw.sectigo_r46).use {
            depo.setCertificateEntry("sectigo_r46", sertifikalar.generateCertificate(it))
        }
        val guven = BirlesikGuven(guvenYoneticisi(null), guvenYoneticisi(depo))
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(guven), null) }.socketFactory
    }

    /** @param depo null ise sistem kökleri */
    private fun guvenYoneticisi(depo: KeyStore?): X509TrustManager {
        val fabrika = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        fabrika.init(depo)
        return fabrika.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private class BirlesikGuven(
        private val sistem: X509TrustManager,
        private val ek: X509TrustManager,
    ) : X509TrustManager {

        override fun checkServerTrusted(zincir: Array<X509Certificate>, tur: String) {
            try {
                sistem.checkServerTrusted(zincir, tur)
            } catch (e: CertificateException) {
                ek.checkServerTrusted(zincir, tur)
                Log.d("EvTV", "Ek kökle doğrulandı: ${zincir.firstOrNull()?.subjectX500Principal}")
            }
        }

        override fun checkClientTrusted(zincir: Array<X509Certificate>, tur: String) =
            sistem.checkClientTrusted(zincir, tur)

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            sistem.acceptedIssuers + ek.acceptedIssuers
    }
}
