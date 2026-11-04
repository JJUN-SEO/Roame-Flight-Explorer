package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.BuildConfig
import travel.roame.takehome.android.R

private enum class AccountPage { Settings, Profile, Privacy, Info }

/**
 * D39: a placeholder like Roame's account tab. The app has no accounts, so only Settings works (light, dark or follow
 * the system); the other rows say what they would do. The same rows and pages as iOS's AccountScreen.
 */
@Composable
fun AccountScreen(appearance: MutableState<Appearance>, onToast: (String) -> Unit) {
    var page by rememberSaveable { mutableStateOf<AccountPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val logOutNote = stringResource(R.string.log_out_placeholder)
    when (page) {
        null -> AccountHome(onOpen = { page = it }, onLogOut = { onToast(logOutNote) })
        AccountPage.Settings -> Subpage(stringResource(R.string.account_settings), onBack = { page = null }) {
            SectionLabel(stringResource(R.string.settings_appearance))
            val options = listOf(stringResource(R.string.appearance_system), stringResource(R.string.appearance_light), stringResource(R.string.appearance_dark))
            SegmentedControl(options, appearance.value.ordinal, onSelect = { appearance.value = Appearance.entries[it] }, Modifier.padding(horizontal = 16.dp))
            Note(stringResource(R.string.appearance_note))
        }
        AccountPage.Profile -> Subpage(stringResource(R.string.account_change_profile), onBack = { page = null }) {
            Note(stringResource(R.string.profile_placeholder))
        }
        AccountPage.Privacy -> Subpage(stringResource(R.string.account_privacy), onBack = { page = null }) {
            Paragraph(stringResource(R.string.privacy_location_title), stringResource(R.string.privacy_location_body))
            Paragraph(stringResource(R.string.privacy_data_title), stringResource(R.string.privacy_data_body))
            Paragraph(stringResource(R.string.privacy_map_title), stringResource(R.string.privacy_map_body_android))
        }
        AccountPage.Info -> Subpage(stringResource(R.string.account_info), onBack = { page = null }) {
            Paragraph(stringResource(R.string.app_name_full), stringResource(R.string.info_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            Paragraph(stringResource(R.string.info_about_title), stringResource(R.string.info_about_body))
            Paragraph(stringResource(R.string.info_credits_title), stringResource(R.string.info_credits_body_android))
            Paragraph(stringResource(R.string.info_photos_title), stringResource(R.string.info_photos_body))
        }
    }
}

@Composable
private fun AccountHome(onOpen: (AccountPage) -> Unit, onLogOut: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle(stringResource(R.string.tab_account), Modifier.semantics { heading() })
        ProfileHeader(onClick = { onOpen(AccountPage.Profile) })
        Spacer(Modifier.height(8.dp))
        ListRow(R.drawable.ms_settings, stringResource(R.string.account_settings), onClick = { onOpen(AccountPage.Settings) })
        ListRow(R.drawable.ms_person, stringResource(R.string.account_change_profile), onClick = { onOpen(AccountPage.Profile) })
        ListRow(R.drawable.ms_lock, stringResource(R.string.account_privacy), onClick = { onOpen(AccountPage.Privacy) })
        ListRow(R.drawable.ms_info, stringResource(R.string.account_info), onClick = { onOpen(AccountPage.Info) })
        ListRow(R.drawable.ms_logout, stringResource(R.string.account_log_out), onClick = onLogOut, showChevron = false, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Roame's profile header: a gradient avatar with an initial, the name, and "View profile". A placeholder here. */
@Composable
private fun ProfileHeader(onClick: () -> Unit) {
    val extras = LocalExtraColors.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).background(Brush.linearGradient(listOf(extras.gradientStart, extras.gradientEnd)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.account_guest_initial), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.account_guest), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.account_view_profile), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Subpage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubpageTopBar(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Paragraph(title: String, body: String) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
