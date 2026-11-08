package com.autoscann.hn;

import android.content.ContentValues;
import android.database.Cursor;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/** Registro de un ticket o factura de gasolina (litros o galones, total en Lempiras). */
public class NuevoGasolinaActivity extends BaseReciboActivity {

    private TextInputLayout tilLitros;
    private EditText etLitros;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_nuevo_gasolina;
    }

    @Override
    protected String getTipo() {
        return DatabaseHelper.CAT_GASOLINA;
    }

    @Override
    protected ReceiptParser.Resultado analizarTexto(String texto) {
        return ReceiptParser.analizar(texto);
    }

    @Override
    protected void enlazarCamposEspecificos() {
        tilLitros = findViewById(R.id.tilLitros);
        etLitros = findViewById(R.id.etLitros);
    }

    @Override
    protected void aplicarResultado(ReceiptParser.Resultado r) {
        if (r.litros > 0) {
            etLitros.setText(String.format(Locale.US, "%.3f", r.litros));
        }
        if (r.unidad != null) {
            tilLitros.setHint(r.unidad.equals("GAL") ? "Galones de combustible" : "Litros de combustible");
        }
    }

    @Override
    protected void cargarCamposEspecificos(Cursor c) {
        etLitros.setText(numeroTexto(c, "litros", "%.3f"));
    }

    @Override
    protected void leerCamposEspecificos(ContentValues v) {
        double litros = numero(etLitros.getText().toString(), "Litros de combustible");
        if (litros <= 0) {
            throw new IllegalArgumentException("Los litros deben ser mayores que cero");
        }
        v.put("litros", litros);
    }

    @Override
    protected String rescatarCamposEspecificos(String textoOcr) {
        StringBuilder sb = new StringBuilder();
        EditText etLitros = findViewById(R.id.etLitros);

        // Si el campo de litros está vacío, intentamos rescatarlo
        if (etLitros != null && etLitros.getText().toString().trim().isEmpty()) {
            double litrosExtra = ReceiptParser.buscarLitrosCrudo(textoOcr);
            if (litrosExtra > 0) {
                etLitros.setText(String.format(Locale.US, "%.2f", litrosExtra));
                sb.append("\n✅ Litros detectados: ").append(String.format(Locale.US, "%.2f", litrosExtra));
            }
        }
        return sb.toString();
    }
}