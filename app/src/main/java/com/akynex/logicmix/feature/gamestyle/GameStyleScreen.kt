package com.akynex.logicmix.feature.gamestyle

import com.akynex.logicmix.core.designsystem.BrandFooter
import com.akynex.logicmix.core.designsystem.LogicMixBackdrop
import com.akynex.logicmix.core.designsystem.LogicMixScreen
import com.akynex.logicmix.core.audio.withBack
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.GameStyleCard
import com.akynex.logicmix.core.designsystem.LogicMixIcon
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.content.GameCategory
import com.akynex.logicmix.domain.content.GameStyle
import com.akynex.logicmix.domain.content.GameStylePrefs
import com.akynex.logicmix.domain.content.StartTarget
import com.akynex.logicmix.feature.home.CategoryGrid

enum class StyleStep { STYLE, TRAINER, CATEGORY, ARCADE }

@StringRes
fun GameStyle.titleRes(): Int = when (this) {
    GameStyle.ARCADE -> R.string.style_arcade
    GameStyle.TRAINER -> R.string.style_trainer
    GameStyle.CATEGORIES -> R.string.style_categories
}

@StringRes
fun GameStyle.descriptionRes(): Int = when (this) {
    GameStyle.ARCADE -> R.string.style_arcade_desc
    GameStyle.TRAINER -> R.string.style_trainer_desc
    GameStyle.CATEGORIES -> R.string.style_categories_desc
}

/** First-launch selector and the Settings "change style" screens share this one flow. */
@Composable
fun GameStyleRoute(
    start: StyleStep,
    onFinished: (StartTarget) -> Unit,
    onBack: () -> Unit,
    viewModel: GameStyleViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val loaded = prefs
    if (loaded == null) {
        LogicMixBackdrop(Modifier.fillMaxSize())
        return
    }
    GameStyleFlow(start, loaded, categories, { s, t, p, a -> viewModel.confirm(s, t, p, a) }, onFinished, onBack)
}

@Composable
internal fun GameStyleFlow(
    start: StyleStep,
    prefs: GameStylePrefs,
    categories: List<GameCategory>,
    onConfirm: (GameStyle, Set<GameCategory>?, GameCategory?, Set<GameCategory>?) -> Unit,
    onFinished: (StartTarget) -> Unit,
    onBack: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(start) }
    var style by rememberSaveable { mutableStateOf(prefs.style) }
    var trainer by rememberSaveable { mutableStateOf(prefs.trainerCategories.map { it.name }) }
    var arcade by rememberSaveable { mutableStateOf(prefs.arcadeCategories.map { it.name }) }
    var single by rememberSaveable { mutableStateOf(prefs.preferredCategory?.name) }
    val selectedTrainer = categories.filter { it.name in trainer }.toSet()
    val selectedArcade = categories.filter { it.name in arcade }.toSet()
    val selectedSingle = categories.firstOrNull { it.name == single }

    // Back from a second step returns to the style cards when the flow started there.
    BackHandler(enabled = step != start) { step = start }

    LogicMixScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LogicMixTokens.spacing)
                .padding(top = LogicMixTokens.spacingL)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = com.akynex.logicmix.core.designsystem.stickyFooterArrangement(LogicMixTokens.spacing),
        ) {
            BackButton { if (step != start) step = start else onBack() }
            when (step) {
                StyleStep.STYLE -> {
                    Header(R.string.style_title, R.string.style_subtitle)
                    GameStyle.entries.forEach { option ->
                        GameStyleCard(
                            option, stringResource(option.titleRes()), stringResource(option.descriptionRes()),
                            selected = style == option, onClick = { style = option },
                        )
                    }
                    ConfirmButton(enabled = style != null) {
                        when (style) {
                            GameStyle.ARCADE -> step = StyleStep.ARCADE
                            GameStyle.TRAINER -> step = StyleStep.TRAINER
                            GameStyle.CATEGORIES -> step = StyleStep.CATEGORY
                            null -> Unit
                        }
                    }
                }
                StyleStep.TRAINER -> {
                    Header(R.string.style_trainer_title, R.string.style_trainer_hint)
                    CategoryGrid(
                        categories, onClick = { c ->
                            trainer = if (c.name in trainer) trainer - c.name else trainer + c.name
                        },
                        selected = selectedTrainer, multi = true,
                    )
                    ConfirmButton(enabled = selectedTrainer.isNotEmpty()) {
                        onConfirm(GameStyle.TRAINER, selectedTrainer, null, null)
                        onFinished(StartTarget.StyleInfo(GameStyle.TRAINER))
                    }
                }
                StyleStep.ARCADE -> {
                    Header(R.string.style_arcade_title, R.string.style_arcade_hint)
                    CategoryGrid(
                        categories, onClick = { c ->
                            arcade = if (c.name in arcade) arcade - c.name else arcade + c.name
                        },
                        selected = selectedArcade, multi = true,
                    )
                    OutlinedButton(
                        { arcade = if (selectedArcade.size == categories.size) emptyList() else categories.map { it.name } },
                        Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget),
                    ) {
                        Text(stringResource(if (selectedArcade.size == categories.size) R.string.style_arcade_clear_all else R.string.style_arcade_select_all))
                    }
                    if (selectedArcade.isEmpty()) {
                        Text(
                            stringResource(R.string.style_arcade_empty), style = LogicMixTokens.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ConfirmButton(enabled = selectedArcade.isNotEmpty()) {
                        onConfirm(GameStyle.ARCADE, null, null, selectedArcade)
                        onFinished(StartTarget.Arcade)
                    }
                }
                StyleStep.CATEGORY -> {
                    Header(R.string.style_category_title, R.string.style_category_hint)
                    CategoryGrid(categories, onClick = { single = it.name }, selected = setOfNotNull(selectedSingle))
                    ConfirmButton(enabled = selectedSingle != null) {
                        selectedSingle?.let {
                            onConfirm(GameStyle.CATEGORIES, null, it, null)
                            onFinished(StartTarget.Category(it))
                        }
                    }
                }
            }
            BrandFooter()
        }
    }
}

@Composable
private fun Header(@StringRes title: Int, @StringRes subtitle: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
        Text(
            stringResource(title), style = LogicMixTokens.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(subtitle), style = LogicMixTokens.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = LogicMixTokens.minTouchTarget), enabled = enabled) {
        Text(stringResource(R.string.style_confirm), style = LogicMixTokens.typography.titleMedium)
    }
}

@Composable
internal fun BackButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(LogicMixTokens.minTouchTarget)
                .clip(LogicMixTokens.radii.medium)
                .background(LogicMixTokens.colors.cardBrush)
                .border(BorderStroke(1.dp, LogicMixTokens.colors.cardBorder), LogicMixTokens.radii.medium)
                .clickable(role = Role.Button, onClick = withBack(onClick)),
            contentAlignment = Alignment.Center,
        ) {
            LogicMixIcon(R.drawable.ic_back, MaterialTheme.colorScheme.onSurface, contentDescription = stringResource(R.string.back))
        }
    }
}
