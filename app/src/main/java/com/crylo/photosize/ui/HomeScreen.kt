package com.crylo.photosize.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crylo.photosize.AppViewModel
import com.crylo.photosize.R
import com.crylo.photosize.core.DocKind
import com.crylo.photosize.core.DocSpec
import com.crylo.photosize.core.Preset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    snackbarHost: @Composable () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Preset?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = snackbarHost,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.custom_size)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SpecWarning() }
                if (vm.custom.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.section_custom)) }
                    items(vm.custom, key = { it.id }) { preset ->
                        PresetCard(
                            preset = preset,
                            initiallyExpanded = true,
                            onDoc = { vm.choose(preset, it); picking = true },
                            onDelete = { deleting = preset },
                        )
                    }
                }
                item { SectionTitle(stringResource(R.string.section_exams)) }
                items(vm.bundled, key = { it.id }) { preset ->
                    PresetCard(preset = preset, onDoc = { vm.choose(preset, it); picking = true })
                }
            }
        }
    }

    if (picking) {
        val doc = vm.doc
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.pick_title, doc?.label.orEmpty())) },
            text = {
                Text(
                    stringResource(
                        if (doc?.kind == DocKind.INK) R.string.pick_hint_ink else R.string.pick_hint_photo,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { picking = false; onGallery() }) { Text(stringResource(R.string.gallery)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false; onCamera() }) { Text(stringResource(R.string.camera)) }
            },
        )
    }
    if (creating) {
        CustomPresetDialog(onDismiss = { creating = false }, onSave = { vm.addCustom(it); creating = false })
    }
    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title, preset.name)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteCustom(preset); deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SpecWarning() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.spec_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PresetCard(
    preset: Preset,
    initiallyExpanded: Boolean = false,
    onDoc: (DocSpec) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(preset.id) { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    preset.documents.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            )
        }
        if (expanded) {
            preset.documents.forEach { doc ->
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().clickable { onDoc(doc) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(doc.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        specLine(doc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    doc.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (preset.notes != null || preset.source != null) {
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    preset.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    preset.source?.let {
                        Text(
                            stringResource(R.string.source, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun specLine(doc: DocSpec): String {
    val size = if (doc.minWidthPx != null) {
        stringResource(
            R.string.spec_px_range,
            doc.minWidthPx,
            doc.minWidthPx * doc.heightPx / doc.widthPx,
            doc.widthPx,
            doc.heightPx,
        )
    } else {
        stringResource(R.string.spec_px, doc.widthPx, doc.heightPx)
    }
    val kb = stringResource(R.string.spec_kb, doc.minKb, doc.maxKb)
    val dpi = doc.dpi?.let { " · " + stringResource(R.string.spec_dpi, it) } ?: ""
    return "$size · $kb$dpi"
}
