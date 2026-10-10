package com.example.myalitrecker.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myalitrecker.ui.components.ParcelCard
import com.example.myalitrecker.util.Constants
import com.example.myalitrecker.util.SignatureHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val signedInAccount by viewModel.signedInAccount.collectAsState()
    val parcels by viewModel.parcels.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val hasGmailAccess = remember(signedInAccount) {
        signedInAccount?.let { viewModel.getAuthManager().hasGmailPermission(it) } ?: false
    }

    // Permission launcher for Gmail scope
    val gmailPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && viewModel.getAuthManager().hasGmailPermission(account)) {
            viewModel.onGoogleSignInResult(account)
            viewModel.syncEmails()
        } else {
            viewModel.setErrorMessage("Доступ к чтению писем не был подтвержден")
        }
    }

    // Main Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.onGoogleSignInResult(account)
            if (!viewModel.getAuthManager().hasGmailPermission(account)) {
                // Request Gmail permission explicitly
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder()
                    .requestScopes(Scope(Constants.GMAIL_SCOPE))
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                gmailPermissionLauncher.launch(client.signInIntent)
            }
        } catch (e: ApiException) {
            val errorDescription = when (e.statusCode) {
                10 -> "Ошибка 10 (DEVELOPER_ERROR):\nПопробуйте кнопку «Альтернативный вход» ниже."
                12500 -> "Ошибка 12500: вход отклонен сервером Google"
                7 -> "Ошибка 7: нет соединения с интернетом"
                16 -> "Отменено пользователем"
                else -> "Код ошибки Google: ${e.statusCode} (${e.localizedMessage ?: "неизвестно"})"
            }
            if (e.statusCode != 16) {
                viewModel.setErrorMessage(errorDescription)
            }
            viewModel.onGoogleSignInResult(null)
        } catch (e: Exception) {
            viewModel.setErrorMessage("Ошибка: ${e.localizedMessage}")
            viewModel.onGoogleSignInResult(null)
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Long
            )
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MyAliTrecker",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        signedInAccount?.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (signedInAccount != null) {
                        IconButton(
                            onClick = { viewModel.syncEmails() },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Синхронизировать почту"
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.signOut() }) {
                            Text("Выйти")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (signedInAccount == null) {
                GoogleSignInCard(
                    onDirectSignInClick = {
                        val client = viewModel.getAuthManager().getGoogleSignInClient()
                        signInLauncher.launch(client.signInIntent)
                    },
                    onBasicSignInClick = {
                        val client = viewModel.getAuthManager().getBasicSignInClient()
                        signInLauncher.launch(client.signInIntent)
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!hasGmailAccess) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Требуется доступ к Gmail",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Для поиска писем от AliExpress предоставьте разрешение.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Button(
                                    onClick = {
                                        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder()
                                            .requestScopes(Scope(Constants.GMAIL_SCOPE))
                                            .build()
                                        val client = GoogleSignIn.getClient(context, gso)
                                        gmailPermissionLauncher.launch(client.signInIntent)
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("Разрешить")
                                }
                            }
                        }
                    }

                    if (parcels.isEmpty()) {
                        EmptyStateView(
                            isSyncing = isSyncing,
                            onSyncClick = { viewModel.syncEmails() },
                            userEmail = signedInAccount?.email,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = parcels,
                                key = { it.parcel.trackingNumber }
                            ) { parcelWithItems ->
                                ParcelCard(
                                    parcelWithItems = parcelWithItems,
                                    onDeleteClick = { trackingNum ->
                                        viewModel.deleteParcel(trackingNum)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSignInCard(
    onDirectSignInClick: () -> Unit,
    onBasicSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSha1 = remember { SignatureHelper.getAppSignatureSha1(context) }

    Card(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Вход через Google Почту",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Приложение автоматически найдет все трек-номера и заказы AliExpress из ваших писем и объединит их в посылки.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDirectSignInClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти через Google")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onBasicSignInClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Альтернативный вход (если ошибка 10)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SHA1", currentSha1))
                        Toast.makeText(context, "SHA-1 скопирован!", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Отпечаток этого APK (нажмите для копирования):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Пакет: ${context.packageName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "SHA-1: $currentSha1",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    userEmail: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Посылок пока нет",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (userEmail != null)
                "В ящике $userEmail пока не найдено сохраненных посылок. Нажмите кнопку ниже, чтобы проверить письма от AliExpress."
            else
                "Нажмите кнопку ниже, чтобы проверить входящие письма от AliExpress и сформировать посылки.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSyncClick,
            enabled = !isSyncing
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Проверяем почту...")
            } else {
                Text("Синхронизировать почту")
            }
        }
    }
}
