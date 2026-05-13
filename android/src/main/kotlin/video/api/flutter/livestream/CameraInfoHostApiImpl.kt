package video.api.flutter.livestream

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Range
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings.Zoom.Companion.DEFAULT_ZOOM_RATIO
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.getCameraCharacteristics

import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isBackCamera
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isFrontCamera
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.extensions.isExternalCamera


import video.api.flutter.livestream.generated.CameraInfoHostApi
import video.api.flutter.livestream.generated.NativeCameraLensDirection


class CameraInfoHostApiImpl(
    var context: Context
) : CameraInfoHostApi {

    private val cameraManager: CameraManager
        get() = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    override fun getSensorRotationDegrees(cameraId: String): Long {
        val characteristics = context.getCameraCharacteristics(cameraId)
        return (characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0).toLong()
    }

    override fun getLensDirection(cameraId: String): NativeCameraLensDirection {
        return when {
            cameraManager.isFrontCamera(cameraId) -> NativeCameraLensDirection.FRONT
            cameraManager.isBackCamera(cameraId) -> NativeCameraLensDirection.BACK
            cameraManager.isExternalCamera(cameraId) -> NativeCameraLensDirection.OTHER
            else -> throw IllegalArgumentException("Invalid camera position for camera $cameraId")
        }
    }

    private fun getZoomRange(cameraId: String): Range<Float> {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        // Try to get zoom ratio range (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (zoomRange != null) {
                return zoomRange
            }
        }

        // Fallback: Use digital zoom (older devices)
        val maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 1.0f

        return Range(1.0f, maxDigitalZoom)
    }

    override fun getMinZoomRatio(cameraId: String): Double {
        return getZoomRange(cameraId).lower.toDouble()
    }

    override fun getMaxZoomRatio(cameraId: String): Double {
        return getZoomRange(cameraId).upper.toDouble()
    }
}

