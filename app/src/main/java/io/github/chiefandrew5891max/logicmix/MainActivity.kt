package io.github.chiefandrew5891max.logicmix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.LocaleApplier
import javax.inject.Inject
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTheme
import io.github.chiefandrew5891max.logicmix.feature.home.ComingSoonScreen
import io.github.chiefandrew5891max.logicmix.feature.home.HomeRoute
import io.github.chiefandrew5891max.logicmix.feature.settings.SettingsScreen

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
                LogicMixNavigation(rememberNavController())
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val COMING_SOON = "coming_soon"
}

@Composable
private fun LogicMixNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(
                onPlay = { navController.navigate(Routes.COMING_SOON) },
                onRewards = { navController.navigate(Routes.COMING_SOON) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.COMING_SOON) {
            ComingSoonScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
