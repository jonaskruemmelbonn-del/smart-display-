package app.smartdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Startet die App nach dem Hochfahren. Mit Root zuverlaessig, sonst ueber die Overlay-Erlaubnis. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                Thread.sleep(3_000)
                if (!Root.launchApp()) {
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
