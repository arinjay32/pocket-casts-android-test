# Podcast Notes: add a personal note to a followed podcast

## Summary
Listeners can now write a short personal note on any podcast they follow, e.g. "Recommended by Sam, start from ep 40". The note appears on the podcast page, can be added, edited or cleared from there, is limited to 500 characters, and is stored locally so it survives app restarts.

## Changes per module

### `modules/services/model`
- `Podcast.kt`: new nullable `note` column (`@ColumnInfo(name = "note")`).
- `AppDatabase.kt`: database version 140 → **141**, with `MIGRATION_140_141` (`ALTER TABLE podcasts ADD COLUMN note TEXT`) registered in `addMigrations`.
- `PodcastDao.kt`: `updateNote(uuid, note)`, a targeted `UPDATE` with the same shape as `updateIsHeaderExpanded`.
- `schemas/.../141.json`: the exported Room schema (generated).

### `modules/services/repositories`
- `PodcastManager` / `PodcastManagerImpl`: `suspend fun updateNote(podcastUuid, note)`.
- `SubscribeManager`: keeps the local note when a podcast is downloaded again and re-inserted with `INSERT OR REPLACE`. This is the same guard that already exists for playback effects and the header-expanded state.

### `modules/services/localization`
- Six new strings: `podcast_note_add`, `_title`, `_placeholder`, `_save`, `_clear`, `_character_count`.

### `modules/features/podcasts`
- **New** `viewmodel/PodcastNoteViewModel.kt`: an assisted-inject `@HiltViewModel` keyed by podcast uuid. It keeps a private `MutableStateFlow` and exposes a public `StateFlow<UiState>`. It loads the saved note, caps edits at 500 characters, trims on save, and stores `null` when the note is blank or cleared.
- **New** `view/podcast/PodcastNotePage.kt`: a Compose bottom sheet built from the design-system wrappers (`BottomSheetAppBar`, multi-line `FormField`, `TextC70` character counter, `RowButton` to save, `RowTextButton` to clear), plus a theme preview.
- **New** `view/podcast/PodcastNoteFragment.kt`: a `BaseDialogFragment` host that dismisses once the save finishes.
- `PodcastHeader.kt`: a new `PodcastNote` card under the podcast controls, shown only for followed podcasts. It shows the note, or "Add a note" when there isn't one, and tapping it opens the editor.
- `PodcastAdapter.kt` / `PodcastFragment.kt`: pass `podcast.note` and the click callback through to the header.
- `FolderAdapter.kt`: a `TODO(podcast-notes)` only (see Skipped).
- **New** test `PodcastNoteViewModelTest.kt` (6 tests).

### `app` (androidTest)
- `AppDatabaseTest.kt`: a `migrate140To141AddsPodcastNote` test, plus `MIGRATION_140_141` added to the full-migration chain.

## Approach
- **Storage: a column on `podcasts`, not a separate store.** The page's header already observes the podcast row (`podcastByUuidRxFlowable`), so writing the note re-renders the header automatically. The legacy LiveData/Rx `PodcastViewModel` didn't need any changes. The design follows the existing local-only `is_header_expanded` column all the way through the layers.
- **Only for followed podcasts.** Unfollowed podcasts are deleted when you leave their page (`deletePodcastIfUnusedBlocking`), so a note on one would disappear silently. The brief also scopes this feature to followed podcasts.
- **A separate editor ViewModel.** It keeps the new state logic small, testable and coroutine-based, instead of adding it to the 985-line legacy `PodcastViewModel`.
- **UI reuse.** The editor copies `FolderEditNamePage` (the folder-name sheet opened from this same page), and the dialog host copies `FolderChooserFragment`. Everything uses Material2 wrappers from `services/compose`.

## Trade-offs
- **Full-row writes.** Any code path that calls `podcastDao.update(podcast)` with a `Podcast` object read before the note was edited would overwrite the note. Every local-only column on this table already carries the same risk. The one known re-insert path (`SubscribeManager`) is guarded.
- **Unfollowing deletes the note.** If the user unfollows and the row gets cleaned up, the note goes with it. That's acceptable for "podcasts they follow", but it's worth confirming with design.
- **Not synced and not in exports.** Notes are local only, by design: server sync is out of scope. They are lost when the app is reinstalled or the user moves to a new device.

## How to test
1. `./gradlew :modules:features:podcasts:testDebugUnitTest --tests "*PodcastNoteViewModelTest"`
2. Migration (needs a device or emulator): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=au.com.shiftyjelly.pocketcasts.models.db.AppDatabaseTest`
3. Manual:
   - Install over an existing build, so the migration from v140 actually runs.
   - Open a podcast you follow and tap **Add a note**. Type a note and watch the counter. Confirm input stops at 500 characters. Tap **Save Note**.
   - The note now appears on the podcast page. Kill the app, relaunch it, and confirm the note is still there.
   - Tap the note, edit it, and save. Then tap it again and choose **Clear Note**. The card goes back to "Add a note".
   - Open a podcast you don't follow. There's no note card.
   - Check the sheet in light and dark themes.

## Assumptions
- A note that is blank, or only whitespace, means "no note" and is stored as `NULL`.
- The note card sits under the follow/folder/notifications controls and stays visible when the header is collapsed, so the note is always on screen.

## Skipped (with reasons)
- **Grid/list indicator** (nice-to-have). The grid binds XML layouts for each grid size, so it needs a design for each layout. Left as `TODO(podcast-notes)` in `FolderAdapter.PodcastViewHolder.bind`.
- **Analytics events.** No event names were specified. Left as `TODO(podcast-notes)` in `PodcastNoteViewModel`.
- **Lint and on-device runs.** I didn't run `aggregatedLintRelease` or the instrumentation tests. Only compile, unit tests, spotless and `:app:assembleDebug` were run locally.

## Next steps
- Add the grid/list note indicator.
- Sync notes through the existing podcast sync (`PodcastSync`) if product wants them on every device.
- Add analytics for note added, edited and cleared.
- Consider including notes in the OPML or settings export.
