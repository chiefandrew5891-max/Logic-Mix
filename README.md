# Logic Mix

Android-прототип. Этап A: автономный запуск, локальный профиль Room, настройка
звука в DataStore, переход между главным экраном и настройками через Navigation
Compose, светлая/тёмная тема Material 3 с временными семантическими токенами.
Финальные макеты и палитра пока не утверждены. Игровые механики и Firebase
в этот этап не входят. Прежний `index.html` сохранён как тестовый файл.

Структура сейчас — один Gradle-модуль `app` с границами пакетами
`core/database`, `core/data`, `core/designsystem`, `feature/home`,
`feature/settings`. При добавлении игровых движков и контента эти границы
можно выделить в отдельные модули.

Для локальной сборки требуются JDK 17 и Android SDK (платформа API 36).

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

CI выполняет те же проверки на push и pull request. Первый запуск не требует
интернета, Google Services или ключей; база создаётся локально. Схему Room v1
сборка экспортирует в `app/schemas/`; перед изменением схемы необходимо
сохранить экспортированную версию, написать миграцию и тест обновления базы.

## Обязательное правило интерфейса: единый футер AKYneX studio

**Каждый новый экран Logic Mix автоматически наследует единый фиксированный фирменный футер AKYneX studio. Это постоянный стандарт дизайна приложения.**

- Футер — один компонент `core/designsystem/BrandFooter.kt`. Это **низ страницы**, а не панель экрана: он последний элемент прокручиваемого содержимого каждого экрана и прокручивается вместе с ним. **Sticky Footer:** на коротких страницах футер прижат к нижней границе доступной области, на длинных стоит после всего контента.
- Новый экран обязан заканчивать свой прокручиваемый `Column(Modifier.fillMaxSize().verticalScroll(...))` вызовом `BrandFooter()` и использовать `verticalArrangement = stickyFooterArrangement(spacing)` (`core/designsystem/StickyFooter.kt`, см. `docs/UI_ARCHITECTURE.md`; фиксированные высоты и отступы запрещены); экраны на `CategoryScaffold` получают его автоматически. Свою копию футера рисовать нельзя.
- Премиум-оформление — только общие компоненты `core/designsystem/PremiumDesign.kt`: `PremiumCard` (`premiumSurface`), `PremiumGoldButton`, `PremiumProgressBar`, `PremiumAvatar`, `PremiumIconButton`, `PremiumSectionHeader` и токены `PremiumTokens`. Эффекты рисуются через `drawWithCache` (без blur и offscreen-слоёв), размеры экрана считаются от доступной ширины. Игровые поля декором не перегружать. Куб на главном экране — `feature/splash/AnimatedCube` с `CubeMotionConfig.Home` (амплитуда пульсации в 3 раза меньше заставки). Интерактивные состояния (Default/Pressed/Selected) задаются в `InteractiveTokens` и `interactiveNeon` (`core/designsystem/InteractiveNeon.kt`): неоновая рамка и внешнее/внутреннее свечение при касании, градиентная заливка при выборе. Заголовки карточек выводятся через `WordSafeText`: слова не разрываются, при нехватке места шрифт уменьшается не ниже 70%. Фон всех экранов — статичные изображения `premium_background_dark/light.webp` (`res/drawable-nodpi`, 800×2200, 150/49 КБ, генератор `tools/gen_background.py`), показываются через `LogicMixBackdrop`/`LogicMixScreen` (`core/designsystem/PremiumBackdrop.kt`) с параллаксом от прокрутки: основной скролл экрана подключается как `backdropVerticalScroll()`, смещение — `translationY` слоя в фазе размещения (`BackdropMath.kt`), без анимаций, таймеров и перерисовки при отсутствии прокрутки. Нажатие карточек начинается с касания (`instantPress`), а не после задержки `clickable` в прокручиваемом контейнере.
- Футер сам учитывает системную навигационную панель (`navigationBarsPadding`, не удваивается с `safeDrawingPadding`).
- Дизайн (текст «© 2026 AKYneX studio™», строка `footer_rights`, шрифты 12/11 sp, цвета `onSurfaceVariant`) менять только осознанно и только в этом компоненте. Оверлеи (`LevelUpHost`) рисуются поверх экрана.
