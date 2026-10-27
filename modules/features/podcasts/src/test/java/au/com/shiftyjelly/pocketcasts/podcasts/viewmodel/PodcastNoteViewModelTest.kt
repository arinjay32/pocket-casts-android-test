package au.com.shiftyjelly.pocketcasts.podcasts.viewmodel

import app.cash.turbine.test
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.podcasts.viewmodel.PodcastNoteViewModel.Companion.MAX_NOTE_LENGTH
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.sharedtest.MainCoroutineRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PodcastNoteViewModelTest {
    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private fun createViewModel(savedNote: String? = null): Pair<PodcastNoteViewModel, PodcastManager> {
        val podcastManager = mock<PodcastManager> {
            on { findPodcastByUuid(PODCAST_UUID) } doReturn Podcast(uuid = PODCAST_UUID, note = savedNote)
        }
        return PodcastNoteViewModel(podcastManager, PODCAST_UUID) to podcastManager
    }

    @Test
    fun `loads the saved note`() = runTest {
        val (viewModel, _) = createViewModel(savedNote = "Start from ep 40")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Start from ep 40", state.note)
            assertTrue(state.isLoaded)
            assertTrue(state.hasSavedNote)
        }
    }

    @Test
    fun `loads an empty note when none is saved`() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.note)
            assertTrue(state.isLoaded)
            assertFalse(state.hasSavedNote)
        }
    }

    @Test
    fun `note change is limited to max length`() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.onNoteChange("a".repeat(MAX_NOTE_LENGTH + 20))

        assertEquals(MAX_NOTE_LENGTH, viewModel.uiState.value.note.length)
    }

    @Test
    fun `save stores the trimmed note`() = runTest {
        val (viewModel, podcastManager) = createViewModel()

        viewModel.onNoteChange("  Recommended by Sam  ")
        viewModel.save()

        verify(podcastManager).updateNote(PODCAST_UUID, "Recommended by Sam")
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `save stores null for a blank note`() = runTest {
        val (viewModel, podcastManager) = createViewModel(savedNote = "Old note")

        viewModel.onNoteChange("   ")
        viewModel.save()

        verify(podcastManager).updateNote(PODCAST_UUID, null)
    }

    @Test
    fun `clear removes the note`() = runTest {
        val (viewModel, podcastManager) = createViewModel(savedNote = "Old note")

        viewModel.clear()

        verify(podcastManager).updateNote(PODCAST_UUID, null)
        assertTrue(viewModel.uiState.value.isDone)
    }

    private companion object {
        const val PODCAST_UUID = "podcast-uuid"
    }
}
