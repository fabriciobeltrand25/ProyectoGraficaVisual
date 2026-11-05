package com.autoscann.hn;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView txtTotal, txtLitros, txtFecha;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar componentes del Dashboard (Solo los que existen ahora)
        txtTotal = findViewById(R.id.txtUltimoTotal);
        txtLitros = findViewById(R.id.txtUltimosLitros);
        txtFecha = findViewById(R.id.txtUltimaFecha);

        databaseHelper = new DatabaseHelper(this);

        // BOTÓN FLOTANTE (+) PARA NUEVO GASTO
        FloatingActionButton fab = findViewById(R.id.fabNuevoGasto);
        fab.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, NuevoGastoActivity.class));
        });

        // Configurar Bottom Navigation
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
        cargarMetricasMes();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_inicio);
        }
    }

    private void cargarMetricasMes() {
        double[] porcentajes = databaseHelper.obtenerEstadisticasMes();
        TextView txtPorcentajeCombustible = findViewById(R.id.txtPorcentajeCombustible);
        TextView txtPorcentajeMantenimiento = findViewById(R.id.txtPorcentajeMantenimiento);

        if (txtPorcentajeCombustible != null && txtPorcentajeMantenimiento != null) {
            txtPorcentajeCombustible.setText(String.format(Locale.US, " Combustible: %.0f%%", porcentajes[0]));
            txtPorcentajeMantenimiento.setText(String.format(Locale.US, " Mantenimiento: %.0f%%", porcentajes[1]));
        }
    }

    private void cargarUltimoGasto() {
        Cursor cursor = databaseHelper.obtenerUltimoGasto();
        try {
            if (cursor.moveToFirst()) {
                double total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"));
                double litros = cursor.getDouble(cursor.getColumnIndexOrThrow("litros"));
                String fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha"));

                txtTotal.setText(String.format(Locale.US, "L. %.2f", total));
                txtLitros.setText(String.format(Locale.US, "Combustible: %s litros", litros));
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
}