
package com.autoscann.hn;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "autoscann.db";
    private static final int DB_VERSION = 2;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }



    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE gastos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "total REAL NOT NULL, " +
                "litros REAL, " +
                "fecha TEXT NOT NULL, " +
                "texto_ocr TEXT, " +
                "categoria TEXT, " +       // NUEVO: Gasolina, Mantenimiento, etc.
                "nombre TEXT, " +          // NUEVO: Nombre personalizado
                "imagen_uri TEXT)";        // NUEVO: Ruta de la imagen guardada
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // En producción, aquí se hace un ALTER TABLE. Para este proyecto, recreamos.
        db.execSQL("DROP TABLE IF EXISTS gastos");
        onCreate(db);
    }


    // Guardar un gasto
    public long insertarGasto(double total, double litros, String fecha,
                              String textoOCR, String categoria,
                              String nombre, String imagenUri) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("total", total);
        values.put("litros", litros);
        values.put("fecha", fecha);
        values.put("texto_ocr", textoOCR);
        values.put("categoria", categoria);
        values.put("nombre", nombre);
        values.put("imagen_uri", imagenUri);
        long resultado = db.insert("gastos", null, values);
        db.close();
        return resultado;
    }




    // Obtener el último gasto registrado
    public Cursor obtenerUltimoGasto() {

        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT * FROM gastos ORDER BY id DESC LIMIT 1",
                null
        );
    }

    public java.util.Map<String, Float> obtenerPorcentajesCategorias() {
        java.util.Map<String, Float> porcentajes = new java.util.HashMap<>();
        SQLiteDatabase db = getReadableDatabase();

        // Agrupamos por categoría y sumamos los totales
        Cursor cursor = db.rawQuery(
                "SELECT categoria, SUM(total) as suma_total FROM gastos GROUP BY categoria", null);

        float totalGeneral = 0;
        java.util.Map<String, Float> sumas = new java.util.HashMap<>();

        if (cursor.moveToFirst()) {
            do {
                String cat = cursor.getString(0);
                if (cat == null) cat = "Gastos"; // Por defecto
                float suma = cursor.getFloat(1);
                sumas.put(cat, suma);
                totalGeneral += suma;
            } while (cursor.moveToNext());
        }
        cursor.close();

        if (totalGeneral > 0) {
            for (java.util.Map.Entry<String, Float> entry : sumas.entrySet()) {
                porcentajes.put(entry.getKey(), (entry.getValue() / totalGeneral) * 100);
            }
        }
        return porcentajes;
    }

    // Obtener todos los gastos para el RecyclerView
    public Cursor obtenerTodosLosGastos() {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery("SELECT * FROM gastos ORDER BY id DESC", null);
    }

    // Obtener el total de gastos del mes actual (asumiendo formato DD/MM/YYYY)
    public double[] obtenerEstadisticasMes() {
        SQLiteDatabase db = getReadableDatabase();
        double totalCombustible = 0;
        double totalMantenimiento = 0;

        // Obtenemos el mes actual en formato MM
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM");
        String mesActual = sdf.format(new java.util.Date());

        // Buscamos todos los registros
        Cursor cursor = db.rawQuery("SELECT total, litros FROM gastos", null);

        if (cursor.moveToFirst()) {
            do {
                // Asumiendo que la fecha está en formato DD/MM/YYYY
                // (Aquí podrías hacer un parseo más robusto si guardas la fecha completa)
                // Para simplificar, si tiene litros > 0 lo contamos como combustible, sino como mantenimiento
                double total = cursor.getDouble(0);
                double litros = cursor.getDouble(1);

                if (litros > 0) {
                    totalCombustible += total;
                } else {
                    totalMantenimiento += total;
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        double totalGeneral = totalCombustible + totalMantenimiento;
        double porcentajeCombustible = 0;
        double porcentajeMantenimiento = 0;

        if (totalGeneral > 0) {
            porcentajeCombustible = (totalCombustible / totalGeneral) * 100;
            porcentajeMantenimiento = (totalMantenimiento / totalGeneral) * 100;
        }

        return new double[]{porcentajeCombustible, porcentajeMantenimiento};
    }

    // Eliminar un gasto
    public boolean eliminarGasto(int id) {
        SQLiteDatabase db = getWritableDatabase();
        int resultado = db.delete("gastos", "id = ?", new String[]{String.valueOf(id)});
        db.close();
        return resultado > 0;
    }




}
