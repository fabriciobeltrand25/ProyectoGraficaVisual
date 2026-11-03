
package com.autoscann.hn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    public static class Resultado {

        public double total = 0;
        public double litros = 0;
        public String fecha = "";

    }

    public static Resultado analizar(String texto) {

        Resultado resultado = new Resultado();

        // Normalizar texto
        String textoNormalizado = texto
                .replace(",", ",")
                .replace("\r", "\n");

        // Buscar TOTAL en lempiras
        Pattern patronTotal = Pattern.compile(
                "(?i)(?:TOTAL(?:\\s+A\\s+PAGAR)?|IMPORTE\\s+TOTAL)" +
                        "[\\s:]*L?\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"
        );

        Matcher matcherTotal = patronTotal.matcher(
                textoNormalizado
        );

        if (matcherTotal.find()) {

            String valor = matcherTotal.group(1)
                    .replace(",", "");

            try {
                resultado.total = Double.parseDouble(valor);
            } catch (NumberFormatException e) {
                resultado.total = 0;
            }
        }

        // Buscar cantidad de litros
        Pattern patronLitros = Pattern.compile(
                "(?i)(?:CANTIDAD|VOLUMEN|LITROS?)" +
                        "[\\s:=]*([0-9]+(?:[.,][0-9]+)?)\\s*(?:LTS?|LITROS?)?"
        );

        Matcher matcherLitros = patronLitros.matcher(
                textoNormalizado
        );

        if (matcherLitros.find()) {

            String valor = matcherLitros.group(1)
                    .replace(",", ".");

            try {
                resultado.litros = Double.parseDouble(valor);
            } catch (NumberFormatException e) {
                resultado.litros = 0;
            }
        }

        // Buscar fecha DD/MM/YYYY o DD-MM-YYYY
        Pattern patronFecha = Pattern.compile(
                "\\b([0-3]?\\d)[/-]([01]?\\d)[/-](\\d{4})\\b"
        );

        Matcher matcherFecha = patronFecha.matcher(
                textoNormalizado
        );

        if (matcherFecha.find()) {

            resultado.fecha = matcherFecha.group(1) + "/" +
                    matcherFecha.group(2) + "/" +
                    matcherFecha.group(3);
        }

        // Buscar fecha YYYY-MM-DD si no se encontró antes
        if (resultado.fecha.isEmpty()) {

            Pattern patronISO = Pattern.compile(
                    "\\b(\\d{4})-(\\d{2})-(\\d{2})\\b"
            );

            Matcher matcherISO = patronISO.matcher(
                    textoNormalizado
            );

            if (matcherISO.find()) {

                resultado.fecha = matcherISO.group(3) + "/" +
                        matcherISO.group(2) + "/" +
                        matcherISO.group(1);
            }
        }

        return resultado;
    }
}
