package com.chunting.reader

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chunting.reader.player.PlayerController
import com.chunting.reader.ui.AppNav
import com.chunting.reader.ui.AppViewModel
import com.chunting.reader.ui.theme.TingReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val s by vm.settingsFlow.collectAsState()
            TingReaderTheme(themeMode = s.themeMode, accentIndex = s.accentIndex) {
                RequestAudioPermission()
                AppNav()
            }
        }
    }
}

@Composable
private fun RequestAudioPermission() {
    val perms = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) { launcher.launch(perms) }
}
