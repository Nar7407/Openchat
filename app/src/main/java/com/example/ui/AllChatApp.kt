package com.example.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AllChatRepository
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SummarizeScreen
import com.example.ui.screens.VideoScreen
import com.example.ui.theme.SwissCanvas
import com.example.ui.viewmodel.AllChatViewModelFactory
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ImageViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SummarizeViewModel
import com.example.ui.viewmodel.VideoViewModel
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute
@Serializable data object SummarizeRoute
@Serializable data object ImageRoute
@Serializable data object VideoRoute
@Serializable data object SettingsRoute

@Composable
fun AllChatApp(
    repository: AllChatRepository,
    sharedTextPayload: String?,
    onSharedPayloadConsumed: () -> Unit
) {
    val factory = AllChatViewModelFactory(repository)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val summarizeViewModel: SummarizeViewModel = viewModel(factory = factory)
    val imageViewModel: ImageViewModel = viewModel(factory = factory)
    val videoViewModel: VideoViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val navController = rememberNavController()

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(sharedTextPayload) {
        if (!sharedTextPayload.isNullOrBlank()) {
            summarizeViewModel.consumeSharedPayload(sharedTextPayload)
            navigateTo(SummarizeRoute)
            onSharedPayloadConsumed()
        }
    }

    Scaffold(
        containerColor = SwissCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChatRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable<ChatRoute> {
                ChatScreen(
                    viewModel = chatViewModel,
                    onOpenSummarize = { navigateTo(SummarizeRoute) },
                    onOpenImage = { navigateTo(ImageRoute) },
                    onOpenVideo = { navigateTo(VideoRoute) },
                    onOpenSettings = { navigateTo(SettingsRoute) }
                )
            }
            composable<SummarizeRoute> {
                SummarizeScreen(
                    viewModel = summarizeViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSettings = { navigateTo(SettingsRoute) }
                )
            }
            composable<ImageRoute> {
                ImageScreen(
                    viewModel = imageViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSettings = { navigateTo(SettingsRoute) }
                )
            }
            composable<VideoRoute> {
                VideoScreen(
                    viewModel = videoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSettings = { navigateTo(SettingsRoute) }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
