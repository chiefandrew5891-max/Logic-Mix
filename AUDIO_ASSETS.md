# Background music

The app builds and runs without any music files. When none exist, the music settings still work and nothing plays
(Logcat, tag `MusicController`: `No music_NN files in res/raw`).

## Directory and filenames

`app/src/main/res/raw/` — `music_01.m4a`, `music_02.m4a`, `music_03.m4a`, `music_04.m4a`, `music_05.m4a`.

The list is not limited to five: any `music_NN.m4a` up to `music_50` (two digits, lowercase) is picked up
automatically, in numeric order. Gaps are fine. For a name outside this pattern, append it to
`MusicPlaylist.extraTrackNames` (`core/audio/MusicPlaylist.kt`). Android resource names allow only `a-z`, `0-9`, `_`.

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
