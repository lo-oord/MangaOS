package com.mangaos.app.presentation.storage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.StoredWork
import kotlinx.coroutines.flow.StateFlow

class LocalStorageViewModel : ViewModel() {

    private val repository = ServiceLocator.localStorage

    val works: StateFlow<List<StoredWork>> = repository.works

    var expandedWorkIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Selection never spans two works, which keeps deletion unambiguous. */
    var selectionOwner by mutableStateOf<String?>(null)
        private set

    var selectedChapterIds by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleExpanded(mangaId: String) {
        expandedWorkIds = if (mangaId in expandedWorkIds) {
            expandedWorkIds - mangaId
        } else {
            expandedWorkIds + mangaId
        }
    }

    fun toggleSelected(mangaId: String, chapterId: String) {
        if (selectionOwner != mangaId) {
            selectionOwner = mangaId
            selectedChapterIds = setOf(chapterId)
            return
        }
        selectedChapterIds = if (chapterId in selectedChapterIds) {
            selectedChapterIds - chapterId
        } else {
            selectedChapterIds + chapterId
        }
        if (selectedChapterIds.isEmpty()) selectionOwner = null
    }

    fun isSelected(mangaId: String, chapterId: String): Boolean =
        selectionOwner == mangaId && chapterId in selectedChapterIds

    fun clearSelection() {
        selectedChapterIds = emptySet()
        selectionOwner = null
    }

    fun deleteChapter(mangaId: String, chapterId: String) {
        repository.deleteChapter(mangaId, chapterId)
        clearSelection()
    }

    fun deleteSelected() {
        val owner = selectionOwner ?: return
        repository.deleteChapters(owner, selectedChapterIds)
        clearSelection()
    }

    fun deleteWork(mangaId: String) {
        repository.deleteWork(mangaId)
        expandedWorkIds = expandedWorkIds - mangaId
        clearSelection()
    }
}
