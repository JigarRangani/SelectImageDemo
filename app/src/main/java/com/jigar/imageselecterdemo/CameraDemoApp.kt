package com.jigar.imageselecterdemo

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.*
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (cameraPermissionState.status.isGranted) {
                val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                val previewView = PreviewView(context).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
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
                    takePicture2(imageCapture, context) { uri ->
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
fun takePicture2(imageCapture: ImageCapture, context: Context, callback: (Uri) -> Unit) {
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
