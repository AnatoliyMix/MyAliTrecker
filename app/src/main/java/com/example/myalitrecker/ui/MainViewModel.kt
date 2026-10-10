package com.example.myalitrecker.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myalitrecker.data.local.AppDatabase
import com.example.myalitrecker.data.local.model.ParcelWithItems
import com.example.myalitrecker.data.remote.GmailAuthManager
import com.example.myalitrecker.data.remote.GmailRepository
import com.example.myalitrecker.data.remote.aliexpress.AliExpressOrderRepository
import com.example.myalitrecker.data.remote.aliexpress.AliExpressSessionManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = GmailAuthManager(application)
    private val gmailRepository = GmailRepository(application)
    private val aliExpressSessionManager = AliExpressSessionManager(application)
    private val aliExpressRepository = AliExpressOrderRepository(application)
    private val parcelDao = AppDatabase.getInstance(application).parcelDao()

    val parcels: StateFlow<List<ParcelWithItems>> = parcelDao.getParcelsWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    private val _isAliExpressLoggedIn = MutableStateFlow(aliExpressSessionManager.isLoggedIn())
    val isAliExpressLoggedIn: StateFlow<Boolean> = _isAliExpressLoggedIn.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _recoverableAuthIntent = MutableStateFlow<Intent?>(null)
    val recoverableAuthIntent: StateFlow<Intent?> = _recoverableAuthIntent.asStateFlow()

    init {
        checkSignedInAccount()
        checkAliExpressSession()
    }

    fun checkSignedInAccount() {
        _signedInAccount.value = authManager.getSignedInAccount()
    }

    fun checkAliExpressSession() {
        _isAliExpressLoggedIn.value = aliExpressSessionManager.isLoggedIn()
    }

    fun onAliExpressLoginSuccess(cookies: String) {
        aliExpressSessionManager.saveCookies(cookies)
        _isAliExpressLoggedIn.value = true
        _syncMessage.value = "Успешный вход в AliExpress! Запускаем синхронизацию..."
        syncAliExpressOrders()
    }

    fun logoutAliExpress() {
        aliExpressSessionManager.clearSession()
        _isAliExpressLoggedIn.value = false
        _syncMessage.value = "Вы вышли из AliExpress"
    }

    fun syncAliExpressOrders() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = null

        viewModelScope.launch {
            val result = aliExpressRepository.syncOrders()
            _isSyncing.value = false
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _syncMessage.value = if (count > 0)
                    "Синхронизация AliExpress: обновлено заказов — $count"
                else
                    "Синхронизация AliExpress: заказы обновлены"
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка синхронизации"
                _syncMessage.value = "AliExpress: $err"
                checkAliExpressSession()
            }
        }
    }

    fun onGoogleSignInResult(account: GoogleSignInAccount?) {
        _signedInAccount.value = account
        if (account != null) {
            syncGmailEmails()
        }
    }

    fun setErrorMessage(msg: String) {
        _syncMessage.value = msg
    }

    fun syncGmailEmails() {
        val account = _signedInAccount.value ?: return
        if (_isSyncing.value) return

        _isSyncing.value = true
        _syncMessage.value = null

        viewModelScope.launch {
            val result = gmailRepository.syncAliExpressEmails(account)
            _isSyncing.value = false
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _syncMessage.value = if (count > 0)
                    "Синхронизация почты: обработано писем — $count"
                else
                    "Синхронизация почты: новых писем не найдено"
            } else {
                val ex = result.exceptionOrNull()
                if (ex is UserRecoverableAuthIOException) {
                    _recoverableAuthIntent.value = ex.intent
                } else {
                    val details = when (ex) {
                        is GoogleJsonResponseException -> {
                            "Google API (${ex.statusCode}): ${ex.details?.message ?: ex.statusMessage}"
                        }
                        else -> ex?.message ?: ex?.localizedMessage ?: ex?.javaClass?.simpleName ?: "Неизвестная ошибка"
                    }
                    _syncMessage.value = "Ошибка: $details"
                }
            }
        }
    }

    fun clearRecoverableAuthIntent() {
        _recoverableAuthIntent.value = null
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
    fun getAliExpressSessionManager(): AliExpressSessionManager = aliExpressSessionManager
}
