package com.icespiritai.offline.auth

import android.util.Base64
import com.icespiritai.offline.BuildConfig
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

object PublicKeyProvider {
    val default: PublicKey by lazy {
        val der = Base64.decode(BuildConfig.LICENSE_PUBLIC_KEY, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }

    val fingerprint: String get() = BuildConfig.LICENSE_KEY_FINGERPRINT
}
