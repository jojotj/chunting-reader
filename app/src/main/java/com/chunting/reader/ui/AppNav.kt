package com.chunting.reader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chunting.reader.ui.folder.FolderScreen
import com.chunting.reader.ui.picker.PickerScreen
import com.chunting.reader.ui.settings.SettingsScreen
import com.chunting.reader.ui.shelf.ShelfScreen

object Routes {
    const val SHELF = "shelf"
    const val PICKER = "picker"
    const val FOLDER = "folder"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()

    NavHost(
        navController = nav,
        startDestination = Routes.SHELF,
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        composable(Routes.SHELF) {
            ShelfScreen(
                vm = vm,
                onOpenFolder = { nav.navigate(Routes.FOLDER) },
                onAdd = { nav.navigate(Routes.PICKER) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCurrentFolder = {
                    vm.openCurrentFolder()
                    nav.navigate(Routes.FOLDER)
                },
            )
        }
        composable(Routes.PICKER) {
            PickerScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.FOLDER) {
            FolderScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

