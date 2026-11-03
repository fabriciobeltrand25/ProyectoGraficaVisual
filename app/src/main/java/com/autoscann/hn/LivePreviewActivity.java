
package com.autoscann.hn;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LivePreviewActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION = 100;

    private PreviewView previewView;
    private TextView txtReconocido;

    private TextRecognizer recognizer;
    private ExecutorService cameraExecutor;

    private boolean procesando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_preview);

        previewView = findViewById(R.id.previewView);
        txtReconocido = findViewById(R.id.txtReconocido);

        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );

        cameraExecutor = Executors.newSingleThreadExecutor();

        verificarPermiso();
    }

    // --------------------------------
    // PERMISO DE CÁMARA
    // --------------------------------

    private void verificarPermiso() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            iniciarCamara();

        } else {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults
        );

        if (requestCode == CAMERA_PERMISSION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            iniciarCamara();

        } else {

            Toast.makeText(this,
                    "Se necesita permiso de cámara",
                    Toast.LENGTH_LONG).show();

            finish();
        }
    }

    // --------------------------------
    // INICIAR CAMERAX
    // --------------------------------

    private void iniciarCamara() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {

            try {

                ProcessCameraProvider cameraProvider =
                        cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .build();

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build();

                imageAnalysis.setAnalyzer(
                        cameraExecutor,
                        this::analizarFotograma
                );

                CameraSelector cameraSelector =
                        CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();

                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Error al iniciar cámara",
                                Toast.LENGTH_SHORT).show()
                );
            }

        }, ContextCompat.getMainExecutor(this));
    }

    // --------------------------------
    // ANALIZAR FOTOGRAMAS OCR
    // --------------------------------

    private void analizarFotograma(ImageProxy imageProxy) {

        if (procesando) {
            imageProxy.close();
            return;
        }

        android.media.Image mediaImage = imageProxy.getImage();

        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        procesando = true;

        InputImage image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        recognizer.process(image)

                .addOnSuccessListener(text -> {

                    String texto = text.getText();

                    if (!texto.trim().isEmpty()) {

                        runOnUiThread(() -> {

                            txtReconocido.setText(
                                    texto
                            );
                        });
                    }

                })

                .addOnFailureListener(e -> {

                    // Error de reconocimiento de este fotograma

                })

                .addOnCompleteListener(task -> {

                    procesando = false;

                    // Liberar el fotograma
                    imageProxy.close();
                });
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (recognizer != null) {
            recognizer.close();
        }
    }
}
