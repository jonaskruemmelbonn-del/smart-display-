package app.smartdisplay

import java.util.concurrent.TimeUnit

/** Kleine Hilfe fuer Root (Magisk). Ohne Root liefern alle Aufrufe einfach false. */
object Root {
    const val PACKAGE = "app.smartdisplay"

    fun run(command: String, timeoutSeconds: Long = 15): Boolean = try {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        process.outputStream.close()
        Thread {
            try {
                process.inputStream.readBytes()
            } catch (e: Exception) {
            }
        }.start()
        if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.exitValue() == 0
        } else {
            process.destroy()
            false
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Einmalige Einrichtung beim Start: Kamera-Erlaubnis erteilen, "ueber anderen Apps anzeigen"
     * erlauben (fuer den Autostart) und die App von der Akku-Optimierung ausnehmen.
     * Magisk fragt beim ersten Mal nach, ob die App Root bekommen darf.
     */
    fun setup() {
        if (!run("pm grant $PACKAGE android.permission.CAMERA")) return
        run("appops set $PACKAGE SYSTEM_ALERT_WINDOW allow")
        run("dumpsys deviceidle whitelist +$PACKAGE")
    }

    fun launchApp(): Boolean = run("am start -n $PACKAGE/.MainActivity")
}
