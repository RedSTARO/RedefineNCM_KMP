package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = modifier.semantics { heading() }.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** A field and its explicit actions, not a list item masquerading as an input. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsTextField(
    value: String,
    label: String,
    isActive: Boolean,
    onCommit: (String) -> Unit,
    obscureText: Boolean = false,
    supportingText: String? = null,
    validate: (String) -> String? = { null },
    onEditorReady: (TextFieldState) -> Unit = {},
    extraActions: @Composable FlowRowScope.() -> Unit = {},
) {
    // A pasted credential or a URL containing user-info may be in this editor. Keep it in
    // memory, never in a Saver, and re-obscure the credential on Activity recreation.
    val draft = inMemoryViewModel { SettingsEditorDraft(value) }
    var revealText by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val editor = rememberEditorFocus(isActive)
    val text = draft.field.text.toString()
    val hasDraft = text != draft.baseline
    var validationError by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(value) {
        if (value != draft.baseline && draft.field.text.toString() == draft.baseline) {
            draft.field.setTextAndPlaceCursorAtEnd(value)
        }
        draft.baseline = value
    }
    SideEffect { onEditorReady(draft.field) }

    // Saving on blur or disposal would turn a rotation, resize, or opening a dialog into an
    // unintended credential or server change. Commit only on Done/Enter or the visible action.
    fun commit() {
        val raw = draft.field.text.toString()
        if (raw != draft.baseline) {
            val error = validate(raw)
            validationError = error
            if (error != null) return
            onCommit(raw)
        }
        revealText = false
        focusManager.clearFocus()
    }

    val fieldModifier = editor.modifier.fillMaxWidth()
    val keyboardOptions = KeyboardOptions(
        keyboardType = if (obscureText) KeyboardType.Password else KeyboardType.Uri,
        autoCorrectEnabled = false,
        imeAction = ImeAction.Done,
        showKeyboardOnFocus = editor.showKeyboardOnFocus,
    )
    val support = (validationError ?: supportingText)?.let { line ->
        @Composable { Text(line, Modifier.semantics { if (validationError != null) liveRegion = LiveRegionMode.Polite }) }
    }
    val errorIcon: (@Composable () -> Unit)? = if (validationError != null) {
        { Icon(AppIcons.ErrorOutline, contentDescription = null) }
    } else null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (obscureText) {
            OutlinedSecureTextField(
                state = draft.field,
                labelPosition = TextFieldLabelPosition.Above(),
                label = { Text(label) },
                supportingText = support,
                trailingIcon = errorIcon,
                isError = validationError != null,
                textObfuscationMode = if (revealText) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = { commit() },
                shape = MaterialTheme.shapes.small,
                modifier = fieldModifier,
            )
        } else {
            OutlinedTextField(
                state = draft.field,
                labelPosition = TextFieldLabelPosition.Above(),
                label = { Text(label) },
                supportingText = support,
                trailingIcon = errorIcon,
                isError = validationError != null,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = { commit() },
                shape = MaterialTheme.shapes.small,
                modifier = fieldModifier,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (obscureText) {
                TextButton(onClick = { revealText = !revealText }) {
                    Text(if (revealText) strings.hide else strings.show)
                }
            }
            extraActions()
            FilledTonalButton(
                onClick = ::commit,
                enabled = hasDraft,
                shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.medium),
                contentPadding = ButtonDefaults.SmallContentPadding,
            ) {
                Text(if (obscureText) strings.save else strings.saveAddress)
            }
        }
    }
}

private class SettingsEditorDraft(initial: String) : ViewModel() {
    val field = TextFieldState(initial)
    var baseline by mutableStateOf(initial)
}

/** Half the segmented gap belongs to each row, so adjacent rows have one gap between them. */
internal fun Modifier.preferenceItem(): Modifier = fillMaxWidth().padding(vertical = ExpressiveLayout.ConnectedItemGap / 2)

@Composable
internal fun SettingsValue(
    label: String,
    value: String,
    supportingText: String,
    index: Int = 0,
    count: Int = 1,
) {
    Surface(
        shape = connectedListItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.preferenceItem(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    label: String,
    index: Int,
    count: Int,
    supportingText: String? = null,
    onUpdate: (Boolean) -> Unit,
) {
    var state by remember(checked) { mutableStateOf(checked) }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = rememberConnectedListItemShape(index, count, interactionSource),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.preferenceItem(),
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = state,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onValueChange = { updated ->
                        state = updated
                        onUpdate(updated)
                    },
                )
                .preferenceContent(supportingText),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreferenceText(label, supportingText, Modifier.weight(1f))
            Switch(checked = state, onCheckedChange = null)
        }
    }
}

@Composable
internal fun SettingsButton(
    label: String,
    leadingIcon: ImageVector,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = rememberConnectedListItemShape(index, count, interactionSource),
        color = scheme.surfaceContainerHigh,
        contentColor = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f),
        interactionSource = interactionSource,
        modifier = Modifier.preferenceItem(),
    ) {
        Row(
            modifier = Modifier.preferenceContent(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Identity always has room to wrap; the actions wrap as a group under it, not beside its name. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountIdentityCard(
    title: String,
    supportingText: String,
    avatarUrl: String? = null,
    signedIn: Boolean = false,
    index: Int = 0,
    count: Int = 1,
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = connectedListItemShape(index, count),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.preferenceItem(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    Surface(shape = CircleShape, color = scheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    ExpressiveFeatureIcon(AppIcons.Person, containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    if (signedIn) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                            Text(strings.signedIn, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                        }
                    }
                }
            }
            if (actions != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
    }
}

/** A row that opens another page. */
@Composable
internal fun SettingsLinkRow(
    label: String,
    supportingText: String,
    index: Int = 0,
    count: Int = 1,
    leadingIcon: ImageVector? = null,
    external: Boolean = false,
    onClick: () -> Unit,
) = PreferenceNavigationRow(
    label = label,
    supportingText = supportingText,
    leadingIcon = leadingIcon,
    trailingIcon = if (external) AppIcons.OpenInNew else AppIcons.KeyboardArrowRight,
    index = index,
    count = count,
    onClick = onClick,
)

/** A row that shows or hides a related editor beneath it. */
@Composable
internal fun SettingsExpanderRow(label: String, supportingText: String, expanded: Boolean, onToggle: () -> Unit) {
    PreferenceNavigationRow(
        label = label,
        supportingText = supportingText,
        leadingIcon = AppIcons.Lock,
        trailingIcon = if (expanded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowRight,
        state = if (expanded) strings.collapse else strings.expand,
        index = 0,
        count = 1,
        onClick = onToggle,
    )
}

@Composable
private fun PreferenceNavigationRow(
    label: String,
    supportingText: String,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector,
    index: Int,
    count: Int,
    state: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = rememberConnectedListItemShape(index, count, interactionSource),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier.preferenceItem().semantics { state?.let { stateDescription = it } },
    ) {
        Row(
            modifier = Modifier.preferenceContent(supportingText),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            PreferenceText(label, supportingText, Modifier.weight(1f))
            Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun Modifier.preferenceContent(supportingText: String? = null): Modifier =
    fillMaxWidth()
        .heightIn(min = if (supportingText == null) 56.dp else 72.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp)

@Composable
private fun PreferenceText(label: String, supportingText: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (supportingText != null) {
            Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
