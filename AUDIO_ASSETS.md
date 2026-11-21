# Background music

The app builds and runs without any music files. When none exist, the music settings still work and nothing plays
(Logcat, tag `MusicController`: `No music_NN files in res/raw`).

## Directory and filenames

`app/src/main/res/raw/` — `music_01.m4a`, `music_02.m4a`, `music_03.m4a`, `music_04.m4a`, `music_05.m4a`.

The list is not limited to five: any `music_NN.m4a` up to `music_50` (two digits, lowercase) is picked up
automatically, in numeric order. Gaps are fine. For a name outside this pattern, append it to
`MusicPlaylist.extraTrackNames` (`core/audio/MusicPlaylist.kt`). Android resource names allow only `a-z`, `0-9`, `_`.

Original (Suno) file names are NOT picked up: rename the files to `music_01.m4a`, `music_02.m4a`, ... Order of play is
the numeric order of the names; the playlist then loops.

## Sound effects

Put the 34 WAV files directly into `app/src/main/assets/sfx/` (no subfolders) with the exact names below.
`SoundEffectsController` (`core/audio/SoundEffectsController.kt`, SoundPool, loaded once at startup) plays them over the
music without touching it. It follows the existing Settings: effects switch and effects volume (independent of music),
no sound while the app is in the background. A missing/broken file is logged (tag `SoundEffects`) and skipped.
Anti-chaos rules live in `core/audio/SfxRules.kt` (double-trigger guard, rate limit for move sounds, UI clicks never
overlap each other or a major reward).

Status: **wired** = hooked to a real event; **pending** = the game event does not exist yet (call `Sfx.X` there later).

| File | Event | Where | Status |
|---|---|---|---|
| sfx_button_tap | the single sound for every card/tab tap: Rewards, Daily, Career, Endless, categories, Continue, Settings | MainActivity | wired |
| sfx_menu_open | unused (Settings now uses button_tap) | - | unused |
| sfx_menu_close | the single Back sound (arrow, system Back, gesture); only when a screen was actually left | MainActivity.goBack | wired |
| sfx_screen_transition | unused | - | unused |
| sfx_toggle | settings switches | SettingsViewModel | wired |
| sfx_card_select | unused (categories use button_tap) | - | unused |
| sfx_correct | right answer (Number Sequences) | GameViewModel.submit | wired |
| sfx_wrong | wrong answer | GameViewModel.submit | wired |
| sfx_matchstick_pickup / _move / _place | matchsticks | - | pending (no Matchsticks game) |
| sfx_letter_select | first letter of a swipe | WordSearchViewModel.onTrace | wired |
| sfx_word_trace_base | next letters of a swipe; pitch rises along a major scale up to +12 semitones, resets per gesture | WordSearchViewModel.onTrace | wired |
| sfx_word_found | main or extra word | WordSearchViewModel.submit | wired |
| sfx_word_invalid | unknown/duplicate word | WordSearchViewModel.submit | wired |
| sfx_puzzle_move / _place | puzzle pieces | - | pending |
| sfx_xp_gain | XP actually awarded | GameViewModel, WordSearchViewModel | wired |
| sfx_bonus_points, sfx_coin_reward, sfx_reward | - | - | pending (no such rewards yet) |
| sfx_combo | `playCombo(multiplier)`: x2 +0, x3 +2, x4 +4, x5 +5, x6 +7, x7+ +9 semitones, rate = 2^(n/12) | SoundEffectsController | pending (no combo yet) |
| sfx_streak, sfx_achievement, sfx_daily_complete | - | - | pending (no such events yet) |
| sfx_level_complete | level solved | GameViewModel, WordSearchViewModel | wired |
| sfx_level_up | player level increased | same | wired |
| sfx_hint | hint used | GameViewModel, WordSearchViewModel | wired |
| sfx_timer_warning, sfx_game_over | - | - | pending (no timer/fail state) |
| sfx_letter_input / _delete | crossword | - | pending (no crossword) |
| sfx_number_delete | each digit actually removed (also while holding backspace; empty field is silent) | GameViewModel.onInput | wired |
| sfx_number_input | each digit typed in the answer field (Number Sequences); no button_tap on the same press | GameViewModel.onInput | wired |

## Format

AAC in an M4A container (`.m4a`), played with AndroidX Media3 ExoPlayer. Keep files modest in size: they go into the APK.

## Adding tracks

1. In Android Studio switch the Project view to **Project** and open `app/src/main/res/raw`.
2. Copy the `.m4a` files there, renamed to `music_01.m4a`, `music_02.m4a`, ...
3. **Build > Rebuild Project** and run on the device. No code changes are needed.
4. Commit: `git add app/src/main/res/raw/*.m4a && git commit -m "feat(audio): add background music" && git push`.

## Behaviour

- One app-wide player (`core/audio/MusicController.kt`): tracks play in order, the playlist loops, no shuffle.
  Navigation does not restart it. `skipToNext()` is ready for a future button.
- Settings > Music switch and Music volume (existing DataStore settings) apply immediately and persist.
- Music pauses when the app goes to the background and resumes on return if enabled. Calls and other apps are handled
  through audio focus. Unplugging headphones pauses it. No foreground service or notification.
- Sound effects will use the separate effects volume; music uses only the music volume.
- A broken track is skipped; if all fail, playback stays silent.

## Testing on a device

Add files, rebuild, open the app: music starts. Toggle the switch and move the slider; press Home (pauses) and return
(resumes); start a call or another player (music yields).
