package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.AllChatRepository

class AllChatViewModelFactory(
    private val repository: AllChatRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(repository) as T
            modelClass.isAssignableFrom(SummarizeViewModel::class.java) -> SummarizeViewModel(repository) as T
            modelClass.isAssignableFrom(ImageViewModel::class.java) -> ImageViewModel(repository) as T
            modelClass.isAssignableFrom(VideoViewModel::class.java) -> VideoViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
