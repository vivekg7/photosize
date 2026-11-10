package com.crylo.photosize.ui

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crylo.photosize.AppViewModel
import com.crylo.photosize.R
import com.crylo.photosize.core.DocKind
import com.crylo.photosize.core.stripHeightPx
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pan and pinch the picture under a fixed frame with the slot's aspect ratio. The frame never
 * moves, so what is inside it is exactly what gets exported; the picture is kept covering the
 * frame at all times so there are never empty edges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(vm: AppViewModel, snackbarHost: @Composable () -> Unit) {
    val source = vm.source ?: return
    val doc = vm.doc ?: return
    val image = remember(source) { source.asImageBitmap() }
    val imgW = source.width.toFloat()
    val imgH = source.height.toFloat()
    val stripLabel = stringResource(R.string.strip_band)
    val measurer = rememberTextMeasurer()
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    val frameColor = MaterialTheme.colorScheme.primary

    // Picture state: zoom relative to "just covers the frame", and the picture point
    // (in source pixels) that sits at the frame centre.
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var center by remember(source) { mutableStateOf(Offset(imgW / 2, imgH / 2)) }
    var frame by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    fun scaleFor(f: androidx.compose.ui.geometry.Rect) = max(f.width / imgW, f.height / imgH) * zoom
    fun clampCenter(c: Offset, f: androidx.compose.ui.geometry.Rect): Offset {
        val s = scaleFor(f)
        val halfW = f.width / 2 / s
        val halfH = f.height / 2 / s
        return Offset(c.x.coerceIn(halfW, max(halfW, imgW - halfW)), c.y.coerceIn(halfH, max(halfH, imgH - halfH)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title, doc.label)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::rotate) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.rotate))
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val margin = with(density) { 24.dp.toPx() }
                val viewW = with(density) { maxWidth.toPx() }
                val viewH = with(density) { maxHeight.toPx() }
                val frameW = minOf(viewW - 2 * margin, (viewH - 2 * margin) * doc.aspect)
                val frameH = frameW / doc.aspect
                frame = androidx.compose.ui.geometry.Rect(
                    Offset((viewW - frameW) / 2, (viewH - frameH) / 2),
                    Size(frameW, frameH),
                )
                Canvas(
                    Modifier.fillMaxSize().pointerInput(source, doc) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 10f)
                            val s = scaleFor(frame)
                            center = clampCenter(center - pan / s, frame)
                        }
                    },
                ) {
                    val f = frame
                    val s = scaleFor(f)
                    val c = clampCenter(center, f)
                    withTransform({
                        translate(f.center.x - c.x * s, f.center.y - c.y * s)
                        scale(s, s, pivot = Offset.Zero)
                    }) {
                        drawImage(image)
                    }
                    // Dim everything outside the frame.
                    drawRect(scrim, Offset.Zero, Size(size.width, f.top))
                    drawRect(scrim, Offset(0f, f.bottom), Size(size.width, size.height - f.bottom))
                    drawRect(scrim, Offset(0f, f.top), Size(f.left, f.height))
                    drawRect(scrim, Offset(f.right, f.top), Size(size.width - f.right, f.height))
                    if (vm.withStrip && doc.kind == DocKind.PHOTO) {
                        val band = f.height * stripHeightPx(doc.heightPx) / doc.heightPx
                        drawRect(Color.White.copy(alpha = 0.85f), Offset(f.left, f.bottom - band), Size(f.width, band))
                        val layout = measurer.measure(
                            stripLabel,
                            TextStyle(color = Color.Black, fontSize = 13.sp, textAlign = TextAlign.Center),
                        )
                        drawText(
                            layout,
                            topLeft = Offset(
                                f.center.x - layout.size.width / 2,
                                f.bottom - band / 2 - layout.size.height / 2,
                            ),
                        )
                    }
                    drawRect(frameColor, f.topLeft, f.size, style = Stroke(width = 2.dp.toPx()))
                    // Rule-of-thirds guides help line up eyes and shoulders.
                    for (i in 1..2) {
                        val x = f.left + f.width * i / 3
                        val y = f.top + f.height * i / 3
                        drawLine(frameColor.copy(alpha = 0.4f), Offset(x, f.top), Offset(x, f.bottom))
                        drawLine(frameColor.copy(alpha = 0.4f), Offset(f.left, y), Offset(f.right, y))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (doc.kind == DocKind.PHOTO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.strip_toggle), Modifier.weight(1f))
                        Switch(checked = vm.withStrip, onCheckedChange = { vm.withStrip = it })
                    }
                }
                Button(
                    onClick = {
                        val f = frame
                        val s = scaleFor(f)
                        val c = clampCenter(center, f)
                        val left = c.x - f.width / 2 / s
                        val top = c.y - f.height / 2 / s
                        vm.cropDone(
                            Rect(
                                left.roundToInt(),
                                top.roundToInt(),
                                (left + f.width / s).roundToInt(),
                                (top + f.height / s).roundToInt(),
                            ),
                        )
                    },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.next)) }
            }
        }
    }
}
