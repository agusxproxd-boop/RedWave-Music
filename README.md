# REDWAVE Music — Android native project

This is the native Android version intended to replace a generic HTML-to-APK wrapper.

What it adds:
- MediaStore audio scan
- READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE compatibility
- Android "Abrir con" registration for audio/*
- Native file picker
- Media3 playback service / MediaSession foundation for notification and lock-screen controls
- WebView UI using the REDWAVE HTML
- JavaScript bridge between the UI and Android

Open this folder as an Android Studio project and build the APK.

Important:
- A generic HTML-to-APK service cannot invent the MediaStore and MediaSession bridge used here.
- On Android 13+, audio from other apps is accessed through READ_MEDIA_AUDIO.
- The UI is still HTML, but the parts that require Android APIs are native Kotlin.

## Build
1. Extract the ZIP.
2. Open the extracted folder in Android Studio.
3. Let Gradle sync/download dependencies.
4. Build > Build APK(s).

## Correcciones aplicadas (revisión)
- `AndroidManifest.xml`: `MainActivity` ahora usa `launchMode="singleTask"` para que "Abrir con" reabra la app correctamente en vez de crear instancias duplicadas.
- `MainActivity.kt`: se añadió `onNewIntent` para procesar un archivo abierto mientras la app ya está corriendo; el inicio del servicio de reproducción usa `ContextCompat.startForegroundService` (evita el crash típico de Android 8+ al reproducir en segundo plano); se conectó un `MediaController` real al `MediaSession` para enviar el estado de reproducción (play/pausa, posición, duración) de vuelta a la interfaz web.
- `PlaybackService.kt`: `ExoPlayer` ahora declara `AudioAttributes` explícitos con manejo de audio focus y se pausa automáticamente al desconectar los audífonos (`setHandleAudioBecomingNoisyEnabled`).
- `app/build.gradle.kts`: se agregó `compileOptions`/`kotlinOptions` (Java 17) y exclusiones de `packaging.resources` para evitar el error de compilación más común con Media3 + AppCompat (archivos `META-INF` duplicados).
- `assets/index.html`: el botón de play/pausa y la barra de progreso ya reflejan el estado real de reproducción nativa (antes solo miraban un `<audio>` HTML que no se usa en modo nativo).

## Por qué no se generó el .apk directamente en esta revisión
Este entorno de revisión no tiene el Android SDK instalado ni acceso a internet para descargar Gradle/dependencias, así que no pude ejecutar la compilación aquí mismo. El proyecto se corrigió a nivel de código fuente. Dos formas reales de obtener el `.apk` instalable:

### Opción A — GitHub Actions (sin instalar nada)
1. Sube esta carpeta a un repositorio de GitHub.
2. El workflow `.github/workflows/build-apk.yml` se ejecuta automáticamente y compila un APK de depuración real.
3. Ve a la pestaña **Actions** del repo → abre la ejecución → descarga el artefacto `redwave-music-debug-apk`.
4. Copia el `.apk` a tu teléfono e instálalo (activa "Instalar apps desconocidas" para el navegador o gestor de archivos que uses).

### Opción B — Android Studio (local)
1. Extrae el ZIP y ábrelo con Android Studio (Hedgehog o más reciente).
2. Deja que Gradle sincronice (descargará el SDK/dependencias automáticamente).
3. Build → Build Bundle(s) / APK(s) → Build APK(s).
4. El APK queda en `app/build/outputs/apk/debug/`.

Ambas rutas producen el mismo proyecto nativo Android real (no un wrapper HTML): MediaStore para escaneo, selector nativo de archivos, `Media3`/`MediaSessionService` para reproducción en segundo plano con notificación y controles, y el `intent-filter` de `audio/*` para aparecer en "Abrir con".
