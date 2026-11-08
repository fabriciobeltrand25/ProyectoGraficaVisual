package com.autoscann.hn;

import android.graphics.Rect;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Utilidades de ML Kit compartidas por las pantallas de captura y el live preview. */
public final class OcrUtil {

    private OcrUtil() {
    }

    /**
     * Arma el texto del OCR fila por fila usando la posición de cada línea,
     * para que cada etiqueta quede junto a su valor aunque ML Kit separe las columnas.
     */
    public static String textoPorFilas(Text text) {
        List<ReceiptParser.Linea> lineas = new ArrayList<>();
        for (Text.TextBlock bloque : text.getTextBlocks()) {
            for (Text.Line linea : bloque.getLines()) {
                Rect caja = linea.getBoundingBox();
                if (caja == null) return text.getText(); // sin posiciones: texto plano
                lineas.add(new ReceiptParser.Linea(linea.getText(), caja.top, caja.bottom, caja.left));
            }
        }
        String filas = ReceiptParser.reconstruirFilas(lineas);
        return filas.isEmpty() ? text.getText() : filas;
    }
}
