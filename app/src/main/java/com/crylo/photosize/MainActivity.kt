package com.crylo.photosize

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.photosize.image.ImageIo
import com.crylo.photosize.ui.CropScreen
import com.crylo.photosize.ui.HomeScreen
import com.crylo.photosize.ui.PhotoSizeTheme
import com.crylo.photosize.ui.ResultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PhotoSizeTheme { App() } }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // Survives the activity being recreated while the camera app is in front.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.load(uri)
    }

    val pending = vm.message?.let { stringResource(it) }
    LaunchedEffect(pending) {
        if (pending != null) {
            vm.message = null
            snackbar.showSnackbar(pending)
        }
    }

    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }
    val host = @Composable { SnackbarHost(snackbar) }

    when (vm.screen) {
        Screen.HOME -> HomeScreen(
            vm = vm,
            snackbarHost = host,
            onGallery = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                val file = ImageIo.newCameraFile(context)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                cameraUri = uri
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    vm.message = R.string.error_no_camera
                }
            },
        )
        Screen.CROP -> CropScreen(vm = vm, snackbarHost = host)
        Screen.RESULT -> ResultScreen(
            vm = vm,
            snackbarHost = host,
            onShare = {
                val bytes = vm.rendered?.fit?.bytes ?: return@ResultScreen
                val file = ImageIo.shareFile(context, bytes, vm.fileName())
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
        )
    }
}
