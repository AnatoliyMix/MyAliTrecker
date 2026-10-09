package com.example.myalitrecker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myalitrecker.data.local.AppDatabase
import com.example.myalitrecker.data.local.model.ParcelWithItems
import com.example.myalitrecker.data.remote.GmailAuthManager
import com.example.myalitrecker.data.remote.GmailRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = GmailAuthManager(application)
    private val gmailRepository = GmailRepository(application)
    private val parcelDao = AppDatabase.getInstance(application).parcelDao()

    val parcels: StateFlow<List<ParcelWithItems>> = parcelDao.getParcelsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        checkSignedInAccount()
    }

    fun checkSignedInAccount() {
        _signedInAccount.value = authManager.getSignedInAccount()
    }

    fun onGoogleSignInResult(account: GoogleSignInAccount?) {
        _signedInAccount.value = account
        if (account != null) {
            syncEmails()
        }
    }

    fun setErrorMessage(msg: String) {
        _syncMessage.value = msg
    }

    fun syncEmails() {
        val account = _signedInAccount.value ?: return
        if (_isSyncing.value) return

        _isSyncing.value = true
        _syncMessage.value = null

        viewModelScope.launch {
            val result = gmailRepository.syncAliExpressEmails(account)
            _isSyncing.value = false
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _syncMessage.value = "Синхронизация завершена: обработано писем — $count"
            } else {
                _syncMessage.value = "Ошибка синхронизации: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun signOut() {
        authManager.signOut {
            _signedInAccount.value = null
        }
    }

    fun deleteParcel(trackingNumber: String) {
        viewModelScope.launch {
            parcelDao.deleteParcel(trackingNumber)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun getAuthManager(): GmailAuthManager = authManager
}
