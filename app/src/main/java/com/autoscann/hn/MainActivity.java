
package com.autoscann.hn;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.mlkit.documentscanner.GmsDocumentScanning;
import com.google.android.gms.mlkit.documentscanner.GmsDocumentScanner;
import com.google.android.gms.mlkit.documentscanner.GmsDocumentScannerOptions;
import com.google.android.gms.mlkit.documentscanner.GmsDocumentScanningResult;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private Button btnEscanear;
    private Button btnGaleria;
    private Button btnLivePreview;

    private TextView txtTotal;
    private TextView txtLitros;
    private TextView txtFecha;

    private TextRecognizer recognizer;
    private DatabaseHelper databaseHelper;

    private String textoActual = "";

    // Resultado del escáner de documentos
    private final ActivityResultLauncher<IntentSenderRequest>
            scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {

                if (result.getResultCode() ==
                        RESULT_OK && result.getData() != null) {

                    GmsDocumentScanningResult scanResult =
                            GmsDocumentScanningResult.fromActivityResultIntent(
                                    result.getData()
                            );

                    if (scanResult != null &&
                            scanResult.getPages() != null &&
                            !scanResult.getPages().isEmpty()) {

                        Uri imageUri = scanResult.getPages()
                                .get(0).getImageUri();

                        procesarImagen(imageUri);
                    }

                } else {
                    Toast.makeText(this,
                            "Escaneo cancelado",
                            Toast.LENGTH_SHORT).show();
                }
            }
    );

    // Selector de imágenes de la galería
    private final ActivityResultLauncher<String>
            galeriaLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {

                if (uri != null) {
                    procesarImagen(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Inicializar componentes
        btnEscanear = findViewById(R.id.btnEscanear);
        btnGaleria = findViewById(R.id.btnGaleria);
        btnLivePreview = findViewById(R.id.btnLivePreview);

        txtTotal = findViewById(R.id.txtUltimoTotal);
        txtLitros = findViewById(R.id.txtUltimosLitros);
        txtFecha = findViewById(R.id.txtUltimaFecha);

        // Inicializar SQLite
        databaseHelper = new DatabaseHelper(this);

        // Inicializar ML Kit OCR
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );

        // Cargar último gasto
        cargarUltimoGasto();

        // Botón escáner
        btnEscanear.setOnClickListener(v ->
                iniciarEscaner()
        );

        // Botón galería
        btnGaleria.setOnClickListener(v ->
                galeriaLauncher.launch("image/*")
        );

        // Botón cámara en tiempo real
        btnLivePreview.setOnClickListener(v -> {

            Intent intent = new Intent(
                    MainActivity.this,
                    LivePreviewActivity.class
            );

            startActivity(intent);
        });
    }

    // ------------------------------------
    // ESCÁNER DE DOCUMENTOS GOOGLE
    // ------------------------------------

    private void iniciarEscaner() {

        GmsDocumentScannerOptions options =
                new GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(false)
                        .setPageLimit(1)
                        .setResultFormats(
                                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                        )
                        .setScannerMode(
                                GmsDocumentScannerOptions.SCANNER_MODE_FULL
                        )
                        .build();

        GmsDocumentScanner scanner =
                GmsDocumentScanning.getClient(options);

        scanner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {

                    IntentSenderRequest request =
                            new IntentSenderRequest.Builder(intentSender)
                                    .build();

                    scannerLauncher.launch(request);

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(this,
                            "Error al iniciar escáner: " +
                                    e.getMessage(),
                            Toast.LENGTH_LONG).show();

                });
    }

    // ------------------------------------
    // PROCESAMIENTO DE IMAGEN
    // ------------------------------------

    private void procesarImagen(Uri uri) {

        try {

            InputImage image = InputImage.fromFilePath(
                    this, uri
            );

            reconocerTexto(image);

        } catch (IOException e) {

            Toast.makeText(this,
                    "No se pudo cargar la imagen",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------
    // RECONOCIMIENTO OCR
    // ------------------------------------

    private void reconocerTexto(InputImage image) {

        recognizer.process(image)

                .addOnSuccessListener(text -> {

                    textoActual = text.getText();

                    if (textoActual.trim().isEmpty()) {

                        Toast.makeText(this,
                                "No se encontró texto",
                                Toast.LENGTH_SHORT).show();

                        return;
                    }

                    // Extraer información
                    ReceiptParser.Resultado datos =
                            ReceiptParser.analizar(textoActual);

                    // Mostrar confirmación
                    mostrarConfirmacion(datos);

                })

                .addOnFailureListener(e -> {

                    Toast.makeText(this,
                            "Error OCR: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                });
    }

    // ------------------------------------
    // CONFIRMAR DATOS DEL RECIBO
    // ------------------------------------

    private void mostrarConfirmacion(
            ReceiptParser.Resultado datos) {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(35, 20, 35, 10);

        EditText inputTotal = new EditText(this);
        inputTotal.setHint("Total en Lempiras");
        inputTotal.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        inputTotal.setText(
                datos.total > 0 ? String.valueOf(datos.total) : ""
        );

        EditText inputLitros = new EditText(this);
        inputLitros.setHint("Litros de combustible");
        inputLitros.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        inputLitros.setText(
                datos.litros > 0 ? String.valueOf(datos.litros) : ""
        );

        EditText inputFecha = new EditText(this);
        inputFecha.setHint("Fecha DD/MM/YYYY");
        inputFecha.setText(datos.fecha);

        layout.addView(inputTotal);
        layout.addView(inputLitros);
        layout.addView(inputFecha);

        new AlertDialog.Builder(this)
                .setTitle("Confirmar gasto vehicular")
                .setMessage("Revisa los datos detectados por OCR.")
                .setView(layout)
                .setPositiveButton("Guardar", (dialog, which) -> {

                    try {

                        double total = Double.parseDouble(
                                inputTotal.getText().toString().trim()
                        );

                        String litrosTexto = inputLitros.getText()
                                .toString().trim();

                        double litros = litrosTexto.isEmpty()
                                ? 0
                                : Double.parseDouble(litrosTexto);

                        String fecha = inputFecha.getText()
                                .toString().trim();

                        if (total <= 0 || fecha.isEmpty()) {

                            Toast.makeText(this,
                                    "Ingresa un total y una fecha válidos",
                                    Toast.LENGTH_SHORT).show();

                            return;
                        }

                        if (litros < 0) {
                            Toast.makeText(this,
                                    "Los litros no pueden ser negativos",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        long id = databaseHelper.insertarGasto(
                                total,
                                litros,
                                fecha,
                                textoActual
                        );

                        if (id != -1) {

                            Toast.makeText(this,
                                    "Gasto guardado correctamente",
                                    Toast.LENGTH_SHORT).show();

                            cargarUltimoGasto();

                        } else {

                            Toast.makeText(this,
                                    "No se pudo guardar el gasto",
                                    Toast.LENGTH_SHORT).show();
                        }

                    } catch (NumberFormatException e) {

                        Toast.makeText(this,
                                "Verifica los valores numéricos",
                                Toast.LENGTH_SHORT).show();
                    }

                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------
    // MOSTRAR ÚLTIMO GASTO
    // ------------------------------------

    private void cargarUltimoGasto() {

        Cursor cursor = databaseHelper.obtenerUltimoGasto();

        try {

            if (cursor.moveToFirst()) {

                double total = cursor.getDouble(
                        cursor.getColumnIndexOrThrow("total")
                );

                double litros = cursor.getDouble(
                        cursor.getColumnIndexOrThrow("litros")
                );

                String fecha = cursor.getString(
                        cursor.getColumnIndexOrThrow("fecha")
                );

                txtTotal.setText(
                        String.format(java.util.Locale.US,
                                "L. %.2f", total)
                );

                txtLitros.setText(
                        "Combustible: " + litros + " litros"
                );

                txtFecha.setText("Fecha: " + fecha);

            } else {

                txtTotal.setText("L. 0.00");
                txtLitros.setText("Combustible: -- litros");
                txtFecha.setText("Fecha: --");
            }

        } finally {
            cursor.close();
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (recognizer != null) {
            recognizer.close();
        }

        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}
