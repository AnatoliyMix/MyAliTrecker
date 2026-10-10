package com.example.myalitrecker.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SignatureHelper {
    fun getAppSignatureSha1(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) {
                return "Не найдена"
            }

            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(signatures[0].toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }
}
