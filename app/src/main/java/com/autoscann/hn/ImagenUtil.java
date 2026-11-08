package com.autoscann.hn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/** Lectura y borrado de las fotos de recibo guardadas en el almacenamiento interno. */
public final class ImagenUtil {

    private ImagenUtil() {
    }

    /**
     * Decodifica una foto reducida para que su lado mayor no supere {@code maxLado} píxeles.
     * Devuelve null si la ruta está vacía, el archivo no existe o no es una imagen válida.
     */
    public static Bitmap decodificarEscalada(String ruta, int maxLado) {
        if (ruta == null || ruta.isEmpty() || !new File(ruta).exists()) return null;

        BitmapFactory.Options medidas = new BitmapFactory.Options();
        medidas.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(ruta, medidas);
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null;

        int muestra = 1;
        while (Math.max(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= maxLado) {
            muestra *= 2;
        }

        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = muestra;
        return BitmapFactory.decodeFile(ruta, opciones);
    }

    /** Borra un archivo de foto si existe. Ignora rutas vacías. */
    public static void borrar(String ruta) {
        if (ruta != null && !ruta.isEmpty()) {
            new File(ruta).delete();
        }
    }
}
