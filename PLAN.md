# Podcast Notes: step 1 findings and proposed slice

## Context
This is the assessment brief "Podcast Notes (90 min)": add, edit and clear a plain-text note (max 500 characters) on a followed podcast, show it on the podcast page, and keep it across app restarts. Constraints: Material2 wrappers, private `MutableStateFlow`/public `StateFlow`, no `!!`, no new modules or dependencies, no edits to docs, warnings count as errors. **Step 1 is investigation only.** I'll write code after you approve.

---

## 1. How the podcast page is built: a mix of Views and Compose
- **Screen**: `PodcastFragment` (`features/podcasts/.../podcasts/view/podcast/PodcastFragment.kt`, 1444 lines) is a View Fragment with ViewBinding (`FragmentPodcastBinding`) and a `RecyclerView`.
- **Adapter**: `PodcastAdapter.kt` mixes View rows (episode rows) and `ComposeView` rows. The page header is the `PodcastHeaderViewHolder` (`PodcastAdapter.kt:800`). It calls `AppTheme(theme.activeTheme) { PodcastHeader(...) }` and gets its data straight from the `Podcast` entity.
- **Header UI**: `PodcastHeader.kt` (Compose, Material2) contains `PodcastHeader` → `PodcastControls` → `PodcastActions` (the follow/folder/notifications/settings buttons) plus `PodcastDetails`.
- **ViewModel**: `PodcastViewModel.kt` (`viewmodel/`, 985 lines) is legacy. It uses `MutableLiveData<UiState>` with a sealed `UiState`, built on Rx. It **observes the podcast row reactively** (`podcastByUuidRxFlowable`, l.191), so **any database write to the podcast re-renders the header for free.**
- ⚠ When you leave the page for an unfollowed podcast, `deletePodcastIfUnusedBlocking` (l.175) deletes the row. So notes are offered **only when `isSubscribed`**, which also matches the brief ("podcasts they follow").

## 2. Closest existing feature to use as a template
| Layer | Template | Why |
|---|---|---|
| Data (local per-podcast column) | `Podcast.isHeaderExpanded` → `PodcastDao.updateIsHeaderExpanded` (l.494) → `PodcastManager(Impl).updateIsHeaderExpanded` (l.94/593) → `PodcastViewModel.updateIsHeaderExpanded` (l.265) | A per-podcast, user-changed, local-only value stored on the `podcasts` row. It's the exact shape of a note. |
| Protect against server refresh | `SubscribeManager.kt:199-203,233,321` copies local-only fields (`isHeaderExpanded`, playback effects) onto the downloaded `Podcast` before `insertRxSingle` (INSERT OR REPLACE) | A note must be copied the same way, or a re-download wipes it. |
| Migration | `MIGRATION_139_140` (`AppDatabase.kt:1541`): a single `ALTER TABLE … ADD COLUMN` | Same thing: a nullable TEXT column. |
| Editor ViewModel | `PodcastSettingsViewModel` (`@HiltViewModel(assistedFactory=…)`, `@Assisted podcastUuid`, `withCreationCallback` in `PodcastSettingsFragment.kt:52`) | The module's own pattern for per-podcast, user-edited settings. |
| Editor UI | `FolderEditNamePage.kt` (`BottomSheetAppBar` + `FormField` + `RowButton`), hosted in a `BaseDialogFragment` (`FolderChooserFragment`, opened from `PodcastFragment.kt:559` with `.show(parentFragmentManager, …)`) | A text editor sheet launched from this same page, in this module, using the design-system wrappers. |
| Length limit | `ChatFeedbackViewModel.onDetailsChange`: `details.take(MAX_DETAILS_LENGTH)` | The existing way to enforce a cap in the ViewModel. |
| Tests | `ChatViewModelTest` style: `MainCoroutineRule`, `mock<PodcastManager>`, Turbine | `PodcastManager` has no fakes and is always mocked. |

## 3. Storage options
| | **A. New Room column `podcasts.note` (recommended)** | **B. Existing per-podcast key-value store** |
|---|---|---|
| Exists today? | Yes, same pattern as `is_header_expanded` | **No.** `Settings`/`UserSetting<T>` are global typed prefs; nothing is keyed by podcast uuid. I'd have to build it (a SharedPreferences store with keys like `podcast_note_<uuid>`). |
| Files touched | `Podcast.kt` (+1 field), `AppDatabase.kt` (v141, `MIGRATION_140_141`, register), `schemas/…/141.json` (generated), `PodcastDao.kt` (+`updateNote`), `PodcastManager.kt` + `Impl` (+`updateNote`), `SubscribeManager.kt` (+1 copy line) = 6 hand edits | A new store class + Hilt binding (`RepositoryModule`), a manager method, and a new Rx/Flow source **merged into `PodcastViewModel`'s legacy Rx pipeline** so the header sees it, plus the grid needs a second lookup = about 5 files, and messier |
| Display wiring | Free: the header already observes the `Podcast` row | You have to combine a second stream into LiveData/Rx |
| Grid indicator (nice-to-have) | Free: grid items are `Podcast`s | An extra per-row lookup |
| Cleanup | Deleted together with the podcast row | Orphaned keys when a podcast is deleted |
| Risks | Migration correctness; full-row `update(podcast)` writes using a stale object could overwrite it (the same risk every local column has today) | No migration needed, but it's not transactional with the podcast and not reactive |
| Effort | About 20–25 minutes, including the KSP schema build | About 35–45 minutes |

I rejected a third option, a separate `podcast_notes` table like `UserPodcastRating`: it needs a new entity, DAO and join, which is more work for no benefit when the relationship is 1:1.

**Recommendation: A.**

## 4. Thinnest vertical slice for requirements 1–3 (build order, compile after each layer)
**Layer 1: model**, then `./gradlew :modules:services:model:compileDebugKotlin`
1. `modules/services/model/.../models/entity/Podcast.kt`: `@ColumnInfo(name = "note") var note: String? = null`
2. `.../models/db/AppDatabase.kt`: `version = 141`, `MIGRATION_140_141` = `ALTER TABLE podcasts ADD COLUMN note TEXT`, then register it in `addMigrations`
3. `.../models/db/dao/PodcastDao.kt`: `@Query("UPDATE podcasts SET note = :note WHERE uuid IS :uuid") suspend fun updateNote(uuid: String, note: String?)`
4. `modules/services/model/schemas/…/141.json` is generated by the build and gets committed

**Layer 2: repositories**, then `:modules:services:repositories:compileDebugKotlin`

5. `.../repositories/podcast/PodcastManager.kt` + `PodcastManagerImpl.kt`: `suspend fun updateNote(podcastUuid: String, note: String?)` (stores `null` when the note is blank)
6. `.../repositories/podcast/SubscribeManager.kt`: add `podcast.note = localPodcast.note` in the existing `localPodcast` block (l.199)

**Layer 3: strings** in `modules/services/localization/src/main/res/values/strings.xml` (add note, edit note, note title, placeholder, save, clear)

**Layer 4: feature UI** (`features/podcasts/.../view/podcast/`), then `:modules:features:podcasts:compileDebugKotlin`

7. **New** `PodcastNoteViewModel.kt`: `@HiltViewModel(assistedFactory)` with `@Assisted podcastUuid`. It holds `private val _uiState = MutableStateFlow(UiState())` and exposes `val uiState: StateFlow`, where `UiState(note, initialNote, isSaved)`. It loads the existing note, `onNoteChange` uses `take(MAX_NOTE_LENGTH = 500)`, and `save()`/`clear()` call `podcastManager.updateNote` and set `isSaved`.
8. **New** `PodcastNotePage.kt`: copies `FolderEditNamePage`, using `BottomSheetAppBar` + multi-line `FormField` + a `RowButton` to save + a `RowTextButton` to clear (only when a note exists), plus a theme preview.
9. **New** `PodcastNoteFragment.kt`: a `BaseDialogFragment` with `newInstance(podcastUuid)` that wraps content in `AppThemeWithBackground` and dismisses when `isSaved` is set.
10. `PodcastHeader.kt`: new params `note: String?` and `onClickNote`. When followed, it shows the note text (Text wrapper, tappable to edit) or an "Add note" entry using the existing `ic_edit` drawable.
11. `PodcastAdapter.kt`: pass `podcast.note`, `podcast.isSubscribed` and `onClickNote` through `PodcastHeaderViewHolder` and the adapter constructor.
12. `PodcastFragment.kt`: `onClickNote = { PodcastNoteFragment.newInstance(uuid).show(parentFragmentManager, "podcast_note") }`

**Then the nice-to-haves, in the order time allows:** the character counter (a single line in the Page, almost free), then `PodcastNoteViewModelTest.kt` (truncation, save trims and stores, clear stores null), then the grid indicator. That last one is `// TODO(podcast-notes):` if time runs short.

**Finishing up:** `NOTES.md`, then a commit on a new branch off `main` (e.g. `feature/podcast-notes`). No push, no PR. Your uncommitted AGP downgrade in `libs.versions.toml` and the untracked `gradle-daemon-jvm.properties` stay out of the commit.

## Verification
- After each layer, run the narrowest `compileDebugKotlin` listed above.
- Run `./gradlew :modules:features:podcasts:testDebugUnitTest --tests "*PodcastNoteViewModelTest"`.
- Run `./gradlew spotlessApply spotlessCheck :app:assembleDebug`.
- Manual check: install the debug build, follow a podcast, add a note, kill and relaunch the app, confirm the note is still there, edit it, clear it, and confirm that typing stops at 500 characters.
