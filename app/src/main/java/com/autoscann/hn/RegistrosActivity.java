package com.autoscann.hn;

import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

public class RegistrosActivity extends AppCompatActivity {

    private RecyclerView rvRegistros;
    private EditText etBuscador;
    private DatabaseHelper dbHelper;
    private ReciboAdapter adapter;
    private String filtroCategoriaActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registros);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        dbHelper = new DatabaseHelper(this);
        rvRegistros = findViewById(R.id.rvRegistrosRecibos);
        etBuscador = findViewById(R.id.etBuscadorRecibos);
        ChipGroup chipGroup = findViewById(R.id.chipGroupCategorias);

        rvRegistros.setLayoutManager(new LinearLayoutManager(this));

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipGastos)) {
                filtroCategoriaActual = "Gastos";
            } else if (checkedIds.contains(R.id.chipMantenimiento)) {
                filtroCategoriaActual = "Mantenimiento";
            } else if (checkedIds.contains(R.id.chipGasolina)) {
                filtroCategoriaActual = "Gasolina";
            } else {
                filtroCategoriaActual = null;
            }

            cargarDatos(etBuscador.getText().toString());
        });

        // Cargar datos iniciales
        cargarDatos("");

        // Configurar Buscador
        etBuscador.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cargarDatos(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void cargarDatos(String filtroTexto) {
        String query = "SELECT * FROM gastos WHERE 1=1";
        java.util.List<String> args = new java.util.ArrayList<>();

        if (!filtroTexto.isEmpty()) {
            query += " AND (nombre LIKE ? OR id LIKE ?)";
            args.add("%" + filtroTexto + "%");
            args.add("%" + filtroTexto + "%");
        }

        if (filtroCategoriaActual != null && !filtroCategoriaActual.isEmpty()) {
            query += " AND categoria = ?";
            args.add(filtroCategoriaActual);
        }

        query += " ORDER BY id DESC";

        Cursor cursor = dbHelper.getReadableDatabase().rawQuery(query, args.toArray(new String[0]));

        // Actualizamos el adaptador
        if (adapter == null) {
            adapter = new ReciboAdapter(this, cursor, this::mostrarOpcionesRegistro);
            rvRegistros.setAdapter(adapter);
        } else {
            adapter.swapCursor(cursor);
        }
    }

    private void mostrarOpcionesRegistro(int id, String fecha, double total, String textoOcr) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opciones para el Recibo #" + id);
        builder.setItems(new CharSequence[]{"Editar", "Eliminar", "Cancelar"}, (dialog, which) -> {
            if (which == 0) {
                // Lógica para Editar (puedes abrir un diálogo para modificar total/litros)
                Toast.makeText(this, "Función Editar en desarrollo", Toast.LENGTH_SHORT).show();
            } else if (which == 1) {
                // Lógica para Eliminar
                if (dbHelper.eliminarGasto(id)) {
                    Toast.makeText(this, "Recibo eliminado", Toast.LENGTH_SHORT).show();
                    cargarDatos(etBuscador.getText().toString()); // Refrescar lista
                } else {
                    Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.show();
    }



}