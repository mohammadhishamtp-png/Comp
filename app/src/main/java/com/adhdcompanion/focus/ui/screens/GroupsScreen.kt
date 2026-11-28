package com.adhdcompanion.focus.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.StudyGroupMember
import com.adhdcompanion.focus.model.StudyMemberStatus
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.components.formatMinutesReadable
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusAmber
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.FocusEmeraldDim
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import com.adhdcompanion.focus.ui.viewmodel.GroupsViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import kotlinx.coroutines.delay

@Composable
fun GroupsScreen(container: AppContainer) {
    val viewModel = viewModel<GroupsViewModel>(factory = ViewModelFactories.groups(container))
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(1800)
            copiedCode = false
        }
    }

    val pulse = rememberInfiniteTransition(label = "live_studying_dot").animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    ).value

    val activeRoom = state.activeRoom

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Study Hall",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnAmoled
                )
                Text(
                    "Live synced rooms · body-doubling & daily rankings",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnAmoledMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = AmoledSurface,
                border = BorderStroke(0.75.dp, AmoledOutline),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { showNameDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit display name",
                        modifier = Modifier.size(14.dp),
                        tint = FocusEmerald
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        state.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = OnAmoled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        state.feedbackMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            AmoledCard(
                containerColor = AmoledSurfaceElevated,
                borderColor = FocusEmerald.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoled,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::clearFeedback) {
                        Text("OK", color = FocusEmerald)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showJoinDialog = true },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusEmerald,
                    contentColor = AmoledBlack
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("join_room_button")
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AmoledBlack
                    )
                } else {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Join Code", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { showCreateDialog = true },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.75.dp, AmoledOutline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("create_room_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnAmoled)
                Spacer(Modifier.width(6.dp))
                Text("Create Room", color = OnAmoled)
            }
        }

        if (state.rooms.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.rooms.forEach { room ->
                    val selected = room.id == activeRoom?.id
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) FocusEmeraldDim else AmoledSurface,
                        border = BorderStroke(
                            0.75.dp,
                            if (selected) FocusEmerald.copy(alpha = 0.6f) else AmoledOutlineSubtle
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { viewModel.selectRoom(room.id) }
                    ) {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) FocusEmerald else OnAmoledMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (activeRoom == null) {
            Spacer(Modifier.height(22.dp))
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "No active study hall yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnAmoled
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Create your own silent study room or join with an invite code to see peers focusing live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoledMuted
                    )
                }
            }
        } else {
            val studyingNowCount = activeRoom.members.count { it.status == StudyMemberStatus.STUDYING }
            Spacer(Modifier.height(14.dp))

            // Room Header Card with 1-tap Copy Invite Code
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(if (studyingNowCount > 0) pulse else 0.4f)
                                        .clip(CircleShape)
                                        .background(if (studyingNowCount > 0) FocusEmerald else OnAmoledMuted)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "$studyingNowCount STUDYING NOW",
                                    style = TabularStatStyle.copy(fontSize = 11.sp),
                                    color = if (studyingNowCount > 0) FocusEmerald else OnAmoledMuted
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(activeRoom.name, style = MaterialTheme.typography.titleLarge, color = OnAmoled)
                            if (activeRoom.description.isNotBlank()) {
                                Text(
                                    activeRoom.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnAmoledMuted
                                )
                            }
                        }
                        if (state.rooms.size > 1) {
                            IconButton(onClick = { viewModel.leaveRoom(activeRoom.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Leave room", tint = OnAmoledMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmoledSurfaceElevated,
                            border = BorderStroke(0.75.dp, FocusEmerald.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(activeRoom.code))
                                    copiedCode = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (copiedCode) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                    contentDescription = "Copy invite code",
                                    tint = FocusEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (copiedCode) "Copied ${activeRoom.code}" else "Invite · ${activeRoom.code}",
                                    style = TabularStatStyle.copy(fontSize = 12.sp),
                                    color = FocusEmerald
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmoledSurfaceElevated,
                            border = BorderStroke(0.75.dp, AmoledOutlineSubtle)
                        ) {
                            Text(
                                text = "Goal · ${formatMinutesReadable(activeRoom.dailyGoalMinutes)}/day",
                                style = TabularStatStyle.copy(fontSize = 12.sp),
                                color = OnAmoledMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Live Leaderboard")
            Spacer(Modifier.height(6.dp))

            val rankedMembers = remember(activeRoom.members) {
                activeRoom.members.sortedWith(
                    compareByDescending<StudyGroupMember> { it.todayMinutes }
                        .thenBy { it.status.ordinal }
                )
            }

            rankedMembers.forEachIndexed { index, member ->
                val statusColor = when (member.status) {
                    StudyMemberStatus.STUDYING -> FocusEmerald
                    StudyMemberStatus.BREAK -> FocusAmber
                    StudyMemberStatus.OFFLINE -> OnAmoledMuted
                }
                val statusLabel = when (member.status) {
                    StudyMemberStatus.STUDYING -> "Focusing"
                    StudyMemberStatus.BREAK -> "Break"
                    StudyMemberStatus.OFFLINE -> "Idle"
                }

                AmoledCard(
                    containerColor = if (member.isMe) AmoledSurfaceElevated else AmoledSurface,
                    borderColor = if (member.isMe) FocusEmerald.copy(alpha = 0.45f) else AmoledOutlineSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = TabularStatStyle.copy(fontSize = 13.sp),
                            color = if (index == 0) FocusAmber else OnAmoledMuted,
                            modifier = Modifier.width(30.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .alpha(if (member.status == StudyMemberStatus.STUDYING) pulse else 1f)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (member.isMe) "${member.name} (You)" else member.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnAmoled,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(member.subjectColorHex))
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = member.activeSubject,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnAmoledMuted
                                )
                            }
                        }

                        Text(
                            text = formatMinutesReadable(member.todayMinutes),
                            style = TabularStatStyle.copy(fontSize = 14.sp),
                            color = OnAmoled
                        )

                        if (!member.isMe) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.sendNudge(member.id, member.name) },
                                modifier = Modifier.testTag("nudge_${member.id}")
                            ) {
                                Icon(
                                    Icons.Filled.Celebration,
                                    contentDescription = "Encourage ${member.name}",
                                    tint = FocusEmerald
                                )
                            }
                        }
                    }
                }
            }

            if (activeRoom.recentNudges.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Room Cheers")
                Spacer(Modifier.height(6.dp))
                activeRoom.recentNudges.take(5).forEach { nudge ->
                    AmoledCard(
                        containerColor = AmoledSurface,
                        borderColor = AmoledOutlineSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "${nudge.fromName} → ${nudge.toName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = FocusEmerald
                            )
                            Text(
                                text = nudge.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoled
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = AmoledSurfaceElevated,
            title = { Text("Join Study Room", color = OnAmoled) },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Invite code (e.g. FOCUS1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.joinRoomByCode(code)
                    showJoinDialog = false
                }) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var goalHours by remember { mutableIntStateOf(4) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = AmoledSurfaceElevated,
            title = { Text("Create Study Room", color = OnAmoled) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Room name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description / rule") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Daily target: ${goalHours}h", style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 4, 6, 8).forEach { hrs ->
                            FilterChip(
                                selected = goalHours == hrs,
                                onClick = { goalHours = hrs },
                                label = { Text("${hrs}h") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FocusEmeraldDim,
                                    selectedLabelColor = FocusEmerald
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createRoom(name, desc, goalHours * 60)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf(state.displayName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = AmoledSurfaceElevated,
            title = { Text("Your Study Name", color = OnAmoled) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateDisplayName(name)
                    showNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }
}
