package com.crylo.photosize

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crylo.photosize.core.DocKind
import com.crylo.photosize.core.DocSpec
import com.crylo.photosize.core.FitResult
import com.crylo.photosize.core.InkCleaner
import com.crylo.photosize.core.Preset
import com.crylo.photosize.core.SizeFitter
import com.crylo.photosize.data.PresetRepository
import com.crylo.photosize.image.ImageIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { HOME, CROP, RESULT }

/** The finished file plus a decoded copy of it, so the preview shows exactly what is saved. */
class Rendered(val fit: FitResult, val preview: ImageBitmap)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PresetRepository(app)
    private val fitter = SizeFitter()

    var bundled by mutableStateOf<List<Preset>>(emptyList()); private set
    var custom by mutableStateOf<List<Preset>>(emptyList()); private set
    var screen by mutableStateOf(Screen.HOME); private set
    var preset by mutableStateOf<Preset?>(null); private set
    var doc by mutableStateOf<DocSpec?>(null); private set

    /** The decoded, upright source picture shown on the crop screen. */
    var source by mutableStateOf<Bitmap?>(null); private set
    var withStrip by mutableStateOf(false)
    var name by mutableStateOf(""); private set
    var date by mutableStateOf(today()); private set
    var clean by mutableStateOf(true); private set
    var threshold by mutableStateOf(0.8f); private set

    var rendered by mutableStateOf<Rendered?>(null); private set
    var busy by mutableStateOf(false); private set

    /** One-shot message for the snackbar; the UI clears it after showing. */
    var message by mutableStateOf<Int?>(null)

    private var cropped: Bitmap? = null
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            val (b, c) = withContext(Dispatchers.IO) { repo.bundled() to repo.custom() }
            bundled = b
            custom = c
        }
    }

    fun choose(preset: Preset, doc: DocSpec) {
        this.preset = preset
        this.doc = doc
        withStrip = doc.nameDate
        clean = doc.kind == DocKind.INK
    }

    fun load(uri: Uri) {
        busy = true
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ImageIo.decodeUpright(getApplication(), uri) }.getOrNull()
            }
            busy = false
            if (bitmap == null) {
                message = R.string.error_decode
            } else {
                source = bitmap
                screen = Screen.CROP
            }
        }
    }

    fun rotate() {
        val src = source ?: return
        viewModelScope.launch { source = withContext(Dispatchers.Default) { ImageIo.rotate90(src) } }
    }

    fun cropDone(rect: Rect) {
        val src = source ?: return
        val spec = doc ?: return
        busy = true
        viewModelScope.launch {
            cropped = withContext(Dispatchers.Default) { ImageIo.crop(src, rect, spec) }
            screen = Screen.RESULT
            render(debounce = false)
        }
    }

    fun updateName(value: String) { name = value; render() }
    fun updateDate(value: String) { date = value; render() }
    fun updateClean(value: Boolean) { clean = value; render(debounce = false) }
    fun updateThreshold(value: Float) { threshold = value; render() }

    private fun render(debounce: Boolean = true) {
        val photo = cropped ?: return
        val spec = doc ?: return
        val strip = if (withStrip && spec.kind == DocKind.PHOTO) name.trim().uppercase() to date.trim() else null
        val cleanInk = clean && spec.kind == DocKind.INK
        val cut = threshold
        renderJob?.cancel()
        busy = true
        renderJob = viewModelScope.launch {
            if (debounce) delay(250)
            val result = try {
                withContext(Dispatchers.Default) {
                val base = if (cleanInk) ImageIo.applyPixels(photo) { p, w, h -> InkCleaner.clean(p, w, h, cut) } else photo
                val picture = ImageIo.compose(base, spec, strip)
                val fit = fitter.fit(
                    ImageIo.JpegEncoder(picture, spec.dpi),
                    spec.widthPx,
                    spec.heightPx,
                    spec.limits,
                    minWidth = spec.floorWidthPx,
                )
                val preview = BitmapFactory.decodeByteArray(fit.bytes, 0, fit.size).asImageBitmap()
                Rendered(fit, preview)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result == null) message = R.string.error_render else rendered = result
            busy = false
        }
    }

    fun fileName(): String {
        val p = preset?.name ?: "photo"
        val d = doc?.label ?: ""
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val base = "${p}_${d}".replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        return "${base}_$stamp.jpg"
    }

    fun save() {
        val bytes = rendered?.fit?.bytes ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ImageIo.saveToPictures(getApplication(), bytes, fileName()) }.isSuccess
            }
            message = if (ok) R.string.saved else R.string.error_save
        }
    }

    fun back() {
        when (screen) {
            Screen.RESULT -> { screen = Screen.CROP; rendered = null }
            Screen.CROP -> { screen = Screen.HOME; source = null; cropped = null }
            Screen.HOME -> Unit
        }
    }

    fun addCustom(preset: Preset) {
        custom = custom + preset
        persistCustom()
    }

    fun deleteCustom(preset: Preset) {
        custom = custom.filterNot { it.id == preset.id }
        persistCustom()
    }

    private fun persistCustom() {
        val snapshot = custom
        viewModelScope.launch(Dispatchers.IO) { repo.saveCustom(snapshot) }
    }

    private fun today(): String = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
}
