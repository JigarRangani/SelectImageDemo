package com.jigar.imageselecterdemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.rememberImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jigar.imageselecterdemo.ui.theme.ImageSelecterDemoTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize ActivityResultLaunchers

        setContent {
            ImageSelecterDemoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
//                    CameraDemoApp()
                    ImagePickerDemo()
                }
            }
        }
    }

}



@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraDemoApp() {
    val cameraPermissionState =
        rememberPermissionState(permission = android.Manifest.permission.CAMERA)
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imagePath by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current

    MaterialTheme {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            if (cameraPermissionState.status.isGranted) {
                val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                val previewView = androidx.camera.view.PreviewView(context).apply {
                    this.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }

                val imageCapture = remember { ImageCapture.Builder().build() }

                LaunchedEffect(cameraProviderFuture) {
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture // Bind imageCapture use case here
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Failed to bind camera use case",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                AndroidView({ previewView }, modifier = Modifier.weight(1f))

                Button(onClick = {
                    takePicture(imageCapture, context) { uri ->
                        imageUri = uri
                        imagePath = uri.path ?: ""
                    }
                }) {
                    Text("Take Picture")
                }

                Text("Image Path: $imagePath")

                imageUri?.let {
                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
                }
            } else {
                Column {
                    Text("Camera permission is required to continue")
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Request permission")
                    }
                }
            }
        }
    }
}

//Log.e("Error saving image",exc.stackTraceToString())
fun takePicture(imageCapture: ImageCapture, context: Context, callback: (Uri) -> Unit) {
    val filename = "JPEG_${
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(System.currentTimeMillis())
    }.jpg"
    val photoFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        filename
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                callback(savedUri)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("Error saving image", exc.stackTraceToString())
                Toast.makeText(context, "Error saving image: ${exc.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })
}

@Composable
fun ImagePickerDemo() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var savedImagePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val startForResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                imageUri = uri
                val savedFile = saveImageToExternalStorage(context, uri)
                savedImagePath = savedFile.absolutePath
            }
        }
    }

    Column {
        Button(onClick = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startForResult.launch(intent)
        }) {
            Text(text = "Select Image")
        }

        Text(text = savedImagePath ?: "Path of the saved image")

        imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Selected Image",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun saveImageToExternalStorage(context: Context, uri: Uri): File {
    val filename = "JPEG_${
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(System.currentTimeMillis())
    }.jpg"
    val inputStream = context.contentResolver.openInputStream(uri)
    val picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val savedFile = File(picturesDirectory, filename)

    inputStream.use { input ->
        FileOutputStream(savedFile).use { output ->
            input?.copyTo(output)
        }
    }

    return savedFile
}

