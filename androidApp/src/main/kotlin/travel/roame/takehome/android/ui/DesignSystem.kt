package travel.roame.takehome.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R

/*
 * D38: the app's own components, drawn the same way here and in iOS's DesignSystem.swift, so the two apps look and
 * behave alike. Sizes are in dp here and points on iOS, which match.
 */

enum class AppTab { Explore, Saved, Account }

/** Roame's tab bar: white, a thin top border, and the selected tab in pink with a pink line along its top. */
@Composable
fun AppTabBar(selected: AppTab, savedCount: Int, onSelect: (AppTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            TabItem(stringResource(R.string.tab_explore), R.drawable.ms_search, selected == AppTab.Explore, null) { onSelect(AppTab.Explore) }
            TabItem(
                stringResource(R.string.tab_saved),
                if (selected == AppTab.Saved) R.drawable.ms_favorite_filled else R.drawable.ms_favorite,
                selected == AppTab.Saved,
                badge = savedCount.takeIf { it > 0 },
            ) { onSelect(AppTab.Saved) }
            TabItem(stringResource(R.string.tab_account), R.drawable.ms_person, selected == AppTab.Account, null) { onSelect(AppTab.Account) }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, @DrawableRes icon: Int, selected: Boolean, badge: Int?, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    // The badge's count is part of what TalkBack reads, unlike Material's tab bar (Step 4.4).
    val description = if (badge != null) pluralStringResource(R.plurals.tab_with_count, badge, label, badge) else label
    Column(
        Modifier.weight(1f)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
        Spacer(Modifier.height(8.dp))
        Box {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            if (badge != null) CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .background(LocalExtraColors.current.brand, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(groupedCount(count), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Roame's segmented control: a grey track with the chosen option as a pink pill. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = selected, onClick = { onSelect(index) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A filter chip: white with a border, or pink when active, with ✕ to clear it on its own. */
@Composable
fun AppChip(label: String, active: Boolean, description: String, onClick: () -> Unit, onClear: (() -> Unit)? = null, clearDescription: String? = null) {
    Row(
        Modifier.height(36.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (active) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 14.dp, end = if (active && onClear != null) 6.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (active && onClear != null) {
            Icon(
                painterResource(R.drawable.ms_close),
                contentDescription = clearDescription,
                modifier = Modifier.padding(start = 4.dp).size(24.dp).clip(CircleShape).clickable(onClick = onClear).padding(3.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** The main action: a pink pill with white text. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    ButtonShape(
        text, onClick, modifier, enabled, leading,
        background = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary,
    )
}

/** A secondary action, such as Remove: pink text on a light pink pill. */
@Composable
fun TintedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    ButtonShape(
        text, onClick, modifier, enabled, leading,
        background = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ButtonShape(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, leading: (@Composable () -> Unit)?, background: Color, content: Color) {
    Row(
        modifier.heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (enabled) background else background.copy(alpha = 0.6f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides content) { leading() }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
}

/** Pink text that acts as a button, such as Reset, with a 44 dp touch target. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** A message at the top of the screen, with an optional action such as Retry. */
@Composable
fun AppToast(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .background(Color(0xFF2B2B2B), RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        if (action != null) {
            Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFB3C4))
            }
        }
    }
}

/** An empty or error screen: an icon in a pink circle, a title, a line of explanation, and an optional action. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** A tab's large title, like Roame's. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineLarge)
}

/** A pushed page's header: back on the left, the title centred. */
@Composable
fun SubpageTopBar(title: String, onBack: () -> Unit) {
    val back = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = back) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(48.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** A Roame list row: an icon, a label and a chevron. */
@Composable
fun ListRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit, showChevron: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = tint)
        if (showChevron) Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** One choice in a sheet: its label, and a pink check when chosen. */
@Composable
fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Checkbox)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(painterResource(R.drawable.ms_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
