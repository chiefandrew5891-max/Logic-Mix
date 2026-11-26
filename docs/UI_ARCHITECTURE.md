# UI: Sticky Footer

Правило для всех экранов (существующих и будущих): `BrandFooter` — последний элемент прокручиваемого `Column`.

- Колонка экрана: `Modifier.fillMaxSize().verticalScroll(...)`, `verticalArrangement = stickyFooterArrangement(spacing)`.
- `verticalScroll` сохраняет минимальную высоту, равную области просмотра (с учётом системных отступов), поэтому `stickyFooterArrangement` прижимает последний элемент к низу, когда контент короче экрана, и оставляет его после контента, когда длиннее. Масштаб шрифта и клавиатура учитываются автоматически.
- Экраны на `CategoryScaffold` получают поведение без дополнительных действий.
- Полноэкранная заставка (`AnimatedSplashScreen`) — оверлей над экраном и футер не показывает.
- Не копировать футер и не задавать высоты вручную.

## Premium-компоненты

- Премиум-оформление — только общие компоненты `core/designsystem/PremiumDesign.kt`: `PremiumAuroraBackground`, `PremiumCard` (`premiumSurface`), `PremiumGoldButton`, `PremiumProgressBar`, `PremiumAvatar`, `PremiumIconButton`, `PremiumSectionHeader` и токены `PremiumTokens`. Эффекты рисуются через `drawWithCache` (без blur и offscreen-слоёв), размеры экрана считаются от доступной ширины. Игровые поля декором не перегружать. Фон `PremiumAuroraBackground` — один независимый анимированный слой за экраном (вращающийся «стеклянный диск» из лент, период каждого движения делит `LOOP_CLOCK_WRAP_SECONDS`, часы `rememberLoopClock` стоят в фоне приложения); куб на главном экране — `feature/splash/AnimatedCube` с `CubeMotionConfig.Home` (амплитуда пульсации в 3 раза меньше заставки). Интерактивные состояния (Default/Pressed/Selected) задаются в `InteractiveTokens` и `interactiveNeon` (`core/designsystem/InteractiveNeon.kt`): неоновая рамка и внешнее/внутреннее свечение при касании, градиентная заливка при выборе. Заголовки карточек выводятся через `WordSafeText`: слова не разрываются, при нехватке места шрифт уменьшается не ниже 70%. Фон всех экранов — `LogicMixBackdrop`/`LogicMixScreen` (`core/designsystem/SilkBackdrop.kt`): базовый градиент и «шёлковые» ленты из тонких кривых Безье, отдельный узор и палитра для тёмной и светлой тем, период всех движений делит 3600 с (бесшовный цикл). Нажатие карточек начинается с касания (`instantPress`), а не после задержки `clickable` в прокручиваемом контейнере.
