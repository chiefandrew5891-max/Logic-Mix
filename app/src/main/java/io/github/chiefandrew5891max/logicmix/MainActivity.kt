package io.github.chiefandrew5891max.logicmix

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
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle
import io.github.chiefandrew5891max.logicmix.domain.content.GameStylePrefs
import io.github.chiefandrew5891max.logicmix.domain.content.StartTarget
import io.github.chiefandrew5891max.logicmix.feature.gamestyle.GameStyleRoute
import io.github.chiefandrew5891max.logicmix.feature.gamestyle.StyleStep
import io.github.chiefandrew5891max.logicmix.feature.home.PrimaryAction
import io.github.chiefandrew5891max.logicmix.feature.home.StyleInfoScreen
import io.github.chiefandrew5891max.logicmix.feature.home.titleRes
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.LocaleApplier
import javax.inject.Inject
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTheme
import io.github.chiefandrew5891max.logicmix.domain.game.GameMode
import io.github.chiefandrew5891max.logicmix.feature.game.GameRoute
import io.github.chiefandrew5891max.logicmix.feature.home.ComingSoonScreen
import io.github.chiefandrew5891max.logicmix.feature.home.HomeRoute
import io.github.chiefandrew5891max.logicmix.feature.settings.SettingsScreen
import io.github.chiefandrew5891max.logicmix.feature.wordsearch.WordSearchRoute

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val language by settings.language.collectAsState(initial = null)
            LaunchedEffect(language) { language?.let { LocaleApplier.apply(it) } }
            LogicMixTheme(themeMode) {
                val gameStyle by settings.gameStyle.collectAsState(initial = null)
                LogicMixNavigation(rememberNavController(), gameStyle)
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val COMING_SOON = "coming_soon"
    const val GAME = "game"
    const val WORD_SEARCH = "word_search"
    const val SUDOKU = "sudoku"
    const val CATEGORY_SOON = "category_soon/{category}"
    const val GAME_STYLE = "game_style/{start}"
    const val STYLE_INFO = "style_info/{style}"

    fun gameStyle(start: StyleStep) = "game_style/${start.name}"
    fun styleInfo(style: GameStyle) = "style_info/${style.name}"

    /** Every category opens a real screen or an honest localized placeholder; none is a dead tap. */
    fun forCategory(category: GameCategory): String = when (category) {
        GameCategory.NUMBER_SEQUENCE -> GAME
        GameCategory.WORD_SEARCH -> WORD_SEARCH
        GameCategory.SUDOKU -> SUDOKU
        else -> "category_soon/${category.name}"
    }

    fun forTarget(target: StartTarget): String = when (target) {
        StartTarget.Default -> GAME
        is StartTarget.Category -> forCategory(target.category)
        is StartTarget.StyleInfo -> styleInfo(target.style)
    }
}

@Composable
private fun LogicMixNavigation(navController: NavHostController, gameStyle: GameStylePrefs?) {
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
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(
                onPlay = { mode ->
                    navController.navigate(if (mode == GameMode.CAREER) Routes.GAME else Routes.COMING_SOON)
                },
                onRewards = { navController.navigate(Routes.COMING_SOON) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onCategory = { navController.navigate(Routes.forCategory(it)) },
                onPrimary = { state ->
                    val target = StartTarget.resolve(state.primaryAction == PrimaryAction.CONTINUE, state.gameStyle, state.categories)
                    navController.navigate(Routes.forTarget(target))
                },
            )
        }
        composable(Routes.GAME) {
            GameRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.WORD_SEARCH) {
            WordSearchRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SUDOKU) {
            ComingSoonScreen(onBack = { navController.popBackStack() }, categoryTitle = GameCategory.SUDOKU.titleRes())
        }
        composable(Routes.CATEGORY_SOON, arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
            val category = GameCategory.entries.firstOrNull { it.name == entry.arguments?.getString("category") }
            ComingSoonScreen(onBack = { navController.popBackStack() }, categoryTitle = category?.titleRes())
        }
        composable(Routes.COMING_SOON) {
            ComingSoonScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GAME_STYLE, arguments = listOf(navArgument("start") { type = NavType.StringType })) { entry ->
            val start = StyleStep.entries.firstOrNull { it.name == entry.arguments?.getString("start") } ?: StyleStep.STYLE
            GameStyleRoute(start = start, onFinished = openFromFlow, onBack = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() },
                onGameStyleStep = { navController.navigate(Routes.gameStyle(it)) },
            )
        }
    }
}
