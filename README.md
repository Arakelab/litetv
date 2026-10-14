# Lite TV

Reproductor de listas M3U para Android TV. Minimalista, sin contenido propio: tú le das las listas.

## Compilar el APK (una sola vez)

1. Crea un repositorio en GitHub (puede ser privado, aunque para el link de Downloader debe ser público).
2. Sube todo el contenido de esta carpeta al repositorio, rama `main`.
3. Entra a la pestaña **Actions**, abre **Build APK** y espera a que termine (unos 4 a 6 minutos). Cada push a `main` lo vuelve a compilar.
4. El APK queda en **Releases** con el nombre `LiteTV.apk`.

## Instalarlo en la TV con Downloader

En Downloader escribe esta URL (cambia USUARIO y REPO):

    https://github.com/USUARIO/REPO/releases/latest/download/LiteTV.apk

Si te pide permiso, activa "Instalar apps desconocidas" para Downloader en los ajustes de la TV.
Siempre apunta a la última versión compilada. La app queda firmada con la misma llave, así que las actualizaciones se instalan encima.

## Uso en la TV

- OK sobre un canal: reproducir.
- Mantener OK sobre un canal: añadir o quitar de favoritos.
- Dentro del reproductor: ▲ canal anterior, ▼ canal siguiente (o CH+/CH−), OK muestra la barra, Atrás sale.
- **Fuentes** (arriba a la derecha): añadir la URL de tu lista M3U, eliminar listas o restaurar las gratuitas.

## Listas

Trae dos listas públicas precargadas (canales gratuitos de Colombia y en español, del proyecto iptv-org). Las listas se guardan en el dispositivo, así que la app abre al instante y se actualiza en segundo plano.

Usa solo listas de las que tengas derecho a ver el contenido.

## Nota sobre la firma

`app/litetv.jks` es una llave de uso personal incluida para que el APK siempre se firme igual. Si publicas la app para otras personas, genera tu propia llave y cámbiala en `app/build.gradle.kts`.
