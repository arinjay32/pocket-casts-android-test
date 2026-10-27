package au.com.shiftyjelly.pocketcasts.podcasts.view.podcast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground
import au.com.shiftyjelly.pocketcasts.compose.bars.BottomSheetAppBar
import au.com.shiftyjelly.pocketcasts.compose.bars.NavigationButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowButton
import au.com.shiftyjelly.pocketcasts.compose.buttons.RowTextButton
import au.com.shiftyjelly.pocketcasts.compose.components.FormField
import au.com.shiftyjelly.pocketcasts.compose.components.TextC70
import au.com.shiftyjelly.pocketcasts.compose.preview.ThemePreviewParameterProvider
import au.com.shiftyjelly.pocketcasts.podcasts.viewmodel.PodcastNoteViewModel
import au.com.shiftyjelly.pocketcasts.podcasts.viewmodel.PodcastNoteViewModel.Companion.MAX_NOTE_LENGTH
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@Composable
internal fun PodcastNotePage(
    state: PodcastNoteViewModel.UiState,
    onNoteChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = modifier,
    ) {
        BottomSheetAppBar(
            title = stringResource(LR.string.podcast_note_title),
            navigationButton = NavigationButton.Close,
            onNavigationClick = onCloseClick,
        )
        FormField(
            value = state.note,
            placeholder = stringResource(LR.string.podcast_note_placeholder),
            onValueChange = onNoteChange,
            singleLine = false,
            enabled = state.isLoaded,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                .heightIn(min = 120.dp)
                .focusRequester(focusRequester),
        )
        TextC70(
            text = stringResource(LR.string.podcast_note_character_count, state.note.length, MAX_NOTE_LENGTH),
            modifier = Modifier
                .align(Alignment.End)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        )
        RowButton(
            text = stringResource(LR.string.podcast_note_save),
            enabled = state.isLoaded,
            onClick = onSaveClick,
        )
        if (state.hasSavedNote) {
            RowTextButton(
                text = stringResource(LR.string.podcast_note_clear),
                onClick = onClearClick,
            )
        }
        // so the bottom sheet goes full height
        Spacer(modifier = Modifier.weight(1f))
    }
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded) {
            focusRequester.requestFocus()
        }
    }
}

@Preview
@Composable
private fun PodcastNotePagePreview(
    @PreviewParameter(ThemePreviewParameterProvider::class) themeType: Theme.ThemeType,
) {
    AppThemeWithBackground(themeType) {
        PodcastNotePage(
            state = PodcastNoteViewModel.UiState(
                note = "Recommended by Sam, start from ep 40",
                savedNote = "Recommended by Sam, start from ep 40",
                isLoaded = true,
            ),
            onNoteChange = {},
            onSaveClick = {},
            onClearClick = {},
            onCloseClick = {},
        )
    }
}
