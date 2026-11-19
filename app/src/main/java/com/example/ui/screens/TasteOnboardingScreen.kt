package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OnboardingArtistOption
import com.example.data.model.OnboardingGenreOption
import com.example.ui.components.OptimizedArtworkImage
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DimSlate
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.ThemeOnPrimary

private const val MIN_GENRES_REQUIRED = 3
private const val MIN_ARTISTS_REQUIRED = 3

/**
 * Full-screen 2-step first-launch music taste onboarding:
 * Step 1: Pick >= 3 genres (Indian + Regional + Global mix)
 * Step 2: Pick >= 3 artists (Indian + Global mix + live search)
 */
@Composable
fun TasteOnboardingScreen(
    genres: List<OnboardingGenreOption>,
    artists: List<OnboardingArtistOption>,
    artistSearchQuery: String,
    artistSearchResults: List<OnboardingArtistOption>,
    isSearchingArtists: Boolean,
    onArtistSearchQueryChange: (String) -> Unit,
    onCompleteOnboarding: (List<String>, List<OnboardingArtistOption>) -> Unit,
    initialGenreTitles: List<String> = emptyList(),
    initialArtistNames: List<String> = emptyList(),
    isEditMode: Boolean = false,
    onDismissEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val selectedGenreMap = remember(initialGenreTitles) {
        mutableStateMapOf<String, OnboardingGenreOption>().apply {
            genres.filter { opt ->
                initialGenreTitles.any { it.equals(opt.title, ignoreCase = true) }
            }.forEach { put(it.id, it) }
        }
    }
    val selectedArtistMap = remember(initialArtistNames) {
        mutableStateMapOf<String, OnboardingArtistOption>().apply {
            initialArtistNames.forEach { savedName ->
                val cleanName = savedName.trim()
                if (cleanName.isNotBlank()) {
                    val matched = artists.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
                    put(
                        cleanName.lowercase(),
                        matched ?: OnboardingArtistOption(name = cleanName, regionTag = "Your Pick")
                    )
                }
            }
        }
    }

    LaunchedEffect(artists) {
        if (artists.isNotEmpty() && selectedArtistMap.isNotEmpty()) {
            selectedArtistMap.keys.toList().forEach { key ->
                val existing = selectedArtistMap[key] ?: return@forEach
                val hydrated = artists.firstOrNull { it.name.equals(existing.name, ignoreCase = true) }
                if (hydrated != null && existing.artworkUrl.isBlank() && hydrated.artworkUrl.isNotBlank()) {
                    selectedArtistMap[key] = hydrated
                }
            }
        }
    }

    val focusManager = LocalFocusManager.current

    BackHandler(enabled = currentStep == 2) {
        focusManager.clearFocus()
        currentStep = 1
    }

    // Consume back press on Step 1 during first-launch, or dismiss when editing taste from Home/Settings
    BackHandler(enabled = currentStep == 1) {
        focusManager.clearFocus()
        if (isEditMode) {
            onDismissEdit()
        }
    }

    val selectedCount = if (currentStep == 1) selectedGenreMap.size else selectedArtistMap.size
    val requiredCount = if (currentStep == 1) MIN_GENRES_REQUIRED else MIN_ARTISTS_REQUIRED
    val isStepReady = selectedCount >= requiredCount

    val stepProgressFraction by animateFloatAsState(
        targetValue = (
            ((currentStep - 1) * 0.5f) +
                (selectedCount.coerceAtMost(requiredCount).toFloat() / requiredCount.toFloat()) * 0.5f
            ).coerceIn(0.08f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "onboardingStepProgress"
    )

    val bgMint = ElectricMint
    val bgViolet = NeonViolet
    val bgCanvas = PitchBlack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgCanvas)
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(
                        bgMint.copy(alpha = 0.14f),
                        bgViolet.copy(alpha = 0.08f),
                        bgCanvas
                    ),
                    startY = 0f,
                    endY = 720f
                )
                onDrawBehind { drawRect(bgBrush) }
            }
            .testTag("taste_onboarding_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
        ) {
            // Top Brand + Step Pill + Selection Counter + Smooth Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PitchBlack)
                                .border(1.dp, ElectricMint.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.spiral_logo),
                                contentDescription = "Echoic",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Surface(
                            color = CarbonElevated,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.75.dp, SubtleBorder)
                        ) {
                            Text(
                                text = "STEP $currentStep OF 2",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val counterBg by animateColorAsState(
                            targetValue = if (isStepReady) ElectricMint.copy(alpha = 0.16f) else CarbonSurface,
                            animationSpec = tween(200),
                            label = "onboardingCounterBg"
                        )
                        val counterBorder by animateColorAsState(
                            targetValue = if (isStepReady) ElectricMint.copy(alpha = 0.7f) else SubtleBorder,
                            animationSpec = tween(200),
                            label = "onboardingCounterBorder"
                        )
                        val counterScale by animateFloatAsState(
                            targetValue = if (isStepReady) 1.03f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "onboardingCounterScale"
                        )
                        Surface(
                            color = counterBg,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.85.dp, counterBorder),
                            modifier = Modifier.graphicsLayer {
                                scaleX = counterScale
                                scaleY = counterScale
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = isStepReady,
                                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)),
                                    exit = scaleOut(tween(120)) + fadeOut(tween(120))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = ElectricMint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (isStepReady) "$selectedCount selected" else "$selectedCount / $requiredCount+ selected",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isStepReady) ElectricMint else MutedSlate
                                )
                            }
                        }

                        if (isEditMode) {
                            Surface(
                                color = CarbonSurface,
                                shape = CircleShape,
                                border = BorderStroke(0.8.dp, SubtleBorder),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        focusManager.clearFocus()
                                        onDismissEdit()
                                    }
                                    .testTag("taste_editor_close_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Close taste editor",
                                        tint = AlabasterWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Animated Progress Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CarbonElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stepProgressFraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ElectricMint, CyberCyan)
                                )
                            )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (currentStep == 1) {
                            "Pick 3 or more genres"
                        } else {
                            "Pick 3 or more artists"
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = AlabasterWhite
                    )
                    Text(
                        text = if (currentStep == 1) {
                            "Blend Indian, regional, and global sounds to personalize your Echoic Home feed."
                        } else {
                            "We'll auto-follow your picks and build your Your Taste Mix."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
            }

            // Step Content (Genres vs Artists)
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (fadeIn(tween(240)) + slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { it / 4 }
                            )).togetherWith(fadeOut(tween(180)) + slideOutHorizontally { -it / 5 })
                        } else {
                            (fadeIn(tween(240)) + slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { -it / 4 }
                            )).togetherWith(fadeOut(tween(180)) + slideOutHorizontally { it / 5 })
                        }
                    },
                    label = "tasteOnboardingStepTransition"
                ) { step ->
                    if (step == 1) {
                        GenreSelectionStepContent(
                            genres = genres,
                            isGenreSelected = { selectedGenreMap.containsKey(it.id) },
                            onToggleGenre = { option ->
                                if (selectedGenreMap.containsKey(option.id)) {
                                    selectedGenreMap.remove(option.id)
                                } else {
                                    selectedGenreMap[option.id] = option
                                }
                            }
                        )
                    } else {
                        val isQueryActive = artistSearchQuery.trim().length >= 2
                        val displayedArtists = remember(
                            artists,
                            artistSearchResults,
                            isQueryActive,
                            selectedArtistMap.keys.toList()
                        ) {
                            if (isQueryActive) {
                                artistSearchResults
                            } else {
                                val customPicks = selectedArtistMap.values.filter { picked ->
                                    artists.none { it.name.equals(picked.name, ignoreCase = true) }
                                }
                                customPicks + artists
                            }
                        }

                        ArtistSelectionStepContent(
                            displayedArtists = displayedArtists,
                            searchQuery = artistSearchQuery,
                            isSearching = isSearchingArtists,
                            isQueryActive = isQueryActive,
                            isArtistSelected = { artist ->
                                selectedArtistMap.containsKey(artist.name.lowercase())
                            },
                            onSearchQueryChange = onArtistSearchQueryChange,
                            onToggleArtist = { artist ->
                                val key = artist.name.lowercase()
                                if (selectedArtistMap.containsKey(key)) {
                                    selectedArtistMap.remove(key)
                                } else {
                                    selectedArtistMap[key] = artist
                                }
                            }
                        )
                    }
                }
            }

            // Docked Matte Bottom Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonSurface)
            ) {
                HorizontalDivider(color = SubtleBorder, thickness = 0.75.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep == 2) {
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                currentStep = 1
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.9.dp, SubtleBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CarbonElevated,
                                contentColor = AlabasterWhite
                            ),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("onboarding_back_button")
                        ) {
                            Text(
                                text = "Back",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    val ctaInteractionSource = remember { MutableInteractionSource() }
                    val isCtaPressed by ctaInteractionSource.collectIsPressedAsState()
                    val ctaScale by animateFloatAsState(
                        targetValue = when {
                            isCtaPressed && isStepReady -> 0.96f
                            else -> 1f
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "onboardingCtaScale"
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (currentStep == 1) {
                                currentStep = 2
                            } else {
                                onCompleteOnboarding(
                                    selectedGenreMap.values.map { it.title },
                                    selectedArtistMap.values.toList()
                                )
                            }
                        },
                        enabled = isStepReady,
                        interactionSource = ctaInteractionSource,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricMint,
                            contentColor = ThemeOnPrimary,
                            disabledContainerColor = CarbonElevated,
                            disabledContentColor = DimSlate
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .graphicsLayer {
                                scaleX = ctaScale
                                scaleY = ctaScale
                            }
                            .testTag(if (currentStep == 1) "onboarding_continue_button" else "onboarding_finish_button")
                    ) {
                        val remaining = (requiredCount - selectedCount).coerceAtLeast(0)
                        val buttonText = when {
                            currentStep == 1 && isStepReady -> "Continue"
                            currentStep == 1 -> "Pick $remaining more genre${if (remaining == 1) "" else "s"}"
                            isStepReady && isEditMode -> "Update Taste Profile"
                            isStepReady -> "Start Listening"
                            else -> "Pick $remaining more artist${if (remaining == 1) "" else "s"}"
                        }
                        AnimatedContent(
                            targetState = buttonText,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.94f))
                                    .togetherWith(fadeOut(tween(110)))
                            },
                            label = "onboardingCtaLabel"
                        ) { label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                                if (currentStep == 1 && isStepReady) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreSelectionStepContent(
    genres: List<OnboardingGenreOption>,
    isGenreSelected: (OnboardingGenreOption) -> Boolean,
    onToggleGenre: (OnboardingGenreOption) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_genres_grid"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = genres,
            key = { it.id }
        ) { genre ->
            val selected = isGenreSelected(genre)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val tileScale by animateFloatAsState(
                targetValue = when {
                    isPressed -> 0.94f
                    selected -> 1.02f
                    else -> 1f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "genreTileScale"
            )
            val containerColor by animateColorAsState(
                targetValue = if (selected) ElectricMint.copy(alpha = 0.16f) else CarbonSurface,
                animationSpec = tween(180),
                label = "genreTileBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) ElectricMint else SubtleBorder,
                animationSpec = tween(180),
                label = "genreTileBorder"
            )
            val borderWidth by animateDpAsState(
                targetValue = if (selected) 1.5.dp else 0.8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "genreTileBorderWidth"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 86.dp)
                    .graphicsLayer {
                        scaleX = tileScale
                        scaleY = tileScale
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(containerColor)
                    .border(
                        width = borderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onToggleGenre(genre) }
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .testTag("onboarding_genre_${genre.id}")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val noteScale by animateFloatAsState(
                            targetValue = if (selected) 1.15f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "genreNoteScale"
                        )
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = if (selected) ElectricMint else CyberCyan,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = noteScale
                                    scaleY = noteScale
                                }
                        )
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(tween(140)))
                                    .togetherWith(scaleOut(tween(110)) + fadeOut(tween(110)))
                            },
                            label = "genreCheckPop"
                        ) { isChecked ->
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected ${genre.title}",
                                    tint = ElectricMint,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .border(1.2.dp, DimSlate, CircleShape)
                                )
                            }
                        }
                    }

                    Text(
                        text = genre.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = if (selected) AlabasterWhite else AlabasterWhite.copy(alpha = 0.94f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = genre.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) ElectricMint.copy(alpha = 0.88f) else MutedSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSelectionStepContent(
    displayedArtists: List<OnboardingArtistOption>,
    searchQuery: String,
    isSearching: Boolean,
    isQueryActive: Boolean,
    isArtistSelected: (OnboardingArtistOption) -> Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleArtist: (OnboardingArtistOption) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "Search Indian or global artists...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DimSlate
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search artists",
                    tint = MutedSlate
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear artist search",
                            tint = MutedSlate
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CarbonSurface,
                unfocusedContainerColor = CarbonSurface,
                focusedBorderColor = ElectricMint,
                unfocusedBorderColor = SubtleBorder,
                focusedTextColor = AlabasterWhite,
                unfocusedTextColor = AlabasterWhite,
                cursorColor = ElectricMint
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("onboarding_artist_search_input")
        )

        when {
            isSearching && displayedArtists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = ElectricMint,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Searching artists...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate
                        )
                    }
                }
            }

            isQueryActive && displayedArtists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No artists matched \"$searchQuery\". Try another name or clear search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("onboarding_artists_grid"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = displayedArtists,
                        key = { _, artist -> "artist_${artist.name.lowercase()}" }
                    ) { index, artist ->
                        val selected = isArtistSelected(artist)
                        OnboardingArtistCard(
                            artist = artist,
                            isSelected = selected,
                            onClick = { onToggleArtist(artist) },
                            modifier = Modifier.testTag("onboarding_artist_$index")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingArtistCard(
    artist: OnboardingArtistOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            isSelected -> 1.04f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "artistCardScale"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) ElectricMint else SubtleBorder,
        animationSpec = tween(180),
        label = "artistRingColor"
    )
    val ringWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.9.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "artistRingWidth"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(CarbonElevated)
                    .border(
                        width = ringWidth,
                        color = ringColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (artist.artworkUrl.isNotBlank()) {
                    OptimizedArtworkImage(
                        artworkUrl = artist.artworkUrl,
                        contentDescription = artist.name,
                        targetSizeDp = 84.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    val initials = remember(artist.name) {
                        artist.name.split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString("") { it.first().uppercaseChar().toString() }
                            .ifBlank { "E" }
                    }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = if (isSelected) ElectricMint else AlabasterWhite
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(tween(140)),
                exit = scaleOut(tween(110)) + fadeOut(tween(110)),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(ElectricMint)
                        .border(2.dp, PitchBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected ${artist.name}",
                        tint = ThemeOnPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                ),
                color = if (isSelected) ElectricMint else AlabasterWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = artist.regionTag,
                style = MaterialTheme.typography.labelSmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
