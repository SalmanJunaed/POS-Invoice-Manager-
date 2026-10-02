package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.pos.data.Localization
import com.example.pos.ui.screens.AuthScreen
import com.example.pos.ui.screens.HistoryScreen
import com.example.pos.ui.screens.InvoiceScreen
import com.example.pos.ui.screens.ProductsScreen
import com.example.pos.ui.screens.ReceiptPreviewDialog
import com.example.pos.ui.screens.SettingsScreen
import com.example.pos.ui.screens.SplashScreen
import com.example.pos.ui.viewmodel.PosViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                PosMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PosMainApp(viewModel: PosViewModel) {
    val context = LocalContext.current
    val activeOperator by viewModel.activeOperator.collectAsState()
    val language by viewModel.language.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val receiptPreviewOpen by viewModel.receiptPreviewOpen.collectAsState()
    val receiptPreviewText by viewModel.receiptPreviewText.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val isDraftFullPaid by viewModel.isDraftFullPaid.collectAsState()

    val isSessionChecking by viewModel.isSessionChecking.collectAsState()

    var showSplash by remember { mutableStateOf(true) }
    var currentNavIndex by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    if (showSplash) {
        SplashScreen(
            language = language,
            isSessionChecking = isSessionChecking,
            onSplashFinished = { showSplash = false }
        )
    } else if (activeOperator == null) {
        // Operator Authentication Gate (Login / Register / Reset)
        AuthScreen(
            viewModel = viewModel,
            language = language,
            onLoginSuccess = {
                currentNavIndex = 0
            }
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pos_main_scaffold"),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                    NavigationBarItem(
                        selected = currentNavIndex == 0,
                        onClick = { currentNavIndex = 0 },
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = "Billing") },
                        label = { Text(Localization.get(Localization.Key.NAV_INVOICE, language)) },
                        modifier = Modifier.testTag("nav_invoice_item")
                    )
                    NavigationBarItem(
                        selected = currentNavIndex == 1,
                        onClick = {
                            currentNavIndex = 1
                            viewModel.refreshHistory()
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text(Localization.get(Localization.Key.NAV_HISTORY, language)) },
                        modifier = Modifier.testTag("nav_history_item")
                    )
                    NavigationBarItem(
                        selected = currentNavIndex == 2,
                        onClick = { currentNavIndex = 2 },
                        icon = { Icon(Icons.Default.Inventory, contentDescription = "Products") },
                        label = { Text(Localization.get(Localization.Key.NAV_PRODUCTS, language)) },
                        modifier = Modifier.testTag("nav_products_item")
                    )
                    NavigationBarItem(
                        selected = currentNavIndex == 3,
                        onClick = { currentNavIndex = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text(Localization.get(Localization.Key.NAV_SETTINGS, language)) },
                        modifier = Modifier.testTag("nav_settings_item")
                    )
                }
            }
        ) { innerPadding ->
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (currentNavIndex) {
                0 -> InvoiceScreen(viewModel = viewModel, modifier = screenModifier)
                1 -> HistoryScreen(viewModel = viewModel, modifier = screenModifier)
                2 -> ProductsScreen(viewModel = viewModel, modifier = screenModifier)
                3 -> SettingsScreen(viewModel = viewModel, modifier = screenModifier)
            }
        }
    }

    // Thermal Receipt Preview Dialog
    if (receiptPreviewOpen) {
        ReceiptPreviewDialog(
            receiptText = receiptPreviewText,
            language = language,
            logoUri = companyHeader?.logoUri,
            companyName = companyHeader?.companyName,
            isFullPaid = isDraftFullPaid,
            onDismiss = { viewModel.closeReceiptPreview() },
            onPrintBluetooth = {
                viewModel.testPrint()
            }
        )
    }
}
