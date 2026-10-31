# Smart Display (Etappe 1)

Smart-Display-App für das moto g42. Material-Design-Look, Google Sans Flex, animierte Hintergründe nach
Jahreszeit, Wetter, Tageszeit und Anlass (Halloween, Weihnachten, Silvester, Karneval, Ostern, Valentinstag).

Enthalten bis Etappe 2:
- Uhr mit animierten Ziffern, Datum, Begrüßung und Feiertagsanzeige
- Wetter für Bonn-Duisdorf (Open-Meteo, kostenlos) mit Vorhersage für fünf Tage
- Nachrichten: aktuelle Schlagzeilen der Tagesschau (öffentliche API, ohne Schlüssel)
- Übersicht kommender Tage: Feiertage NRW, Ferienbeginn und Ferienende, besondere Tage
- Helligkeitssensor: Display-Helligkeit passt sich an, nachts warme, sehr dunkle Farben und nur noch die Uhr
- Anwesenheit: Die Frontkamera erkennt Bewegung (kleine Graustufenbilder, nichts wird gespeichert oder gesendet).
  Ohne Bewegung geht das Display tagsüber nach 5 Minuten, nachts nach 2 Minuten aus (schwarz, auf AMOLED praktisch aus).
  Bewegung schaltet es wieder ein. Nachts ist es im Zimmer meist zu dunkel für die Kamera: dann Hand heben oder antippen.
- Winken: Hand kurz über den Näherungssensor (oben am Hörer) bewegen. Display aus. Nochmal winken: wieder an.
  Ein manuelles Ausschalten bleibt bestehen, bis wieder gewunken oder getippt wird.
- Einbrennschutz: Der Inhalt wandert alle paar Minuten um wenige Pixel.
- Autostart nach dem Hochfahren und Startbildschirm-Modus (Kiosk), siehe unten
- Seitenwechsel alle 20 Sekunden, Antippen blättert weiter
- Keine Vibration, keine Emojis, keine Smart-Home-Funktionen, kein Kalender

Noch nicht enthalten (kommt in den nächsten Etappen): Alarmerkennung mit Taschenlampe und Infos zum Alarm (Etappe 3),
Musikerkennung und Zusatz-Widgets (Etappe 4).

## Root, Autostart und Kiosk

Beim ersten Start fragt Magisk, ob "Smart Display" Root bekommen darf: erlauben. Die App richtet damit selbst ein:
Kamera-Erlaubnis, "Über anderen Apps anzeigen" (für den Autostart) und Ausnahme von der Akku-Optimierung.
Ohne Root fragt Android normal nach der Kamera-Erlaubnis, der Autostart klappt dann nur, wenn
"Über anderen Apps anzeigen" von Hand erlaubt wurde (Einstellungen, Apps, Smart Display).

Kiosk-Modus (optional): Einstellungen, Apps, Standard-Apps, Start-App, "Smart Display" wählen. Dann landet auch
die Home-Taste immer in der App.

## APK bauen lassen (GitHub, kostenlos, nichts zu installieren)

1. Auf github.com ein kostenloses Konto anlegen und anmelden.
2. Oben rechts auf das Plus, dann "New repository". Name zum Beispiel `smart-display`, auf "Private" stellen,
   "Create repository" klicken.
3. Auf der neuen Seite den Link "uploading an existing file" anklicken.
4. Die ZIP-Datei auf dem Rechner entpacken. Alle Dateien und Ordner in das Browserfenster ziehen.
   Wichtig: Der Ordner `.github` beginnt mit einem Punkt und ist im Dateimanager versteckt.
   Unter Fedora im Dateimanager `Strg + H` drücken, dann wird er sichtbar und lässt sich mit hochladen.
5. Unten auf "Commit changes" klicken.
6. Oben auf den Reiter "Actions". Der Lauf "APK bauen" startet von selbst und dauert etwa 5 bis 10 Minuten.
   Beim ersten Mal fragt GitHub eventuell, ob Workflows aktiviert werden sollen: bestätigen.
7. Wenn der Lauf einen grünen Haken hat, anklicken. Unten bei "Artifacts" liegt `smart-display-apk`.
   Herunterladen und entpacken, darin liegt `app-release.apk`.
8. Die APK aufs Handy bringen (USB-Kabel oder Cloud) und antippen. Android fragt einmal nach der Erlaubnis,
   Apps aus dieser Quelle zu installieren: erlauben.

Wird der Haken rot: Den Lauf öffnen, auf den roten Schritt klicken, die Fehlerzeilen kopieren und an Claude
schicken. Das ist beim ersten Bau gut möglich und meist schnell behoben.

## Schrift

Google Sans Flex wird beim Bauen automatisch aus dem Google-Fonts-Repository geladen. Falls das nicht klappt
(im Protokoll steht dann "Schrift nicht gefunden"), die APK wird trotzdem gebaut und verwendet die
Systemschrift. Wer die Schrift selbst hat, legt die `.ttf`-Datei in `app/src/main/assets/fonts/` und baut neu.

## Anpassen

- Standort: `Weather.kt`, Koordinaten in `ENDPOINT`
- Schulferien: `Days.kt`, Liste `ferien` (aktuell bis Januar 2028)
- Farben je Jahreszeit und Anlass: `Theme.kt`
