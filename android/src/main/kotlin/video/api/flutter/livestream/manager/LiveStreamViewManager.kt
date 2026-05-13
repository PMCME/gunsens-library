package video.api.flutter.livestream.manager

import android.Manifest
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresPermission
import io.flutter.view.TextureRegistry
import io.github.thibaultbee.streampack.core.elements.encoders.AudioCodecConfig
import io.github.thibaultbee.streampack.core.elements.encoders.VideoCodecConfig
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.interfaces.open
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import io.github.thibaultbee.streampack.core.interfaces.startPreview
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.interfaces.stopPreview
import io.github.thibaultbee.streampack.core.logger.Logger
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LiveStreamViewManager(
    private val streamer: SingleStreamer,
    textureRegistry: TextureRegistry,
    private val permissionsManager: PermissionsManager,
    private val onConnectionSucceeded: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onConnectionFailed: (String) -> Unit,
    private val onGenericError: (Exception) -> Unit,
    private val onVideoSizeChanged: (Size) -> Unit,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val flutterTexture = textureRegistry.createSurfaceTexture()
    val textureId: Long
        get() = flutterTexture.id()

    private var _isPreviewing = false

    val isStreaming: Boolean
        get() = streamer.isStreamingFlow.value

    private var _videoConfig: VideoCodecConfig? = null
    val videoConfig: VideoCodecConfig
        get() = _videoConfig!!

    fun setVideoConfig(
        videoConfig: VideoCodecConfig,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isStreaming) {
            throw UnsupportedOperationException("You have to stop streaming first")
        }

        onVideoSizeChanged(videoConfig.resolution)

        val wasPreviewing = _isPreviewing
        if (wasPreviewing) {
            stopPreview()
        }
        scope.launch {
            streamer.setVideoConfig(videoConfig)
        }

        _videoConfig = videoConfig
        if (wasPreviewing) {
            startPreview(onSuccess, onError)
        } else {
            onSuccess()
        }
    }

    private var _audioConfig: AudioCodecConfig? = null
    val audioConfig: AudioCodecConfig
        get() = _audioConfig!!

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun setAudioConfig(
        audioConfig: AudioCodecConfig,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isStreaming) {
            throw UnsupportedOperationException("You have to stop streaming first")
        }

        permissionsManager.requestPermission(
            Manifest.permission.RECORD_AUDIO,
            onGranted = {
                scope.launch {
                    try {
                        streamer.setAudioConfig(audioConfig)
                        _audioConfig = audioConfig
                        withContext(Dispatchers.Main) {
                            onSuccess()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError(e)
                        }
                    }
                }
            },
            onShowPermissionRationale = { _ ->
                onError(SecurityException("Missing permission Manifest.permission.RECORD_AUDIO"))
            },
            onDenied = {
                onError(SecurityException("Missing permission Manifest.permission.RECORD_AUDIO"))
            })
    }

    var isMuted: Boolean
        get() = streamer.audioInput?.isMuted == true
        set(value) {
            streamer.audioInput?.isMuted = value
        }

    val camera: String
        get() {
            val videoSource = streamer.videoInput?.sourceFlow?.value
            return (videoSource as? ICameraSource)?.cameraId.orEmpty()
        }


    @RequiresPermission(Manifest.permission.CAMERA)
    fun setCamera(
        camera: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        permissionsManager.requestPermission(
            Manifest.permission.CAMERA,
            onGranted = {
                scope.launch {
                    try {
                        streamer.setCameraId(camera)
                        if (_isPreviewing) {
                            streamer.stopPreview()

                            streamer.startPreview(getSurface(videoConfig.resolution))
                        }

                        withContext(Dispatchers.Main) {
                            onSuccess()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError(e)
                        }
                    }
                }
            },
            onShowPermissionRationale = { _ ->
                onError(
                    SecurityException(
                        "Missing permission Manifest.permission.CAMERA"
                    )
                )
            },
            onDenied = {
                onError(
                    SecurityException(
                        "Missing permission Manifest.permission.CAMERA"
                    )
                )
            }
        )
    }


    init {

    }

    fun dispose() {

        scope.launch {
            try {
                if (streamer.isStreamingFlow.value) {
                    streamer.stopStream()
                }

            } catch (e: Exception) {
                Log.e("LiveStreamViewManager stopStream", "${e.message}")
            }
            try {

                if (streamer.isOpenFlow.value) {
                    streamer.close()
                }
            } catch (e: Exception) {
                Log.e("LiveStreamViewManager close", "${e.message}")
            }
            try {
                streamer.stopPreview()
            } catch (e: Exception) {
                Log.e("LiveStreamViewManager stopPreview", "${e.message}")
            }
            try {
                streamer.release()
            } catch (e: Exception) {
                Log.e("LiveStreamViewManager release", "${e.message}")
            }

            withContext(Dispatchers.Main) {
                try {
                    flutterTexture.release()
                } catch (e: Exception) {
                    Log.e("LiveStreamViewManager release", "${e.message}")
                }
                scope.cancel()
            }
        }
    }


    fun startStream(url: String) {
        scope.launch {
            try {
                streamer.open(url)
                Logger.e("startStream", "start stream: $url")
                streamer.startStream()
            } catch (e: Exception) {
                streamer.close()
                withContext(Dispatchers.Main) {
                    Logger.e("startStream", "start stream: ${e.message}")
                    onDisconnected()
                    onError(e)
                }
            }
        }
    }

     fun stopStream(callback: (Result<Unit>) -> Unit) {
         scope.launch {
            try {
                if (streamer.isStreamingFlow.value) {
                    streamer.stopStream()
                }
            } catch (_: Exception) {
            }

            try {

                if (streamer.isOpenFlow.value) {
                    streamer.close()
                }
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
                Log.e("LiveStreamViewManager", "${e.message}")
            }
         }

    }


    fun startPreview(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        permissionsManager.requestPermission(
            Manifest.permission.CAMERA,
            onGranted = {
                if (_videoConfig == null) {
                    onError(IllegalStateException("Video has not been configured!"))
                } else {
                    scope.launch {
                        try {
                            if(!_isPreviewing){
                                streamer.startPreview(getSurface(videoConfig.resolution))
                            }
                            _isPreviewing = true
                            withContext(Dispatchers.Main) {
                                onSuccess()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                onError(e)
                            }
                        }
                    }
                }
            },
            onShowPermissionRationale = { _ ->
                /**
                 * Require an AppCompat theme to use MaterialAlertDialogBuilder
                 *
                 * context.showDialog(
                R.string.permission_required,
                R.string.camera_permission_required_message,
                android.R.string.ok,
                onPositiveButtonClick = { onRequiredPermissionLastTime() }
                )*/
                onError(SecurityException("Missing permission Manifest.permission.CAMERA"))
            },
            onDenied = {
                onError(SecurityException("Missing permission Manifest.permission.CAMERA"))
            })
    }

    fun stopPreview() {
        scope.launch {
            streamer.stopPreview()
        }

        _isPreviewing = false
    }

    private fun getSurface(resolution: Size): Surface {
        val surfaceTexture = flutterTexture.surfaceTexture().apply {
            setDefaultBufferSize(
                resolution.width,
                resolution.height
            )
        }
        return Surface(surfaceTexture)
    }


    fun onSuccess() {
        onConnectionSucceeded()
    }

    fun onLost(message: String) {
        onDisconnected()
    }

    fun onFailed(message: String) {
        onConnectionFailed(message)
    }

    fun onError(error: Throwable) {
        onGenericError(error as? Exception ?: RuntimeException(error))
    }
}