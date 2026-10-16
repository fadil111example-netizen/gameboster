package com.gameboost.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111640), Bg)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xE60B0F1F)) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Neon,
                        selectedTextColor = Neon,
                        indicatorColor = Color(0x2200E5FF),
                        unselectedIconColor = TextLo,
                        unselectedTextColor = TextLo,
                    )
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                        icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Beranda") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                        icon = { Icon(Icons.Rounded.SportsEsports, null) }, label = { Text("Game") },
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                        icon = { Icon(Icons.Rounded.Tune, null) }, label = { Text("Pengaturan") },
                    )
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> GamesScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}
