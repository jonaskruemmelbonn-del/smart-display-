package app.smartdisplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private var cameraGranted by mutableStateOf(false)
    private var resumeTick by mutableIntStateOf(0)

    private val cameraRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val fontFamily = loadFontFamily(this)
        val lightFlow = lightLevel(this)
        val proximityFlow = proximityNear(this)

        // Erst mit Root versuchen (keine Rueckfrage noetig), sonst normale Abfrage.
        cameraGranted = hasCameraPermission()
        thread {
            Root.setup()
            runOnUiThread {
                cameraGranted = hasCameraPermission()
                if (!cameraGranted) cameraRequest.launch(Manifest.permission.CAMERA)
            }
        }

        setContent {
            val luxState: State<Float?> = lightFlow.collectAsState(initial = null)
            SmartDisplayApp(
                lux = luxState.value,
                fontFamily = fontFamily,
                onBrightness = { setBrightness(it) },
                cameraGranted = cameraGranted,
                resumeTick = resumeTick,
                proximity = proximityFlow,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = lp
    }
}

/** Helligkeitssensor als geglaetteter Fluss in Lux. Ohne Sensor endet der Fluss sofort. */
private fun lightLevel(context: Context): Flow<Float> = callbackFlow {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
    if (sensor == null) {
        close()
        return@callbackFlow
    }
    var average = -1f
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values[0]
            average = if (average < 0f) v else average * 0.9f + v * 0.1f
            trySend(average)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    awaitClose { manager.unregisterListener(listener) }
}.distinctUntilChanged { old, new -> abs(old - new) <= max(0.5f, old * 0.15f) }
