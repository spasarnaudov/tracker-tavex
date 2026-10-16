package io.github.spasarnaudov.trackertavex.feature.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.R
import io.github.spasarnaudov.trackertavex.core.model.AdminUserSummary
import io.github.spasarnaudov.trackertavex.core.model.LoginHistoryEntry
import io.github.spasarnaudov.trackertavex.core.model.LoginStatEntry
import io.github.spasarnaudov.trackertavex.core.ui.components.EmptyState
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenError
import io.github.spasarnaudov.trackertavex.core.ui.components.FullScreenLoading
import io.github.spasarnaudov.trackertavex.core.ui.components.LoadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(viewModel: AdminUsersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val tabs = AdminTab.entries

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_admin_users_title)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label()) },
                    )
                }
            }

            when (state.selectedTab) {
                AdminTab.USERS -> UsersTab(state.usersStatus, state.users, state.usersError, viewModel::loadUsers)
                AdminTab.LOGIN_STATS -> LoginStatsTab(state.loginStatsStatus, state.loginStats, state.loginStatsError, viewModel::loadLoginStats)
                AdminTab.LOGIN_HISTORY -> LoginHistoryTab(state.loginHistoryStatus, state.loginHistory, state.loginHistoryError, viewModel::loadLoginHistory)
            }
        }
    }
}

@Composable
private fun AdminTab.label(): String = when (this) {
    AdminTab.USERS -> stringResource(R.string.screen_admin_users_tab_users)
    AdminTab.LOGIN_STATS -> stringResource(R.string.screen_admin_users_tab_login_stats)
    AdminTab.LOGIN_HISTORY -> stringResource(R.string.screen_admin_users_tab_login_history)
}

@Composable
private fun UsersTab(status: LoadStatus, users: List<AdminUserSummary>, error: String?, onRetry: () -> Unit) {
    val genericErrorMessage = stringResource(R.string.common_error_generic)
    val inactiveSuffix = stringResource(R.string.screen_admin_users_inactive_suffix)

    when (status) {
        LoadStatus.LOADING -> FullScreenLoading()
        LoadStatus.ERROR -> FullScreenError(message = error ?: genericErrorMessage, onRetry = onRetry)
        LoadStatus.EMPTY -> EmptyState(message = stringResource(R.string.screen_admin_users_empty_users))
        LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(users, key = { it.id }) { user ->
                ListItem(
                    headlineContent = { Text(user.username) },
                    supportingContent = {
                        val roleText = stringResource(R.string.common_role_label, user.role)
                        val inactiveText = user.active?.let { active -> if (!active) inactiveSuffix else "" } ?: ""
                        Text(roleText + inactiveText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LoginStatsTab(status: LoadStatus, stats: List<LoginStatEntry>, error: String?, onRetry: () -> Unit) {
    val genericErrorMessage = stringResource(R.string.common_error_generic)

    when (status) {
        LoadStatus.LOADING -> FullScreenLoading()
        LoadStatus.ERROR -> FullScreenError(message = error ?: genericErrorMessage, onRetry = onRetry)
        LoadStatus.EMPTY -> EmptyState(message = stringResource(R.string.screen_admin_users_empty_login_stats))
        LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(stats) { entry ->
                ListItem(headlineContent = { Text(entry.label) }, supportingContent = { Text(entry.value) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LoginHistoryTab(status: LoadStatus, history: List<LoginHistoryEntry>, error: String?, onRetry: () -> Unit) {
    val genericErrorMessage = stringResource(R.string.common_error_generic)

    when (status) {
        LoadStatus.LOADING -> FullScreenLoading()
        LoadStatus.ERROR -> FullScreenError(message = error ?: genericErrorMessage, onRetry = onRetry)
        LoadStatus.EMPTY -> EmptyState(message = stringResource(R.string.screen_admin_users_empty_login_history))
        LoadStatus.CONTENT -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history) { entry ->
                ListItem(
                    headlineContent = { Text(entry.fields.joinToString(" · ") { "${it.label}: ${it.value}" }) },
                )
                HorizontalDivider()
            }
        }
    }
}
