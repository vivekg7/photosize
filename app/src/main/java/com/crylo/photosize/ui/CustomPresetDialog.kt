package com.crylo.photosize.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crylo.photosize.R
import com.crylo.photosize.core.DocKind
import com.crylo.photosize.core.DocSpec
import com.crylo.photosize.core.Preset

/** A one-document preset for a form the bundled list doesn't cover. */
@Composable
fun CustomPresetDialog(onDismiss: () -> Unit, onSave: (Preset) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var ink by rememberSaveable { mutableStateOf(false) }
    var width by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var minKb by rememberSaveable { mutableStateOf("") }
    var maxKb by rememberSaveable { mutableStateOf("") }
    var dpi by rememberSaveable { mutableStateOf("") }

    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val lo = minKb.ifBlank { "0" }.toIntOrNull()
    val hi = maxKb.toIntOrNull()
    val d = dpi.ifBlank { null }?.toIntOrNull()
    val dimsOk = w != null && h != null && w in 16..6000 && h in 16..6000
    val kbOk = lo != null && hi != null && hi in 1..10_000 && lo in 0..hi
    val dpiOk = dpi.isBlank() || (d != null && d in 1..2400)
    val valid = name.isNotBlank() && dimsOk && kbOk && dpiOk

    val photoLabel = stringResource(R.string.kind_photo)
    val inkLabel = stringResource(R.string.kind_ink)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.custom_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !ink,
                        onClick = { ink = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(photoLabel) }
                    SegmentedButton(
                        selected = ink,
                        onClick = { ink = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(inkLabel) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(width, { width = it }, stringResource(R.string.width_px), Modifier.weight(1f))
                    NumberField(height, { height = it }, stringResource(R.string.height_px), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minKb, { minKb = it }, stringResource(R.string.min_kb), Modifier.weight(1f))
                    NumberField(maxKb, { maxKb = it }, stringResource(R.string.max_kb), Modifier.weight(1f))
                }
                NumberField(dpi, { dpi = it }, stringResource(R.string.dpi_optional), Modifier.fillMaxWidth())
                if (!valid && (width.isNotEmpty() || maxKb.isNotEmpty())) {
                    Text(
                        stringResource(R.string.custom_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val doc = DocSpec(
                        id = "doc",
                        label = if (ink) inkLabel else photoLabel,
                        kind = if (ink) DocKind.INK else DocKind.PHOTO,
                        widthPx = w!!,
                        heightPx = h!!,
                        minKb = lo!!,
                        maxKb = hi!!,
                        dpi = d,
                    )
                    onSave(Preset(id = "custom-${System.currentTimeMillis()}", name = name.trim(), documents = listOf(doc), custom = true))
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
