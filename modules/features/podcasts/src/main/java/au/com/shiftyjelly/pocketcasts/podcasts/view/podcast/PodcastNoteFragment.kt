package au.com.shiftyjelly.pocketcasts.podcasts.view.podcast

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.extensions.contentWithoutConsumedInsets
import au.com.shiftyjelly.pocketcasts.podcasts.viewmodel.PodcastNoteViewModel
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class PodcastNoteFragment : BaseDialogFragment() {
    private val podcastUuid get() = requireNotNull(requireArguments().getString(ARG_PODCAST_UUID))

    private val viewModel by viewModels<PodcastNoteViewModel>(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<PodcastNoteViewModel.Factory> { factory ->
                factory.create(podcastUuid)
            }
        },
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = contentWithoutConsumedInsets {
        AppThemeWithBackground(theme.activeTheme) {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.isDone) {
                if (uiState.isDone) {
                    dismiss()
                }
            }

            PodcastNotePage(
                state = uiState,
                onNoteChange = viewModel::onNoteChange,
                onSaveClick = viewModel::save,
                onClearClick = viewModel::clear,
                onCloseClick = { dismiss() },
            )
        }
    }

    companion object {
        private const val ARG_PODCAST_UUID = "ARG_PODCAST_UUID"

        fun newInstance(podcastUuid: String): PodcastNoteFragment {
            return PodcastNoteFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PODCAST_UUID, podcastUuid)
                }
            }
        }
    }
}
