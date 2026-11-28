package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.StudyRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class GroupsUiState(
    val rooms: List<StudyRoom> = emptyList(),
    val activeRoomId: String = "",
    val displayName: String = "Scholar",
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null
) {
    val activeRoom: StudyRoom?
        get() = rooms.firstOrNull { it.id == activeRoomId } ?: rooms.firstOrNull()
}

class GroupsViewModel(container: AppContainer) : ViewModel() {

    private val repo = container.studyGroupRepository

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observeRooms(),
                repo.observeActiveRoomId(),
                repo.observeDisplayName()
            ) { rooms, activeId, name ->
                Triple(rooms, activeId, name)
            }.collect { (rooms, activeId, name) ->
                _uiState.value = _uiState.value.copy(
                    rooms = rooms,
                    activeRoomId = activeId,
                    displayName = name
                )
            }
        }
    }

    fun selectRoom(roomId: String) {
        repo.selectRoom(roomId)
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            repo.setDisplayName(name)
            _uiState.value = _uiState.value.copy(feedbackMessage = "Display name updated.")
        }
    }

    fun createRoom(name: String, description: String, dailyGoalMinutes: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            repo.createRoom(name, description, dailyGoalMinutes)
                .onSuccess { created ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        feedbackMessage = "Created '${created.name}' · Invite code: ${created.code}"
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        feedbackMessage = err.message ?: "Could not create room."
                    )
                }
        }
    }

    fun joinRoomByCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            repo.joinRoomByCode(code)
                .onSuccess { joined ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        feedbackMessage = "Joined ${joined.name} (${joined.code})."
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        feedbackMessage = err.message ?: "Could not join room."
                    )
                }
        }
    }

    fun leaveRoom(roomId: String) {
        viewModelScope.launch {
            repo.leaveRoom(roomId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(feedbackMessage = "Left study room.")
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(feedbackMessage = err.message ?: "Could not leave room.")
                }
        }
    }

    fun sendNudge(memberId: String, memberName: String) {
        val roomId = _uiState.value.activeRoom?.id ?: return
        viewModelScope.launch {
            repo.sendNudge(
                roomId = roomId,
                memberId = memberId,
                message = "Locked in with you — finish this sprint!"
            ).onSuccess {
                _uiState.value = _uiState.value.copy(feedbackMessage = "Sent cheer to $memberName.")
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(feedbackMessage = err.message ?: "Could not send cheer.")
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }
}
