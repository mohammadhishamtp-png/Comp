package com.adhdcompanion.focus.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.EnergyLevel
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.viewmodel.DoubtSolverViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories

@Composable
fun DoubtSolverScreen(container: AppContainer) {
    val viewModel = viewModel<DoubtSolverViewModel>(factory = ViewModelFactories.doubtSolver(container))
    val state by viewModel.uiState.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceInput() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Doubt solver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Drop a confusing concept or problem. Get plain intuition, clear steps, and one tiny 2-minute practice step.",
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
        Button(
            onClick = viewModel::solveDoubt,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("solve_doubt_button")
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isLoading) "Breaking it down…" else "Break down this doubt")
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        state.solution?.let { sol ->
            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Breakdown")
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

            Spacer(Modifier.height(8.dp))

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
        }

        Spacer(Modifier.height(24.dp))
    }
}
