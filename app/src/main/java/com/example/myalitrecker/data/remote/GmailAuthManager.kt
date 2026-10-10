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
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(Constants.GMAIL_SCOPE))
            .requestIdToken(Constants.WEB_CLIENT_ID)
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    fun getBasicSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(Constants.WEB_CLIENT_ID)
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    fun hasGmailPermission(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(account, Scope(Constants.GMAIL_SCOPE))
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun signOut(onComplete: () -> Unit) {
        getGoogleSignInClient().signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
