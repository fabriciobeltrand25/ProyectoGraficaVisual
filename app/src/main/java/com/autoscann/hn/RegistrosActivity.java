package com.autoscann.hn;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

public class RegistrosActivity extends AppCompatActivity {

    private RecyclerView rvRegistros;
    private EditText etBuscador;
    private TextView txtVacio;
    private DatabaseHelper dbHelper;
    private ReciboAdapter adapter;
    /** Categoría por la que se filtra; null = todas. */
    private String filtroCategoriaActual = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registros);

        dbHelper = new DatabaseHelper(this);
        rvRegistros = findViewById(R.id.rvRegistrosRecibos);
        etBuscador = findViewById(R.id.etBuscadorRecibos);
        txtVacio = findViewById(R.id.txtVacio);
        ChipGroup chipGroup = findViewById(R.id.chipGroupCategorias);

        findViewById(R.id.btnAtrasRegistros).setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        rvRegistros.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReciboAdapter(this, new ReciboAdapter.Acciones() {
            @Override
            public void onVer(int id) {
                previsualizarImagen(id);
            }

            @Override
            public void onEditar(int id) {
                abrirEditor(id);
            }

            @Override
            public void onEliminar(int id) {
                confirmarEliminar(id);
            }
        });
        rvRegistros.setAdapter(adapter);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipGasolina)) {
                filtroCategoriaActual = DatabaseHelper.CAT_GASOLINA;
            } else if (checkedIds.contains(R.id.chipMantenimiento)) {
                filtroCategoriaActual = DatabaseHelper.CAT_MANTENIMIENTO;
            } else {
                filtroCategoriaActual = null;
            }
            cargarDatos();
        });

        etBuscador.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cargarDatos();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar: al volver de editar o de crear puede haber cambios
        cargarDatos();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adapter.swapCursor(null); // cierra el último cursor
        dbHelper.close();
    }

    private void cargarDatos() {
        String texto = etBuscador == null ? "" : etBuscador.getText().toString().trim();
        adapter.swapCursor(dbHelper.buscarRegistros(texto, filtroCategoriaActual));
        txtVacio.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------
    // Acciones de cada tarjeta: Ver, Editar, Eliminar
    // ---------------------------------------------------------------

    private void abrirEditor(int id) {
        String categoria = leerColumna(id, "categoria");
        Class<?> destino = DatabaseHelper.CAT_MANTENIMIENTO.equals(categoria)
                ? NuevoMantenimientoActivity.class
                : NuevoGasolinaActivity.class;

        Intent intent = new Intent(this, destino);
        intent.putExtra(BaseReciboActivity.EXTRA_ID, id);
        startActivity(intent);
    }

    private void previsualizarImagen(int id) {
        Bitmap bitmap = ImagenUtil.decodificarEscalada(leerColumna(id, "imagen_uri"), 1600);
        if (bitmap == null) {
            Toast.makeText(this, "Este registro no tiene una imagen disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView vista = new ImageView(this);
        vista.setImageBitmap(bitmap);
        vista.setAdjustViewBounds(true);
        vista.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int margen = (int) (12 * getResources().getDisplayMetrics().density);
        vista.setPadding(margen, margen, margen, margen);
        vista.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Recibo #" + id)
                .setView(vista)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void confirmarEliminar(int id) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar registro #" + id)
                .setMessage("Se borrará el registro y su foto. ¿Continuar?")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    String rutaImagen = leerColumna(id, "imagen_uri");
                    if (dbHelper.eliminarRegistro(id)) {
                        ImagenUtil.borrar(rutaImagen);
                        Toast.makeText(this, "Registro eliminado", Toast.LENGTH_SHORT).show();
                        cargarDatos();
                    } else {
                        Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Valor de una columna de un registro; "" si no existe o es NULL. */
    private String leerColumna(int id, String columna) {
        Cursor c = dbHelper.obtenerRegistroPorId(id);
        try {
            if (!c.moveToFirst()) return "";
            int i = c.getColumnIndexOrThrow(columna);
            return c.isNull(i) ? "" : c.getString(i);
        } finally {
            c.close();
        }
    }
}
