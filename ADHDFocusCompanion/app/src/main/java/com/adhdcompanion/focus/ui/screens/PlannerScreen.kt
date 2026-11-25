package com.adhdcompanion.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockCategory
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.EnergyLevel
import com.adhdcompanion.focus.ui.viewmodel.PlannerViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import java.util.Calendar
import java.util.Locale

@Composable
fun PlannerScreen(container: AppContainer) {
    val viewModel = viewModel<PlannerViewModel>(factory = ViewModelFactories.planner(container))
    val state by viewModel.uiState.collectAsState()
    val blocks by viewModel.todaysBlocks.collectAsState()

    var editingBlock by remember { mutableStateOf<DayBlock?>(null) }
    var showBlockDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Day planner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tell the AI your hours, energy, and goals — or add/edit blocks directly. Everything saves straight to Room.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.availableHours,
            onValueChange = viewModel::updateHours,
            label = { Text("Available hours today") },
            placeholder = { Text("e.g. 9am-1pm, 3pm-6pm") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("planner_hours_input")
        )

        Spacer(Modifier.height(12.dp))
        Text("Energy level", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyLevel.entries.forEach { level ->
                AssistChip(
                    onClick = { viewModel.updateEnergy(level) },
                    label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = if (state.energyLevel == level) {
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

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.goalsAndBlocks,
            onValueChange = viewModel::updateGoals,
            label = { Text("Goals / required blocks") },
            placeholder = { Text("e.g. meds at 9, meditation, study Chapter 4, meal at 1") },
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("planner_goals_input")
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = viewModel::generatePlan,
                enabled = !state.isGenerating,
                modifier = Modifier
                    .weight(1f)
                    .testTag("generate_plan_button")
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (state.isGenerating) "Generating…" else "Generate plan")
            }

            OutlinedButton(
                onClick = {
                    editingBlock = null
                    showBlockDialog = true
                },
                modifier = Modifier.testTag("add_block_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add block")
            }
        }

        state.statusMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (blocks.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Text("Today's blocks (${blocks.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            blocks.sortedBy { it.startMinuteOfDay }.forEach { block ->
                BlockRow(
                    block = block,
                    onToggle = { viewModel.toggleCompleted(block) },
                    onEdit = {
                        editingBlock = block
                        showBlockDialog = true
                    },
                    onDelete = { viewModel.deleteBlock(block) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showBlockDialog) {
        BlockEditorDialog(
            initial = editingBlock,
            onDismiss = { showBlockDialog = false },
            onSave = { title, why, microStep, startMin, durationMin, category, isBuffer ->
                viewModel.saveBlock(
                    existing = editingBlock,
                    title = title,
                    why = why,
                    microStep = microStep,
                    startMinuteOfDay = startMin,
                    durationMinutes = durationMin,
                    category = category,
                    isDecompressionBuffer = isBuffer
                )
                showBlockDialog = false
            }
        )
    }
}

@Composable
private fun BlockRow(
    block: DayBlock,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (block.isDecompressionBuffer)
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (block.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = "Toggle complete",
                    tint = if (block.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatMinutes(block.startMinuteOfDay)} (${block.durationMinutes}m) · ${block.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (block.isCompleted) TextDecoration.LineThrough else null
                )
                if (block.why.isNotBlank()) {
                    Text(
                        block.why,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (block.microStep.isNotBlank()) {
                    Text(
                        "Step: ${block.microStep}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit block")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "Remove block")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorDialog(
    initial: DayBlock?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        why: String,
        microStep: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        category: BlockCategory,
        isBuffer: Boolean
    ) -> Unit
) {
    val cal = remember { Calendar.getInstance() }
    val defaultStartMinute = initial?.startMinuteOfDay
        ?: (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))

    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var why by remember { mutableStateOf(initial?.why.orEmpty()) }
    var microStep by remember { mutableStateOf(initial?.microStep.orEmpty()) }
    var durationMinutes by remember { mutableIntStateOf(initial?.durationMinutes ?: 30) }
    var category by remember { mutableStateOf(initial?.category ?: BlockCategory.STUDY) }
    var isBuffer by remember { mutableStateOf(initial?.isDecompressionBuffer ?: false) }

    val timeState = rememberTimePickerState(
        initialHour = defaultStartMinute / 60,
        initialMinute = defaultStartMinute % 60,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add block" else "Edit block") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Block title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = why,
                    onValueChange = { why = it },
                    label = { Text("Why it matters (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = microStep,
                    onValueChange = { microStep = it },
                    label = { Text("Tiny first step") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Start time", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                TimeInput(state = timeState)

                Spacer(Modifier.height(8.dp))
                Text("Duration", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 25, 45, 60, 90).forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                isBuffer = (cat == BlockCategory.BUFFER)
                            },
                            label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Decompression buffer", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isBuffer, onCheckedChange = { isBuffer = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startMin = timeState.hour * 60 + timeState.minute
                    onSave(title, why, microStep, startMin, durationMinutes, category, isBuffer)
                },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatMinutes(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "%d:%02d %s".format(Locale.getDefault(), hour12, minute, amPm)
}
