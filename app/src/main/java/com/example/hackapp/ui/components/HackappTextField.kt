package com.example.hackapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.ui.theme.HackappTheme

/** Figma "Text field" (M3 outlined), with the clear icon shown once it has a value. */
@Composable
fun HackappTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
        ),
        trailingIcon = if (value.isNotEmpty() && enabled) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Cancel, contentDescription = stringResource(R.string.action_clear_field, label))
                }
            }
        } else {
            null
        },
        colors = colors,
    )
}

/**
 * A read-only field that opens a picker when tapped (date, time, budget, District).
 * TalkBack sees one button named [label] whose state is [value]; the field itself is silent.
 * [choiceList] adds the drop-down arrow for pickers that open a list of options.
 */
@Composable
fun HackappPickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    choiceList: Boolean = false,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            trailingIcon = if (choiceList) {
                { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = label
                    if (value.isNotBlank()) stateDescription = value
                }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        )
    }
}

@Preview(name = "Text fields", showBackground = true, widthDp = PreviewDefaults.WIDTH_DP)
@Composable
private fun HackappTextFieldPreview() = HackappTheme {
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HackappTextField("nikola@exemple.ca", {}, stringResource(R.string.auth_email), keyboardType = KeyboardType.Email)
        HackappTextField("secret", {}, stringResource(R.string.auth_password), isPassword = true)
        HackappPickerField("sam. 28 nov. 2026", stringResource(R.string.planning_date), {})
        HackappPickerField("Ville-Marie", stringResource(R.string.profile_district_of_stay), {}, choiceList = true)
    }
}
