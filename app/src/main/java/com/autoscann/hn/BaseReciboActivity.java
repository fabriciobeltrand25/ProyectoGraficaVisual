package com.autoscann.hn;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseReciboActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "registro_id";
    public static final String EXTRA_TEXTO_OCR = "texto_ocr_live";

    private static final String TAG = "BaseReciboActivity";

    protected DatabaseHelper dbHelper;
    protected EditText etComercio, etTotal, etFecha;

    protected TextInputLayout tilComercio, tilTotal, tilFecha;

    protected int idEditando = -1;
    protected String textoOcr = "";

    private TextRecognizer recognizer;
    private LinearLayout panelEstado;
    private TextView txtEstado;
    private ProgressBar progressEstado;
    private ImageView ivPreview;
    private View layoutSinFoto;
    private final ExecutorService trabajo = Executors.newSingleThreadExecutor();
    private String imagenActual = "";
    private String imagenOriginal = "";

    protected abstract int getLayoutId();

    protected abstract String getTipo();

    protected abstract ReceiptParser.Resultado analizarTexto(String texto);

    protected abstract void aplicarResultado(ReceiptParser.Resultado r);

    protected abstract void enlazarCamposEspecificos();

    protected abstract void cargarCamposEspecificos(Cursor c);

    // CAMBIO AQUÍ: Ahora devuelve boolean (true = hay error)
    protected abstract boolean leerCamposEspecificos(ContentValues v);

    protected abstract String rescatarCamposEspecificos(String textoOcr);

    private final ActivityResultLauncher<String> galeriaLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    reemplazarImagen(copiarImagen(uri));
                    escanearGaleria(uri, imagenActual);
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    GmsDocumentScanningResult scan =
                            GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                    if (scan != null && scan.getPages() != null && !scan.getPages().isEmpty()) {
                        Uri uri = scan.getPages().get(0).getImageUri();
                        reemplazarImagen(copiarImagen(uri));
                        procesarImagen(uri);
                    }
                } else {
                    Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> liveLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String texto = result.getData().getStringExtra(EXTRA_TEXTO_OCR);
                    if (texto != null && !texto.trim().isEmpty()) {
                        aplicarTextoOcr(texto);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());

        dbHelper = new DatabaseHelper(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        TextView txtTipo = findViewById(R.id.txtTipoRecibo);
        if (txtTipo != null) {
            txtTipo.setText(getString(R.string.tipo_recibo_formato, getTipo()));
        }

        etComercio = findViewById(R.id.etComercio);
        etTotal = findViewById(R.id.etTotal);
        etFecha = findViewById(R.id.etFecha);

        tilComercio = findViewById(R.id.tilComercio);
        tilTotal = findViewById(R.id.tilTotal);
        tilFecha = findViewById(R.id.tilFecha);

        panelEstado = findViewById(R.id.panelEstado);
        txtEstado = findViewById(R.id.txtEstado);
        progressEstado = findViewById(R.id.progressEstado);
        ivPreview = findViewById(R.id.ivPreview);
        layoutSinFoto = findViewById(R.id.layoutSinFoto);

        enlazarCamposEspecificos();

        findViewById(R.id.btnAtras).setOnClickListener(v -> cancelar());
        findViewById(R.id.btnEscanear).setOnClickListener(v -> iniciarEscaner());
        findViewById(R.id.btnGaleria).setOnClickListener(v -> galeriaLauncher.launch("image/*"));
        findViewById(R.id.btnLive).setOnClickListener(v ->
                liveLauncher.launch(new Intent(this, LivePreviewActivity.class)));

        View btnRecargar = findViewById(R.id.btnRecargar);
        if (btnRecargar != null) {
            btnRecargar.setOnClickListener(v -> recargarCamposOcr());
        }

        findViewById(R.id.btnVerOcr).setOnClickListener(v -> mostrarTextoOcr());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> cancelar());
        findViewById(R.id.btnGuardar).setOnClickListener(v -> guardar());

        // Aquí dejamos tu máscara de fecha intacta tal como la tenías
        etFecha.addTextChangedListener(new MascaraFecha());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelar();
            }
        });

        idEditando = getIntent().getIntExtra(EXTRA_ID, -1);
        if (idEditando != -1) {
            cargarRegistro(idEditando);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.close();
        if (dbHelper != null) dbHelper.close();
        trabajo.shutdown();
    }

    private void iniciarEscaner() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> scannerLauncher.launch(
                        new IntentSenderRequest.Builder(intentSender).build()))
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Error al iniciar escáner: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void procesarImagen(Uri uri) {
        mostrarEstado("Leyendo el texto de la imagen…", true);
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            recognizer.process(image)
                    .addOnSuccessListener(this::mostrarTextoLeido)
                    .addOnFailureListener(e -> mostrarErrorLectura());
        } catch (Exception e) {
            Log.e(TAG, "No se pudo cargar la imagen desde la URI", e);
            mostrarErrorLectura();
        }
    }

    private void escanearGaleria(Uri uri, String rutaCopia) {
        mostrarEstado("Escaneando la foto de la galería…", true);
        try {
            recognizer.process(InputImage.fromFilePath(this, uri))
                    .addOnSuccessListener(primera -> trabajo.execute(() -> {
                        String mejorada = mejorarRecorte(rutaCopia, primera);
                        runOnUiThread(() -> releerMejorada(primera, mejorada));
                    }))
                    .addOnFailureListener(e -> mostrarErrorLectura());
        } catch (Exception e) {
            Log.e(TAG, "No se pudo leer la foto de la galería", e);
            mostrarErrorLectura();
        }
    }

    private void releerMejorada(Text primera, String mejorada) {
        if (mejorada == null) {
            mostrarTextoLeido(primera);
            return;
        }
        mostrarEstado("Releyendo la foto mejorada…", true);
        try {
            recognizer.process(InputImage.fromFilePath(this, Uri.fromFile(new File(mejorada))))
                    .addOnSuccessListener(segunda -> {
                        ImagenUtil.borrar(mejorada);
                        mostrarTextoLeido(contarLineas(segunda) >= contarLineas(primera) ? segunda : primera);
                    })
                    .addOnFailureListener(e -> {
                        ImagenUtil.borrar(mejorada);
                        mostrarTextoLeido(primera);
                    });
        } catch (Exception e) {
            ImagenUtil.borrar(mejorada);
            mostrarTextoLeido(primera);
        }
    }

    private String mejorarRecorte(String rutaCopia, Text text) {
        if (rutaCopia == null || rutaCopia.isEmpty()) return null;

        Rect area = null;
        for (Text.TextBlock bloque : text.getTextBlocks()) {
            for (Text.Line linea : bloque.getLines()) {
                Rect caja = linea.getBoundingBox();
                if (caja == null) continue;
                if (area == null) area = new Rect(caja);
                else area.union(caja);
            }
        }
        if (area == null) return null;

        try {
            BitmapFactory.Options medidas = new BitmapFactory.Options();
            medidas.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(rutaCopia, medidas);
            int anchoOrig = medidas.outWidth;
            int altoOrig = medidas.outHeight;
            if (anchoOrig <= 0 || altoOrig <= 0) return null;

            int rotacion = leerRotacionExif(rutaCopia);
            boolean girada = rotacion == 90 || rotacion == 270;
            int anchoEsperado = girada ? altoOrig : anchoOrig;

            int muestra = 1;
            while (Math.max(anchoOrig, altoOrig) / (muestra * 2) >= 3000) muestra *= 2;
            BitmapFactory.Options opciones = new BitmapFactory.Options();
            opciones.inSampleSize = muestra;
            Bitmap decodificada = BitmapFactory.decodeFile(rutaCopia, opciones);
            if (decodificada == null) return null;

            Bitmap original = decodificada;
            if (rotacion != 0) {
                Matrix giro = new Matrix();
                giro.postRotate(rotacion);
                original = Bitmap.createBitmap(decodificada, 0, 0,
                        decodificada.getWidth(), decodificada.getHeight(), giro, true);
            }

            float escala = (float) original.getWidth() / anchoEsperado;
            int margen = (int) (0.03f * Math.max(original.getWidth(), original.getHeight()));
            int izq = Math.max(0, (int) (area.left * escala) - margen);
            int arr = Math.max(0, (int) (area.top * escala) - margen);
            int der = Math.min(original.getWidth(), (int) (area.right * escala) + margen);
            int aba = Math.min(original.getHeight(), (int) (area.bottom * escala) + margen);
            if (der - izq < 200 || aba - arr < 200) return null;

            Bitmap recorte = Bitmap.createBitmap(original, izq, arr, der - izq, aba - arr);

            float c = 1.5f;
            float t = (1f - c) * 128f;
            ColorMatrix contraste = new ColorMatrix(new float[]{
                    c, 0, 0, 0, t,
                    0, c, 0, 0, t,
                    0, 0, c, 0, t,
                    0, 0, 0, 1, 0});
            ColorMatrix gris = new ColorMatrix();
            gris.setSaturation(0);
            contraste.postConcat(gris);

            Bitmap mejorada = Bitmap.createBitmap(recorte.getWidth(), recorte.getHeight(), Bitmap.Config.ARGB_8888);
            Paint pintura = new Paint();
            pintura.setColorFilter(new ColorMatrixColorFilter(contraste));
            new Canvas(mejorada).drawBitmap(recorte, 0, 0, pintura);

            File destino = new File(getCacheDir(), "ocr_mejorada_" + System.currentTimeMillis() + ".jpg");
            try (OutputStream out = new FileOutputStream(destino)) {
                mejorada.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
            return destino.getAbsolutePath();
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "No se pudo mejorar la foto", e);
            return null;
        }
    }

    private static int leerRotacionExif(String ruta) {
        try {
            int orientacion = new ExifInterface(ruta).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientacion) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    private void mostrarTextoLeido(Text text) {
        mostrarEstado("Texto leído: " + contarLineas(text) + " líneas", false);
        try {
            aplicarTextoOcr(OcrUtil.textoPorFilas(text));
        } catch (Exception e) {
            Toast.makeText(this, "Error procesando texto OCR", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarErrorLectura() {
        mostrarEstado("No se pudo leer la imagen. Prueba con otra foto.", false);
    }

    private static int contarLineas(Text text) {
        int n = 0;
        for (Text.TextBlock bloque : text.getTextBlocks()) {
            n += bloque.getLines().size();
        }
        return n;
    }

    private void mostrarEstado(String mensaje, boolean cargando) {
        panelEstado.setVisibility(View.VISIBLE);
        txtEstado.setText(mensaje);
        progressEstado.setVisibility(cargando ? View.VISIBLE : View.GONE);
    }

    private void aplicarTextoOcr(String texto) {
        textoOcr = texto;
        try {
            ReceiptParser.Resultado r = analizarTexto(texto);

            if (r.total > 0) etTotal.setText(String.format(Locale.US, "%.2f", r.total));
            if (!r.fecha.isEmpty()) etFecha.setText(r.fecha);
            if (!r.comercio.isEmpty()) etComercio.setText(r.comercio);
            aplicarResultado(r);

            String mensaje = r.validado
                    ? "Datos verificados con la validación cruzada"
                    : "Revisa los datos antes de guardar.";
            if (r.fecha.isEmpty()) {
                mensaje += "\nNo se leyó la fecha: escríbela como DD/MM/AAAA.";
            }
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "El texto no se pudo procesar del todo. Usa 'Recargar'.", Toast.LENGTH_LONG).show();
        }
    }

    private String copiarImagen(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            File destino = new File(getFilesDir(), "recibo_" + System.currentTimeMillis() + ".jpg");
            try (OutputStream out = new FileOutputStream(destino)) {
                byte[] buffer = new byte[4096];
                int leidos;
                while ((leidos = in.read(buffer)) > 0) {
                    out.write(buffer, 0, leidos);
                }
            }
            return destino.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo copiar la imagen", e);
            return "";
        }
    }

    private void reemplazarImagen(String nueva) {
        descartarImagenNoGuardada();
        imagenActual = nueva;
        actualizarPreview();
    }

    private void descartarImagenNoGuardada() {
        if (!imagenActual.isEmpty() && !imagenActual.equals(imagenOriginal)) {
            ImagenUtil.borrar(imagenActual);
        }
        imagenActual = imagenOriginal;
    }

    private void actualizarPreview() {
        Bitmap foto = ImagenUtil.decodificarEscalada(imagenActual, 900);
        if (foto != null) {
            ivPreview.setImageBitmap(foto);
            ivPreview.setVisibility(View.VISIBLE);
            layoutSinFoto.setVisibility(View.GONE);
        } else {
            ivPreview.setImageDrawable(null);
            ivPreview.setVisibility(View.GONE);
            layoutSinFoto.setVisibility(View.VISIBLE);
        }
    }

    private void cargarRegistro(int id) {
        Cursor c = dbHelper.obtenerRegistroPorId(id);
        try {
            if (!c.moveToFirst()) {
                Toast.makeText(this, "Este registro ya no existe", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            etComercio.setText(texto(c, "nombre"));
            etTotal.setText(numeroTexto(c, "total", "%.2f"));
            etFecha.setText(texto(c, "fecha"));
            textoOcr = texto(c, "texto_ocr");
            imagenOriginal = texto(c, "imagen_uri");
            imagenActual = imagenOriginal;
            actualizarPreview();
            cargarCamposEspecificos(c);
        } finally {
            c.close();
        }
    }

    // --- LÓGICA DE GUARDADO CON VALIDACIÓN EN TODOS LOS CAMPOS ---
    private void guardar() {
        if (tilComercio != null) tilComercio.setError(null);
        if (tilTotal != null) tilTotal.setError(null);
        if (tilFecha != null) tilFecha.setError(null);

        ContentValues v = new ContentValues();
        boolean hayError = false;

        try {
            double total = numero(etTotal.getText().toString(), "Total en L.");
            if (total <= 0) {
                if (tilTotal != null) tilTotal.setError("El total debe ser mayor a cero");
                hayError = true;
            } else {
                v.put("total", total);
            }
        } catch (IllegalArgumentException e) {
            if (tilTotal != null) tilTotal.setError(e.getMessage());
            hayError = true;
        }

        String fecha = ReceiptParser.validarFechaManual(etFecha.getText().toString(), LocalDate.now());
        if (fecha.isEmpty()) {
            if (tilFecha != null) tilFecha.setError("Fecha inválida o futura (usa DD/MM/AAAA)");
            hayError = true;
        } else {
            v.put("fecha", fecha);
        }

        v.put("nombre", etComercio.getText().toString().trim());

        // AQUÍ EVALUAMOS SI LOS CAMPOS ESPECÍFICOS TIENEN ERROR
        if (leerCamposEspecificos(v)) {
            hayError = true;
        }

        if (hayError) {
            return; // Si hay cualquier error en cualquier campo, frena y pinta los rojos
        }

        v.put("categoria", getTipo());
        v.put("texto_ocr", textoOcr);
        v.put("imagen_uri", imagenActual);

        if (idEditando == -1) {
            if (dbHelper.insertarRegistro(v) == -1) {
                Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (dbHelper.actualizarRegistro(idEditando, v) <= 0) {
                Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!imagenOriginal.isEmpty() && !imagenOriginal.equals(imagenActual)) {
                ImagenUtil.borrar(imagenOriginal);
            }
        }

        Toast.makeText(this, "Registro guardado", Toast.LENGTH_LONG).show();
        finish();
    }

    private void mostrarTextoOcr() {
        String texto = textoOcr.isEmpty()
                ? "Aún no hay texto leído. Escanea, carga una imagen o usa Live."
                : textoOcr;

        boolean algoSeRelleno = false;
        StringBuilder mensajeExtra = new StringBuilder();

        if (!textoOcr.isEmpty()) {
            if (etComercio.getText().toString().trim().isEmpty()) {
                String comercioExtra = ReceiptParser.buscarComercioCrudo(textoOcr);
                if (!comercioExtra.isEmpty()) {
                    etComercio.setText(comercioExtra);
                    algoSeRelleno = true;
                    mensajeExtra.append("\n✅ Comercio detectado: ").append(comercioExtra);
                }
            }

            if (etFecha.getText().toString().trim().isEmpty()) {
                String fechaExtra = ReceiptParser.buscarFechaCrudo(textoOcr);
                if (!fechaExtra.isEmpty()) {
                    etFecha.setText(fechaExtra);
                    algoSeRelleno = true;
                    mensajeExtra.append("\n✅ Fecha detectada: ").append(fechaExtra);
                }
            }

            String totalTexto = etTotal.getText().toString().trim();
            if (totalTexto.isEmpty() || totalTexto.equals("0") || totalTexto.equals("0.00")) {
                double totalExtra = ReceiptParser.buscarTotalCrudo(textoOcr);
                if (totalExtra > 0) {
                    etTotal.setText(String.format(Locale.US, "%.2f", totalExtra));
                    algoSeRelleno = true;
                    mensajeExtra.append("\n✅ Total detectado: L. ")
                            .append(String.format(Locale.US, "%.2f", totalExtra));
                }
            }

            String extraEspecifico = rescatarCamposEspecificos(textoOcr);
            if (!extraEspecifico.isEmpty()) {
                algoSeRelleno = true;
                mensajeExtra.append(extraEspecifico);
            }
        }

        if (algoSeRelleno) {
            texto = "⚠️ [ANÁLISIS EXTRA - SEGUNDA OPORTUNIDAD]\nSe encontraron datos que faltaban:"
                    + mensajeExtra + "\n\n--- TEXTO COMPLETO ---\n\n" + texto;
        }

        TextView vista = new TextView(this);
        vista.setText(texto);
        vista.setTextIsSelectable(true);
        vista.setTextSize(13);
        int margen = (int) (16 * getResources().getDisplayMetrics().density);
        vista.setPadding(margen, margen, margen, margen);

        ScrollView desplazable = new ScrollView(this);
        desplazable.addView(vista);

        final String textoCopiar = texto;
        new AlertDialog.Builder(this)
                .setTitle("Texto leído por el OCR")
                .setView(desplazable)
                .setPositiveButton("Copiar", (dialog, which) -> {
                    ClipboardManager portapapeles = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (portapapeles != null) {
                        portapapeles.setPrimaryClip(ClipData.newPlainText("Texto OCR", textoCopiar));
                    }
                    Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void recargarCamposOcr() {
        if (textoOcr.isEmpty()) {
            Toast.makeText(this, "No hay texto para recargar. Escanea una imagen primero.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ReceiptParser.Resultado r = analizarTexto(textoOcr);
            if (r.total > 0) etTotal.setText(String.format(Locale.US, "%.2f", r.total));
            if (!r.fecha.isEmpty()) etFecha.setText(r.fecha);
            if (!r.comercio.isEmpty()) etComercio.setText(r.comercio);
            aplicarResultado(r);

            if (etComercio.getText().toString().trim().isEmpty()) {
                etComercio.setText(ReceiptParser.buscarComercioCrudo(textoOcr));
            }
            if (etFecha.getText().toString().trim().isEmpty()) {
                etFecha.setText(ReceiptParser.buscarFechaCrudo(textoOcr));
            }
            if (etTotal.getText().toString().trim().isEmpty()) {
                double t = ReceiptParser.buscarTotalCrudo(textoOcr);
                if (t > 0) etTotal.setText(String.format(Locale.US, "%.2f", t));
            }
            rescatarCamposEspecificos(textoOcr);

            Toast.makeText(this, "Campos recargados con éxito", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al recargar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void cancelar() {
        descartarImagenNoGuardada();
        finish();
    }

    protected static String texto(Cursor c, String columna) {
        int i = c.getColumnIndexOrThrow(columna);
        return c.isNull(i) ? "" : c.getString(i);
    }

    protected static String numeroTexto(Cursor c, String columna, String formato) {
        int i = c.getColumnIndexOrThrow(columna);
        return c.isNull(i) ? "" : String.format(Locale.US, formato, c.getDouble(i));
    }

    protected static double numero(String entrada, String campo) {
        String t = entrada == null ? "" : entrada.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Este campo es obligatorio");
        if (t.contains(".") && t.contains(",")) t = t.replace(",", "");
        else t = t.replace(",", ".");
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor no válido");
        }
    }

    protected static double numeroOpcional(String entrada, String campo) {
        return entrada == null || entrada.trim().isEmpty() ? 0 : numero(entrada, campo);
    }

    // --- TU MÁSCARA DE FECHA ORIGINAL ---
    // --- MÁSCARA DE FECHA MEJORADA (Acepta escribir / manualmente) ---
    private static class MascaraFecha implements TextWatcher {
        private boolean formateando = false;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (formateando) return;

            String textoActual = s.toString();

            // 1. Extraemos solo los números (ignoramos si el usuario tecleó /)
            String digitos = textoActual.replaceAll("\\D", "");

            // 2. Límite de 8 dígitos (DDMMAAAA)
            if (digitos.length() > 8) {
                digitos = digitos.substring(0, 8);
            }

            // 3. Formateamos insertando las barras
            StringBuilder formateado = new StringBuilder();
            for (int i = 0; i < digitos.length(); i++) {
                if (i == 2 || i == 4) formateado.append('/');
                formateado.append(digitos.charAt(i));
            }

            // 4. Si el texto que se va a mostrar es diferente al que está en pantalla, lo actualizamos
            if (!formateado.toString().equals(textoActual)) {
                formateando = true;
                s.replace(0, s.length(), formateado.toString());
                formateando = false;
            }
        }
    }

    
}