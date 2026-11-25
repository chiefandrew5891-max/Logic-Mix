package com.akynex.logicmix

import com.akynex.logicmix.core.audio.LocalUiFeedback
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.content.GameStyle
import com.akynex.logicmix.domain.content.GameStylePrefs
import com.akynex.logicmix.domain.content.StartTarget
import com.akynex.logicmix.feature.gamestyle.GameStyleRoute
import com.akynex.logicmix.feature.gamestyle.StyleStep
import com.akynex.logicmix.feature.home.PrimaryAction
import com.akynex.logicmix.feature.arcade.ArcadeRoute
import com.akynex.logicmix.feature.home.StyleInfoScreen
import com.akynex.logicmix.feature.home.titleRes
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.akynex.logicmix.core.audio.Sfx
import com.akynex.logicmix.core.audio.SoundEffectsController
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.designsystem.ThemeMode
import com.akynex.logicmix.core.locale.LocaleApplier
import javax.inject.Inject
import com.akynex.logicmix.core.designsystem.LogicMixTheme
import com.akynex.logicmix.domain.game.GameMode
import com.akynex.logicmix.feature.game.GameRoute
import com.akynex.logicmix.feature.home.ComingSoonScreen
import com.akynex.logicmix.feature.matchstick.MatchstickRoute
import com.akynex.logicmix.feature.orb.OrbRoute
import com.akynex.logicmix.feature.wordlogic.WordLogicRoute
import com.akynex.logicmix.feature.crossword.CrosswordRoute
import com.akynex.logicmix.feature.twodots.TwoDotsRoute
import com.akynex.logicmix.feature.visual.VisualRoute
import com.akynex.logicmix.feature.sudoku.SudokuRoute
import com.akynex.logicmix.feature.home.HomeRoute
import com.akynex.logicmix.feature.privacy.PrivacyPolicyScreen
import com.akynex.logicmix.feature.settings.SettingsScreen
import com.akynex.logicmix.feature.wordsearch.WordSearchRoute

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sfx: SoundEffectsController
    @Inject lateinit var monetization: com.akynex.logicmix.core.monetization.Monetization
    @Inject lateinit var games: com.akynex.logicmix.core.games.GamesCoordinator
    @Inject lateinit var review: com.akynex.logicmix.core.review.ReviewCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) monetization.gatherConsent(this)
        // Cold start only: a recreated activity (rotation, process restore) has a saved state and skips the splash.
        val showSplashInitially = savedInstanceState == null
        setContent {
            var showSplash by rememberSaveable { mutableStateOf(showSplashInitially) }
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val language by settings.language.collectAsState(initial = null)
            LaunchedEffect(language) { language?.let { LocaleApplier.apply(it) } }
            LogicMixTheme(themeMode) {
                val gameStyle by settings.gameStyle.collectAsState(initial = null)
                CompositionLocalProvider(LocalUiFeedback provides sfx) {
                    androidx.compose.foundation.layout.Box {
                        LogicMixNavigation(rememberNavController(), gameStyle, sfx, review)
                        com.akynex.logicmix.feature.levelup.LevelUpHost()
                        if (showSplash) {
                            BackHandler { }
                            com.akynex.logicmix.feature.splash.AnimatedSplashScreen { showSplash = false }
                        }
                    }
                }
            }
        }
    }

    // The review flow may only start while this screen is in front (no ad, consent form or other activity on top).
    override fun onResume() {
        super.onResume()
        review.attach(this)
        games.attach(this)
    }

    override fun onPause() {
        review.detach(this)
        games.detach(this)
        super.onPause()
    }
}

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val COMING_SOON = "coming_soon"
    const val GAME = "game"
    const val WORD_SEARCH = "word_search"
    const val SUDOKU = "sudoku"
    const val MATCHSTICKS = "matchsticks"
    const val ORB_LINES = "orb_lines"
    const val TWO_DOTS = "two_dots"
    const val WORD_LOGIC = "word_logic"
    const val MINI_CROSSWORD = "mini_crossword"
    const val VISUAL_LOGIC = "visual_logic"
    const val CATEGORY_SOON = "category_soon/{category}"
    const val GAME_STYLE = "game_style/{start}"
    const val STYLE_INFO = "style_info/{style}"
    const val ARCADE = "arcade"
    const val PRIVACY_POLICY = "privacy_policy"
    const val LEADERBOARDS = "leaderboards"

    fun gameStyle(start: StyleStep) = "game_style/${start.name}"
    fun styleInfo(style: GameStyle) = "style_info/${style.name}"

    /** Every category opens a real screen or an honest localized placeholder; none is a dead tap. */
    fun forCategory(category: GameCategory): String = when (category) {
        GameCategory.NUMBER_SEQUENCE -> GAME
        GameCategory.WORD_SEARCH -> WORD_SEARCH
        GameCategory.SUDOKU -> SUDOKU
        GameCategory.MATCHSTICK -> MATCHSTICKS
        GameCategory.ORB_LINES -> ORB_LINES
        GameCategory.TWO_DOTS -> TWO_DOTS
        GameCategory.WORD_LOGIC -> WORD_LOGIC
        GameCategory.MINI_CROSSWORD -> MINI_CROSSWORD
        GameCategory.VISUAL_LOGIC -> VISUAL_LOGIC
        else -> "category_soon/${category.name}"
    }

    fun forTarget(target: StartTarget): String = when (target) {
        StartTarget.Default -> GAME
        is StartTarget.Category -> forCategory(target.category)
        is StartTarget.StyleInfo -> styleInfo(target.style)
        StartTarget.Arcade -> ARCADE
    }
}

@Composable
private fun LogicMixNavigation(navController: NavHostController, gameStyle: GameStylePrefs?, sfx: SoundEffectsController, review: com.akynex.logicmix.core.review.ReviewCoordinator) {
    // The selector is pushed once per launch for installs that have not completed it yet.
    var onboardingShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gameStyle?.onboardingDone) {
        if (gameStyle != null && !gameStyle.onboardingDone && !onboardingShown) {
            onboardingShown = true
            navController.navigate(Routes.gameStyle(StyleStep.STYLE))
        }
    }
    val openFromFlow: (StartTarget) -> Unit = { target ->
        navController.navigate(Routes.forTarget(target)) { popUpTo(Routes.HOME) }
    }
    // Pops only when there is a screen to return to, so a double tap can never empty the stack.
    val goBack: () -> Unit = {
        if (navController.previousBackStackEntry != null && navController.popBackStack()) sfx.play(Sfx.MENU_CLOSE)
    }
    val current by navController.currentBackStackEntryAsState()
    // Keyed by the route, so recomposition and rotation (same route) never re-trigger it.
    val route = current?.destination?.route
    LaunchedEffect(route) { if (route == Routes.HOME) review.onMainMenu() }
    BackHandler(enabled = current?.destination?.route != Routes.HOME && navController.previousBackStackEntry != null, onBack = goBack)
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(
                onPlay = { mode ->
                    sfx.play(Sfx.BUTTON_TAP)
                    navController.navigate(if (mode == GameMode.CAREER) Routes.GAME else Routes.COMING_SOON)
                },
                onRewards = { sfx.play(Sfx.BUTTON_TAP); navController.navigate(Routes.COMING_SOON) },
                onSettings = { sfx.play(Sfx.BUTTON_TAP); navController.navigate(Routes.SETTINGS) },
                onLeaderboards = { sfx.play(Sfx.BUTTON_TAP); navController.navigate(Routes.LEADERBOARDS) },
                onCategory = { sfx.play(Sfx.BUTTON_TAP); navController.navigate(Routes.forCategory(it)) },
                onPrimary = { state ->
                    sfx.play(Sfx.BUTTON_TAP)
                    val target = StartTarget.resolve(state.primaryAction == PrimaryAction.CONTINUE, state.gameStyle, state.categories)
                    navController.navigate(Routes.forTarget(target))
                },
            )
        }
        composable(Routes.GAME) {
            GameRoute(onBack = goBack)
        }
        composable(Routes.WORD_SEARCH) {
            WordSearchRoute(onBack = goBack)
        }
        composable(Routes.SUDOKU) {
            SudokuRoute(onBack = goBack)
        }
        composable(Routes.ORB_LINES) {
            OrbRoute(onBack = goBack)
        }
        composable(Routes.TWO_DOTS) {
            TwoDotsRoute(onBack = goBack)
        }
        composable(Routes.WORD_LOGIC) {
            WordLogicRoute(onBack = goBack)
        }
        composable(Routes.MINI_CROSSWORD) {
            CrosswordRoute(onBack = goBack)
        }
        composable(Routes.VISUAL_LOGIC) {
            VisualRoute(onBack = goBack)
        }
        composable(Routes.MATCHSTICKS) {
            MatchstickRoute(onBack = goBack)
        }
        composable(Routes.CATEGORY_SOON, arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
            val category = GameCategory.entries.firstOrNull { it.name == entry.arguments?.getString("category") }
            ComingSoonScreen(onBack = goBack, categoryTitle = category?.titleRes())
        }
        composable(Routes.COMING_SOON) {
            ComingSoonScreen(onBack = goBack)
        }
        composable(Routes.GAME_STYLE, arguments = listOf(navArgument("start") { type = NavType.StringType })) { entry ->
            val start = StyleStep.entries.firstOrNull { it.name == entry.arguments?.getString("start") } ?: StyleStep.STYLE
            GameStyleRoute(start = start, onFinished = openFromFlow, onBack = goBack)
        }
        composable(Routes.ARCADE) {
            ArcadeRoute(
                onBack = goBack,
                onEditCategories = { navController.navigate(Routes.gameStyle(StyleStep.ARCADE)) },
            )
        }
        composable(Routes.STYLE_INFO, arguments = listOf(navArgument("style") { type = NavType.StringType })) { entry ->
            val style = GameStyle.fromStored(entry.arguments?.getString("style")) ?: GameStyle.ARCADE
            StyleInfoScreen(
                style = style,
                onHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onPlayAvailable = { navController.navigate(Routes.GAME) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = goBack,
                onGameStyleStep = { navController.navigate(Routes.gameStyle(it)) },
                onPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
            )
        }
        composable(Routes.LEADERBOARDS) {
            com.akynex.logicmix.feature.leaderboard.LeaderboardScreen(onBack = goBack)
        }
        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = goBack)
        }
    }
}
