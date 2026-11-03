
package com.autoscann.hn;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "autoscann.db";
    private static final int DB_VERSION = 1;

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
                "texto_ocr TEXT)";

        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,
                          int oldVersion, int newVersion) {

        db.execSQL("DROP TABLE IF EXISTS gastos");
        onCreate(db);
    }

    // Guardar un gasto
    public long insertarGasto(double total,
                              double litros,
                              String fecha,
                              String textoOCR) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put("total", total);
        values.put("litros", litros);
        values.put("fecha", fecha);
        values.put("texto_ocr", textoOCR);

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
}
