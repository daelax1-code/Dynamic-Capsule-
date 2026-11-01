# Capsule Island

Dynamic Island für Android im Stil von **Material 3 Expressive** – gebaut für das **Nothing Phone (3a)** (mittiges Kameraloch), funktioniert aber auf jedem Android-12+-Gerät mit Kameraloch.

## Funktionen

- **Musik & Videos:** rotierendes Cover, Equalizer, Wellen-Fortschrittsbalken zum Spulen, Play/Pause-Knopf mit Form-Morph, Vor/Zurück per Knopf oder Wischen
- **Benachrichtigungen:** klappen automatisch auf, Aktionen direkt antippen, **direkt antworten** ohne die App zu öffnen
- **Timer & Stoppuhr** aus der Uhr-App, live tickend
- **Downloads & Fortschritt** (Play Store, Browser, Telegram …) mit Fortschrittsring
- **Laden** beim Ein- und Ausstecken
- **Schnellmenü:** Uhrzeit, Akku, Taschenlampe (mit Stärke, falls unterstützt), Helligkeit, Lautstärke, bis zu 5 App-Verknüpfungen
- **Zwei Ereignisse gleichzeitig:** zweite Blase rechts neben der Pille
- **Gesten:** Tippen, Doppeltippen, lange drücken, wischen in alle Richtungen
- **Ruhezustand unsichtbar** – die Pille erscheint nur, wenn etwas passiert
- Material-You-Farben vom Hintergrundbild, Akzentfarbe aus dem Albumcover
- Größe, Position und Animationsstil einstellbar

## Installieren

1. Auf dem Handy unter **Releases** die neueste `CapsuleIsland-*.apk` herunterladen und installieren.
2. App öffnen und die drei Berechtigungen geben:
   - **Bedienungshilfe** (zeichnet die Island über der Statusleiste). Ist der Schalter ausgegraut:
     *Einstellungen → Apps → Capsule Island → ⋮ → „Eingeschränkte Einstellungen zulassen“*, dann nochmal.
   - **Benachrichtigungszugriff**
   - **Systemeinstellungen ändern** (optional, für den Helligkeitsregler)
3. Unter „Ausprobieren“ die Demo-Ereignisse antippen.

Updates: neue APK einfach über die alte installieren – alle Builds sind mit demselben Schlüssel signiert.

## Bauen

Jeder Push auf `main` baut über GitHub Actions automatisch eine APK und veröffentlicht sie als Release.
Lokal: `./gradlew assembleRelease` (JDK 17, Android SDK 35).

## Aufbau

| Datei | Aufgabe |
|---|---|
| `IslandService.kt` | Bedienungshilfe-Dienst, startet das Overlay, erkennt Laden |
| `OverlayController.kt` | Overlay-Fenster über der Kamera, misst das Kameraloch |
| `NotificationService.kt` | Benachrichtigungen, Medien-Sitzungen, Timer, Downloads |
| `IslandStore.kt` | Gemeinsamer Zustand aller Ereignisse |
| `ui/IslandRoot.kt` | Zustände, Größen, Gesten, Übergänge |
| `ui/IslandContents.kt` | Inhalte für Musik, Timer, Download, Laden, Benachrichtigung |
| `ui/QuickPanel.kt` | Schnellmenü |
| `ui/Expressive.kt` | Wellen-Fortschritt, Morph-Knopf, Equalizer, Regler |
| `MainActivity.kt` | Einstellungs-App |
