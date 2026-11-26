package com.adhdcompanion.focus.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.data.ai.DoubtBackend
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.EnergyLevel
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.viewmodel.DoubtSolverViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories

@Composable
fun DoubtSolverScreen(container: AppContainer) {
    val viewModel = viewModel<DoubtSolverViewModel>(factory = ViewModelFactories.doubtSolver(container))
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshLocalModelInfo()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceInput() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImage(uri)
        }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importOfflineModel(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Doubt solver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            state.activeBackend?.let { backend ->
                BackendBadge(backend = backend)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Hybrid AI: streams text fast via Groq GPT-OSS, switches to Gemini Flash-Lite for photos or fallback, and falls back to on-device LiteRT offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = state.doubtText,
                onValueChange = viewModel::updateDoubtText,
                label = { Text("What's the doubt?") },
                placeholder = { Text("e.g. Why does Bayes' theorem divide by P(B)?") },
                minLines = 3,
                modifier = Modifier
                    .weight(1f)
                    .testTag("doubt_input")
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (state.isListening) {
                        viewModel.stopVoiceInput()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.testTag("doubt_mic_button")
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Speak doubt",
                    tint = if (state.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.testTag("attach_doubt_image_button")
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.attachedImageUri != null) "Change photo" else "Attach photo")
            }

            if (state.attachedImageUri != null) {
                AssistChip(
                    onClick = { viewModel.attachImage(null) },
                    label = { Text("Photo attached") },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove attached photo",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.subjectText,
            onValueChange = viewModel::updateSubjectText,
            label = { Text("Subject / context (optional)") },
            placeholder = { Text("e.g. Probability, Calculus, Organic Chem, DSA") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("doubt_subject_input")
        )

        Spacer(Modifier.height(12.dp))
        Text("Energy level (optional)", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyLevel.entries.forEach { level ->
                val selected = state.energyLevel == level
                AssistChip(
                    onClick = { viewModel.updateEnergyLevel(if (selected) null else level) },
                    label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = if (selected) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = viewModel::solveDoubt,
                enabled = !state.isLoading,
                modifier = Modifier
                    .weight(1f)
                    .testTag("solve_doubt_button")
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isLoading) "Streaming…" else "Break down this doubt")
            }

            if (state.isLoading) {
                OutlinedButton(
                    onClick = viewModel::cancelGeneration,
                    modifier = Modifier.testTag("cancel_doubt_button")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }

        // Offline LiteRT-LM Model Import Card
        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Offline Fallback Model (.litertlm)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.localModelInfo.exists) {
                        "${state.localModelInfo.fileName} • ${state.localModelInfo.formattedSize}"
                    } else {
                        "No offline model imported (kept outside APK)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.localModelInfo.exists) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isImportingModel && !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_offline_model_button")
                ) {
                    if (state.isImportingModel) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Importing…")
                    } else {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select Offline Model (.litertlm)")
                    }
                }
            }
        }

        state.statusMessage?.let { status ->
            Spacer(Modifier.height(10.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        state.errorMessage?.let { err ->
            Spacer(Modifier.height(10.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // Live progressive stream output while generating (before final parsed cards appear)
        if (state.isLoading && state.streamingText.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("doubt_streaming_card")
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("Live response")
                        state.activeBackend?.let { BackendBadge(backend = it) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.streamingText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        val sol = state.solution
        if (sol != null) {
            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionLabel("Breakdown")
                    state.activeBackend?.let { backend ->
                        BackendBadge(backend = backend)
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (state.isSpeaking) viewModel.stopSpeaking() else viewModel.speakSolution()
                    },
                    modifier = Modifier.testTag("doubt_speak_button")
                ) {
                    Icon(
                        if (state.isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isSpeaking) "Stop audio" else "Read aloud")
                }
            }

            state.statusNote?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // 1. Core Intuition
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "CORE INTUITION",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sol.coreIntuition,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // 2. Step-by-step reasoning
            if (sol.stepByStep.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        SectionLabel("Step-by-step reasoning")
                        Spacer(Modifier.height(4.dp))
                        sol.stepByStep.forEachIndexed { idx, step ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "${idx + 1}.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(26.dp)
                                )
                                Text(
                                    step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. 2-minute micro-step + send to schedule / dump
            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    SectionLabel("Tiny 2-minute micro-step")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sol.twoMinuteMicroStep,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = viewModel::sendMicroStepToSchedule,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("doubt_to_schedule_button")
                        ) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("To schedule")
                        }
                        OutlinedButton(
                            onClick = viewModel::sendMicroStepToDump,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("doubt_to_dump_button")
                        ) {
                            Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("To dump")
                        }
                    }
                }
            }

            state.actionFeedback?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = viewModel::reset) {
                Text("Ask another doubt")
            }
        } else {
            state.actionFeedback?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BackendBadge(backend: DoubtBackend) {
    val (bg, fg) = when (backend) {
        DoubtBackend.GROQ_GPT_OSS ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DoubtBackend.GEMINI_FLASH_LITE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        DoubtBackend.OFFLINE_LITERT ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        modifier = Modifier.testTag("doubt_backend_badge")
    ) {
        Text(
            text = backend.badgeLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
