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
            modelClass.isAssignableFrom(ProviderViewModel::class.java) -> ProviderViewModel(repository.providerConfigRepository) as T
            modelClass.isAssignableFrom(ProviderConfigViewModel::class.java) -> ProviderConfigViewModel(repository.providerConfigRepository) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(repository) as T
            modelClass.isAssignableFrom(SummarizeViewModel::class.java) -> SummarizeViewModel(repository) as T
            modelClass.isAssignableFrom(ImageViewModel::class.java) -> ImageViewModel(repository) as T
            modelClass.isAssignableFrom(VideoViewModel::class.java) -> VideoViewModel(repository) as T
            modelClass.isAssignableFrom(ArenaViewModel::class.java) -> ArenaViewModel(repository) as T
            modelClass.isAssignableFrom(KnowledgeViewModel::class.java) -> KnowledgeViewModel(repository) as T
            modelClass.isAssignableFrom(PipelineViewModel::class.java) -> PipelineViewModel(repository) as T
            modelClass.isAssignableFrom(RewriteStudioViewModel::class.java) -> RewriteStudioViewModel(repository) as T
            modelClass.isAssignableFrom(CodeDebuggerViewModel::class.java) -> CodeDebuggerViewModel(repository) as T
            modelClass.isAssignableFrom(YouTubeExtractorViewModel::class.java) -> YouTubeExtractorViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
