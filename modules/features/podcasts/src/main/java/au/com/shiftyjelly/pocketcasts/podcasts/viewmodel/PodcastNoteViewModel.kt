package au.com.shiftyjelly.pocketcasts.podcasts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PodcastNoteViewModel.Factory::class)
class PodcastNoteViewModel @AssistedInject constructor(
    private val podcastManager: PodcastManager,
    @Assisted private val podcastUuid: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedNote = podcastManager.findPodcastByUuid(podcastUuid)?.note.orEmpty()
            _uiState.update { it.copy(note = savedNote, savedNote = savedNote, isLoaded = true) }
        }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note.take(MAX_NOTE_LENGTH)) }
    }

    // TODO(podcast-notes): Track note saved and cleared analytics events once event names are agreed.
    fun save() {
        updateNote(_uiState.value.note)
    }

    fun clear() {
        updateNote(null)
    }

    private fun updateNote(note: String?) {
        viewModelScope.launch {
            podcastManager.updateNote(podcastUuid, note?.trim()?.takeIf { it.isNotEmpty() })
            _uiState.update { it.copy(isDone = true) }
        }
    }

    data class UiState(
        val note: String = "",
        val savedNote: String = "",
        val isLoaded: Boolean = false,
        val isDone: Boolean = false,
    ) {
        val hasSavedNote get() = savedNote.isNotBlank()
    }

    @AssistedFactory
    interface Factory {
        fun create(podcastUuid: String): PodcastNoteViewModel
    }

    companion object {
        const val MAX_NOTE_LENGTH = 500
    }
}
