

package video.api.flutter.livestream

import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSettings
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import kotlinx.coroutines.runBlocking
import video.api.flutter.livestream.generated.CameraSettingsHostApi
import video.api.flutter.livestream.manager.InstanceManager


class CameraSettingsHostApiImpl(
    private val instanceManager: InstanceManager
) : CameraSettingsHostApi {

    private val cameraSource: ICameraSource?
        get() = instanceManager.getInstance().videoInput?.sourceFlow?.value as? ICameraSource

    private val zoomSettings: CameraSettings.Zoom?
        get() = cameraSource?.settings?.zoom

    override fun setZoomRatio(zoomRatio: Double) {
        val zoom = zoomSettings ?: run {
            // You can call onError if you have access, otherwise just return
            return
        }

        runBlocking {
            try {
                zoom.setZoomRatio(zoomRatio.toFloat())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getZoomRatio(): Double {
        return try {
            runBlocking {
                zoomSettings?.getZoomRatio()?.toDouble() ?: 1.0
            }
        } catch (e: Exception) {
            1.0
        }
    }
}

/*class CameraSettingsHostApiImpl(
    private val instanceManager: InstanceManager
) : CameraSettingsHostApi {

    private val settings: CameraSettings
        get() = instanceManager.getInstance().videoInput?.settings?.camera
            ?: throw IllegalStateException("Camera settings not available")

    override fun setZoomRatio(zoomRatio: Double) {
        runBlocking {
            try {
                settings.zoom.setZoomRatio(zoomRatio.toFloat())
            } catch (e: Exception) {
                // You can log or handle error if needed
                e.printStackTrace()
            }
        }
    }

    override fun getZoomRatio(): Double {
        return try {
            settings.zoom.zoomRatio.toDouble()
        } catch (e: Exception) {
            1.0 // default value
        }
    }
}*/
