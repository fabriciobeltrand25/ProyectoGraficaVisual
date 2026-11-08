package com.autoscann.hn;

import android.content.ContentValues;
import android.database.Cursor;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class NuevoMantenimientoActivity extends BaseReciboActivity {

    private EditText etDescripcion, etCantidad, etPrecioUnitario;
    private TextInputLayout tilDescripcion;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_nuevo_mantenimiento;
    }

    @Override
    protected String getTipo() {
        return DatabaseHelper.CAT_MANTENIMIENTO;
    }

    @Override
    protected ReceiptParser.Resultado analizarTexto(String texto) {
        return ReceiptParser.analizarMantenimiento(texto);
    }

    @Override
    protected void enlazarCamposEspecificos() {
        etDescripcion = findViewById(R.id.etDescripcion);
        etCantidad = findViewById(R.id.etCantidad);
        etPrecioUnitario = findViewById(R.id.etPrecioUnitario);
        tilDescripcion = findViewById(R.id.tilDescripcion);
    }

    @Override
    protected void aplicarResultado(ReceiptParser.Resultado r) {
        if (!r.descripcion.isEmpty()) {
            etDescripcion.setText(r.descripcion);
        }
        if (r.cantidad > 0) {
            etCantidad.setText(formatoCantidad(r.cantidad));
        }
        if (r.precioUnitario > 0) {
            etPrecioUnitario.setText(String.format(Locale.US, "%.2f", r.precioUnitario));
        }
    }

    @Override
    protected void cargarCamposEspecificos(Cursor c) {
        etDescripcion.setText(texto(c, "descripcion"));
        etCantidad.setText(numeroTexto(c, "cantidad", "%.2f"));
        etPrecioUnitario.setText(numeroTexto(c, "precio_unitario", "%.2f"));
    }

    // VALIDACIÓN CON RETORNO BOOLEAN
    @Override
    protected boolean leerCamposEspecificos(ContentValues v) {
        if (tilDescripcion != null) tilDescripcion.setError(null);

        String descripcion = etDescripcion.getText().toString().trim();
        if (descripcion.isEmpty()) {
            if (tilDescripcion != null) tilDescripcion.setError("Describe el repuesto o servicio");
            return true; // Hay error, se pinta de rojo
        }

        double cantidad = numeroOpcional(etCantidad.getText().toString(), "Cantidad");
        if (cantidad <= 0) cantidad = 1;

        double total = v.getAsDouble("total");
        double precio = numeroOpcional(etPrecioUnitario.getText().toString(), "Precio unitario");
        if (precio <= 0) precio = total / cantidad;

        v.put("descripcion", descripcion);
        v.put("cantidad", cantidad);
        v.put("precio_unitario", precio);
        return false; // Todo bien
    }

    private static String formatoCantidad(double cantidad) {
        return cantidad == Math.floor(cantidad)
                ? String.format(Locale.US, "%.0f", cantidad)
                : String.format(Locale.US, "%.2f", cantidad);
    }

    @Override
    protected String rescatarCamposEspecificos(String textoOcr) {
        StringBuilder sb = new StringBuilder();
        if (etDescripcion != null && etDescripcion.getText().toString().trim().isEmpty()) {
            ReceiptParser.Resultado r = ReceiptParser.analizarMantenimiento(textoOcr);
            if (r.descripcion != null && !r.descripcion.isEmpty()) {
                etDescripcion.setText(r.descripcion);
                sb.append("\n✅ Repuestos detectados: ").append(r.descripcion);
            }
        }
        return sb.toString();
    }
}