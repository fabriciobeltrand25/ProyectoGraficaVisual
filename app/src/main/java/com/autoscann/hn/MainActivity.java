package com.autoscann.hn;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView txtTotal, txtLitros, txtFecha;
    private TextView txtTotalRegistros, txtTotalAcumulado;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtTotal = findViewById(R.id.txtUltimoTotal);
        txtLitros = findViewById(R.id.txtUltimosLitros);
        txtFecha = findViewById(R.id.txtUltimaFecha);
        txtTotalRegistros = findViewById(R.id.txtTotalRegistros);
        txtTotalAcumulado = findViewById(R.id.txtTotalAcumulado);

        databaseHelper = new DatabaseHelper(this);

        // BOTÓN FLOTANTE (+): primero se confirma el tipo de factura
        FloatingActionButton fab = findViewById(R.id.fabNuevoGasto);
        fab.setOnClickListener(v -> elegirTipoFactura());

        // ACCIONES RÁPIDAS: van directo a cada pantalla
        findViewById(R.id.btnNuevaGasolina).setOnClickListener(v ->
                startActivity(new Intent(this, NuevoGasolinaActivity.class)));
        findViewById(R.id.btnNuevoMantenimiento).setOnClickListener(v ->
                startActivity(new Intent(this, NuevoMantenimientoActivity.class)));
        View.OnClickListener abrirRegistros = v ->
                startActivity(new Intent(this, RegistrosActivity.class));
        findViewById(R.id.btnVerRegistros).setOnClickListener(abrirRegistros);
        findViewById(R.id.btnVerTodo).setOnClickListener(abrirRegistros);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_inicio);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_inicio) {
                return true;
            } else if (itemId == R.id.nav_registros) {
                startActivity(new Intent(MainActivity.this, RegistrosActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarUltimoGasto();
        cargarResumen();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_inicio);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseHelper.close();
    }

    /** Diálogo de confirmación: ¿Gasolina o Mantenimiento? */
    private void elegirTipoFactura() {
        String[] tipos = {"Gasolina", "Mantenimiento"};
        new AlertDialog.Builder(this)
                .setTitle("¿Qué factura vas a registrar?")
                .setItems(tipos, (dialog, which) -> {
                    Class<?> destino = which == 0
                            ? NuevoGasolinaActivity.class
                            : NuevoMantenimientoActivity.class;
                    startActivity(new Intent(MainActivity.this, destino));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Tarjetas de resumen: cantidad de registros y suma acumulada. */
    private void cargarResumen() {
        double[] resumen = databaseHelper.obtenerResumen();
        txtTotalRegistros.setText(String.format(Locale.US, "%d", (long) resumen[0]));
        txtTotalAcumulado.setText(String.format(Locale.US, "L. %,.2f", resumen[1]));
    }

    private void cargarUltimoGasto() {
        Cursor cursor = databaseHelper.obtenerUltimoGasto();
        try {
            if (cursor.moveToFirst()) {
                double total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"));
                String fecha = textoColumna(cursor, "fecha");
                String categoria = textoColumna(cursor, "categoria");

                txtTotal.setText(String.format(Locale.US, "L. %.2f", total));
                if (DatabaseHelper.CAT_MANTENIMIENTO.equals(categoria)) {
                    String descripcion = textoColumna(cursor, "descripcion");
                    txtLitros.setText("Mantenimiento: " + (descripcion.isEmpty() ? "--" : descripcion));
                } else {
                    double litros = cursor.getDouble(cursor.getColumnIndexOrThrow("litros"));
                    txtLitros.setText(String.format(Locale.US, "Combustible: %.3f litros", litros));
                }
                txtFecha.setText(String.format(Locale.US, "Fecha: %s", fecha));
            } else {
                txtTotal.setText("L. 0.00");
                txtLitros.setText("Combustible: -- litros");
                txtFecha.setText("Fecha: --");
            }
        } finally {
            cursor.close();
        }
    }

    private static String textoColumna(Cursor c, String columna) {
        int i = c.getColumnIndexOrThrow(columna);
        return c.isNull(i) ? "" : c.getString(i);
    }
}
