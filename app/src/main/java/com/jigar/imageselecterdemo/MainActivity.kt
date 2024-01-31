package com.jigar.imageselecterdemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jigar.imageselecterdemo.ui.theme.ImageSelecterDemoTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
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
                    HomeScreen()
                }
            }
        }
    }

}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen() {
    val cameraPermissionState =
        rememberPermissionState(permission = android.Manifest.permission.CAMERA)
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageUri2 by remember { mutableStateOf<Uri?>(null) }
    var imagePath by remember { mutableStateOf("") }
    var imageFile by remember { mutableStateOf<File?>(null) }
    var openDialogClick by remember { mutableStateOf(false) }

    val startForResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                imageUri = uri
                val savedFile = saveImageToExternalStorage(context, uri)
                imagePath = savedFile.absolutePath
            }
        }
    }

    val launcherForCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess) {
            imageUri2?.let  { uri ->
                imageUri = uri
//                val savedFile = saveImageToExternalStorage(context, uri)
                imagePath = imageFile!!.absolutePath
            }
        }
    }


    if (openDialogClick) {
        ChooseImageSourceDialog(
            showDialog = openDialogClick,
            setShowDialog = { openDialogClick = it },
            onCameraClick = {
                // Implement Camera Click Logic
                openDialogClick = false
                imageFile = createImageFile(context)
                imageUri2 = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile!!)
                launcherForCamera.launch(imageUri2)
            },
            onGalleryClick = {
                // Implement Gallery Click Logic
                openDialogClick = false
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startForResult.launch(intent)
            }
        )
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (cameraPermissionState.status.isGranted) {
                Column {
                    Button(onClick = {
                        openDialogClick = true
                    }) {
                        Text(text = "Select Image")
                    }

                    Text(text = imagePath ?: "Path of the saved image")

                    imageUri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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

@Composable
fun ChooseImageSourceDialog(
    showDialog: Boolean,
    setShowDialog: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { setShowDialog(false) },
            title = { Text("Select Image Source") },
            text = { Text("Would you like to take a new photo or choose one from the gallery?") },
            confirmButton = {
                Button(
                    onClick = {
                        setShowDialog(false)
                        onCameraClick()
                    }
                ) {
                    Text("Camera")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        setShowDialog(false)
                        onGalleryClick()
                    }
                ) {
                    Text("Gallery")
                }
            }
        )
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
    val picturesDirectory =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val savedFile = File(picturesDirectory, filename)

    inputStream.use { input ->
        FileOutputStream(savedFile).use { output ->
            input?.copyTo(output)
        }
    }

    return savedFile
}
fun createImageFile(context: Context): File {
    // This function creates an empty file to store the captured image
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)!!
    return File.createTempFile(
        "AJPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}
fun checkGalleryForImages(context: Context): Boolean {
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    var cursor: Cursor? = null

    try {
        cursor = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            // Images are present
            return true
        }
    } catch (e: Exception) {
        Log.e("ImagePicker", "Error checking gallery images", e)
    } finally {
        cursor?.close()
    }

    // No images found or error occurred
    return false
}

