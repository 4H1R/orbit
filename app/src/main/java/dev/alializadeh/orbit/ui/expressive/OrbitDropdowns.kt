package dev.alializadeh.orbit.ui.expressive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction

/**
 * A read-only field that opens a menu of [options] (the Contact method type). [leadingIcon] draws
 * an option's icon, both in the field and in the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OrbitSelectField(
    selected: T,
    options: List<T>,
    label: String,
    optionText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = optionText(selected)
    val fieldState = rememberTextFieldState(selectedText)
    LaunchedEffect(selectedText) { fieldState.setTextAndPlaceCursorAtEnd(selectedText) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            state = fieldState,
            readOnly = true,
            label = { Text(label) },
            lineLimits = TextFieldLineLimits.SingleLine,
            leadingIcon = leadingIcon?.let { { it(selected) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    leadingIcon = leadingIcon?.let { { it(option) } },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** One line of an [OrbitAutocompleteField]'s menu. */
data class OrbitSuggestion(val text: String, val label: String = text)

/**
 * A text field with suggestions below it while it has focus and there's something to suggest
 * (Tags, Likes…). Enter calls [onSubmit]; picking a suggestion calls [onSuggestion].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitAutocompleteField(
    state: TextFieldState,
    label: String,
    suggestions: List<OrbitSuggestion>,
    onSuggestion: (OrbitSuggestion) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    inputTransformation: InputTransformation? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state.text) { dismissed = false }
    val expanded = focused && !dismissed && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!it) dismissed = true },
        modifier = modifier,
    ) {
        OutlinedTextField(
            state = state,
            label = { Text(label) },
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = inputTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { onSubmit() },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged { focused = it.isFocused }
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { dismissed = true },
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.label) },
                    onClick = { onSuggestion(suggestion) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
