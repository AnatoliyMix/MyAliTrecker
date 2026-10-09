package com.example.myalitrecker.data.remote

import android.content.Context
import com.example.myalitrecker.util.Constants
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

class GmailAuthManager(private val context: Context) {

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(Constants.GMAIL_SCOPE))

        if (Constants.WEB_CLIENT_ID.isNotBlank() && !Constants.WEB_CLIENT_ID.startsWith("YOUR_")) {
            gsoBuilder.requestIdToken(Constants.WEB_CLIENT_ID)
        }

        return GoogleSignIn.getClient(context, gsoBuilder.build())
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, Scope(Constants.GMAIL_SCOPE))) {
            account
        } else {
            null
        }
    }

    fun signOut(onComplete: () -> Unit) {
        getGoogleSignInClient().signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
