package com.autoscann.hn;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    public static class Resultado {
        public double total = 0;
        public double litros = 0;
        public double precio = 0;
        public String fecha = "";
        public boolean validado = false;
        public String comercio = "";
        public String unidad = "L";
        public String descripcion = "";
        public double cantidad = 0;
        public double precioUnitario = 0;
    }

    public static class Linea {
        final String texto;
        final int top, bottom, left;

        public Linea(String texto, int top, int bottom, int left) {
            this.texto = texto;
            this.top = top;
            this.bottom = bottom;
            this.left = left;
        }

        double centro() { return (top + bottom) / 2.0; }
        int alto() { return Math.max(1, bottom - top); }
    }

    private static final Pattern MONTO = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+|\\d+)[.,](\\d{2})(?![\\d])");
    private static final Pattern DECIMAL = Pattern.compile("(?<![\\d.,])(\\d+)[.,](\\d{1,3})(?![\\d])");

    // REGLA CORREGIDA: Ahora acepta enteros o decimales sin romperse
    private static final Pattern NUMERO_GENERAL = Pattern.compile("(?<![\\d.,])(\\d+(?:[.,]\\d{1,3})?)(?![\\d])");

    private static final Pattern TOTAL_FUERTE = Pattern.compile("\\bTOTAL\\s*A\\s*PAGAR\\b|\\bA\\s*PAGAR\\b|\\bIMPORTE\\s*TOTAL\\b|\\bTOTAL\\s*FACTURA\\b|\\bGRAN\\s*TOTAL\\b");
    private static final Pattern TOTAL_DEBIL = Pattern.compile("\\bTOTAL\\b");
    private static final Pattern TOTAL_EXCLUIR = Pattern.compile("ISV|IVA|IMPUESTO|PRECIO|PRODUCTO|\\bCANT|EXENTO|GRAVADO|DESCUENTO|EXONERADO");
    private static final Pattern PRODUCTO_COMBUSTIBLE = Pattern.compile("\\b(GAS(?:OLINA)?|SUPER|REGULAR|DIESEL|V-?POWER|KEROSENE)\\b");
    private static final Pattern CANTIDAD_ETIQUETA = Pattern.compile("\\b(?:CANTIDAD|CANT|VOLUMEN|LITROS?|GALONES?)\\.?[\\s:=]*(\\d+[.,]\\d{1,3})(?![\\d])");
    private static final Pattern CANTIDAD_UNIDAD = Pattern.compile("(?<![\\d.,])(\\d+[.,]\\d{1,3})\\s*(?:LTS?|LITROS?|GAL(?:ONES)?|GLS?)\\b");
    private static final Pattern FECHA_DMY = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*(\\d{4})(?!\\d)");
    private static final Pattern FECHA_ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern FECHA_EMISION = Pattern.compile("FECHA\\s*(?:DE\\s*)?E[MN]");
    private static final Pattern FECHA_EXCLUIR = Pattern.compile("L[I1L1][MNW]|VENC|RANGO");
    private static final Pattern TOKEN_NUMERICO = Pattern.compile("[0-9OoDQIl|SsBZ.,/\\-]+");
    private static final Pattern GALON = Pattern.compile("\\b(?:GAL(?:ONES?)?|GLS?)\\b");
    private static final Pattern FECHA_MES = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[/.-]\\s*([A-Z]{3,})\\s*[/.-]\\s*(\\d{4})(?!\\d)");
    private static final Pattern FECHA_CORTA = Pattern.compile("(?<!\\d)(\\d{2})\\s*[/.-]\\s*(\\d{2})\\s*[/.-]\\s*(\\d{2})(?!\\d)");
    private static final Pattern FECHA_MANUAL = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern NO_COMERCIO = Pattern.compile("\\d|\\b(?:RTN|CAI|FACTURA|RANGO|TEL|BOULEVARD|BLVD|COL|CALLE|AVENIDA|AV|BARRIO|CAJERO|CLIENTE|FECHA|DIRECCION|TICKET|RECIBO|CAJA|SURTIDOR|PRECIO|CANTIDAD|CODIGO|UNITARIO|DESC\\w*|TOTAL)\\b");
    private static final Pattern NO_ARTICULO = Pattern.compile("\\b(?:TOTAL|SUBTOTAL|ISV|I\\.?S\\.?V|I\\.?O\\.?V|IVA|IMPUEST\\w*|IMPORTE\\w*|CAMBIO|PAGO|EFECTIVO|TARJETA|DESCUENTOS?|REBAJ\\w*|OTORG\\w*|EXENT\\w*|GRAVAD\\w*|EXONER\\w*|VENTA|VENITA|RTN|FACTURA|RANGO|CAI|FECHA|HORA|LIMITE|VENC|CAJERO|CLIENTE|RECIBIDO|VUELTO|TEL|PRECIO|CANTIDAD|CODIGO|UNITARIO|DESC\\w*)\\b");
    private static final Pattern CODIGO = Pattern.compile("\\b[A-Z0-9]+-[A-Z0-9-]+\\b");
    private static final Pattern CANTIDAD_ENTERA = Pattern.compile("(?<![\\w.,])(\\d{1,3})(?![\\w.,])");
    private static final Pattern RUIDO_SUELTO = Pattern.compile("(^|\\s)(?:L\\.?|\\$|X)(?=\\s|$)");

    public static String reconstruirFilas(List<Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) return "";
        List<Linea> ordenadas = new ArrayList<>(lineas);
        Collections.sort(ordenadas, (a, b) -> Double.compare(a.centro(), b.centro()));
        List<List<Linea>> filas = new ArrayList<>();
        List<Linea> filaActual = null;
        double centroFila = 0;
        int altoFila = 0;
        for (Linea l : ordenadas) {
            if (filaActual != null && Math.abs(l.centro() - centroFila) <= 0.5 * Math.min(l.alto(), altoFila)) {
                filaActual.add(l);
                centroFila = (centroFila * (filaActual.size() - 1) + l.centro()) / filaActual.size();
                altoFila = Math.max(altoFila, l.alto());
            } else {
                filaActual = new ArrayList<>();
                filaActual.add(l);
                filas.add(filaActual);
                centroFila = l.centro();
                altoFila = l.alto();
            }
        }
        StringBuilder sb = new StringBuilder();
        for (List<Linea> fila : filas) {
            Collections.sort(fila, (a, b) -> Integer.compare(a.left, b.left));
            for (int i = 0; i < fila.size(); i++) {
                if (i > 0) sb.append("  ");
                sb.append(fila.get(i).texto);
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static Resultado analizar(String texto) {
        return analizar(texto, LocalDate.now());
    }

    public static Resultado analizar(String texto, LocalDate hoy) {
        Resultado r = new Resultado();
        if (texto == null || texto.trim().isEmpty()) return r;
        List<String> lineas = prepararLineas(texto);
        r.litros = buscarCantidad(lineas);
        r.unidad = buscarUnidad(lineas);
        r.total = buscarTotal(lineas);
        r.fecha = buscarFecha(lineas, hoy);
        r.comercio = buscarComercio(lineas);
        validarCruzado(r, lineas);
        return r;
    }

    public static Resultado analizarMantenimiento(String texto) {
        return analizarMantenimiento(texto, LocalDate.now());
    }

    public static Resultado analizarMantenimiento(String texto, LocalDate hoy) {
        Resultado r = new Resultado();
        if (texto == null || texto.trim().isEmpty()) return r;
        List<String> lineas = prepararLineas(texto);
        r.fecha = buscarFecha(lineas, hoy);
        r.comercio = buscarComercio(lineas);
        r.total = buscarTotal(lineas);

        List<String> descripciones = new ArrayList<>();
        double sumaArticulos = 0;
        int articulos = 0;
        double ultimaCantidad = 0;
        double ultimoPrecio = 0;

        for (int idx = 0; idx < lineas.size(); idx++) {
            String l = lineas.get(idx);
            if (NO_ARTICULO.matcher(l).find()) continue;

            List<Double> montos = new ArrayList<>();
            Matcher m = MONTO.matcher(l);
            while (m.find()) montos.add(aNumero(m.group(1), m.group(2)));
            if (montos.size() < 2) continue;

            double totalLinea = montos.get(montos.size() - 1);
            String sinMontos = MONTO.matcher(l).replaceAll(" ");
            String resto = sinMontos;
            double cantidad = 1;
            Matcher cant = CANTIDAD_ENTERA.matcher(sinMontos);
            if (cant.find()) {
                cantidad = Math.max(1, parsear(cant.group(1)));
                resto = sinMontos.substring(0, cant.start()) + " " + sinMontos.substring(cant.end());
            }

            double[] precioYDescuento = buscarPrecioYDescuento(cantidad, montos);
            if (precioYDescuento == null) continue;
            double precio = precioYDescuento[0];

            String desc = limpiarDescripcion(resto);
            if (desc.isEmpty() && idx > 0) desc = nombreDeLineaAnterior(lineas.get(idx - 1));
            if (!desc.isEmpty()) descripciones.add(desc);

            sumaArticulos += totalLinea;
            ultimaCantidad = cantidad;
            ultimoPrecio = precio;
            articulos++;
        }

        r.descripcion = String.join("; ", descripciones);
        if (articulos == 1) {
            r.cantidad = ultimaCantidad;
            r.precioUnitario = ultimoPrecio;
        }

        boolean totalLeido = r.total > 0;
        if (!totalLeido) r.total = sumaArticulos;

        if (totalLeido && sumaArticulos > 0) {
            for (double factor : new double[]{1.0, 1.15, 1.18}) {
                if (cuadra(1, sumaArticulos * factor, r.total)) {
                    r.validado = true;
                    break;
                }
            }
        }
        return r;
    }

    private static String limpiarDescripcion(String texto) {
        String desc = CODIGO.matcher(texto).replaceAll(" ");
        return RUIDO_SUELTO.matcher(desc).replaceAll(" ")
                .replaceAll("\\s+", " ")
                .replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "")
                .trim();
    }

    private static String nombreDeLineaAnterior(String anterior) {
        if (NO_ARTICULO.matcher(anterior).find()) return "";
        if (MONTO.matcher(anterior).find()) return "";
        if (!anterior.matches(".*[A-Z]{4,}.*")) return "";
        return limpiarDescripcion(anterior);
    }

    private static double[] buscarPrecioYDescuento(double cantidad, List<Double> montos) {
        double total = montos.get(montos.size() - 1);
        for (int i = 0; i < montos.size() - 1; i++) {
            double precio = montos.get(i);
            if (cuadra(cantidad, precio, total)) return new double[]{precio, 0};
            for (int j = 0; j < montos.size() - 1; j++) {
                if (j == i) continue;
                double descuento = montos.get(j);
                if (cuadra(cantidad, precio, total + descuento)) return new double[]{precio, descuento};
            }
        }
        return null;
    }

    public static String validarFechaManual(String texto, LocalDate hoy) {
        if (texto == null) return "";
        Matcher m = FECHA_MANUAL.matcher(texto.trim());
        if (!m.matches()) return "";
        return formatearFecha(m.group(1), m.group(2), m.group(3), hoy);
    }

    private static String buscarUnidad(List<String> lineas) {
        for (String l : lineas) {
            if (GALON.matcher(l).find()) return "GAL";
        }
        return "L";
    }

    private static String buscarComercio(List<String> lineas) {
        for (String l : lineas) {
            if (l.length() < 4) continue;
            if (NO_COMERCIO.matcher(l).find()) continue;
            if (!l.matches(".*[A-Z]{3}.*")) continue;
            return l;
        }
        return "";
    }

    private static List<String> prepararLineas(String texto) {
        String[] crudas = texto.replace("\r", "\n").split("\n");
        List<String> lineas = new ArrayList<>();
        for (String l : crudas) {
            String n = sinAcentos(corregirDigitos(l)).toUpperCase(Locale.ROOT).trim();
            n = n.replaceAll("(?<![A-Z])L\\.(?=\\s*\\d)", "L ");
            if (!n.isEmpty()) lineas.add(n);
        }
        return lineas;
    }

    private static String sinAcentos(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    static String corregirDigitos(String linea) {
        Matcher m = TOKEN_NUMERICO.matcher(linea);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            String token = m.group();
            boolean tieneDigito = token.matches(".*\\d.*");
            boolean palabraCompleta = (m.start() == 0 || !Character.isLetter(linea.charAt(m.start() - 1)))
                    && (m.end() == linea.length() || !Character.isLetter(linea.charAt(m.end())));
            sb.append(linea, ultimo, m.start());
            if (tieneDigito && palabraCompleta) {
                StringBuilder t = new StringBuilder();
                for (char c : token.toCharArray()) {
                    switch (c) {
                        case 'O': case 'o': case 'D': case 'Q': t.append('0'); break;
                        case 'I': case 'l': case '|': t.append('1'); break;
                        case 'S': case 's': t.append('5'); break;
                        case 'B': t.append('8'); break;
                        case 'Z': t.append('2'); break;
                        default: t.append(c);
                    }
                }
                sb.append(t);
            } else {
                sb.append(token);
            }
            ultimo = m.end();
        }
        sb.append(linea.substring(ultimo));
        return sb.toString();
    }

    private static double buscarCantidad(List<String> lineas) {
        for (int i = 0; i < lineas.size(); i++) {
            String l = lineas.get(i);
            Matcher prod = PRODUCTO_COMBUSTIBLE.matcher(l);
            if (!prod.find()) continue;

            // USAMOS LA REGLA SEGURA AQUI
            Matcher dec = NUMERO_GENERAL.matcher(l.substring(0, prod.start()));
            if (dec.find()) {
                // Si el grupo 2 existe, es decimal. Si no, es entero.
                if (dec.groupCount() >= 2 && dec.group(2) != null) return aNumero(dec.group(1), dec.group(2));
                return Double.parseDouble(dec.group(1).replace(",", "."));
            }
        }

        for (String l : lineas) {
            Matcher m = CANTIDAD_ETIQUETA.matcher(l);
            if (m.find()) return parsear(m.group(1));
        }
        for (String l : lineas) {
            Matcher m = CANTIDAD_UNIDAD.matcher(l);
            if (m.find()) return parsear(m.group(1));
        }
        return 0;
    }

    private static double buscarTotal(List<String> lineas) {
        double totalFuerte = 0;
        for (String l : lineas) {
            Matcher m = TOTAL_FUERTE.matcher(l);
            if (m.find()) {
                double v = primerMonto(l.substring(m.end()));
                if (v > 0) totalFuerte = Math.max(totalFuerte, v);
            }
        }
        if (totalFuerte > 0) return totalFuerte;

        double mejorDebil = 0;
        for (String l : lineas) {
            Matcher m = TOTAL_DEBIL.matcher(l);
            if (m.find() && !TOTAL_EXCLUIR.matcher(l).find()) {
                mejorDebil = Math.max(mejorDebil, primerMonto(l.substring(m.end())));
            }
        }
        return mejorDebil;
    }

    private static String buscarFecha(List<String> lineas, LocalDate hoy) {
        String mejor = "";
        int mejorPuntaje = 0;
        for (String l : lineas) {
            boolean esLimite = FECHA_EXCLUIR.matcher(l).find();
            boolean esEmision = FECHA_EMISION.matcher(l).find();
            int puntaje = esLimite ? 0 : (esEmision ? 3 : 2);
            if (puntaje <= mejorPuntaje) continue;
            String f = extraerFecha(l, hoy);
            if (!f.isEmpty()) {
                mejor = f;
                mejorPuntaje = puntaje;
            }
        }
        return mejor;
    }

    private static String extraerFecha(String linea, LocalDate hoy) {
        Matcher m = FECHA_DMY.matcher(linea);
        while (m.find()) {
            String f = formatearFecha(m.group(1), m.group(2), m.group(3), hoy);
            if (!f.isEmpty()) return f;
        }
        m = FECHA_ISO.matcher(linea);
        while (m.find()) {
            String f = formatearFecha(m.group(3), m.group(2), m.group(1), hoy);
            if (!f.isEmpty()) return f;
        }
        m = FECHA_MES.matcher(linea);
        while (m.find()) {
            String mes = numeroDeMes(m.group(2));
            if (mes == null) continue;
            String f = formatearFecha(m.group(1), mes, m.group(3), hoy);
            if (!f.isEmpty()) return f;
        }
        if (linea.contains("FECHA")) {
            m = FECHA_CORTA.matcher(linea);
            while (m.find()) {
                String f = formatearFecha(m.group(1), m.group(2), "20" + m.group(3), hoy);
                if (!f.isEmpty()) return f;
            }
        }
        return "";
    }

    private static String numeroDeMes(String nombre) {
        if (nombre.length() < 3) return null;
        switch (nombre.substring(0, 3)) {
            case "ENE": return "01";
            case "FEB": return "02";
            case "MAR": return "03";
            case "ABR": return "04";
            case "MAY": return "05";
            case "JUN": return "06";
            case "JUL": return "07";
            case "AGO": return "08";
            case "SEP": return "09";
            case "OCT": return "10";
            case "NOV": return "11";
            case "DIC": return "12";
            default: return null;
        }
    }

    private static String formatearFecha(String d, String mes, String a, LocalDate hoy) {
        int dia = Integer.parseInt(d);
        int m = Integer.parseInt(mes);
        int anio = Integer.parseInt(a);
        if (anio < 2000 || anio > 2100) return "";
        LocalDate fecha;
        try {
            fecha = LocalDate.of(anio, m, dia);
        } catch (DateTimeException e) {
            return "";
        }
        if (fecha.isAfter(hoy)) return "";
        return String.format(Locale.US, "%02d/%02d/%04d", dia, m, anio);
    }

    private static void validarCruzado(Resultado r, List<String> lineas) {
        List<Double> montos = new ArrayList<>();
        List<Double> cantidades = new ArrayList<>();
        for (String l : lineas) {
            Matcher m = MONTO.matcher(l);
            while (m.find()) {
                double v = aNumero(m.group(1), m.group(2));
                if (v > 0) montos.add(v);
            }
            Matcher d = NUMERO_GENERAL.matcher(l);
            while (d.find()) {
                if (d.groupCount() >= 2 && d.group(2) != null) {
                    double v = aNumero(d.group(1), d.group(2));
                    if (v > 0) cantidades.add(v);
                }
            }
        }

        if (r.litros > 0 && r.total > 0) {
            for (double p : montos) {
                if (cuadra(r.litros, p, r.total)) {
                    r.precio = p;
                    r.validado = true;
                    return;
                }
            }
            return;
        }

        if (r.litros > 0) {
            for (double p : montos) {
                for (double t : montos) {
                    if (t > p && cuadra(r.litros, p, t)) {
                        r.precio = p;
                        r.total = t;
                        r.validado = true;
                        return;
                    }
                }
            }
        }

        if (r.total > 0) {
            for (double c : cantidades) {
                if (c == r.total) continue;
                for (double p : montos) {
                    if (p != r.total && cuadra(c, p, r.total)) {
                        r.litros = c;
                        r.precio = p;
                        r.validado = true;
                        return;
                    }
                }
            }
        }

        if (r.total == 0) {
            for (double v : montos) r.total = Math.max(r.total, v);
        }
    }

    private static boolean cuadra(double cantidad, double precio, double total) {
        double tolerancia = Math.max(0.02, total * 0.002);
        return Math.abs(cantidad * precio - total) <= tolerancia;
    }

    private static double primerMonto(String s) {
        Matcher m = MONTO.matcher(s);
        return m.find() ? aNumero(m.group(1), m.group(2)) : 0;
    }

    private static double aNumero(String entero, String decimales) {
        return parsear(entero.replace(",", "") + "." + decimales);
    }

    private static double parsear(String s) {
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- METODOS DE RESCATE ---
    public static String buscarComercioCrudo(String texto) {
        if (texto == null) return "";
        String[] lineas = prepararLineas(texto).toArray(new String[0]);
        for (String l : lineas) {
            if (l.length() < 4) continue;
            if (l.matches(".*\\d.*") && !l.matches(".*[A-Z]{3}.*")) continue;
            if (NO_COMERCIO.matcher(l).find()) continue;
            return l;
        }
        return "";
    }

    public static String buscarFechaCrudo(String texto) {
        if (texto == null) return "";
        String[] lineas = prepararLineas(texto).toArray(new String[0]);
        for (String l : lineas) {
            String f = extraerFecha(l, LocalDate.now());
            if (!f.isEmpty()) return f;
        }
        return "";
    }

    public static double buscarLitrosCrudo(String texto) {
        if (texto == null) return 0;
        String[] lineas = prepararLineas(texto).toArray(new String[0]);
        for (String l : lineas) {
            if (l.contains("GAS") || l.contains("LITRO") || l.contains("GALON") || l.contains("DIESEL") || l.contains("SUPER")) {
                Matcher m = NUMERO_GENERAL.matcher(l);
                if (m.find()) {
                    if (m.groupCount() >= 2 && m.group(2) != null) return aNumero(m.group(1), m.group(2));
                    return Double.parseDouble(m.group(1).replace(",", "."));
                }
            }
        }
        double max = 0;
        for (String l : lineas) {
            Matcher m = NUMERO_GENERAL.matcher(l);
            while (m.find()) {
                if (m.groupCount() >= 2 && m.group(2) != null) {
                    double v = aNumero(m.group(1), m.group(2));
                    if (v > 1 && v < 200) max = Math.max(max, v);
                }
            }
        }
        return max;
    }

    public static double buscarTotalCrudo(String texto) {
        if (texto == null) return 0;
        String[] lineas = prepararLineas(texto).toArray(new String[0]);
        double max = 0;
        for (String l : lineas) {
            Matcher m = MONTO.matcher(l);
            while (m.find()) {
                double v = aNumero(m.group(1), m.group(2));
                if (v > max) max = v;
            }
        }
        return max;
    }
}