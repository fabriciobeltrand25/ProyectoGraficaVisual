package com.autoscann.hn;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Base de datos de registros de facturas. Una sola tabla "gastos" con dos tipos:
 * Gasolina (litros) y Mantenimiento (descripción, cantidad y precio unitario).
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String CAT_GASOLINA = "Gasolina";
    public static final String CAT_MANTENIMIENTO = "Mantenimiento";

    private static final String DB_NAME = "autoscann.db";
    private static final int DB_VERSION = 3;
    private static final String TABLA = "gastos";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLA + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "total REAL NOT NULL, " +
                "litros REAL, " +            // Gasolina: cantidad de combustible
                "cantidad REAL, " +          // Mantenimiento: unidades del artículo
                "precio_unitario REAL, " +   // Mantenimiento
                "descripcion TEXT, " +       // Mantenimiento: repuestos o servicios
                "fecha TEXT NOT NULL, " +    // DD/MM/AAAA
                "texto_ocr TEXT, " +
                "categoria TEXT, " +         // Gasolina | Mantenimiento
                "nombre TEXT, " +            // Comercio, gasolinera o taller
                "imagen_uri TEXT)");         // Ruta absoluta de la foto
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // La versión 1 no tenía categoría ni foto: se recrea la tabla (comportamiento original).
            db.execSQL("DROP TABLE IF EXISTS " + TABLA);
            onCreate(db);
            return;
        }
        if (oldVersion < 3) {
            // Migración conservando los datos existentes
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN cantidad REAL");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN precio_unitario REAL");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN descripcion TEXT");
        }
    }

    // ---------------------------------------------------------------
    // Escritura
    // ---------------------------------------------------------------

    /** Inserta un registro. Devuelve el id nuevo o -1 si falla. */
    public long insertarRegistro(ContentValues valores) {
        return getWritableDatabase().insert(TABLA, null, valores);
    }

    /** Actualiza un registro existente. Devuelve el número de filas modificadas. */
    public int actualizarRegistro(int id, ContentValues valores) {
        return getWritableDatabase().update(TABLA, valores, "id = ?",
                new String[]{String.valueOf(id)});
    }

    public boolean eliminarRegistro(int id) {
        return getWritableDatabase().delete(TABLA, "id = ?",
                new String[]{String.valueOf(id)}) > 0;
    }

    // ---------------------------------------------------------------
    // Lectura
    // ---------------------------------------------------------------

    /** Un registro por id (el cursor debe cerrarse). */
    public Cursor obtenerRegistroPorId(int id) {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLA + " WHERE id = ?",
                new String[]{String.valueOf(id)});
    }

    /** El registro más reciente (el cursor debe cerrarse). */
    public Cursor obtenerUltimoGasto() {
        return getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLA + " ORDER BY id DESC LIMIT 1", null);
    }

    /** Cantidad de registros y suma de todos los totales, en {cantidad, total}. */
    public double[] obtenerResumen() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM " + TABLA, null);
        try {
            if (!c.moveToFirst()) return new double[]{0, 0};
            return new double[]{c.getDouble(0), c.getDouble(1)};
        } finally {
            c.close();
        }
    }

    /**
     * Registros para la lista: filtra por texto (nombre o id) y por categoria, si se indican.
     * El cursor debe cerrarse.
     */
    public Cursor buscarRegistros(String texto, String categoria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLA + " WHERE 1=1");
        List<String> args = new ArrayList<>();

        if (texto != null && !texto.isEmpty()) {
            sql.append(" AND (nombre LIKE ? OR CAST(id AS TEXT) LIKE ?)");
            args.add("%" + texto + "%");
            args.add("%" + texto + "%");
        }
        if (categoria != null && !categoria.isEmpty()) {
            sql.append(" AND categoria = ?");
            args.add(categoria);
        }
        sql.append(" ORDER BY id DESC");

        return getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
    }

    /**
     * Porcentaje del gasto del mes actual en {combustible, mantenimiento}, sobre el total
     * de esos dos tipos. Las fechas se guardan como DD/MM/AAAA, así que el mes es MM/AAAA.
     */
    public double[] obtenerEstadisticasMes() {
        String mesActual = new SimpleDateFormat("MM/yyyy", Locale.US).format(new Date());

        double combustible = 0;
        double mantenimiento = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT categoria, SUM(total) FROM " + TABLA +
                        " WHERE substr(fecha, 4, 7) = ? GROUP BY categoria",
                new String[]{mesActual});
        try {
            while (c.moveToNext()) {
                String categoria = c.isNull(0) ? "" : c.getString(0);
                double suma = c.getDouble(1);
                if (CAT_GASOLINA.equals(categoria)) {
                    combustible += suma;
                } else if (CAT_MANTENIMIENTO.equals(categoria)) {
                    mantenimiento += suma;
                }
            }
        } finally {
            c.close();
        }

        double general = combustible + mantenimiento;
        if (general <= 0) return new double[]{0, 0};
        return new double[]{(combustible / general) * 100, (mantenimiento / general) * 100};
    }
}
