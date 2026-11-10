# Dashboard de accesibilidad acumulada

Dashboard HTML para analizar descargas normalizadas de accesibilidad/demoras en Atencion Primaria.

## Uso

1. Abre `index.html` en el navegador.
2. Pulsa `Cargar Excel`.
3. Selecciona la descarga `.xlsx` normalizada.
4. El dashboard calcula KPIs, evolucion, tramos oficiales, mapa de calor, distribucion y ranking en local.

## App Android e impresión

La [APK Android 1.1.0](downloads/accesibilidad-media-ap-1.1.0.apk) puede instalarse encima de la versión 1.0.0 distribuida con la misma firma. La app sigue cargando la página de GitHub Pages.

Con un Excel cargado, **Informe evolutivo** abre el diálogo de impresión del sistema Android. Desde ese diálogo se puede elegir una impresora o guardar como PDF. El informe se prepara en el dispositivo y se muestra para impresión en una WebView separada con la red bloqueada.

El código de la app está en [android/](android/). Para compilarlo, usa JDK 17 o compatible y Android SDK 35, y ejecuta `gradlew.bat assembleDebug` desde esa carpeta.

## Privacidad

Los datos se procesan siempre en local, dentro del navegador. Ningun Excel se sube a la nube, a internet ni a servidores externos.

El HTML incluye una politica de seguridad que bloquea conexiones externas y recursos remotos para respetar la confidencialidad de los datos.
