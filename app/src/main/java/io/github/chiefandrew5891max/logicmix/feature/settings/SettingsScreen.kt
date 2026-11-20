package io.github.chiefandrew5891max.logicmix.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.chiefandrew5891max.logicmix.BuildConfig
import io.github.chiefandrew5891max.logicmix.R
import io.github.chiefandrew5891max.logicmix.core.data.AudioSettings
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixIcon
import io.github.chiefandrew5891max.logicmix.core.designsystem.LogicMixTokens
import io.github.chiefandrew5891max.logicmix.core.designsystem.ThemeMode
import io.github.chiefandrew5891max.logicmix.core.locale.AppLanguage

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val audio by viewModel.audio.collectAsState(initial = DefaultAudio)
    val theme by viewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val language by viewModel.language.collectAsState(initial = AppLanguage.SYSTEM)
    SettingsContent(
        audio = audio,
        theme = theme,
        language = language,
        onBack = onBack,
        onMusicEnabled = viewModel::setMusicEnabled,
        onEffectsEnabled = viewModel::setEffectsEnabled,
        onMusicVolume = viewModel::setMusicVolume,
        onEffectsVolume = viewModel::setEffectsVolume,
        onVibration = viewModel::setVibrationEnabled,
        onTheme = viewModel::setThemeMode,
        onLanguage = viewModel::setLanguage,
    )
}

@Composable
internal fun SettingsContent(
    audio: AudioSettings,
    theme: ThemeMode,
    language: AppLanguage,
    onBack: () -> Unit,
    onMusicEnabled: (Boolean) -> Unit,
    onEffectsEnabled: (Boolean) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onEffectsVolume: (Float) -> Unit,
    onVibration: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
) {
    val colors = LogicMixTokens.colors
    Box(Modifier.fillMaxSize().background(colors.backgroundBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .padding(top = LogicMixTokens.spacingL)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
                Box(
                    Modifier
                        .size(LogicMixTokens.minTouchTarget)
                        .clip(LogicMixTokens.radii.medium)
                        .background(colors.cardBrush)
                        .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.medium)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    LogicMixIcon(R.drawable.ic_back, MaterialTheme.colorScheme.onSurface, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.settings), style = LogicMixTokens.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            }

            Section(stringResource(R.string.settings_section_audio)) {
                SwitchRow(R.drawable.ic_music, stringResource(R.string.settings_music), stringResource(R.string.settings_music_desc), audio.musicEnabled, onMusicEnabled)
                VolumeRow(stringResource(R.string.settings_music_volume), audio.musicVolume, audio.musicEnabled, onMusicVolume)
                SwitchRow(R.drawable.ic_effects, stringResource(R.string.settings_effects), stringResource(R.string.settings_effects_desc), audio.effectsEnabled, onEffectsEnabled)
                VolumeRow(stringResource(R.string.settings_effects_volume), audio.effectsVolume, audio.effectsEnabled, onEffectsVolume)
                SwitchRow(R.drawable.ic_vibration, stringResource(R.string.settings_vibration), stringResource(R.string.settings_vibration_desc), audio.vibrationEnabled, onVibration)
                Text(stringResource(R.string.settings_audio_note), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section(stringResource(R.string.settings_section_appearance)) {
                Row(horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                    ThemeTile(R.drawable.ic_theme_system, R.string.theme_system, theme == ThemeMode.SYSTEM, { onTheme(ThemeMode.SYSTEM) }, Modifier.weight(1f))
                    ThemeTile(R.drawable.ic_theme_light, R.string.theme_light, theme == ThemeMode.LIGHT, { onTheme(ThemeMode.LIGHT) }, Modifier.weight(1f))
                    ThemeTile(R.drawable.ic_theme_dark, R.string.theme_dark, theme == ThemeMode.DARK, { onTheme(ThemeMode.DARK) }, Modifier.weight(1f))
                }
            }

            Text(stringResource(R.string.settings_section_language), style = LogicMixTokens.typography.titleMedium, color = colors.gold)
            LanguageAccordion(language, onLanguage)

            Section(stringResource(R.string.settings_section_info)) {
                InfoRow(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), null)
                Text(stringResource(R.string.settings_about), style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.settings_about_body), style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InfoRow(stringResource(R.string.settings_help), stringResource(R.string.settings_planned))
                InfoRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_planned))
            }
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.language_name_en)
    AppLanguage.UKRAINIAN -> stringResource(R.string.language_name_uk)
    AppLanguage.RUSSIAN -> stringResource(R.string.language_name_ru)
    AppLanguage.ITALIAN -> stringResource(R.string.language_name_it)
    AppLanguage.SPANISH -> stringResource(R.string.language_name_es)
    AppLanguage.GERMAN -> stringResource(R.string.language_name_de)
    AppLanguage.FRENCH -> stringResource(R.string.language_name_fr)
    AppLanguage.CHINESE_SIMPLIFIED -> stringResource(R.string.language_name_zh)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = LogicMixTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
        Text(title, style = LogicMixTokens.typography.titleMedium, color = colors.gold)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(LogicMixTokens.radii.large)
                .background(colors.cardBrush)
                .border(BorderStroke(1.dp, colors.cardBorder), LogicMixTokens.radii.large)
                .padding(LogicMixTokens.spacingM),
            verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS),
        ) { content() }
    }
}

@Composable
private fun SwitchRow(@DrawableRes icon: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
    ) {
        LogicMixIcon(icon, MaterialTheme.colorScheme.secondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = LogicMixTokens.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(label: String, volume: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.volume_percent, (volume * 100).toInt()), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sliderColors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = LogicMixTokens.colors.xpTrack,
            disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledInactiveTrackColor = LogicMixTokens.colors.xpTrack.copy(alpha = 0.5f),
        )
        Slider(
            value = volume,
            onValueChange = onChange,
            enabled = enabled,
            colors = sliderColors,
            modifier = Modifier.semantics { contentDescription = label },
            thumb = {
                Box(Modifier.size(20.dp).clip(CircleShape).background(if (enabled) MaterialTheme.colorScheme.primary else sliderColors.disabledThumbColor))
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    enabled = enabled,
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
    }
}

@Composable
private fun ThemeTile(@DrawableRes icon: Int, label: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LogicMixTokens.colors
    val border = if (selected) MaterialTheme.colorScheme.primary else colors.cardBorder
    Column(
        modifier
            .clip(LogicMixTokens.radii.medium)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), LogicMixTokens.radii.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = LogicMixTokens.spacingM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs),
    ) {
        LogicMixIcon(icon, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 28.dp)
        Text(stringResource(label), style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

/** Expanded list: every language except the selected one, in the original order. */
internal fun languageOptions(selected: AppLanguage): List<AppLanguage> = AppLanguage.entries.filter { it != selected }

@Composable
internal fun LanguageAccordion(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val colors = LogicMixTokens.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "arrow")
    val shape = LogicMixTokens.radii.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), shape)
            .animateContentSize(tween(220)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.DropdownList) { expanded = !expanded }
                .padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                languageLabel(selected),
                style = LogicMixTokens.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            LogicMixIcon(R.drawable.ic_arrow_down, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.rotate(rotation), size = 20.dp)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                languageOptions(selected).forEach { option ->
                    val isSelected = false
                    Text(
                        languageLabel(option),
                        style = LogicMixTokens.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { this.selected = isSelected }
                            .clickable(role = Role.Button) {
                                expanded = false
                                onSelect(option)
                            }
                            .padding(horizontal = LogicMixTokens.spacingM, vertical = LogicMixTokens.spacingS)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, status: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
        LogicMixIcon(R.drawable.ic_info, MaterialTheme.colorScheme.secondary)
        Text(title, style = LogicMixTokens.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (status != null) Text(status, style = LogicMixTokens.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
