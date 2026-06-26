# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build & Run

Open in Android Studio and use the standard Run button, or via Gradle:

```bash
./gradlew assembleDebug          # debug build
./gradlew installDebug           # install on connected device/emulator
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests
```

Min SDK: 26 (Android 8.0). Target/Compile SDK: 36. Language: Kotlin. Build system: Gradle (`.kts`). Code generation: KSP (not KAPT).

---

## Architecture Overview

**Stack**: Jetpack Compose + Material 3 · Hilt DI · Room v7 · DataStore Preferences · Firebase Auth + Firestore · `StateFlow` + `collectAsState` · one `ViewModel` per screen + shared VMs for calendar state and settings.

```
ScalendarApp (@HiltAndroidApp)
│
├── navigation/
│   ├── AppNavigation.kt     ← Root composable: ModalNavigationDrawer + BottomNavBar + NavHost
│   └── NavRoutes.kt         ← Sealed class of route strings + CalendarView enum (DAY|WEEK|MONTH)
│
├── data/
│   ├── model/               ← EntryCategory, TimeOfDay enums + displayName() live in Entry.kt
│   │                          (Entry data class is an UNUSED stub — do not add logic to it)
│   ├── datastore/
│   │   └── SettingsDataStore.kt  ← DataStore Preferences wrapper; keys for theme/lang/homeView,
│   │                                cat_color_<id> overrides, default_notif_<cat> reminders
│   ├── database/
│   │   ├── entity/          ← EntryEntity, NoteEntity, UserCalendarEntity  (@Entity)
│   │   ├── dao/             ← EntryDao, NoteDao, UserCalendarDao  (Flow-returning queries)
│   │   ├── converter/       ← Converters.kt  (LocalDate/LocalDate?/LocalTime?/enums ↔ String)
│   │   └── ScalendarDatabase.kt  ← Room DB v9, migrations 1→2→3→4→5→6→7→8→9
│   └── repository/          ← EntryRepository, NoteRepository, SettingsRepository,
│                               UserCalendarRepository (thin wrappers)
│
├── di/
│   └── DatabaseModule.kt    ← @Singleton DB + DAO providers; all 8 migrations registered here
│       Note: AuthRepository, FirestoreRepository use @Inject constructor — no @Provides needed
│
├── notification/
│   ├── AlarmReceiver.kt     ← BroadcastReceiver — posts the notification
│   ├── BootReceiver.kt      ← Re-schedules alarms after device reboot
│   └── ReminderManager.kt   ← @Singleton; schedules/cancels AlarmManager exact alarms
│
├── util/
│   └── DateUtils.kt         ← LocalDate.startOfWeek() / endOfWeek() (Monday-anchored)
│
└── ui/
    ├── shared/
    │   └── SharedCalendarViewModel.kt  ← single source of truth: selectedDate, calendarView,
    │                                      categoryFilters; navigation helpers nextDay/prevDay/
    │                                      nextWeek/previousWeek/nextMonth/previousMonth
    ├── theme/
    │   └── EntryColors.kt   ← ALL entry color logic (see Color System below)
    ├── component/
    │   ├── WheelPicker.kt   ← WheelColumn (circular/linear), WheelDatePicker, WheelTimePicker,
    │   │                       nextRoundedTime()
    │   └── InlineCalendarPicker.kt  ← month-grid tap-to-select, Monday-first
    └── screen/
        ├── auth/            ← AuthScreen + AuthViewModel  (login/register; shown before main NavHost)
        ├── dayview/         ← DayViewScreen + DayViewViewModel  (primary screen)
        ├── weekview/        ← WeekViewScreen + WeekViewViewModel
        ├── monthview/       ← MonthViewScreen + MonthViewViewModel
        ├── journal/         ← JournalScreen + JournalViewModel
        ├── notes/           ← NotesScreen + NotesViewModel
        ├── settings/        ← SettingsScreen + SettingsViewModel (self-contained local navigation,
        │                      persists via DataStore + Room)
        └── addentry/        ← AddEntryScreen + AddEntryViewModel  ⚠ DEAD CODE — not wired
                                into AppNavigation; add-entry is handled by AddEntrySheet
                                inside DayViewScreen instead.
```

---

## Key Patterns

### Shared State Across Calendar Screens
`SharedCalendarViewModel` is obtained with `hiltViewModel()` in `AppNavigation` and **passed as a constructor parameter** to each calendar screen. Calendar screens do NOT call `hiltViewModel<SharedCalendarViewModel>()` themselves.

`SettingsViewModel` is also hoisted in `AppNavigation` with `hiltViewModel()` and passed directly to `SettingsScreen`. Its `uiState.colorOverrides` is also passed to `CalendarDrawerContent` so indicator colors in the drawer reflect user-chosen overrides.

### Data Model Reality
`data/model/Entry.kt` and `data/model/Note.kt` both contain **unused** stub classes (`Entry`, `Note`). All active code works directly with `EntryEntity` / `NoteEntity` from the Room layer. `EntryCategory` and `TimeOfDay` happen to live in `Entry.kt` — they are the only things worth importing from either model file.

### Room Database Migrations
Current version: **9**. Adding a column requires:
1. Add field to the entity data class.
2. Write `MIGRATION_N_(N+1)` in `ScalendarDatabase.kt`.
3. Register it in `DatabaseModule.kt` `.addMigrations(...)`.

**All migrations:**
| Migration | SQL |
|-----------|-----|
| 1→2 | `ADD COLUMN description TEXT NOT NULL DEFAULT ''` |
| 2→3 | `ADD COLUMN linkedNoteId INTEGER` |
| 3→4 | `ADD COLUMN color TEXT NOT NULL DEFAULT 'DEFAULT'` · `reminderOffsets TEXT NOT NULL DEFAULT ''` · `isRecurring INTEGER NOT NULL DEFAULT 0` |
| 4→5 | `ADD COLUMN deadlineDate TEXT` |
| 5→6 | `ADD COLUMN location TEXT NOT NULL DEFAULT ''` |
| 6→7 | `CREATE TABLE user_calendars (id TEXT PK, name TEXT, colorHex TEXT)` |
| 7→8 | `ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'` |
| 8→9 | `ADD COLUMN seriesId TEXT NOT NULL DEFAULT ''` |

### Recurrence Expansion
Recurring entries are **expanded on save**: `DayViewViewModel.saveEntry()` calls `expandRecurrence()` to insert one `EntryEntity` per occurrence. Types: `"DAILY"`, `"WEEKLY"`, `"MONTHLY"`, `"YEARLY"`, `"NONE"`. Each copy stores `recurrenceType` (shown in detail sheet) and a shared `seriesId` UUID (for series-delete). Editing an existing entry (`id != 0`) always updates in-place — never re-expands. `deleteEntrySeries(entry)` deletes all rows sharing the same `seriesId` (falls back to title+category+recurrenceType for old entries with empty seriesId).

### Color System (`ui/theme/EntryColors.kt`)
**Never** define entry color logic in a screen file; always import from `ui.theme`.

All colors are **hardcoded hex** to prevent Material 3 theme-seed tinting (the app's forest-green primary seed would otherwise bleed into `primaryFixed`/`secondaryFixed` for CLASS/SPORT cards).

`EntryCategory.cardColor()` / `onCardColor()` — pastel card backgrounds and their text colors:
| Category | `cardColor()` | `onCardColor()` |
|----------|---------------|-----------------|
| TASK | `#FFCCBC` (light deep-orange) | `#7B2C00` |
| CLASS | `#F5F5F5` (light gray) | `#424242` |
| SPORT | `#F1F8E9` (light green) | `#2E7D32` |
| EXAM | `#FFE082` (light amber) | `#7B5800` |
| BIRTHDAY | `MaterialTheme.colorScheme.tertiaryFixed` | `onTertiaryFixed` |
| EVENT | `#E3F2FD` (light blue) | `#0D47A1` |

`indicatorColor(cat, overrides)` in `AppNavigation.kt` — drawer checkbox colours (saturated, user-overridable via Settings):
| Category | Hex |
|----------|-----|
| TASK | `#F4511E` (deep orange-red) |
| CLASS | `#9E9E9E` (gray) |
| SPORT | `#66BB6A` (green) |
| EXAM | `#FFA000` (amber) |
| BIRTHDAY | `#EC4899` (pink) |
| EVENT | `#1E88E5` (blue) |

`ENTRY_COLOR_OPTIONS` — 9 named custom colors stored as strings in `EntryEntity.color` (e.g. `"BLUE"`, `"DEFAULT"`).
`EntryEntity.displayBgColor()` / `displayFgColor()` — used in all views; custom color overrides category default.

---

## EntryEntity Fields

```kotlin
id              : Long         // PrimaryKey autoGenerate
title           : String
category        : EntryCategory
timeOfDay       : TimeOfDay    // ANYTIME | MORNING | AFTERNOON | EVENING
startTime       : LocalTime?
endTime         : LocalTime?
date            : LocalDate    // scheduled date (entry appears here in day/week/month)
isCompleted     : Boolean = false
isImportant     : Boolean = false
description     : String  = ""
linkedNoteId    : Long?   = null
color           : String  = "DEFAULT"
reminderOffsets : String  = ""   // comma-separated minutes e.g. "30" = fire 30 min before
isRecurring     : Boolean = false
recurrenceType  : String  = "NONE"   // NONE|DAILY|WEEKLY|MONTHLY|YEARLY — stored per occurrence
seriesId        : String  = ""       // UUID shared by all occurrences in a recurring series; "" = standalone
deadlineDate    : LocalDate? = null  // TASK only; shown as deadline chip in week/month views
location        : String  = ""       // optional venue/room shown below time in Day/Week/Journal
```

## NoteEntity Fields

```kotlin
id       : Long      // PrimaryKey autoGenerate
title    : String
content  : String
date     : LocalDate
isPinned : Boolean = false
```

`NoteDao.getAll()` orders by `isPinned DESC, date DESC`. `getPinned()` exists but is unused — pinned filtering is done in `NotesViewModel`.

---

## AddEntrySheet (DayViewScreen)

The add-entry UI is a `ModalBottomSheet` (`skipPartiallyExpanded = true`) inside `DayViewScreen.kt`. Header row has "Hủy" (left) and "Lưu" (right).

**Cancel confirmation dialog** (`showCancelConfirm`):
- Pressing "Hủy" when `title.isNotBlank()` shows a GCal-style confirmation dialog.
- Both buttons are labeled "Hủy": top button (red = actually discard), bottom button (primary blue = continue editing).
- This matches Vietnamese GCal behavior — both buttons saying "Hủy" is intentional.

**Category-specific LaunchedEffects:**
```kotlin
LaunchedEffect(isAllDay) {
    if (isAllDay) {
        startTime = null; endTime = null; activePicker = ActivePicker.NONE
    } else if (startTime == null) {
        startTime = defaultStart   // restore when un-checking all-day
    }
}
LaunchedEffect(selectedCategory) {
    when (selectedCategory) {
        EntryCategory.TASK -> { isAllDay = true; activePicker = ActivePicker.NONE }
        else -> { if (isAllDay && startTime == null) isAllDay = false }
    }
    reminderMinutes = defaultNotifs[selectedCategory]   // GCal-style: reset to category default
}
```

`defaultNotifs: Map<EntryCategory, Int?>` is collected from `DayViewViewModel.defaultNotifs` (backed by `SettingsRepository.allDefaultNotifs`). Initial `reminderMinutes` = `defaultNotifs[initialCategory]`.

**Date/time section branches on `isTask` / `isBirthday`:**
- **TASK**: "Cả ngày" switch (defaults ON) + **tappable** date row → `InlineCalendarPicker` (toggle via `activePicker == START_DATE`). No time pickers. Deadline handled in Section 4c.
- **BIRTHDAY**: tappable date row → `WheelDatePicker`. "Cả ngày" switch. If `!isAllDay`: inline `Surface` cards + `WheelTimePicker` (same style as other categories). **Recurrence section hidden** — birthday has its own "Bao gồm năm" yearly toggle instead.
- **All other categories**: GCal-style inline pickers via `ActivePicker` enum (`NONE | START_DATE | START_TIME | END_DATE | END_TIME`). Tapping a chip toggles `InlineCalendarPicker` or `WheelTimePicker`.

**EntryDetailSheet** (`internal fun`, bottom of `DayViewScreen.kt`):
- Opens when user taps an entry card in Day or Week view
- Read-only: title, date/time, category, reminder, recurrence label, location, description
- "Sửa" button → animates sheet away → opens `AddEntrySheet` for editing
- "Xóa sự kiện": recurring entries → radio dialog ("Sự kiện này" / "Tất cả sự kiện"); non-recurring → deletes immediately
- WeekView/MonthView use `actionsVm: DayViewViewModel = hiltViewModel()` for save/delete

**FAB**: All 3 calendar views share `EntryFab` + `EntryFabMenuOverlay` from `ui/component/EntryFab.kt`. Placed in an outer `Box(fillMaxSize)` that wraps the Scaffold.

**Section 4c — Task deadline** (`isTask` only):
- `deadlineDate == null` → shows "Thêm thời hạn" button; tapping sets `deadlineDate = selectedDate; activePicker = END_DATE`
- `deadlineDate != null` → shows "Đến hạn lúc [EEEE, d MMM]" + X to clear + `InlineCalendarPicker` toggled by `activePicker == END_DATE`
- `END_DATE` value is reused for the deadline calendar picker (safe: TASK and non-TASK are mutually exclusive)

**Section 5 — Location** (before description):
- `BasicTextField` with `Icons.Outlined.LocationOn` icon
- Stored in `entry.location`, displayed in Day/Week/Journal cards when non-blank

**`derivedTimeOfDay = remember(startTime, isAllDay)`:**
- `isAllDay || startTime == null` → ANYTIME
- `startTime.hour < 12` → MORNING; `< 18` → AFTERNOON; else → EVENING

---

## Week View Layout

**Constants:**
```kotlin
SWIM_LANE_HEIGHT  = 130  // dp per MORNING/AFTERNOON/EVENING row
LABEL_COL_W       = 40   // dp — narrow left label column
COL_WIDTH         = 108.dp
HEADER_DATE_H     = 72   // dp — day abbreviation + day number
HEADER_ANYTIME_H  = 44   // dp — ANYTIME + deadline chips strip
HEADER_ROW_H      = 116  // dp total
```

- TopAppBar actions: Search (no-op) · ChevronLeft (`sharedVm.previousWeek()`) · ChevronRight (`sharedVm.nextWeek()`) · CalendarToday
- ANYTIME entries appear as chips in the header strip (not swim-lane rows).
- Deadline chips merged into ANYTIME strip: `allChips = anytimeEntries.map { it to false } + deadlineEntries.map { it to true }`, max 3 visible.
- Swim-lane rows: MORNING / AFTERNOON / EVENING only. Each cell shows up to 2 `WeekEntryCard`s + overflow "+N".
- `WeekEntryCard` shows location below time when `entry.location.isNotBlank()` (9sp font, `LocationOn` icon).
- Left label column and day columns share the same `verticalScroll` state; only day columns have `horizontalScroll`.

---

## Month View

- TopAppBar actions: Search (no-op) · CalendarToday (`viewModel.goToToday()` + `sharedVm.selectDate(LocalDate.now())`)
- No prev/next arrows (removed). Use month chip strip to navigate.
- `MonthViewViewModel.goToToday()` resets to `YearMonth.now()`.
- Chips show ±60 months with year-separator labels at year boundaries.

---

## ViewModels with Dual-Flow Pattern (Week + Month)

Both `WeekViewViewModel` and `MonthViewViewModel` use `combine()` inside `flatMapLatest` to merge regular entries and deadline entries:

```kotlin
combine(
    repo.getByDateRange(start, end),
    repo.getByDeadlineDateRange(start, end),
) { entries, deadlines ->
    UiState(
        entries         = entries.groupBy { it.date },
        deadlineEntries = deadlines.filter { it.deadlineDate != null }
                                   .groupBy { it.deadlineDate!! },
    )
}
```

---

## Settings Screen (SettingsScreen.kt)

Self-contained — no new routes in `AppNavigation`. Uses a local sealed interface `SettingsNav` and `BackHandler` for sub-screen navigation. Receives `SettingsViewModel` from `AppNavigation`.

**Sub-screens:**
| Screen | Key features |
|--------|-------------|
| Main | LazyColumn: Chung → General · user email header · built-in calendars (color dot = persisted override) · custom calendars (from Room) · "Thêm lịch" · Special section · About · Feedback (no-op) · Đăng xuất (calls `authVm.signOut()` → returns to AuthScreen) |
| Account | Avatar initials "QTA", Full Name `OutlinedTextField`, Email row, Add Password (no-op), 2FA switch, Delete Account (no-op) |
| General | Radio groups: Home View · Theme · Language — state from `SettingsViewModel`, changes persist immediately to DataStore |
| CalEdit | Color picker `ModalBottomSheet` (13 named colors) — saves immediately on pick. Default notification `DropdownMenu` with `REMINDER_PRESETS` — saves immediately on select. Hidden for HOLIDAY (no EntryCategory). Delete button only for `"CUSTOM_"` ids |
| About | App icon, "Scalendar", "Phiên bản 1.0.0" |

**Persistence layers:**
- General settings (theme/lang/homeView) + color overrides + default notifications → **DataStore Preferences** via `SettingsDataStore` / `SettingsRepository`
- Custom calendars → **Room** `user_calendars` table via `UserCalendarEntity` / `UserCalendarDao` / `UserCalendarRepository`
- Custom calendar color edits: re-insert via `userCalRepo.insert` with `OnConflictStrategy.REPLACE`
- Built-in / special color edits: stored as `cat_color_<id>` key in DataStore

**`SettingsNav.CalEdit` fields**: `id`, `name`, `colorHex`, `isSpecial`, `defaultNotifMinutes: Int? = null`

**Color palette** (`CAL_COLORS`): 13 named hex colors with Vietnamese names. `hexToColor(hex)` uses `android.graphics.Color.parseColor("#$hex")`.

**`REMINDER_PRESETS`**: `List<Pair<Int?, String>>` — null/"Không có thông báo", 0, 5, 10, 15, 30, 60, 120, 1440 minutes.

**HOLIDAY note**: `"HOLIDAY"` is NOT an `EntryCategory` enum value → `runCatching { EntryCategory.valueOf("HOLIDAY") }.getOrNull()` returns null → no default notification section in CalEdit for HOLIDAY.

**Built-in calendar ids**: `TASK`, `CLASS`, `SPORT`, `EXAM`, `EVENT`.
**Special calendar ids**: `BIRTHDAY`, `HOLIDAY`.

---

## Firebase Auth + Firestore Sync

**Auth gate**: `AppNavigation` collects `authVm.isLoggedIn: StateFlow<Boolean>` (backed by `FirebaseAuth.AuthStateListener` via `callbackFlow`). If false, renders `AuthScreen` instead of the main NavHost. `SharingStarted.Eagerly` ensures immediate evaluation without needing a subscriber.

**`AuthScreen`** has two tabs (Đăng nhập / Đăng ký), email + password fields, loading indicator inside button, and a Snackbar for errors. `friendlyError()` in `AuthViewModel` maps Firebase error codes to Vietnamese messages.

**On sign-in** → `pullFromFirestore(user)`: fetches all Firestore documents for that uid and calls `repo.insert()` for each (REPLACE semantics via `OnConflictStrategy.REPLACE`).

**On sign-up** → `pushLocalToFirestore(user)`: reads all local Room data via `getAllOnce()` and upserts each record to Firestore. `getAllOnce()` is a `suspend` (non-Flow) query added to all three DAOs and repositories.

**Write-through sync** — every local Room mutation is immediately mirrored to Firestore (fire-and-forget, wrapped in `runCatching`). Affected ViewModels:
| ViewModel | Synced operations |
|-----------|-------------------|
| `DayViewViewModel` | `saveEntry` (upsert), `deleteEntry` (delete), `toggleComplete` (partial update) |
| `JournalViewModel` | `toggleComplete` (partial update) |
| `NotesViewModel` | `addNote` (upsert), `deleteNote` (delete), `togglePin` (partial update) |
| `SettingsViewModel` | `addUserCalendar` (upsert), `deleteUserCalendar` (delete) |

**`FirestoreRepository`** paths: `users/{uid}/entries/{id}`, `users/{uid}/notes/{id}`, `users/{uid}/user_calendars/{id}`. All writes use `SetOptions.merge()` for upserts; partial updates use `.update(field, value)`. `EntryEntity` / `NoteEntity` serialise `LocalDate`/`LocalTime` as ISO strings (`.toString()`), enums as `.name`. Deserialisation uses `runCatching` so malformed Firestore documents are silently skipped.

**`google-services.json`** must be placed in `app/` before building. Firebase Auth email/password sign-in must be enabled in the Firebase console.

---

## Notification Reminders

`ReminderManager` stores `reminderOffsets` as a **comma-separated list of minute values** (e.g. `"30"` = fire 30 minutes before event start). Event base time = `entry.startTime` if set, else 09:00. Alarms use `setExactAndAllowWhileIdle`; falls back to `setAndAllowWhileIdle` on Android 12+ without exact-alarm permission. `AlarmReceiver` posts to channel `"scalendar_reminders"` (created in `ScalendarApp.onCreate()`). Required manifest permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`.

⚠ There is no `updateEntry()` path — `DayViewViewModel` only cancels reminders on delete. Editing an entry's time/reminder requires delete + re-add.

---

## Journal Screen

Shows only `EXAM`, `BIRTHDAY`, `EVENT` entries that are **not completed**, grouped by `YearMonth`, in a ±6-month rolling window. Notes on the same date auto-appear alongside entries via `JournalViewModel.notesByDate`. TASK/CLASS/SPORT entries do not appear in Journal. Entry cards show location when `entry.location.isNotBlank()`.

---

## Notes Screen

`NotesViewModel` groups notes into time buckets: PINNED / TODAY / LAST_7_DAYS / LAST_30_DAYS / LAST_MONTH / OLDER. Search is done in-memory via `_query` + `combine`. `NoteDao.getPinned()` and `NoteRepository.getPinned()` exist but are unused (pinned filtering happens inside `NotesViewModel`).

---

## Navigation Flow

Bottom nav: **Ghi chú** (Notes) · **Lịch** (Calendar) · **Nhật ký** (Journal) · **Cài đặt** (Settings).

Tapping "Lịch" routes to the sub-screen matching `calendarView` in `SharedCalendarViewModel`. The drawer (only enabled on calendar routes) switches Day/Week/Month and toggles category filters. Tapping a day in Week or Month view calls `sharedVm.selectDate(date)` then navigates to DayView.

Outer `Scaffold` uses `contentWindowInsets = WindowInsets(0)` to prevent double status-bar padding when `enableEdgeToEdge()` is active in `MainActivity`.

---

## Known Issues / TODOs

| # | Location | Issue |
|---|----------|-------|
| 1 | `ui/screen/addentry/` | `AddEntryScreen.kt` + `AddEntryViewModel.kt` are dead code — not wired into navigation. |
| 2 | `ReminderManager` | `requestCode`/`notifId` = `((entryId * 100) + minutesBefore).toInt()` — can overflow for large `entryId` values after many recurrence expansions. |
| 3 | All 3 calendar views | Search `IconButton` is a no-op placeholder (`onClick = { /* search */ }`). |
| 4 | `MonthViewScreen` | Month chip navigation updates only `MonthViewViewModel._yearMonth`, not `sharedVm.selectedDate`. Tapping a day cell does update `sharedVm`. |
| 5 | `DayViewViewModel` | `_date` is synced from `SharedCalendarViewModel` via `LaunchedEffect` in the screen — one-frame lag; briefly shows `LocalDate.now()` data before the correct date loads. |
| 6 | `WeekViewScreen` "Hôm nay" button | Calls `sharedVm.selectDate(LocalDate.now())` but does NOT navigate to DayView. |
| 7 | `NoteDao.getPinned()` | Exposed in DAO and Repository but unused; pinned filtering is done in `NotesViewModel`. |
| 8 | `SettingsScreen` | Custom calendars do not appear in AddEntrySheet's category picker (only built-in `EntryCategory` entries shown). |
| 9 | Onboarding | Splash → Onboarding steps → Auth flow not yet implemented (plan exists in plan file). |

---

## UI Locale & Language Switching

The app supports **Vietnamese (VI)** and **English (EN)** switching via Settings → General → Ngôn ngữ.

**How locale switching works:**
- `util/LocaleHelper.kt` — stores lang in SharedPreferences (`"scalendar_locale"` prefs file, key `"lang"`); `wrap()` creates a locale-overridden Context via `createConfigurationContext`.
- `MainActivity.attachBaseContext()` — calls `LocaleHelper.wrap(newBase, getLang(newBase))` so the Activity starts with the correct locale from the first frame.
- `MainActivity` `LaunchedEffect(uiState.lang)` — writes the DataStore lang to SharedPreferences and calls `recreate()` if locale mismatch detected.
- All `DateTimeFormatter` instances are created **inside composables** using `remember(locale) { DateTimeFormatter.ofPattern("...", locale) }` where `locale = LocalConfiguration.current.locales[0]`. No file-level `private val` formatters remain in screen files.
- DOW labels (T2–CN / Mo–Su) and `InlineCalendarPicker` grid headers are also locale-branched via `remember(locale)`.

**String resources:**
- `res/values/strings.xml` — Vietnamese (default), 80+ strings covering all UI labels.
- `res/values-en/strings.xml` — English translations.
- `EntryCategory.localizedName()` and `TimeOfDay.localizedName()` are `@Composable` extensions in `Entry.kt` backed by `stringResource()`. Use these in composables; keep `displayName()` for non-composable contexts (ViewModel logic, etc.).

**Day-of-week header abbreviations:** Vietnamese = T2–T7/CN; English = Mo–Su. Handled in `InlineCalendarPicker`, `MonthViewScreen`, and `JournalScreen.shortDayName(locale)`.

**Note:** `AddEntryScreen.kt` (dead code) still has hardcoded VI formatters — acceptable since it's not wired into navigation.


