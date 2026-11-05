package com.autoscann.hn;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class NuevoGastoActivity extends AppCompatActivity {

    private static final String TAG = "NuevoGastoActivity";

    private EditText etNombre, etTotal, etLitros, etFecha, etDescripcion;
    private String categoriaSeleccionada = "Gastos";
    private String imagenUriString = "";
    private String textoOcr = "";
    private DatabaseHelper dbHelper;
    private TextRecognizer recognizer;

    private final ActivityResultLauncher<IntentSenderRequest> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    GmsDocumentScanningResult scanResult =
                            GmsDocumentScanningResult.fromActivityResultIntent(result.getData());

                    if (scanResult != null && scanResult.getPages() != null && !scanResult.getPages().isEmpty()) {
                        Uri imageUri = scanResult.getPages().get(0).getImageUri();
                        imagenUriString = copiarImagenAlmacenamientoInterno(imageUri);
                        procesarImagen(imageUri);
                    }
                } else {
                    Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<String> galeriaLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    imagenUriString = copiarImagenAlmacenamientoInterno(uri);
                    procesarImagen(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nuevo_gasto);

        dbHelper = new DatabaseHelper(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        etNombre = findViewById(R.id.etNombreGasto);
        etTotal = findViewById(R.id.etTotal);
        etLitros = findViewById(R.id.etLitros);
        etFecha = findViewById(R.id.etFecha);
        etDescripcion = findViewById(R.id.etDescripcion);

        Button btnEscaner = findViewById(R.id.btnEscanear);
        Button btnGaleria = findViewById(R.id.btnGaleriaNuevo);
        Button btnLivePreview = findViewById(R.id.btnLivePreviewNuevo);
        Button btnManual = findViewById(R.id.btnManualNuevo);
        ImageView btnAtras = findViewById(R.id.btnAtras);

        // Botón Retroceso
        btnAtras.setOnClickListener(v -> finish());

        // Botones de captura
        btnGaleria.setOnClickListener(v -> galeriaLauncher.launch("image/*"));
        btnEscaner.setOnClickListener(v -> iniciarEscanerDocumentos());
        btnLivePreview.setOnClickListener(v -> {
            startActivity(new Intent(NuevoGastoActivity.this, LivePreviewActivity.class));
        });

        btnManual.setOnClickListener(v -> guardarGasto());

        findViewById(R.id.btnSelectorCategoria).setOnClickListener(v -> {
            String[] opciones = {"Gastos", "Mantenimiento", "Gasolina"};
            new AlertDialog.Builder(this)
                    .setTitle("Seleccionar Categoría")
                    .setItems(opciones, (dialog, which) -> {
                        categoriaSeleccionada = opciones[which];
                        Toast.makeText(this, "Categoría: " + categoriaSeleccionada, Toast.LENGTH_SHORT).show();
                        autocompletarNombre();
                    }).show();
        });
    }

    private String copiarImagenAlmacenamientoInterno(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File archivoDestino = new File(getFilesDir(), "recibo_" + System.currentTimeMillis() + ".jpg");
            OutputStream outputStream = new FileOutputStream(archivoDestino);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();

            return archivoDestino.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void autocompletarNombre() {
        if (etNombre.getText().toString().trim().isEmpty()) {
            etNombre.setText(String.format(Locale.US, "Gasto_%s", categoriaSeleccionada));
        }
    }

    private void iniciarEscanerDocumentos() {
        GmsDocumentScannerOptions options =
                new GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(false)
                        .setPageLimit(1)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build();

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);

        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    IntentSenderRequest request = new IntentSenderRequest.Builder(intentSender).build();
                    scannerLauncher.launch(request);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error al iniciar escáner: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void procesarImagen(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        textoOcr = text.getText();
                        ReceiptParser.Resultado datos = ReceiptParser.analizar(textoOcr);

                        etTotal.setText(datos.total > 0 ? String.valueOf(datos.total) : "");
                        etLitros.setText(datos.litros > 0 ? String.valueOf(datos.litros) : "");
                        etFecha.setText(datos.fecha);
                        etDescripcion.setText(textoOcr.substring(0, Math.min(50, textoOcr.length())) + "...");

                        Toast.makeText(this, "Datos extraídos. Revisa y guarda.", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error OCR", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.e(TAG, "No se pudo cargar la imagen desde la URI", e);
        }
    }

    private void guardarGasto() {
        String nombre = etNombre.getText().toString().trim();
        String totalStr = etTotal.getText().toString().trim();
        String litrosStr = etLitros.getText().toString().trim();
        String fecha = etFecha.getText().toString().trim();
        String descripcion = etDescripcion.getText().toString().trim();

        if (totalStr.isEmpty() || fecha.isEmpty()) {
            Toast.makeText(this, "Total y Fecha son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        double total = Double.parseDouble(totalStr);
        double litros = litrosStr.isEmpty() ? 0 : Double.parseDouble(litrosStr);

        if (!descripcion.isEmpty() && textoOcr.isEmpty()) {
            textoOcr = descripcion;
        }

        long id = dbHelper.insertarGasto(total, litros, fecha, textoOcr, categoriaSeleccionada, nombre, imagenUriString);

        if (id != -1) {
            if (nombre.isEmpty()) {
                dbHelper.getWritableDatabase().execSQL(
                        "UPDATE gastos SET nombre = ? WHERE id = ?",
                        new String[]{"Gasto_" + categoriaSeleccionada + "_#" + id, String.valueOf(id)}
                );
            }

            Toast.makeText(this, "Gasto Registrado correctamente", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.close();
        if (dbHelper != null) dbHelper.close();
    }
}