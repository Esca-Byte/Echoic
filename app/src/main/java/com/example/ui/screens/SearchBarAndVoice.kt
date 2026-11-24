package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.SoftTonalCircleIconButton
import com.example.ui.theme.AlabasterWhite
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.SubtleBorder
import java.util.Locale

/**
 * Modular Search Bar with zero-permission system Voice Search via RecognizerIntent.
 * Spoken transcripts populate and immediately submit the live multi-source search query.
 */
@Composable
internal fun VoiceEnabledSearchBar(
    searchQuery: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val voicePrompt = stringResource(R.string.voice_search_prompt)
    val voiceUnavailableText = stringResource(R.string.voice_search_unavailable)

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                onQueryChange(spoken)
                onSubmitSearch(spoken)
            }
        }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate
            )
        },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    color = ElectricMint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MutedSlate
                )
            }
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                if (searchQuery.isNotEmpty()) {
                    SoftTonalCircleIconButton(
                        icon = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                        onClick = onClearSearch,
                        size = 32.dp,
                        iconSize = 16.dp,
                        containerColor = CarbonElevated,
                        modifier = Modifier.testTag("clear_search_button")
                    )
                }

                SoftTonalCircleIconButton(
                    icon = Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.voice_search_cd),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                        }
                        runCatching {
                            speechLauncher.launch(intent)
                        }.onFailure {
                            Toast.makeText(context, voiceUnavailableText, Toast.LENGTH_SHORT).show()
                        }
                    },
                    size = 34.dp,
                    iconSize = 18.dp,
                    isActive = true,
                    activeTint = ElectricMint,
                    containerColor = ElectricMint.copy(alpha = 0.14f),
                    modifier = Modifier.testTag("voice_search_button")
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(searchQuery) }),
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CarbonSurface,
            unfocusedContainerColor = CarbonSurface,
            focusedBorderColor = ElectricMint,
            unfocusedBorderColor = SubtleBorder.copy(alpha = 0.7f),
            focusedTextColor = AlabasterWhite,
            unfocusedTextColor = AlabasterWhite,
            cursorColor = ElectricMint
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_input_field")
    )
}
