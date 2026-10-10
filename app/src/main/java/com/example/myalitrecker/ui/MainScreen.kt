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
import com.example.myalitrecker.ui.components.AliExpressLoginDialog
import com.example.myalitrecker.ui.components.ParcelCard
import com.example.myalitrecker.util.Constants
import com.example.myalitrecker.util.SignatureHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val signedInAccount by viewModel.signedInAccount.collectAsState()
    val isAliExpressLoggedIn by viewModel.isAliExpressLoggedIn.collectAsState()
    val parcels by viewModel.parcels.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val recoverableAuthIntent by viewModel.recoverableAuthIntent.collectAsState()

    var showAliExpressLoginDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: В пути (с треками), 1: Ожидают отправки (без трека)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Separate parcels into "In Transit" (with actual tracking) and "Pending shipment" (PENDING_...)
    val inTransitParcels = remember(parcels) {
        parcels.filter { !it.parcel.trackingNumber.startsWith("PENDING_") }
    }
    val pendingParcels = remember(parcels) {
        parcels.filter { it.parcel.trackingNumber.startsWith("PENDING_") }
    }

    // Launcher for UserRecoverableAuthIOException (Google consent dialog)
    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.syncGmailEmails()
        } else {
            viewModel.setErrorMessage("Доступ к чтению писем Gmail не был подтвержден")
        }
    }

    LaunchedEffect(recoverableAuthIntent) {
        recoverableAuthIntent?.let { intent ->
            recoverableAuthLauncher.launch(intent)
            viewModel.clearRecoverableAuthIntent()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.onGoogleSignInResult(account)
        } catch (e: ApiException) {
            if (e.statusCode != 16) {
                viewModel.setErrorMessage("Ошибка Google: код ${e.statusCode}")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isAliExpressLoggedIn) {
                                Text(
                                    text = "AliExpress подключен ✓",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (signedInAccount != null) {
                                Text(
                                    text = signedInAccount?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Прямая синхронизация заказов",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Sync button
                    IconButton(
                        onClick = {
                            if (isAliExpressLoggedIn) {
                                showAliExpressLoginDialog = true
                            } else if (signedInAccount != null) {
                                viewModel.syncGmailEmails()
                            } else {
                                showAliExpressLoginDialog = true
                            }
                        },
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
                                contentDescription = "Синхронизировать"
                            )
                        }
                    }

                    // AliExpress status button
                    if (!isAliExpressLoggedIn) {
                        FilledTonalButton(
                            onClick = { showAliExpressLoginDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("AliExpress", fontSize = 12.sp)
                        }
                    } else {
                        IconButton(onClick = { viewModel.logoutAliExpress() }) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Выйти из AliExpress",
                                tint = MaterialTheme.colorScheme.error
                            )
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
            Column(modifier = Modifier.fillMaxSize()) {
                // If neither AliExpress nor Gmail is connected, show banner with choice
                if (!isAliExpressLoggedIn && signedInAccount == null) {
                    ConnectionHeaderBanner(
                        onOpenAliExpress = { showAliExpressLoginDialog = true },
                        onOpenGoogle = {
                            val client = viewModel.getAuthManager().getBasicSignInClient()
                            signInLauncher.launch(client.signInIntent)
                        }
                    )
                }

                // Two Tabs: "В пути" (с треками) и "Ожидают отправки" (без трека)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "В пути (${inTransitParcels.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Ожидают отправки (${pendingParcels.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                // Content of current tab
                val currentList = if (selectedTab == 0) inTransitParcels else pendingParcels

                if (currentList.isEmpty()) {
                    EmptyTabStateView(
                        isPendingTab = selectedTab == 1,
                        isSyncing = isSyncing,
                        onSyncClick = {
                            if (isAliExpressLoggedIn) showAliExpressLoginDialog = true
                            else if (signedInAccount != null) viewModel.syncGmailEmails()
                            else showAliExpressLoginDialog = true
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = currentList,
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

    if (showAliExpressLoginDialog) {
        AliExpressLoginDialog(
            initialIsLoggedIn = isAliExpressLoggedIn,
            onDismissRequest = { showAliExpressLoginDialog = false },
            onOrdersExtracted = { orders, cookies ->
                showAliExpressLoginDialog = false
                viewModel.onOrdersExtractedFromWebView(orders, cookies)
            }
        )
    }
}

@Composable
fun ConnectionHeaderBanner(
    onOpenAliExpress: () -> Unit,
    onOpenGoogle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Подключите ваш аккаунт",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Для прямой синхронизации всех заказов и трек-номеров выполните вход:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAliExpress,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Войти в AliExpress")
                }
                OutlinedButton(
                    onClick = onOpenGoogle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Вход через Google")
                }
            }
        }
    }
}

@Composable
fun EmptyTabStateView(
    isPendingTab: Boolean,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isPendingTab) Icons.Default.HourglassEmpty else Icons.Default.LocalShipping,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isPendingTab) "Нет заказов, ожидающих отправки" else "Нет посылок в пути",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isPendingTab)
                    "Здесь будут отображаться покупки по номерам заказов, пока продавец не передал их в доставку."
                else
                    "Как только товару будет присвоен трек-номер (внутренний китайский или международный), он появится здесь.",
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
                    Text("Синхронизируем...")
                } else {
                    Text("Обновить заказы")
                }
            }
        }
    }
}
