package com.autoscann.hn;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ReciboAdapter extends RecyclerView.Adapter<ReciboAdapter.ViewHolder> {

    private Cursor cursor;
    private final Context context;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int id, String fecha, double total, String textoOcr);
    }

    public ReciboAdapter(Context context, Cursor cursor, OnItemClickListener listener) {
        this.context = context;
        this.cursor = cursor;
        this.listener = listener;
    }

    public void swapCursor(Cursor newCursor) {
        this.cursor = newCursor;
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
        String nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre"));
        String categoria = cursor.getString(cursor.getColumnIndexOrThrow("categoria"));
        String fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha"));
        String imagenUri = cursor.getString(cursor.getColumnIndexOrThrow("imagen_uri"));
        String textoOcr = cursor.getString(cursor.getColumnIndexOrThrow("texto_ocr"));
        double total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"));

        String categoriaSegura = (categoria == null || categoria.isEmpty()) ? "Gastos" : categoria;
        String tituloMostrar = (nombre != null && !nombre.isEmpty()) ? nombre : "Gasto_" + categoriaSegura;

        holder.tvTituloRecibo.setText(tituloMostrar + "  | #" + id + " |");
        holder.tvIdRecibo.setVisibility(View.GONE);
        holder.tvFechaRecibo.setText("Fecha: " + fecha);

        if (imagenUri != null && !imagenUri.isEmpty()) {
            holder.imgRecibo.setImageURI(Uri.parse(imagenUri));
        } else {
            holder.imgRecibo.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(id, fecha, total, textoOcr);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cursor == null ? 0 : cursor.getCount();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgRecibo;
        TextView tvTituloRecibo, tvIdRecibo, tvFechaRecibo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgRecibo = itemView.findViewById(R.id.imgRecibo);
            tvTituloRecibo = itemView.findViewById(R.id.tvTituloRecibo);
            tvIdRecibo = itemView.findViewById(R.id.tvIdRecibo);
            tvFechaRecibo = itemView.findViewById(R.id.tvFechaRecibo);
        }
    }
}