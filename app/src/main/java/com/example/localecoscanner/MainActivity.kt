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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.concurrent.Executors

import kotlin.math.roundToInt


/**
 * Local Eco-Scanner
 *
 * CameraX + Jetpack Compose + Gemini image analysis.
 *
 * Gemini API key:
 *
 * local.properties:
 *
 * GEMINI_API_KEY=your_key_here
 *
 * BuildConfig exposes it as:
 *
 * BuildConfig.GEMINI_API_KEY
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            EcoScannerTheme {
                EcoScannerApp()
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* App configuration                                                          */
/* -------------------------------------------------------------------------- */

private const val GEMINI_MODEL_NAME = "gemini-2.5-flash"

private const val MAX_IMAGE_DIMENSION = 1600


private const val SYSTEM_INSTRUCTION =
    "You are an expert eco-friendly assistant. Analyze this object image. " +
            "Provide highly actionable instructions under three distinct headers: " +
            "1. Is it Recyclable?, 2. Proper Disposal Steps, 3. Creative Upcycling Ideas. " +
            "Keep responses concise, structured, and easy to read."


/* -------------------------------------------------------------------------- */
/* Local recycling profile                                                    */
/* -------------------------------------------------------------------------- */

private const val PREFS_NAME =
    "eco_scanner_preferences"

private const val KEY_PROVINCE =
    "province"

private const val KEY_PROVINCE_CODE =
    "province_code"

private const val KEY_CITY =
    "city"

private const val KEY_POSTAL_CODE =
    "postal_code"

private const val KEY_HOME_TYPE =
    "home_type"


/**
 * User's Canadian recycling location.
 *
 * This is stored locally on the phone.
 */
private data class RecyclingLocation(
    val province: String,
    val provinceCode: String,
    val city: String,
    val postalCode: String,
    val homeType: String
)


private val CANADIAN_PROVINCES =
    listOf(
        "Alberta" to "AB",
        "British Columbia" to "BC",
        "Manitoba" to "MB",
        "New Brunswick" to "NB",
        "Newfoundland and Labrador" to "NL",
        "Northwest Territories" to "NT",
        "Nova Scotia" to "NS",
        "Nunavut" to "NU",
        "Ontario" to "ON",
        "Prince Edward Island" to "PE",
        "Quebec" to "QC",
        "Saskatchewan" to "SK",
        "Yukon" to "YT"
    )


private val HOME_TYPES =
    listOf(
        "House",
        "Apartment",
        "Condo",
        "Co-op",
        "Other"
    )


/**
 * Stores the local recycling profile.
 */
private fun saveRecyclingLocation(
    context: Context,
    location: RecyclingLocation
) {

    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            KEY_PROVINCE,
            location.province
        )
        .putString(
            KEY_PROVINCE_CODE,
            location.provinceCode
        )
        .putString(
            KEY_CITY,
            location.city
        )
        .putString(
            KEY_POSTAL_CODE,
            location.postalCode
        )
        .putString(
            KEY_HOME_TYPE,
            location.homeType
        )
        .apply()
}


/**
 * Returns the saved recycling profile.
 *
 * null means the user has not completed setup yet.
 */
private fun loadRecyclingLocation(
    context: Context
): RecyclingLocation? {

    val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )


    val province =
        preferences
            .getString(
                KEY_PROVINCE,
                null
            )
            ?.trim()
            .orEmpty()


    val provinceCode =
        preferences
            .getString(
                KEY_PROVINCE_CODE,
                null
            )
            ?.trim()
            .orEmpty()


    val city =
        preferences
            .getString(
                KEY_CITY,
                null
            )
            ?.trim()
            .orEmpty()


    val postalCode =
        preferences
            .getString(
                KEY_POSTAL_CODE,
                null
            )
            ?.trim()
            .orEmpty()


    val homeType =
        preferences
            .getString(
                KEY_HOME_TYPE,
                null
            )
            ?.trim()
            .orEmpty()


    if (
        province.isBlank() ||
        provinceCode.isBlank() ||
        city.isBlank() ||
        postalCode.isBlank() ||
        homeType.isBlank()
    ) {
        return null
    }


    return RecyclingLocation(
        province = province,
        provinceCode = provinceCode,
        city = city,
        postalCode = postalCode,
        homeType = homeType
    )
}


/**
 * Converts:
 *
 * M5V2T6
 *
 * into:
 *
 * M5V 2T6
 */
private fun normalizePostalCode(
    postalCode: String
): String {

    val compact =
        postalCode
            .uppercase()
            .replace(
                " ",
                ""
            )


    return if (compact.length == 6) {

        "${compact.substring(0, 3)} ${compact.substring(3)}"

    } else {

        postalCode
            .trim()
            .uppercase()
    }
}


/**
 * Basic Canadian postal-code validation.
 */
private fun isValidCanadianPostalCode(
    postalCode: String
): Boolean {

    val compact =
        postalCode
            .uppercase()
            .replace(
                " ",
                ""
            )


    val regex =
        Regex(
            "^[ABCEGHJ-NPRSTVXY]\\d[ABCEGHJ-NPRSTV-Z]\\d[ABCEGHJ-NPRSTV-Z]\\d$"
        )


    return regex.matches(
        compact
    )
}


/* -------------------------------------------------------------------------- */
/* Gemini                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * Sends:
 *
 * 1. Photo
 * 2. Province
 * 3. Municipality
 * 4. Postal code
 * 5. Residence type
 *
 * to Gemini.
 */
private suspend fun analyzeWithGemini(
    bitmap: Bitmap,
    location: RecyclingLocation
): String =
    withContext(Dispatchers.IO) {

        val apiKey =
            BuildConfig
                .GEMINI_API_KEY
                .trim()


        if (apiKey.isBlank()) {

            throw IllegalStateException(
                "Gemini API key was not loaded. " +
                        "Check GEMINI_API_KEY in local.properties and rebuild the app."
            )
        }


        if (
            apiKey == "YOUR_API_KEY" ||
            apiKey == "BuildConfig.GEMINI_API_KEY"
        ) {

            throw IllegalStateException(
                "Gemini API key is still using a placeholder value."
            )
        }


        val generativeModel =
            GenerativeModel(
                modelName =
                    GEMINI_MODEL_NAME,

                apiKey =
                    apiKey,

                systemInstruction =
                    content {

                        text(
                            SYSTEM_INSTRUCTION +
                                    " The user is located in Canada. " +
                                    "Use the supplied province or territory, municipality, " +
                                    "postal code, and residence type when determining disposal guidance. " +
                                    "Canadian recycling rules vary by municipality. " +
                                    "Do not intentionally substitute another municipality's rules. " +
                                    "If you are not confident that a specific municipal rule is current, " +
                                    "clearly tell the user to verify that point with their municipality."
                        )
                    }
            )


        val localPrompt =
            """
            Analyze the photographed object for this Canadian resident.

            LOCATION
            Country: Canada
            Province/Territory: ${location.province} (${location.provinceCode})
            City/Municipality: ${location.city}
            Postal Code: ${location.postalCode}
            Residence Type: ${location.homeType}

            First identify the photographed object and its likely material.

            Then provide exactly these three sections:

            1. Is it Recyclable?
            2. Proper Disposal Steps
            3. Creative Upcycling Ideas

            LOCAL GUIDANCE REQUIREMENTS:

            - Make disposal guidance relevant to ${location.city}, ${location.province}.
            - Consider the residence type: ${location.homeType}.
            - Clearly state the most appropriate disposal stream when possible:
              curbside recycling, garbage, organics, hazardous waste,
              depot/drop-off, donation, special collection, or another appropriate stream.
            - Do not use another Canadian municipality's rules as though they apply here.
            - Do not invent collection schedules, bin colours, accepted materials,
              drop-off locations, fees, or municipal regulations.
            - If the exact current municipal rule cannot be established confidently,
              explicitly say: "Verify with your municipality."
            - Keep the response concise, practical, and easy to follow.
            """.trimIndent()


        val inputContent =
            content {

                image(
                    bitmap
                )

                text(
                    localPrompt
                )
            }


        try {

            val response =
                generativeModel.generateContent(
                    inputContent
                )


            response.text
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: throw IllegalStateException(
                    "Gemini returned an empty response. Please try another photo."
                )

        } catch (exception: Exception) {

            throw IllegalStateException(
                exception.message
                    ?: "Gemini analysis failed. Check your internet connection and try again.",
                exception
            )
        }
    }


/* -------------------------------------------------------------------------- */
/* Main Compose app                                                           */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoScannerApp() {

    val context =
        LocalContext.current


    val lifecycleOwner =
        LocalLifecycleOwner.current


    val coroutineScope =
        rememberCoroutineScope()


    /*
     * Saved location profile.
     */
    val recyclingLocation =
        remember {

            mutableStateOf(
                loadRecyclingLocation(
                    context
                )
            )
        }


    /*
     * First launch:
     *
     * null location -> show setup.
     */
    val showLocationSetup =
        remember {

            mutableStateOf(
                recyclingLocation.value == null
            )
        }


    /*
     * Camera permission.
     */
    val hasCameraPermission =
        remember {

            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }


    val permissionRequested =
        remember {

            mutableStateOf(
                false
            )
        }


    /*
     * CameraX capture instance.
     */
    val imageCapture =
        remember {

            mutableStateOf<ImageCapture?>(
                null
            )
        }


    /*
     * Last captured image.
     */
    val capturedBitmap =
        remember {

            mutableStateOf<Bitmap?>(
                null
            )
        }


    /*
     * Gemini response.
     */
    val geminiResult =
        remember {

            mutableStateOf<String?>(
                null
            )
        }


    /*
     * Capture / analysis loading state.
     */
    val isLoading =
        remember {

            mutableStateOf(
                false
            )
        }


    /*
     * Result bottom sheet state.
     */
    val showResultSheet =
        remember {

            mutableStateOf(
                false
            )
        }


    /*
     * Human-readable error.
     */
    val errorMessage =
        remember {

            mutableStateOf<String?>(
                null
            )
        }


    /*
     * Camera capture executor.
     */
    val cameraExecutor =
        remember {

            Executors.newSingleThreadExecutor()
        }


    DisposableEffect(Unit) {

        onDispose {

            cameraExecutor.shutdown()
        }
    }


    /* ---------------------------------------------------------------------- */
    /* Camera permission                                                      */
    /* ---------------------------------------------------------------------- */

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission.value =
                granted


            if (!granted) {

                errorMessage.value =
                    null
            }
        }


    /*
     * Ask for camera permission only AFTER location setup.
     */
    LaunchedEffect(
        showLocationSetup.value
    ) {

        if (
            !showLocationSetup.value &&
            !hasCameraPermission.value
        ) {

            permissionRequested.value =
                true


            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    /*
     * Re-check camera permission when returning from Settings.
     */
    DisposableEffect(
        lifecycleOwner,
        context
    ) {

        val observer =
            LifecycleEventObserver { _, event ->

                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {

                    hasCameraPermission.value =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                }
            }


        lifecycleOwner.lifecycle.addObserver(
            observer
        )


        onDispose {

            lifecycleOwner.lifecycle.removeObserver(
                observer
            )
        }
    }


    /* ---------------------------------------------------------------------- */
    /* Reset scanner                                                          */
    /* ---------------------------------------------------------------------- */

    fun resetScanner() {

        showResultSheet.value =
            false


        capturedBitmap.value =
            null


        geminiResult.value =
            null


        errorMessage.value =
            null


        isLoading.value =
            false
    }


    /* ---------------------------------------------------------------------- */
    /* Capture + analysis                                                     */
    /* ---------------------------------------------------------------------- */

    fun captureAndAnalyze() {

        if (isLoading.value) {
            return
        }


        val currentLocation =
            recyclingLocation.value


        /*
         * Location is required before scanning.
         */
        if (currentLocation == null) {

            showLocationSetup.value =
                true

            return
        }


        val currentImageCapture =
            imageCapture.value


        if (currentImageCapture == null) {

            errorMessage.value =
                "Camera is still starting. Please try again."

            return
        }


        isLoading.value =
            true


        errorMessage.value =
            null


        currentImageCapture.takePicture(

            cameraExecutor,

            object :
                ImageCapture.OnImageCapturedCallback() {


                override fun onCaptureSuccess(
                    image: ImageProxy
                ) {

                    try {

                        val rawBitmap =
                            image.toBitmap()


                        val rotatedBitmap =
                            rotateBitmap(
                                source =
                                    rawBitmap,

                                rotationDegrees =
                                    image.imageInfo.rotationDegrees
                            )


                        val preparedBitmap =
                            downscaleBitmap(
                                bitmap =
                                    rotatedBitmap
                            )


                        coroutineScope.launch {

                            capturedBitmap.value =
                                preparedBitmap


                            try {

                                val result =
                                    analyzeWithGemini(
                                        bitmap =
                                            preparedBitmap,

                                        location =
                                            currentLocation
                                    )


                                geminiResult.value =
                                    result


                                isLoading.value =
                                    false


                                showResultSheet.value =
                                    true


                            } catch (exception: Exception) {

                                isLoading.value =
                                    false


                                errorMessage.value =
                                    exception.message
                                        ?: "Could not analyze this item. Please try again."
                            }
                        }


                    } catch (_: Exception) {

                        coroutineScope.launch {

                            isLoading.value =
                                false


                            errorMessage.value =
                                "Could not process the captured photo."
                        }


                    } finally {

                        image.close()
                    }
                }


                override fun onError(
                    exception: ImageCaptureException
                ) {

                    coroutineScope.launch {

                        isLoading.value =
                            false


                        errorMessage.value =
                            exception.message
                                ?: "Photo capture failed. Please try again."
                    }
                }
            }
        )
    }


    /* ---------------------------------------------------------------------- */
    /* Main screen selection                                                  */
    /* ---------------------------------------------------------------------- */

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            Color.Black
    ) {

        when {

            /*
             * First launch / Change Location.
             */
            showLocationSetup.value -> {

                LocationSetupScreen(
                    currentLocation =
                        recyclingLocation.value,

                    onSave = { newLocation ->

                        saveRecyclingLocation(
                            context =
                                context,

                            location =
                                newLocation
                        )


                        recyclingLocation.value =
                            newLocation


                        showLocationSetup.value =
                            false
                    }
                )
            }


            /*
             * Normal scanner.
             */
            hasCameraPermission.value -> {

                ScannerScreen(
                    imageCaptureState =
                        imageCapture,

                    isLoading =
                        isLoading.value,

                    errorMessage =
                        errorMessage.value,

                    location =
                        recyclingLocation.value!!,

                    onChangeLocation = {

                        if (!isLoading.value) {

                            showLocationSetup.value =
                                true
                        }
                    },

                    onCapture = {

                        captureAndAnalyze()
                    },

                    onCameraError = { message ->

                        errorMessage.value =
                            message
                    }
                )
            }


            /*
             * Camera permission denied.
             */
            else -> {

                PermissionDeniedScreen(
                    permissionRequested =
                        permissionRequested.value,

                    onGrantPermission = {

                        val activity =
                            context as? ComponentActivity


                        val canRequestAgain =
                            activity == null ||
                                    ActivityCompat
                                        .shouldShowRequestPermissionRationale(
                                            activity,
                                            Manifest.permission.CAMERA
                                        )


                        if (
                            canRequestAgain ||
                            !permissionRequested.value
                        ) {

                            permissionRequested.value =
                                true


                            cameraPermissionLauncher.launch(
                                Manifest.permission.CAMERA
                            )

                        } else {

                            context.openAppSettings()
                        }
                    }
                )
            }
        }


        /* ------------------------------------------------------------------ */
        /* Result bottom sheet                                                */
        /* ------------------------------------------------------------------ */

        if (
            showResultSheet.value &&
            capturedBitmap.value != null &&
            geminiResult.value != null &&
            recyclingLocation.value != null
        ) {

            val sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded =
                        true
                )


            ModalBottomSheet(
                onDismissRequest = {

                    resetScanner()
                },

                sheetState =
                    sheetState,

                containerColor =
                    Color(0xFFF8FAF7),

                contentColor =
                    Color(0xFF172018)
            ) {

                ResultSheetContent(
                    bitmap =
                        capturedBitmap.value!!,

                    result =
                        geminiResult.value!!,

                    location =
                        recyclingLocation.value!!,

                    onScanAnother = {

                        resetScanner()
                    }
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Location setup                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun LocationSetupScreen(
    currentLocation: RecyclingLocation?,
    onSave: (RecyclingLocation) -> Unit
) {

    val province =
        remember(currentLocation) {

            mutableStateOf(
                currentLocation
                    ?.province
                    .orEmpty()
            )
        }


    val city =
        remember(currentLocation) {

            mutableStateOf(
                currentLocation
                    ?.city
                    .orEmpty()
            )
        }


    val postalCode =
        remember(currentLocation) {

            mutableStateOf(
                currentLocation
                    ?.postalCode
                    .orEmpty()
            )
        }


    val homeType =
        remember(currentLocation) {

            mutableStateOf(
                currentLocation
                    ?.homeType
                    .orEmpty()
            )
        }


    val provinceExpanded =
        remember {

            mutableStateOf(
                false
            )
        }


    val homeTypeExpanded =
        remember {

            mutableStateOf(
                false
            )
        }


    val validationError =
        remember {

            mutableStateOf<String?>(
                null
            )
        }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF101713)
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 26.dp,
                    vertical = 30.dp
                ),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {


        Box(
            modifier =
                Modifier
                    .size(
                        88.dp
                    )
                    .clip(
                        CircleShape
                    )
                    .background(
                        Color(0xFF22372A)
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    "♻",

                fontSize =
                    42.sp
            )
        }


        Spacer(
            modifier =
                Modifier.height(
                    22.dp
                )
        )


        Text(
            text =
                "Where are you recycling?",

            color =
                Color.White,

            fontSize =
                26.sp,

            fontWeight =
                FontWeight.Bold,

            textAlign =
                TextAlign.Center
        )


        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )


        Text(
            text =
                "Set your Canadian municipality so recycling and disposal advice can be tailored to your area.",

            color =
                Color.White.copy(
                    alpha = 0.72f
                ),

            fontSize =
                15.sp,

            lineHeight =
                21.sp,

            textAlign =
                TextAlign.Center
        )


        Spacer(
            modifier =
                Modifier.height(
                    30.dp
                )
        )


        /* ------------------------------------------------------------------ */
        /* Province                                                           */
        /* ------------------------------------------------------------------ */

        Text(
            text =
                "Province / Territory",

            color =
                Color.White,

            fontWeight =
                FontWeight.SemiBold,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )


        Box(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            OutlinedButton(
                onClick = {

                    provinceExpanded.value =
                        true
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            56.dp
                        ),

                shape =
                    RoundedCornerShape(
                        16.dp
                    )
            ) {

                Text(
                    text =
                        province.value.ifBlank {

                            "Select province or territory"
                        },

                    modifier =
                        Modifier.fillMaxWidth(),

                    textAlign =
                        TextAlign.Start
                )
            }


            DropdownMenu(
                expanded =
                    provinceExpanded.value,

                onDismissRequest = {

                    provinceExpanded.value =
                        false
                }
            ) {

                CANADIAN_PROVINCES.forEach {
                        provinceEntry ->


                    DropdownMenuItem(
                        text = {

                            Text(
                                text =
                                    "${provinceEntry.first} (${provinceEntry.second})"
                            )
                        },

                        onClick = {

                            province.value =
                                provinceEntry.first


                            provinceExpanded.value =
                                false


                            validationError.value =
                                null
                        }
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )


        /* ------------------------------------------------------------------ */
        /* Municipality                                                       */
        /* ------------------------------------------------------------------ */

        OutlinedTextField(
            value =
                city.value,

            onValueChange = {

                city.value =
                    it.take(
                        60
                    )


                validationError.value =
                    null
            },

            label = {

                Text(
                    text =
                        "City / Municipality"
                )
            },

            placeholder = {

                Text(
                    text =
                        "Toronto"
                )
            },

            singleLine =
                true,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )


        /* ------------------------------------------------------------------ */
        /* Postal code                                                        */
        /* ------------------------------------------------------------------ */

        OutlinedTextField(
            value =
                postalCode.value,

            onValueChange = { newValue ->

                postalCode.value =
                    newValue
                        .uppercase()
                        .filter {
                                character ->

                            character.isLetterOrDigit() ||
                                    character == ' '
                        }
                        .take(
                            7
                        )


                validationError.value =
                    null
            },

            label = {

                Text(
                    text =
                        "Postal Code"
                )
            },

            placeholder = {

                Text(
                    text =
                        "M5V 2T6"
                )
            },

            supportingText = {

                Text(
                    text =
                        "Used to improve local recycling guidance."
                )
            },

            singleLine =
                true,

            keyboardOptions =
                KeyboardOptions(
                    capitalization =
                        KeyboardCapitalization.Characters,

                    keyboardType =
                        KeyboardType.Text
                ),

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )


        /* ------------------------------------------------------------------ */
        /* Residence type                                                     */
        /* ------------------------------------------------------------------ */

        Text(
            text =
                "Residence Type",

            color =
                Color.White,

            fontWeight =
                FontWeight.SemiBold,

            modifier =
                Modifier.fillMaxWidth()
        )


        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )


        Box(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            OutlinedButton(
                onClick = {

                    homeTypeExpanded.value =
                        true
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            56.dp
                        ),

                shape =
                    RoundedCornerShape(
                        16.dp
                    )
            ) {

                Text(
                    text =
                        homeType.value.ifBlank {

                            "Select residence type"
                        },

                    modifier =
                        Modifier.fillMaxWidth(),

                    textAlign =
                        TextAlign.Start
                )
            }


            DropdownMenu(
                expanded =
                    homeTypeExpanded.value,

                onDismissRequest = {

                    homeTypeExpanded.value =
                        false
                }
            ) {

                HOME_TYPES.forEach {
                        type ->


                    DropdownMenuItem(
                        text = {

                            Text(
                                text =
                                    type
                            )
                        },

                        onClick = {

                            homeType.value =
                                type


                            homeTypeExpanded.value =
                                false


                            validationError.value =
                                null
                        }
                    )
                }
            }
        }


        validationError.value?.let {
                message ->


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            Text(
                text =
                    message,

                color =
                    Color(0xFFFFB4AB),

                fontSize =
                    14.sp,

                lineHeight =
                    19.sp,

                textAlign =
                    TextAlign.Center
            )
        }


        Spacer(
            modifier =
                Modifier.height(
                    28.dp
                )
        )


        /* ------------------------------------------------------------------ */
        /* Save                                                               */
        /* ------------------------------------------------------------------ */

        Button(
            onClick = {

                val normalizedCity =
                    city.value
                        .trim()


                val normalizedPostal =
                    normalizePostalCode(
                        postalCode.value
                    )


                when {

                    province.value.isBlank() -> {

                        validationError.value =
                            "Please select your province or territory."
                    }


                    normalizedCity.isBlank() -> {

                        validationError.value =
                            "Please enter your city or municipality."
                    }


                    !isValidCanadianPostalCode(
                        normalizedPostal
                    ) -> {

                        validationError.value =
                            "Please enter a valid Canadian postal code, for example M5V 2T6."
                    }


                    homeType.value.isBlank() -> {

                        validationError.value =
                            "Please select your residence type."
                    }


                    else -> {

                        val provinceCode =
                            CANADIAN_PROVINCES
                                .firstOrNull {

                                    it.first ==
                                            province.value
                                }
                                ?.second
                                .orEmpty()


                        val newLocation =
                            RecyclingLocation(
                                province =
                                    province.value,

                                provinceCode =
                                    provinceCode,

                                city =
                                    normalizedCity,

                                postalCode =
                                    normalizedPostal,

                                homeType =
                                    homeType.value
                            )


                        validationError.value =
                            null


                        onSave(
                            newLocation
                        )
                    }
                }
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        58.dp
                    ),

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        Color(0xFF80DC91),

                    contentColor =
                        Color(0xFF102114)
                ),

            shape =
                RoundedCornerShape(
                    18.dp
                )
        ) {

            Text(
                text =
                    if (
                        currentLocation == null
                    ) {

                        "Continue to Scanner"

                    } else {

                        "Save Location"
                    },

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )


        Text(
            text =
                "Your location profile is stored only on this device.",

            color =
                Color.White.copy(
                    alpha = 0.52f
                ),

            fontSize =
                12.sp,

            textAlign =
                TextAlign.Center
        )
    }
}


/* -------------------------------------------------------------------------- */
/* Scanner screen                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun ScannerScreen(
    imageCaptureState: MutableState<ImageCapture?>,
    isLoading: Boolean,
    errorMessage: String?,
    location: RecyclingLocation,
    onChangeLocation: () -> Unit,
    onCapture: () -> Unit,
    onCameraError: (String) -> Unit
) {

    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {


        CameraPreview(
            imageCaptureState =
                imageCaptureState,

            onCameraError =
                onCameraError
        )


        /*
         * Header overlay.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        180.dp
                    )
                    .background(
                        Color.Black.copy(
                            alpha = 0.30f
                        )
                    )
        )


        Column(
            modifier =
                Modifier
                    .align(
                        Alignment.TopCenter
                    )
                    .statusBarsPadding()
                    .padding(
                        top = 18.dp
                    ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            Text(
                text =
                    "♻  Local Eco-Scanner",

                color =
                    Color.White,

                fontSize =
                    21.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )


            Text(
                text =
                    "Center an item and take a photo",

                color =
                    Color.White.copy(
                        alpha = 0.82f
                    ),

                fontSize =
                    14.sp
            )


            Spacer(
                modifier =
                    Modifier.height(
                        9.dp
                    )
            )


            Text(
                text =
                    "📍 ${location.city}, ${location.provinceCode}  •  Change location",

                color =
                    Color(0xFFB9F5C3),

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.SemiBold,

                modifier =
                    Modifier.clickable(
                        enabled =
                            !isLoading
                    ) {

                        onChangeLocation()
                    }
            )
        }


        /*
         * Scanner frame.
         */
        Box(
            modifier =
                Modifier
                    .align(
                        Alignment.Center
                    )
                    .fillMaxWidth(
                        0.78f
                    )
                    .aspectRatio(
                        0.78f
                    )
                    .border(
                        width =
                            2.dp,

                        color =
                            Color.White.copy(
                                alpha = 0.8f
                            ),

                        shape =
                            RoundedCornerShape(
                                28.dp
                            )
                    )
        )


        /*
         * Capture controls.
         */
        Column(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .navigationBarsPadding()
                    .padding(
                        bottom =
                            26.dp
                    ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            Text(
                text =
                    if (isLoading) {

                        "Analyzing for ${location.city}…"

                    } else {

                        "Tap to scan"
                    },

                color =
                    Color.White,

                fontSize =
                    14.sp,

                fontWeight =
                    FontWeight.Medium
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            CaptureButton(
                enabled =
                    !isLoading,

                onClick =
                    onCapture
            )
        }


        errorMessage?.let {
                message ->


            ErrorBanner(
                message =
                    message,

                modifier =
                    Modifier
                        .align(
                            Alignment.TopCenter
                        )
                        .statusBarsPadding()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 115.dp
                        )
            )
        }


        if (isLoading) {

            LoadingOverlay(
                location =
                    location
            )
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

    val context =
        LocalContext.current


    val lifecycleOwner =
        LocalLifecycleOwner.current


    val previewView =
        remember {

            PreviewView(context).apply {

                scaleType =
                    PreviewView.ScaleType.FILL_CENTER


                implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE
            }
        }


    DisposableEffect(
        lifecycleOwner,
        previewView
    ) {

        var cameraProvider:
                ProcessCameraProvider? =
            null


        var disposed =
            false


        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(
                context
            )


        val mainExecutor =
            ContextCompat.getMainExecutor(
                context
            )


        cameraProviderFuture.addListener({

            if (disposed) {

                return@addListener
            }


            try {

                val provider =
                    cameraProviderFuture.get()


                cameraProvider =
                    provider


                val preview =
                    Preview.Builder()
                        .build()
                        .also {

                            it.setSurfaceProvider(
                                previewView.surfaceProvider
                            )
                        }


                val captureUseCase =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture
                                .CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build()


                provider.unbindAll()


                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    captureUseCase
                )


                imageCaptureState.value =
                    captureUseCase


            } catch (exception: Exception) {

                imageCaptureState.value =
                    null


                onCameraError(
                    exception.message
                        ?: "Unable to start the camera."
                )
            }

        }, mainExecutor)


        onDispose {

            disposed =
                true


            imageCaptureState.value =
                null


            cameraProvider
                ?.unbindAll()
        }
    }


    AndroidView(
        factory = {

            previewView
        },

        modifier =
            Modifier.fillMaxSize()
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
        modifier =
            Modifier
                .size(
                    94.dp
                )
                .semantics {

                    contentDescription =
                        "Capture item photo"
                }
                .clip(
                    CircleShape
                )
                .background(
                    Color.Black.copy(
                        alpha = 0.28f
                    )
                )
                .border(
                    width =
                        4.dp,

                    color =
                        if (enabled) {

                            Color.White

                        } else {

                            Color.White.copy(
                                alpha = 0.45f
                            )
                        },

                    shape =
                        CircleShape
                )
                .clickable(
                    enabled =
                        enabled,

                    onClick =
                        onClick
                ),

        contentAlignment =
            Alignment.Center
    ) {


        Box(
            modifier =
                Modifier
                    .size(
                        70.dp
                    )
                    .clip(
                        CircleShape
                    )
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
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF101713)
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    32.dp
                ),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {


        Box(
            modifier =
                Modifier
                    .size(
                        92.dp
                    )
                    .clip(
                        CircleShape
                    )
                    .background(
                        Color(0xFF22372A)
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    "♻",

                fontSize =
                    46.sp
            )
        }


        Spacer(
            modifier =
                Modifier.height(
                    28.dp
                )
        )


        Text(
            text =
                "Camera access required",

            color =
                Color.White,

            fontSize =
                25.sp,

            fontWeight =
                FontWeight.Bold,

            textAlign =
                TextAlign.Center
        )


        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )


        Text(
            text =
                if (permissionRequested) {

                    "Eco-Scanner needs camera access to photograph items for recycling analysis."

                } else {

                    "Allow camera access to start scanning recyclable items."
                },

            color =
                Color.White.copy(
                    alpha = 0.72f
                ),

            fontSize =
                16.sp,

            lineHeight =
                23.sp,

            textAlign =
                TextAlign.Center
        )


        Spacer(
            modifier =
                Modifier.height(
                    30.dp
                )
        )


        Button(
            onClick =
                onGrantPermission,

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        Color(0xFF80DC91),

                    contentColor =
                        Color(0xFF102114)
                ),

            shape =
                RoundedCornerShape(
                    18.dp
                ),

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        56.dp
                    )
        ) {

            Text(
                text =
                    "Grant Permission",

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    16.sp
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Loading                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun LoadingOverlay(
    location: RecyclingLocation
) {

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.48f
                    )
                ),

        contentAlignment =
            Alignment.Center
    ) {


        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(0xEE162019)
                ),

            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(
                        horizontal =
                            32.dp,

                        vertical =
                            28.dp
                    ),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {


                CircularProgressIndicator(
                    color =
                        Color(0xFF81DA91),

                    trackColor =
                        Color.White.copy(
                            alpha =
                                0.15f
                        )
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            18.dp
                        )
                )


                Text(
                    text =
                        "Analyzing your item",

                    color =
                        Color.White,

                    fontWeight =
                        FontWeight.SemiBold,

                    fontSize =
                        17.sp
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )


                Text(
                    text =
                        "Checking guidance for ${location.city}, ${location.provinceCode}…",

                    color =
                        Color.White.copy(
                            alpha =
                                0.68f
                        ),

                    fontSize =
                        13.sp,

                    textAlign =
                        TextAlign.Center
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
        modifier =
            modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xEE3D211E)
            ),

        shape =
            RoundedCornerShape(
                18.dp
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    16.dp
                )
        ) {

            Text(
                text =
                    "Something went wrong",

                color =
                    Color.White,

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    15.sp
            )


            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )


            Text(
                text =
                    message,

                color =
                    Color.White.copy(
                        alpha = 0.82f
                    ),

                fontSize =
                    13.sp,

                lineHeight =
                    18.sp
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Results                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ResultSheetContent(
    bitmap: Bitmap,
    result: String,
    location: RecyclingLocation,
    onScanAnother: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    start =
                        22.dp,

                    end =
                        22.dp,

                    bottom =
                        28.dp
                )
    ) {


        Text(
            text =
                "Eco Analysis",

            color =
                Color(0xFF172018),

            fontWeight =
                FontWeight.Bold,

            fontSize =
                26.sp
        )


        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )


        Text(
            text =
                "📍 ${location.city}, ${location.provinceCode} ${location.postalCode}",

            color =
                Color(0xFF17642F),

            fontSize =
                14.sp,

            fontWeight =
                FontWeight.SemiBold
        )


        Spacer(
            modifier =
                Modifier.height(
                    4.dp
                )
        )


        Text(
            text =
                "${location.homeType} • Local recycling guidance",

            color =
                Color(0xFF5B675C),

            fontSize =
                13.sp
        )


        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )


        Image(
            bitmap =
                bitmap.asImageBitmap(),

            contentDescription =
                "Scanned item",

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        230.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            24.dp
                        )
                    ),

            contentScale =
                ContentScale.Crop
        )


        Spacer(
            modifier =
                Modifier.height(
                    24.dp
                )
        )


        EcoResultText(
            result =
                result
        )


        Spacer(
            modifier =
                Modifier.height(
                    22.dp
                )
        )


        /*
         * Important:
         *
         * Location-aware prompting is NOT yet the same as official
         * municipality-source grounding.
         */
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(0xFFEAF4EB)
                ),

            shape =
                RoundedCornerShape(
                    18.dp
                )
        ) {

            Text(
                text =
                    "Guidance is tailored to ${location.city}, but municipal programs can change. " +
                            "If the result says to verify a local rule, confirm it with your municipality.",

                color =
                    Color(0xFF405044),

                fontSize =
                    13.sp,

                lineHeight =
                    18.sp,

                modifier =
                    Modifier.padding(
                        16.dp
                    )
            )
        }


        Spacer(
            modifier =
                Modifier.height(
                    24.dp
                )
        )


        Button(
            onClick =
                onScanAnother,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        56.dp
                    ),

            shape =
                RoundedCornerShape(
                    18.dp
                ),

            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        Color(0xFF1E6C37),

                    contentColor =
                        Color.White
                )
        ) {

            Text(
                text =
                    "Scan Another Item",

                fontSize =
                    16.sp,

                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Gemini response formatting                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun EcoResultText(
    result: String
) {

    val lines =
        result.lines()


    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {

        lines.forEach {
                rawLine ->


            val line =
                rawLine
                    .trim()
                    .removePrefix(
                        "###"
                    )
                    .removePrefix(
                        "##"
                    )
                    .removePrefix(
                        "#"
                    )
                    .removePrefix(
                        "**"
                    )
                    .removeSuffix(
                        "**"
                    )
                    .trim()


            if (line.isEmpty()) {

                Spacer(
                    modifier =
                        Modifier.height(
                            3.dp
                        )
                )

            } else {

                val isSectionHeader =
                    line.startsWith(
                        "1."
                    ) ||
                            line.startsWith(
                                "2."
                            ) ||
                            line.startsWith(
                                "3."
                            ) ||
                            line.startsWith(
                                "Is it Recyclable?",
                                ignoreCase =
                                    true
                            ) ||
                            line.startsWith(
                                "Proper Disposal Steps",
                                ignoreCase =
                                    true
                            ) ||
                            line.startsWith(
                                "Creative Upcycling Ideas",
                                ignoreCase =
                                    true
                            )


                Text(
                    text =
                        line,

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

                    lineHeight =
                        22.sp
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* Bitmap helpers                                                             */
/* -------------------------------------------------------------------------- */

private fun rotateBitmap(
    source: Bitmap,
    rotationDegrees: Int
): Bitmap {

    if (
        rotationDegrees == 0
    ) {

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
 * Downscales large camera photos before Gemini upload.
 */
private fun downscaleBitmap(
    bitmap: Bitmap
): Bitmap {

    val largestDimension =
        maxOf(
            bitmap.width,
            bitmap.height
        )


    if (
        largestDimension <=
        MAX_IMAGE_DIMENSION
    ) {

        return bitmap
    }


    val scale =
        MAX_IMAGE_DIMENSION.toFloat() /
                largestDimension.toFloat()


    val newWidth =
        (bitmap.width * scale)
            .roundToInt()
            .coerceAtLeast(
                1
            )


    val newHeight =
        (bitmap.height * scale)
            .roundToInt()
            .coerceAtLeast(
                1
            )


    return Bitmap.createScaledBitmap(
        bitmap,
        newWidth,
        newHeight,
        true
    )
}


/* -------------------------------------------------------------------------- */
/* Android Settings helper                                                    */
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


    startActivity(
        intent
    )
}


/* -------------------------------------------------------------------------- */
/* Theme                                                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun EcoScannerTheme(
    content: @Composable () -> Unit
) {

    val colors =
        darkColorScheme(
            primary =
                Color(0xFF80DC91),

            onPrimary =
                Color(0xFF102114),

            background =
                Color(0xFF101713),

            surface =
                Color(0xFF172019),

            onSurface =
                Color.White
        )


    MaterialTheme(
        colorScheme =
            colors,

        content =
            content
    )
}