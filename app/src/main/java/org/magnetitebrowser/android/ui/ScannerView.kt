package org.magnetitebrowser.android.ui

import android.Manifest
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.magnetitebrowser.android.model.JoinLink
import java.util.concurrent.Executors

/**
 * The camera, looking for the QR code that Magnetite on a computer shows
 * beside its sync code. Hands the first one it reads to [onRead]; other QR
 * codes are passed over. Nothing the camera sees is kept or sent.
 */
@Composable
fun ScannerView(onRead: (JoinLink) -> Unit, onDone: () -> Unit) {
    // Whether the camera may be used; null while the system asks.
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { ask.launch(Manifest.permission.CAMERA) }

    Screen("Scan the QR Code", onDone) {
        when (allowed) {
            true -> {
                Camera(onRead)
                Hint("On your computer: Settings → Sync → Show sync code.")
            }
            false -> Hint("Magnetite may not use the camera. Allow it in the system's settings for this app, or type the 24 words instead.")
            null -> {}
        }
    }
}

/** The camera's picture; every frame is searched for a join link until one is found. */
@Composable
private fun Camera(onRead: (JoinLink) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val read by rememberUpdatedState(onRead)
    val frames = remember { Executors.newSingleThreadExecutor() }
    // Read and written on the main thread only: false once the scanner is gone.
    var shown by remember { mutableStateOf(true) }
    val camera = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // The code has many small squares: the default 640 × 480 is too coarse for it.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
            // Only the frames' thread touches it: one link is handed on, not one per frame.
            var found = false
            setImageAnalysisAnalyzer(frames) { frame ->
                val link = frame.use(::qrText)?.let(JoinLink::parse)
                if (link == null || found) return@setImageAnalysisAnalyzer
                found = true
                // The controller is the main thread's, and a link read just
                // as the scanner was closed is dropped.
                ContextCompat.getMainExecutor(context).execute {
                    if (!shown) return@execute
                    clearImageAnalysisAnalyzer()
                    read(link)
                }
            }
        }
    }
    DisposableEffect(owner) {
        camera.bindToLifecycle(owner)
        onDispose {
            shown = false
            camera.unbind()
            frames.shutdown()
        }
    }
    AndroidView(
        { PreviewView(it).apply { controller = camera } },
        Modifier.fillMaxWidth().padding(top = 8.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)).testTag("scan.camera"),
    )
}

/** The text of a QR code in [frame], if it shows one. Its brightness plane is all that is looked at. */
private fun qrText(frame: ImageProxy): String? {
    val plane = frame.planes[0]
    val bytes = ByteArray(plane.rowStride * frame.height)
    // The last row may end before the stride does.
    plane.buffer.get(bytes, 0, minOf(bytes.size, plane.buffer.remaining()))
    return qrText(bytes, plane.rowStride, frame.width, frame.height)
}

/**
 * The text of a QR code in a picture given as its brightness values, row by
 * row with [stride] bytes each of which the first [width] count; `null`
 * when it shows none.
 */
internal fun qrText(brightness: ByteArray, stride: Int, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(brightness, stride, height, 0, 0, width, height, false)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: ReaderException) {
        null
    }
}
