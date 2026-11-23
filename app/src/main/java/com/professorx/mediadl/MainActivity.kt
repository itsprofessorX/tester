package com.professorx.mediadl

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.professorx.mediadl.ui.CelebrationBanner
import com.professorx.mediadl.ui.DownloadsScreen
import com.professorx.mediadl.ui.HomeScreen
import com.professorx.mediadl.ui.LibraryScreen
import com.professorx.mediadl.ui.SettingsScreen
import com.professorx.mediadl.ui.SettingsSheet

class MainActivity : ComponentActivity() {
    private val vm: DlViewModel by lazy { (application as App).dl }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        handleIncomingIntent(intent)
        setContent {
            MediaDownTheme(prefs = vm.prefs) {
                MediaDownApp(vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.onShared(it) }
            return
        }
        val retryUrl = i.getStringExtra(Notifier.EXTRA_RETRY_URL)
        if (!retryUrl.isNullOrBlank()) {
            vm.enqueueUrl(retryUrl)
            vm.activeTab = NavTab.DOWNLOADS
            return
        }
        if (i.getBooleanExtra(MediaDownWidgetProvider.EXTRA_FROM_WIDGET_PASTE, false)) {
            vm.activeTab = NavTab.HOME
        }
    }
}

@Composable
fun MediaDownApp(vm: DlViewModel) {
    val ctx = LocalContext.current
    val colors = LocalMediaColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var settingsSheet by remember { mutableStateOf(SettingsSheet.NONE) }

    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { vm.importCookies(it) }
    }
    val igLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshCookies()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                vm.checkClipboard(clip)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeQueueCount = vm.tasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.CONVERTING
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        containerColor = colors.background,
        bottomBar = {
            Column(modifier = Modifier.background(colors.surface).navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp
                ) {
                    NavTab.entries.forEach { tab ->
                        val selected = vm.activeTab == tab
                        val icon = when (tab) {
                            NavTab.HOME -> Icons.Rounded.Home
                            NavTab.DOWNLOADS -> Icons.Rounded.Download
                            NavTab.LIBRARY -> Icons.Rounded.PhotoLibrary
                            NavTab.SETTINGS -> Icons.Rounded.Settings
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                settingsSheet = SettingsSheet.NONE
                                vm.activeTab = tab
                            },
                            icon = {
                                if (tab == NavTab.DOWNLOADS && activeQueueCount > 0) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = colors.accent) {
                                            Text("$activeQueueCount", fontSize = 10.sp)
                                        }
                                    }) {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.accent,
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                                indicatorColor = colors.surfaceElevated
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (vm.activeTab) {
                NavTab.HOME -> HomeScreen(
                    vm = vm,
                    onOpenInstagramLogin = { igLogin.launch(Intent(ctx, IgLoginActivity::class.java)) },
                    onOpenNotifications = {
                        settingsSheet = SettingsSheet.NOTIFICATIONS
                        vm.activeTab = NavTab.SETTINGS
                    }
                )
                NavTab.DOWNLOADS -> DownloadsScreen(vm = vm)
                NavTab.LIBRARY -> LibraryScreen(vm = vm)
                NavTab.SETTINGS -> SettingsScreen(
                    vm = vm,
                    initialSheet = settingsSheet,
                    onOpenInstagramLogin = { igLogin.launch(Intent(ctx, IgLoginActivity::class.java)) },
                    onPickCookiesFile = { cookiePicker.launch(arrayOf("text/*", "application/octet-stream")) }
                )
            }

            CelebrationBanner(
                title = vm.celebrationTitle,
                reducedMotion = vm.prefs.reducedMotion,
                onDismiss = { vm.celebrationTitle = null }
            )
        }
    }
}
