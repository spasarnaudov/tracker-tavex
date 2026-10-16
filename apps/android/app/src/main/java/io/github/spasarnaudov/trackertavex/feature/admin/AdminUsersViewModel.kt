package io.github.spasarnaudov.trackertavex.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.spasarnaudov.trackertavex.core.data.AdminRepository
import io.github.spasarnaudov.trackertavex.core.model.AdminUserSummary
import io.github.spasarnaudov.trackertavex.core.model.LoginHistoryEntry
import io.github.spasarnaudov.trackertavex.core.model.LoginStatEntry
import io.github.spasarnaudov.trackertavex.core.network.ApiResult
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus
import io.github.spasarnaudov.trackertavex.core.ui.format.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminTab { USERS, LOGIN_STATS, LOGIN_HISTORY }

data class AdminUsersUiState(
    val selectedTab: AdminTab = AdminTab.USERS,
    val usersStatus: LoadStatus = LoadStatus.LOADING,
    val users: List<AdminUserSummary> = emptyList(),
    val usersError: String? = null,
    val loginStatsStatus: LoadStatus = LoadStatus.LOADING,
    val loginStats: List<LoginStatEntry> = emptyList(),
    val loginStatsError: String? = null,
    val loginHistoryStatus: LoadStatus = LoadStatus.LOADING,
    val loginHistory: List<LoginHistoryEntry> = emptyList(),
    val loginHistoryError: String? = null,
)

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    fun loadLoginHistory() {
        _uiState.update { it.copy(loginHistoryStatus = LoadStatus.LOADING, loginHistoryError = null) }
        viewModelScope.launch {
            when (val result = adminRepository.getLoginHistory()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        loginHistoryStatus = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT,
                        loginHistory = result.data,
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(loginHistoryStatus = LoadStatus.ERROR, loginHistoryError = result.error.toUserMessage())
                }
            }
        }
    }

    fun loadLoginStats() {
        _uiState.update { it.copy(loginStatsStatus = LoadStatus.LOADING, loginStatsError = null) }
        viewModelScope.launch {
            when (val result = adminRepository.getLoginStats()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        loginStatsStatus = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT,
                        loginStats = result.data,
                    )
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(loginStatsStatus = LoadStatus.ERROR, loginStatsError = result.error.toUserMessage())
                }
            }
        }
    }

    fun loadUsers() {
        _uiState.update { it.copy(usersStatus = LoadStatus.LOADING, usersError = null) }
        viewModelScope.launch {
            when (val result = adminRepository.getUsers()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(usersStatus = if (result.data.isEmpty()) LoadStatus.EMPTY else LoadStatus.CONTENT, users = result.data)
                }

                is ApiResult.Error -> _uiState.update {
                    it.copy(usersStatus = LoadStatus.ERROR, usersError = result.error.toUserMessage())
                }
            }
        }
    }

    fun selectTab(tab: AdminTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            AdminTab.USERS -> if (_uiState.value.usersStatus == LoadStatus.LOADING) loadUsers()
            AdminTab.LOGIN_STATS -> loadLoginStats()
            AdminTab.LOGIN_HISTORY -> loadLoginHistory()
        }
    }
}
