package com.adhdcompanion.focus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.alarm.TaskAlarmScheduler
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockCategory
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.EnergyLevel
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.components.SegmentedModeBar
import com.adhdcompanion.focus.ui.components.TenMinuteTimelineCard
import com.adhdcompanion.focus.ui.components.formatMinutesReadable
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusAmber
import com.adhdcompanion.focus.ui.theme.FocusCoral
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import com.adhdcompanion.focus.ui.viewmodel.PlannerViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

@Composable
fun PlannerScreen(
    container: AppContainer,
    onStartFocusForSubject: (Long, String) -> Unit = { _, _ -> }
) {
    val viewModel = viewModel<PlannerViewModel>(factory = ViewModelFactories.planner(container))
    val state by viewModel.uiState.collectAsState()
    val blocks by viewModel.todaysBlocks.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val timelineCells by viewModel.timelineCells.collectAsState()
    val todaysSessions by viewModel.todaysSessions.collectAsState()
    val todos by viewModel.todaysTodos.collectAsState()
    val examGoals by viewModel.examGoals.collectAsState()

    val context = LocalContext.current
    val canScheduleExact = remember(container) {
        container.taskAlarmScheduler.canScheduleExactAlarms()
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Timeline & Tasks, 1 = AI Voice Alarms
    var editingBlock by remember { mutableStateOf<DayBlock?>(null) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showTodoDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val studiedMinutesToday = remember(todaysSessions) {
        todaysSessions.sumOf { it.durationSeconds } / 60
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Plan & Timeline",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = OnAmoled
        )
        Text(
            text = "10-minute blocks, subject checklist & exact voice alarms",
            style = MaterialTheme.typography.bodySmall,
            color = OnAmoledMuted
        )

        Spacer(Modifier.height(14.dp))

        SegmentedModeBar(
            options = listOf(
                "Timeline & Tasks",
                if (blocks.isNotEmpty()) "Voice Alarms (${blocks.size})" else "AI Voice Alarms"
            ),
            selectedIndex = activeTab,
            onSelect = { activeTab = it },
            testTagPrefix = "planner_tab"
        )

        Spacer(Modifier.height(16.dp))

        if (activeTab == 0) {
            // 1. Minimalist Exam D-Day Hero Bar
            val primaryGoal = examGoals.firstOrNull()
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Flag, contentDescription = null, tint = FocusAmber, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = primaryGoal?.title ?: "Set Exam / Target Countdown",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnAmoled
                                )
                                if (primaryGoal != null) {
                                    val todayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
                                    val daysLeft = (primaryGoal.targetDateEpoch - todayEpoch).coerceAtLeast(0L)
                                    Text(
                                        text = "D-$daysLeft · Daily goal ${formatMinutesReadable(primaryGoal.targetDailyMinutes)}",
                                        style = TabularStatStyle.copy(fontSize = 12.sp),
                                        color = FocusAmber
                                    )
                                } else {
                                    Text("Track D-Day countdown & daily hours", style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                                }
                            }
                        }
                        TextButton(
                            onClick = { showGoalDialog = true },
                            modifier = Modifier.testTag("edit_exam_goal_button")
                        ) {
                            Text(if (primaryGoal == null) "Set Goal" else "Update", color = FocusEmerald)
                        }
                    }
                    if (primaryGoal != null) {
                        Spacer(Modifier.height(10.dp))
                        val progress = (studiedMinutesToday.toFloat() / primaryGoal.targetDailyMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = FocusEmerald,
                            trackColor = AmoledSurfaceHigh,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${formatMinutesReadable(studiedMinutesToday)} / ${formatMinutesReadable(primaryGoal.targetDailyMinutes)} today",
                            style = TabularStatStyle.copy(fontSize = 11.5.sp),
                            color = OnAmoledMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. YPT 10-Min Grid
            TenMinuteTimelineCard(cells = timelineCells)

            Spacer(Modifier.height(18.dp))

            // 3. Minimalist Subject To-Do Checklist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Subject Checklist · ${todos.count { it.isCompleted }}/${todos.size}")
                TextButton(
                    onClick = { showTodoDialog = true },
                    modifier = Modifier.testTag("add_study_todo_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = FocusEmerald)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Task", color = FocusEmerald)
                }
            }

            if (todos.isEmpty()) {
                AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                    Text(
                        "Add today's problem sets or chapters per subject, then tap Play to jump right into focus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoledMuted,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                    todos.forEachIndexed { idx, todo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleTodo(todo) }) {
                                Icon(
                                    if (todo.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = "Toggle task",
                                    tint = if (todo.isCompleted) FocusEmerald else OnAmoledSubtle
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = todo.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (todo.isCompleted) OnAmoledMuted else OnAmoled,
                                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(todo.subjectColorHex))
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "${todo.subjectName} · ${todo.estimatedMinutes}m",
                                        style = TabularStatStyle.copy(fontSize = 11.sp),
                                        color = OnAmoledMuted
                                    )
                                }
                            }
                            IconButton(onClick = { onStartFocusForSubject(todo.subjectId, todo.subjectName) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Focus on ${todo.subjectName}", tint = FocusEmerald)
                            }
                            IconButton(onClick = { viewModel.deleteTodo(todo) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Delete task", tint = OnAmoledSubtle)
                            }
                        }
                        if (idx < todos.lastIndex) {
                            HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
                        }
                    }
                }
            }
        } else {
            // TAB 1: AI DAY SCHEDULER & VOICE ALARMS
            if (!canScheduleExact) {
                AmoledCard(
                    containerColor = AmoledSurfaceElevated,
                    borderColor = FocusAmber.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Allow exact alarms so every task announces on time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAmoled,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { context.startActivity(TaskAlarmScheduler.requestExactAlarmPermissionIntent(context)) },
                            modifier = Modifier.testTag("grant_exact_alarm_button")
                        ) { Text("Allow", color = FocusAmber) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("AI Day Architect")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.availableHours,
                        onValueChange = viewModel::updateHours,
                        label = { Text("Available hours today") },
                        placeholder = { Text("e.g. 9am-1pm, 3pm-6pm") },
                        modifier = Modifier.fillMaxWidth().testTag("planner_hours_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Energy level", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnergyLevel.entries.forEach { level ->
                            AssistChip(
                                onClick = { viewModel.updateEnergy(level) },
                                label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = if (state.energyLevel == level) {
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = FocusEmerald,
                                        labelColor = AmoledBlack
                                    )
                                } else AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.goalsAndBlocks,
                        onValueChange = viewModel::updateGoals,
                        label = { Text("Goals / required blocks") },
                        placeholder = { Text("e.g. meds at 9, Physics Ch4, meal at 1") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("planner_goals_input")
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = viewModel::generatePlan,
                            enabled = !state.isGenerating,
                            colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald, contentColor = AmoledBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("generate_plan_button")
                        ) {
                            if (state.isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AmoledBlack)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (state.isGenerating) "Building…" else "Generate", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                editingBlock = null
                                showBlockDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_block_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Block", color = OnAmoled)
                        }
                    }
                }
            }

            state.statusMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = FocusEmerald, style = MaterialTheme.typography.bodySmall)
            }
            state.errorMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = FocusCoral, style = MaterialTheme.typography.bodySmall)
            }

            if (blocks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("Scheduled Voice-Alarm Blocks (${blocks.size})")
                Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showTodoDialog) {
        AddStudyTodoDialog(
            subjects = subjects,
            onDismiss = { showTodoDialog = false },
            onSave = { subject, title, estMinutes ->
                viewModel.addTodo(subject, title, estMinutes)
                showTodoDialog = false
            }
        )
    }

    if (showGoalDialog) {
        ExamGoalEditorDialog(
            onDismiss = { showGoalDialog = false },
            onSave = { title, targetEpoch, targetDailyMin ->
                viewModel.saveExamGoal(title, targetEpoch, targetDailyMin)
                showGoalDialog = false
            }
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStudyTodoDialog(
    subjects: List<StudySubject>,
    onDismiss: () -> Unit,
    onSave: (StudySubject?, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
    var estMinutes by remember { mutableIntStateOf(30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Add Subject Task", color = OnAmoled) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task (e.g. 25 Electrostatics problems)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Subject", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjects.forEach { sub ->
                        FilterChip(
                            selected = selectedSubject?.id == sub.id,
                            onClick = { selectedSubject = sub },
                            label = { Text(sub.name) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Estimated time", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        FilterChip(
                            selected = estMinutes == mins,
                            onClick = { estMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedSubject, title, estMinutes) }, enabled = title.isNotBlank()) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamGoalEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, Int) -> Unit
) {
    var title by remember { mutableStateOf("Exam Target") }
    var daysOut by remember { mutableIntStateOf(45) }
    var dailyHours by remember { mutableIntStateOf(4) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Exam Countdown Goal", color = OnAmoled) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam / Milestone name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Days until exam", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(14, 30, 45, 60, 90, 180).forEach { d ->
                        FilterChip(selected = daysOut == d, onClick = { daysOut = d }, label = { Text("${d}d") })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Daily target hours", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4, 6, 8).forEach { h ->
                        FilterChip(selected = dailyHours == h, onClick = { dailyHours = h }, label = { Text("${h}h") })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val todayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
                    onSave(title, todayEpoch + daysOut.toLong(), dailyHours * 60)
                },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BlockRow(
    block: DayBlock,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = if (block.isDecompressionBuffer) FocusAmber.copy(alpha = 0.4f) else AmoledOutlineSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (block.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = "Toggle complete",
                    tint = if (block.isCompleted) FocusEmerald else OnAmoledSubtle
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatMinutes(block.startMinuteOfDay)} (${block.durationMinutes}m) · ${block.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnAmoled,
                    textDecoration = if (block.isCompleted) TextDecoration.LineThrough else null
                )
                if (block.why.isNotBlank()) {
                    Text(block.why, style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                }
                if (block.microStep.isNotBlank()) {
                    Text("Step: ${block.microStep}", style = MaterialTheme.typography.labelSmall, color = FocusEmerald)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit block", tint = OnAmoledMuted)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "Remove block", tint = OnAmoledSubtle)
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
        containerColor = AmoledSurfaceElevated,
        title = { Text(if (initial == null) "Add block" else "Edit block", color = OnAmoled) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
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
                Text("Start time", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(4.dp))
                TimeInput(state = timeState)
                Spacer(Modifier.height(8.dp))
                Text("Duration", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 25, 45, 60, 90).forEach { mins ->
                        FilterChip(selected = durationMinutes == mins, onClick = { durationMinutes = mins }, label = { Text("${mins}m") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
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
                    Text("Decompression buffer", style = MaterialTheme.typography.bodyMedium, color = OnAmoled)
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
