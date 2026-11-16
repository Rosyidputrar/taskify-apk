package com.rosyidputrar.taskify.feature.auth.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles

/**
 * Input kode 6 digit berbentuk kotak-kotak (pola Material Code Input). Satu field tersembunyi
 * menampung teks sehingga tempel & isi otomatis SMS/authenticator tetap bekerja.
 */
@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(length)
            onValueChange(digits)
            if (digits.length == length) onComplete(digits)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onComplete(value) }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .semantics { contentDescription = "Kode $length digit" },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(length) { index ->
                    val char = value.getOrNull(index)
                    val active = index == value.length
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char?.toString() ?: "", style = TaskifyTextStyles.numeric.copy(fontFamily = TaskifyTextStyles.code.fontFamily))
                    }
                }
            }
        },
    )
}
