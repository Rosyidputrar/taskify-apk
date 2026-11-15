package com.rosyidputrar.taskify.feature.finance.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.rosyidputrar.taskify.core.common.Rupiah

/**
 * Input nominal: menerima "25rb", "1,5jt", "Rp 50.000". Di bawahnya tampil nilai yang dibaca
 * ("= Rp 1.500.000") supaya tidak ada salah tafsir; galat tidak muncul selagi sufiks diketik.
 */
@Composable
fun MoneyField(text: String, onText: (String) -> Unit, label: String, modifier: Modifier = Modifier, imeAction: ImeAction = ImeAction.Next) {
    val parsed = Rupiah.parse(text)
    val error = text.isNotBlank() && (parsed == null || parsed <= 0) && !Rupiah.isIncomplete(text)
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text(label) },
        prefix = { Text("Rp ") },
        singleLine = true,
        isError = error,
        supportingText = {
            Text(
                when {
                    error -> "Nominal tidak valid. Contoh: 25000, 25rb, 1,5jt"
                    parsed != null && parsed > 0 -> "= ${Rupiah.format(parsed)}"
                    else -> "Bisa ketik 25rb atau 1,5jt"
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = imeAction),
        modifier = modifier,
    )
}

/** Nilai awal kolom nominal dari angka tersimpan (tanpa simbol, berpemisah ribuan). */
fun moneyText(amount: Long?): String = amount?.takeIf { it > 0 }?.let { Rupiah.format(it, withSymbol = false) } ?: ""
