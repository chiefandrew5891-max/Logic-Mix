package io.github.chiefandrew5891max.logicmix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.chiefandrew5891max.logicmix.core.data.SettingsRepository
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage
import io.github.chiefandrew5891max.logicmix.core.locale.LocaleApplier
import javax.inject.Inject
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTheme
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.feature.home.HomeViewModel
import io.github.chiefandrew5891max.logicmix.feature.settings.SettingsScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val language by settings.language.collectAsState(initial = AppLanguage.fromStored(null))
            LaunchedEffect(language) { LocaleApplier.apply(language) }
            LogicMixTheme(themeMode) {
                LogicMixNavigation(rememberNavController())
            }
        }
    }
}

@Composable
private fun LogicMixNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = hiltViewModel()
            val profile by viewModel.profile.collectAsState(initial = null)
            Scaffold { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(LogicMixTokens.spacing),
                    verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing),
                ) {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                    Text(if (profile == null) stringResource(R.string.loading_profile) else stringResource(R.string.local_profile))
                    if (profile != null) Text(stringResource(R.string.offline_ready))
                    Button(onClick = { navController.navigate("settings") }) {
                        Text(stringResource(R.string.settings))
                    }
                }
            }
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
