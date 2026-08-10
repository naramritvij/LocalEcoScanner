package com.example.localecoscanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

import kotlin.math.roundToInt


/**
 * Local Eco-Scanner
 *
 * Single-Activity / single-file example using:
 *
 * - Jetpack Compose
 * - Material 3
 * - CameraX
 * - Google Generative AI Android SDK
 *
 * IMPORTANT:
 * The Google Generative AI Android SDK used here is now deprecated.
 * It is retained because this example intentionally matches the requested:
 *
 * GenerativeModel(
 *     modelName = "gemini-2.5-flash",
 *     apiKey = "YOUR_API_KEY"
 * )
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allows the camera UI to extend behind system bars.
        enableEdgeToEdge()

        setContent {
            EcoScannerTheme {
                EcoScannerApp()
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Gemini configuration                                                       */
/* -------------------------------------------------------------------------- */

private const val SYSTEM_INSTRUCTION =
    "You are an expert eco-friendly assistant. Analyze this object image. " +
            "Provide highly actionable instructions under three distinct headers: " +
            "1. Is it Recyclable?, 2. Proper Disposal Steps, 3. Creative Upcycling Ideas. " +
            "Keep responses concise, structured, and easy to read."


/**
 * Sends the captured Bitmap to Gemini.
 *
 * The Bitmap is JPEG-compressed first so a high-resolution CameraX image
 * does not unnecessarily increase request size or memory usage.
 *
 * IMPORTANT FOR A REAL RELEASE:
 * Do not publish an APK containing a real Gemini API key. Anyone can extract
 * client-side secrets from an Android APK.
 */
private suspend fun analyzeWithGemini(bitmap: Bitmap): String =
    withContext(Dispatchers.IO) {

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = "YOUR_API_KEY",
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            }
        )

        val inputContent = content {
            image(bitmap)

            text(
                "Analyze the photographed object. " +
                        "Return only the requested three eco-guidance sections."
            )
        }

        val response = generativeModel.generateContent(inputContent)

        response.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException(
                "Gemini returned an empty response. Please try another photo."
            )
    }


/**
 * Compresses a Bitmap before sending it over the network.
 */

/*
private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            85,
            output
        )

        output.toByteArray()
    }
}
*/

/* -------------------------------------------------------------------------- */
/* Main Compose screen                                                        */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoScannerApp() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    /*
     * UI STATE
     *
     * Every simple screen value is backed by remember { mutableStateOf(...) }.
     *
     * Changes to these states automatically cause the relevant composables
     * to recompose.
     */

    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Tracks whether Android has already displayed a permission request.
    val permissionRequested = remember {
        mutableStateOf(false)
    }

    // CameraX writes its active ImageCapture instance here.
    val imageCapture = remember {
        mutableStateOf<ImageCapture?>(null)
    }

    // Bitmap displayed inside the result bottom sheet.
    val capturedBitmap = remember {
        mutableStateOf<Bitmap?>(null)
    }

    // Gemini's textual response.
    val geminiResult = remember {
        mutableStateOf<String?>(null)
    }

    // True while CameraX is capturing or Gemini is analyzing.
    val isLoading = remember {
        mutableStateOf(false)
    }

    // Controls whether the result ModalBottomSheet is visible.
    val showResultSheet = remember {
        mutableStateOf(false)
    }

    // Any camera/network/API failure is surfaced here.
    val errorMessage = remember {
        mutableStateOf<String?>(null)
    }


    /*
     * Camera operations should not perform bitmap work on the UI thread.
     */
    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }


    /*
     * Android runtime permission contract.
     *
     * The callback directly updates Compose permission state.
     */
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission.value = granted

            if (!granted) {
                errorMessage.value = null
            }
        }


    /*
     * Ask for the camera permission on first launch.
     */
    LaunchedEffect(Unit) {
        if (!hasCameraPermission.value) {
            permissionRequested.value = true

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    /*
     * If the user leaves the app to grant permission through Android Settings,
     * re-check permission when this Activity resumes.
     */
    DisposableEffect(lifecycleOwner, context) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission.value =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    /*
     * Resets scan-related state.
     *
     * CameraX remains bound to the Activity lifecycle, so resetting these
     * values immediately makes the scanner ready for another item.
     */
    fun resetScanner() {
        showResultSheet.value = false
        capturedBitmap.value = null
        geminiResult.value = null
        errorMessage.value = null
        isLoading.value = false
    }


    /*
     * CAPTURE + GEMINI PIPELINE
     */
    fun captureAndAnalyze() {

        if (isLoading.value) {
            return
        }

        val currentImageCapture = imageCapture.value

        if (currentImageCapture == null) {
            errorMessage.value =
                "Camera is still starting. Please try again."
            return
        }

        // The loading overlay appears immediately after tapping capture.
        isLoading.value = true
        errorMessage.value = null

        currentImageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {

                override fun onCaptureSuccess(image: ImageProxy) {

                    try {
                        /*
                         * ImageProxy.toBitmap() gives us a Bitmap from the
                         * CameraX capture.
                         *
                         * CameraX provides rotation metadata separately, so
                         * rotate the Bitmap before displaying/uploading it.
                         */
                        val rawBitmap = image.toBitmap()

                        val rotatedBitmap = rotateBitmap(
                            source = rawBitmap,
                            rotationDegrees = image.imageInfo.rotationDegrees
                        )

                        /*
                         * Camera sensors can produce very large photos.
                         *
                         * 1600 px is plenty for ordinary object recognition
                         * while substantially reducing memory/network usage.
                         */
                        val preparedBitmap = downscaleBitmap(
                            bitmap = rotatedBitmap,
                            maximumDimension = 1600
                        )

                        coroutineScope.launch {

                            // Save image for the future bottom sheet.
                            capturedBitmap.value = preparedBitmap

                            try {
                                /*
                                 * analyzeWithGemini() is suspendable, so this
                                 * does not block Compose's UI thread.
                                 */
                                val result =
                                    analyzeWithGemini(preparedBitmap)

                                geminiResult.value = result

                                // Hide the loading overlay.
                                isLoading.value = false

                                /*
                                 * Adding ModalBottomSheet to composition causes
                                 * Material 3 to animate it upward automatically.
                                 */
                                showResultSheet.value = true

                            } catch (exception: Exception) {

                                isLoading.value = false

                                errorMessage.value =
                                    exception.message
                                        ?: "Could not analyze this item. Please try again."
                            }
                        }

                    } catch (exception: Exception) {

                        coroutineScope.launch {
                            isLoading.value = false

                            errorMessage.value =
                                "Could not process the captured photo."
                        }

                    } finally {

                        /*
                         * ImageProxy MUST always be closed or CameraX can stop
                         * delivering future captures.
                         */
                        image.close()
                    }
                }


                override fun onError(exception: ImageCaptureException) {

                    coroutineScope.launch {
                        isLoading.value = false

                        errorMessage.value =
                            exception.message
                                ?: "Photo capture failed. Please try again."
                    }
                }
            }
        )
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {

        if (hasCameraPermission.value) {

            ScannerScreen(
                imageCaptureState = imageCapture,
                isLoading = isLoading.value,
                errorMessage = errorMessage.value,
                onCapture = {
                    captureAndAnalyze()
                },
                onCameraError = { message ->
                    errorMessage.value = message
                }
            )

        } else {

            PermissionDeniedScreen(
                permissionRequested = permissionRequested.value,
                onGrantPermission = {

                    val activity = context as? ComponentActivity

                    /*
                     * shouldShowRequestPermissionRationale() normally becomes
                     * false if Android will no longer show the dialog.
                     *
                     * In that situation, "Grant Permission" opens the app's
                     * Android settings page instead.
                     */
                    val canRequestAgain =
                        activity == null ||
                                ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity,
                                    Manifest.permission.CAMERA
                                )

                    if (
                        canRequestAgain ||
                        !permissionRequested.value
                    ) {
                        permissionRequested.value = true

                        cameraPermissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    } else {

                        context.openAppSettings()
                    }
                }
            )
        }


        /*
         * RESULT BOTTOM SHEET
         */
        if (
            showResultSheet.value &&
            capturedBitmap.value != null &&
            geminiResult.value != null
        ) {

            val sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true
                )

            ModalBottomSheet(
                onDismissRequest = {
                    resetScanner()
                },
                sheetState = sheetState,
                containerColor = Color(0xFFF8FAF7),
                contentColor = Color(0xFF172018)
            ) {

                ResultSheetContent(
                    bitmap = capturedBitmap.value!!,
                    result = geminiResult.value!!,
                    onScanAnother = {
                        resetScanner()
                    }
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Camera scanner                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun ScannerScreen(
    imageCaptureState: MutableState<ImageCapture?>,
    isLoading: Boolean,
    errorMessage: String?,
    onCapture: () -> Unit,
    onCameraError: (String) -> Unit
) {

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        CameraPreview(
            imageCaptureState = imageCaptureState,
            onCameraError = onCameraError
        )


        /*
         * Dark translucent header background to maintain readability over
         * light camera scenes.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color.Black.copy(alpha = 0.28f))
        )


        /* App heading */

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "♻  Local Eco-Scanner",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "Center an item and take a photo",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp
            )
        }


        /* Minimal scanner frame */

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .aspectRatio(0.78f)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(28.dp)
                )
        )


        /* Capture controls */

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text =
                    if (isLoading) {
                        "Analyzing item…"
                    } else {
                        "Tap to scan"
                    },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            CaptureButton(
                enabled = !isLoading,
                onClick = onCapture
            )
        }


        /*
         * API/camera errors remain visible without destroying the camera
         * preview, allowing the user to immediately retry.
         */
        errorMessage?.let { message ->

            ErrorBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 90.dp
                    )
            )
        }


        /*
         * Loading overlay appears from the moment capture starts until Gemini
         * either succeeds or throws an exception.
         */
        if (isLoading) {

            LoadingOverlay()
        }
    }
}


/* -------------------------------------------------------------------------- */
/* CameraX preview                                                            */
/* -------------------------------------------------------------------------- */

@Composable
private fun CameraPreview(
    imageCaptureState: MutableState<ImageCapture?>,
    onCameraError: (String) -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    /*
     * PreviewView is CameraX's recommended View-based preview surface.
     *
     * AndroidView lets it live underneath the Compose overlay.
     */
    val previewView = remember {

        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER

            /*
             * COMPATIBLE uses a texture-backed implementation where possible,
             * which works reliably underneath Compose overlays.
             */
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE
        }
    }


    DisposableEffect(
        lifecycleOwner,
        previewView
    ) {

        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        val mainExecutor =
            ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({

            if (disposed) {
                return@addListener
            }

            try {

                val provider =
                    cameraProviderFuture.get()

                cameraProvider = provider


                val preview =
                    Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(
                                previewView.surfaceProvider
                            )
                        }


                val imageCapture =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build()


                /*
                 * Remove any previously bound use cases before binding this
                 * scanner's Preview + ImageCapture.
                 */
                provider.unbindAll()


                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )


                /*
                 * Expose the bound capture use case to our scanner button.
                 */
                imageCaptureState.value =
                    imageCapture

            } catch (exception: Exception) {

                imageCaptureState.value = null

                onCameraError(
                    exception.message
                        ?: "Unable to start the camera."
                )
            }

        }, mainExecutor)


        onDispose {

            disposed = true

            imageCaptureState.value = null

            cameraProvider?.unbindAll()
        }
    }


    AndroidView(
        factory = {
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}


/* -------------------------------------------------------------------------- */
/* Capture button                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun CaptureButton(
    enabled: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(94.dp)
            .semantics {
                contentDescription = "Capture item photo"
            }
            .clip(CircleShape)
            .background(
                Color.Black.copy(alpha = 0.28f)
            )
            .border(
                width = 4.dp,
                color =
                    if (enabled) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.45f)
                    },
                shape = CircleShape
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        Color(0xFF79D58A)
                    } else {
                        Color(0xFF6D766E)
                    }
                )
        )
    }
}


/* -------------------------------------------------------------------------- */
/* Permission screen                                                          */
/* -------------------------------------------------------------------------- */

@Composable
private fun PermissionDeniedScreen(
    permissionRequested: Boolean,
    onGrantPermission: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101713))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Color(0xFF22372A)),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "♻",
                fontSize = 46.sp
            )
        }

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        Text(
            text = "Camera access required",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text =
                if (permissionRequested) {
                    "Eco-Scanner needs camera access to photograph items for recycling analysis."
                } else {
                    "Allow camera access to start scanning recyclable items."
                },
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 16.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Button(
            onClick = onGrantPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF80DC91),
                contentColor = Color(0xFF102114)
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {

            Text(
                text = "Grant Permission",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Loading                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun LoadingOverlay() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(alpha = 0.48f)
            ),
        contentAlignment = Alignment.Center
    ) {

        Card(
            colors = CardDefaults.cardColors(
                containerColor =
                    Color(0xEE162019)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {

            Column(
                modifier = Modifier.padding(
                    horizontal = 32.dp,
                    vertical = 28.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                CircularProgressIndicator(
                    color = Color(0xFF81DA91),
                    trackColor =
                        Color.White.copy(alpha = 0.15f)
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                Text(
                    text = "Analyzing your item",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "Checking recycling and upcycling options…",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Error banner                                                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                Color(0xEE3D211E)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Something went wrong",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = message,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Result bottom sheet                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun ResultSheetContent(
    bitmap: Bitmap,
    result: String,
    onScanAnother: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                start = 22.dp,
                end = 22.dp,
                bottom = 28.dp
            )
    ) {

        Text(
            text = "Eco Analysis",
            color = Color(0xFF172018),
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Gemini's recycling and upcycling guidance",
            color = Color(0xFF5B675C),
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )


        /*
         * Visual result: display the exact image used for Gemini analysis.
         */
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Scanned item",
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(
                    RoundedCornerShape(24.dp)
                ),
            contentScale = ContentScale.Crop
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )


        /*
         * Render Gemini's three requested sections.
         *
         * A small formatter makes Markdown-style headers cleaner without
         * introducing a third-party Markdown dependency.
         */
        EcoResultText(
            result = result
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor =
                    Color(0xFFEAF4EB)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {

            Text(
                text =
                    "Recycling programs vary by municipality. " +
                            "Confirm local collection rules when the material is uncertain.",
                color = Color(0xFF405044),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onScanAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    Color(0xFF1E6C37),
                contentColor =
                    Color.White
            )
        ) {

            Text(
                text = "Scan Another Item",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


/**
 * Lightweight formatter for Gemini's structured response.
 *
 * It deliberately avoids a Markdown dependency so the project remains
 * Compose + Android SDK + CameraX + Gemini only.
 */
@Composable
private fun EcoResultText(
    result: String
) {

    val lines = result.lines()

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        lines.forEach { rawLine ->

            val line =
                rawLine
                    .trim()
                    .removePrefix("###")
                    .removePrefix("##")
                    .removePrefix("#")
                    .removePrefix("**")
                    .removeSuffix("**")
                    .trim()

            if (line.isEmpty()) {

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

            } else {

                val isSectionHeader =
                    line.startsWith("1.") ||
                            line.startsWith("2.") ||
                            line.startsWith("3.") ||
                            line.startsWith("Is it Recyclable?", ignoreCase = true) ||
                            line.startsWith("Proper Disposal Steps", ignoreCase = true) ||
                            line.startsWith("Creative Upcycling Ideas", ignoreCase = true)

                Text(
                    text = line,
                    color =
                        if (isSectionHeader) {
                            Color(0xFF17642F)
                        } else {
                            Color(0xFF263229)
                        },
                    fontSize =
                        if (isSectionHeader) {
                            18.sp
                        } else {
                            15.sp
                        },
                    fontWeight =
                        if (isSectionHeader) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    lineHeight = 22.sp
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Bitmap helpers                                                             */
/* -------------------------------------------------------------------------- */

/**
 * Applies CameraX's rotation metadata to the captured Bitmap.
 */
private fun rotateBitmap(
    source: Bitmap,
    rotationDegrees: Int
): Bitmap {

    if (rotationDegrees == 0) {
        return source
    }

    val matrix =
        Matrix().apply {
            postRotate(
                rotationDegrees.toFloat()
            )
        }

    return Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        matrix,
        true
    )
}


/**
 * Reduces enormous camera images before storing/sending them.
 *
 * Aspect ratio is preserved.
 */
private fun downscaleBitmap(
    bitmap: Bitmap,
    maximumDimension: Int
): Bitmap {

    val largestDimension =
        maxOf(
            bitmap.width,
            bitmap.height
        )

    if (largestDimension <= maximumDimension) {
        return bitmap
    }

    val scale =
        maximumDimension.toFloat() /
                largestDimension.toFloat()

    val newWidth =
        (bitmap.width * scale)
            .roundToInt()
            .coerceAtLeast(1)

    val newHeight =
        (bitmap.height * scale)
            .roundToInt()
            .coerceAtLeast(1)

    return Bitmap.createScaledBitmap(
        bitmap,
        newWidth,
        newHeight,
        true
    )
}


/* -------------------------------------------------------------------------- */
/* Android settings helper                                                    */
/* -------------------------------------------------------------------------- */

private fun Context.openAppSettings() {

    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts(
                "package",
                packageName,
                null
            )
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

    startActivity(intent)
}


/* -------------------------------------------------------------------------- */
/* Minimal Material 3 theme                                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun EcoScannerTheme(
    content: @Composable () -> Unit
) {

    val colors =
        darkColorScheme(
            primary = Color(0xFF80DC91),
            onPrimary = Color(0xFF102114),
            background = Color(0xFF101713),
            surface = Color(0xFF172019),
            onSurface = Color.White
        )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}