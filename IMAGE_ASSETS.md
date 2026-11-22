# Image assets (12 PNG files)

The app builds and runs **without** these files: missing images are replaced by a Compose-drawn placeholder
(and the text title on the home screen). Once a correctly named file is added and the app is rebuilt,
the real image is used automatically — no Kotlin changes are needed. Lookup is by resource name
(`core/designsystem/NeonComponents.kt`, `rememberDrawableId`).

## Destination

`app/src/main/res/drawable-nodpi/`

## Files

| Purpose | Filename |
|---|---|
| Matchsticks | `game_matchsticks.png` |
| Number sequences | `game_number_sequences.png` |
| Word logic | `game_word_logic.png` |
| Mini crosswords | `game_mini_crosswords.png` |
| Visual logic | `game_visual_logic.png` |
| Fillwords | `game_fillwords.png` |
| Sudoku | `game_sudoku.png` |
| Orb Lines | `game_orb_lines.png` |
| Two Dots | `game_two_dots.png` |
| Arcade style | `mode_arcade.png` |
| Trainer style | `mode_trainer.png` |
| Individual categories style | `mode_categories.png` |
| Main-screen wordmark | `logo_logic_mix_main.png` |

Names must be lowercase, use only `a-z`, `0-9`, `_`, and keep the `.png` extension. Do not add titles to the images:
category and style names are real localized text drawn by the app. The launcher icon is not affected.

## Adding them

1. In Android Studio's Project view switch to **Project**, open `app/src/main/res/drawable-nodpi`.
2. Copy the 11 PNG files into that folder (drag and drop, or paste), confirm the dialog.
3. Rebuild: **Build > Rebuild Project**, then Run on the device.
4. Commit and push:

```
git add app/src/main/res/drawable-nodpi/*.png
git commit -m "feat(ui): add 3D category, style and logo artwork"
git push origin <your-branch>
```

`app/src/main/res/raw/keep.xml` keeps these resources from being stripped if resource shrinking is enabled later.

## Reward icons
`GoldStar` and `GoldTrophy` (core/designsystem/RewardIcons.kt) are programmatic placeholders; final artwork should be provided as transparent PNG/WebP (`reward_gold_star`, `reward_gold_trophy`).

## 3D-иконки главного экрана (`app/src/main/res/drawable-nodpi/`)

PNG с прозрачным фоном. Если файла нет, показывается прежняя векторная иконка; код менять не нужно.

| Файл | Где используется |
|---|---|
| `icon_daily_challenge.png` | карточка «Ежедневные испытания» |
| `icon_rewards_trophy.png` | карточка «Награды», `GoldTrophy` |
| `icon_career.png` | карточка «Карьера» |
| `icon_endless_mode.png` | карточка «Бесконечный режим» |
| `icon_bonus_star.png` | `GoldStar` (XP/бонусы) |

## Правила для новых игр

- Экран обязан использовать `GameHeader(onRestart = ...)` — Restart с подтверждением.
- Подписи кнопок — через `GameToolRow` / `rememberButtonFit` / `FitLabel` (без переноса внутри слов).
