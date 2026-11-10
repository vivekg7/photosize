package com.crylo.photosize.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.crylo.photosize.AppViewModel
import com.crylo.photosize.R
import com.crylo.photosize.core.DocKind
import com.crylo.photosize.core.FitResult
import com.crylo.photosize.core.formatKb
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: AppViewModel, snackbarHost: @Composable () -> Unit, onShare: () -> Unit) {
    val doc = vm.doc ?: return
    val rendered = vm.rendered
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.preset?.name.orEmpty() + " · " + doc.label) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (rendered != null) {
                        Image(
                            rendered.preview,
                            contentDescription = stringResource(R.string.preview),
                            // Nearest-neighbour so a small exam image is shown as it really is,
                            // not smoothed into looking better than the file.
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .aspectRatio(rendered.fit.width.toFloat() / rendered.fit.height)
                                .border(1.dp, MaterialTheme.colorScheme.outline),
                        )
                    }
                }
                rendered?.let { SizeCard(it.fit, doc.minKb, doc.maxKb, doc.widthPx) }

                if (doc.kind == DocKind.INK) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.clean_toggle))
                            Text(
                                stringResource(R.string.clean_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = vm.clean, onCheckedChange = vm::updateClean)
                    }
                    if (vm.clean) {
                        Text(stringResource(R.string.threshold), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = vm.threshold, onValueChange = vm::updateThreshold, valueRange = 0.5f..0.95f)
                    }
                }
                if (doc.kind == DocKind.PHOTO && vm.withStrip) {
                    OutlinedTextField(
                        value = vm.name,
                        onValueChange = vm::updateName,
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = vm.date,
                        onValueChange = vm::updateDate,
                        label = { Text(stringResource(R.string.photo_date)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                doc.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ready = rendered != null && rendered.fit.status == FitResult.Status.OK && !vm.busy
                OutlinedButton(onClick = onShare, enabled = ready, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
                Button(onClick = vm::save, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun SizeCard(fit: FitResult, minKb: Int, maxKb: Int, fullWidth: Int) {
    val ok = fit.status == FitResult.Status.OK
    val good = if (MaterialTheme.colorScheme.background.luminanceIsDark()) Color(0xFF81C995) else Color(0xFF1E8E3E)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (ok) good else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.result_line, formatKb(fit.size), fit.width, fit.height),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(
                    R.string.result_detail,
                    NumberFormat.getIntegerInstance().format(fit.size),
                    fit.quality,
                    minKb,
                    maxKb,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ok) Text(stringResource(R.string.result_too_large), color = MaterialTheme.colorScheme.error)
            if (fit.width < fullWidth) {
                Text(stringResource(R.string.result_shrunk), style = MaterialTheme.typography.bodySmall)
            }
            if (fit.paddedBytes > 0) {
                Text(stringResource(R.string.result_padded), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
