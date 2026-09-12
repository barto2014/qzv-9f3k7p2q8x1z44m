# Barto TV

Versión corregida de Barto TV, preparada para subir a GitHub y desplegar en Vercel.

## Incluye

- Reproductor HLS para enlaces HTTP y HTTPS.
- Proxy `/api/stream` para evitar bloqueos de contenido mixto y CORS.
- Reproducción MPD/DASH, FLV, YouTube y páginas externas.
- Botón “Volver” abajo a la izquierda en todos los reproductores.
- Botón “Volver” con foco automático en YouTube.
- Panel de administración y datos de canales incluidos.

## Subir a GitHub

1. Extraé este ZIP.
2. Subí todos los archivos y carpetas al repositorio.
3. Conservá la carpeta `api/` y el archivo `vercel.json`.
4. Desplegá el repositorio en Vercel.

La API de reproducción está en `api/[...path].js`.