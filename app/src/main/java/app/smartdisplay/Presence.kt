package app.smartdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------------------
// Anwesenheit: Die Frontkamera liefert winzige Graustufenbilder (ca. 160 x 120).
// Verglichen wird nur ein grobes Raster, nichts wird gespeichert oder gesendet.
// ---------------------------------------------------------------------------

private const val GRID_W = 32
private const val GRID_H = 24

/** Gibt bei jeder erkannten Bewegung vor der Frontkamera ein Signal aus. */
@SuppressLint("MissingPermission")
fun motionEvents(context: Context): Flow<Unit> = callbackFlow {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = try {
        manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        }
    } catch (e: Exception) {
        null
    }
    val size = id?.let {
        manager.getCameraCharacteristics(it)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.filter { s -> s.width >= 160 && s.height >= 120 }
            ?.minByOrNull { s -> s.width * s.height }
    }
    if (id == null || size == null) {
        close()
        awaitClose { }
        return@callbackFlow
    }

    val thread = HandlerThread("presence").also { it.start() }
    val handler = Handler(thread.looper)
    val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)

    var previous: IntArray? = null
    var previousMean = 0
    var lastProcessed = 0L

    reader.setOnImageAvailableListener({ r ->
        val image = r.acquireLatestImage()
        if (image != null) {
            try {
                val t = SystemClock.elapsedRealtime()
                if (t - lastProcessed >= 400L) {
                    lastProcessed = t
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val rowStride = plane.rowStride
                    val pixelStride = plane.pixelStride
                    val grid = IntArray(GRID_W * GRID_H)
                    var sum = 0
                    for (gy in 0 until GRID_H) {
                        val y = gy * image.height / GRID_H + image.height / (2 * GRID_H)
                        for (gx in 0 until GRID_W) {
                            val x = gx * image.width / GRID_W + image.width / (2 * GRID_W)
                            val v = buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
                            grid[gy * GRID_W + gx] = v
                            sum += v
                        }
                    }
                    val mean = sum / grid.size
                    val prev = previous
                    if (mean < 6) {
                        // Zu dunkel fuer eine verlaessliche Erkennung.
                        previous = null
                    } else {
                        if (prev != null) {
                            var changed = 0
                            for (i in grid.indices) {
                                // Globale Helligkeitsaenderungen (Licht an/aus) werden herausgerechnet.
                                val d = (grid[i] - mean) - (prev[i] - previousMean)
                                if (abs(d) > 20) changed++
                            }
                            val share = changed.toFloat() / grid.size
                            if (share in 0.03f..0.7f) trySend(Unit)
                        }
                        previous = grid
                        previousMean = mean
                    }
                }
            } finally {
                image.close()
            }
        }
    }, handler)

    var camera: CameraDevice? = null
    var session: CameraCaptureSession? = null

    try {
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                try {
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                    }.build()
                    @Suppress("DEPRECATION")
                    device.createCaptureSession(
                        listOf(reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                try {
                                    s.setRepeatingRequest(request, null, handler)
                                } catch (e: Exception) {
                                    this@callbackFlow.close()
                                }
                            }

                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                this@callbackFlow.close()
                            }
                        },
                        handler,
                    )
                } catch (e: Exception) {
                    this@callbackFlow.close()
                }
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
                this@callbackFlow.close()
            }

            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                this@callbackFlow.close()
            }
        }, handler)
    } catch (e: Exception) {
        close()
    }

    awaitClose {
        try { session?.close() } catch (e: Exception) { }
        try { camera?.close() } catch (e: Exception) { }
        try { reader.close() } catch (e: Exception) { }
        thread.quitSafely()
    }
}

// ---------------------------------------------------------------------------
// Winken: Die Hand ueber dem Naehe-Sensor (oben am Hoerer) kurz vorbeibewegen.
// ---------------------------------------------------------------------------

/** true = etwas ist direkt am Sensor, false = frei. Ohne Sensor endet der Fluss. */
fun proximityNear(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    if (sensor == null) {
        close()
        awaitClose { }
        return@callbackFlow
    }
    val threshold = min(sensor.maximumRange, 5f)
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(event.values[0] < threshold)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { manager.unregisterListener(listener) }
}.distinctUntilChanged()
