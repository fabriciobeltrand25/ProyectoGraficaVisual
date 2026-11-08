package com.autoscann.hn;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.Locale;

public class ReciboAdapter extends RecyclerView.Adapter<ReciboAdapter.ViewHolder> {

    /** Acciones que el usuario puede hacer sobre un registro. */
    public interface Acciones {
        void onVer(int id);

        void onEditar(int id);

        void onEliminar(int id);
    }

    private Cursor cursor;
    private final Context context;
    private final Acciones acciones;

    public ReciboAdapter(Context context, Acciones acciones) {
        this.context = context;
        this.acciones = acciones;
    }

    /** Cambia los datos mostrados. El cursor anterior se cierra. */
    public void swapCursor(Cursor nuevo) {
        if (cursor != null && cursor != nuevo) {
            cursor.close();
        }
        cursor = nuevo;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_card_recibo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (cursor == null || !cursor.moveToPosition(position)) {
            return;
        }

        int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        String nombre = leerTexto("nombre");
        String categoria = leerTexto("categoria");
        String fecha = leerTexto("fecha");
        String imagenUri = leerTexto("imagen_uri");
        double total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"));

        boolean esMantenimiento = DatabaseHelper.CAT_MANTENIMIENTO.equals(categoria);

        holder.tvTituloRecibo.setText(!nombre.isEmpty() ? nombre : (!categoria.isEmpty() ? categoria : "Registro"));
        holder.tvIdRecibo.setText("#" + id);
        holder.tvCategoria.setText(categoria.isEmpty() ? "Registro" : categoria);
        holder.tvCategoria.setTextColor(context.getColor(
                esMantenimiento ? R.color.brand_orange : R.color.brand_green));
        holder.tvFechaRecibo.setText(String.format(Locale.US, "Fecha: %s   ·   L. %.2f", fecha, total));

        Bitmap miniatura = ImagenUtil.decodificarEscalada(imagenUri, 240);
        if (miniatura != null) {
            holder.imgRecibo.setImageBitmap(miniatura);
        } else {
            holder.imgRecibo.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Tocar la tarjeta abre la previsualización; los botones tienen su propia acción
        holder.itemView.setOnClickListener(v -> acciones.onVer(id));
        holder.btnVer.setOnClickListener(v -> acciones.onVer(id));
        holder.btnEditar.setOnClickListener(v -> acciones.onEditar(id));
        holder.btnEliminar.setOnClickListener(v -> acciones.onEliminar(id));
    }

    @Override
    public int getItemCount() {
        return cursor == null ? 0 : cursor.getCount();
    }

    /** Texto de una columna del registro actual; "" si es NULL. */
    private String leerTexto(String columna) {
        int i = cursor.getColumnIndexOrThrow(columna);
        return cursor.isNull(i) ? "" : cursor.getString(i);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView imgRecibo;
        TextView tvTituloRecibo, tvIdRecibo, tvCategoria, tvFechaRecibo;
        MaterialButton btnVer, btnEditar, btnEliminar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgRecibo = itemView.findViewById(R.id.imgRecibo);
            tvTituloRecibo = itemView.findViewById(R.id.tvTituloRecibo);
            tvIdRecibo = itemView.findViewById(R.id.tvIdRecibo);
            tvCategoria = itemView.findViewById(R.id.tvCategoria);
            tvFechaRecibo = itemView.findViewById(R.id.tvFechaRecibo);
            btnVer = itemView.findViewById(R.id.btnVerRecibo);
            btnEditar = itemView.findViewById(R.id.btnEditarRecibo);
            btnEliminar = itemView.findViewById(R.id.btnEliminarRecibo);
        }
    }
}
