package com.mobiledrivetech.recorder

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.common.util.concurrent.ListenableFuture
import com.mobiledrivetech.recorder.core.ui.com.overlaycamera.OverlayCameraView
import com.mobiledrivetech.recorder.ui.theme.VideoCaptureTheme
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.P)
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoCaptureTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val permissionState =
                        rememberPermissionState(permission = android.Manifest.permission.CAMERA)

                    CameraView(permissionState)

                    LaunchedEffect(key1 = LocalContext.current) {
                        permissionState.launchPermissionRequest()
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraView(permissionState: PermissionState) {
    PermissionRequired(
        permissionState = permissionState,
        permissionNotGrantedContent = { /* ... */ },
        permissionNotAvailableContent = { /* ... */ }
    ) {
        val cameraSelector: MutableState<CameraSelector> = remember {
            mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
        }

        val cameraProviderFuture : MutableState<ListenableFuture<ProcessCameraProvider>?> = remember {
            mutableStateOf(null)
        }

        val cameraProvider:MutableState<ProcessCameraProvider?> = remember {
            mutableStateOf(null)
        }

        val mContext:Context = LocalContext.current

        val recorder:MutableState<VideoCapture<Recorder>?> = remember {
            mutableStateOf(null)
        }

        val lifecycleOwner = LocalLifecycleOwner.current

        val isCameraReady : MutableState<Boolean> = remember {
            mutableStateOf(false)
        }

        LaunchedEffect(key1 = LocalContext.current){
            cameraProviderFuture.value = ProcessCameraProvider.getInstance(mContext)

            cameraProviderFuture.value!!.addListener(Runnable {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                cameraProvider.value = cameraProviderFuture.value!!.get()

                isCameraReady.value = true
            }, ContextCompat.getMainExecutor(mContext))
        }

        if(isCameraReady.value){
            AndroidView(factory = {
                OverlayCameraView(it).apply {
                    if(cameraProvider.value != null){
                        createVideoCaptureUseCase(lifecycleOwner, cameraSelector.value, this.provider, mContext.mainExecutor, cameraProvider.value!!)
                    }
                }
            })
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
fun createVideoCaptureUseCase(
    lifecycleOwner: LifecycleOwner,
    cameraSelector: CameraSelector,
    previewProvider: Preview.SurfaceProvider,
    mainExecutor:Executor,
    cameraProvider:ProcessCameraProvider
): VideoCapture<Recorder> {
    Log.d("Trace", "createVideoCaptureUseCase")
    val preview = Preview.Builder()
        .build()
        .apply { setSurfaceProvider(previewProvider) }

    val qualitySelector = QualitySelector.from(
        Quality.FHD,
        FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
    )
    val recorder = Recorder.Builder()
        .setExecutor(mainExecutor)
        .setQualitySelector(qualitySelector)
        .build()
    val videoCapture = VideoCapture.withOutput(recorder)

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        videoCapture
    )

    return videoCapture
}

