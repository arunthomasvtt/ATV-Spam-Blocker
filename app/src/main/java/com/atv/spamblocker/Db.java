package com.atv.spamblocker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {

    private static final String NAME = "spamblocker.db";
    private static final int VERSION = 1;

    public Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE series(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pattern TEXT UNIQUE NOT NULL, note TEXT DEFAULT '', source TEXT DEFAULT 'manual'," +
                "created_at INTEGER, blocked_count INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE blocked_calls(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "number TEXT, pattern TEXT, ts INTEGER)");
        db.execSQL("CREATE INDEX idx_blocked_ts ON blocked_calls(ts)");
        ContentValues cv = new ContentValues();
        cv.put("pattern", "140");
        cv.put("note", "Known telemarketing prefix (Jio)");
        cv.put("source", "builtin");
        cv.put("created_at", System.currentTimeMillis());
        db.insert("series", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    public List<Series> getSeries() {
        List<Series> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, pattern, note, source, created_at, blocked_count FROM series" +
                " ORDER BY blocked_count DESC, created_at DESC", null)) {
            while (c.moveToNext()) {
                out.add(new Series(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getLong(4), c.getInt(5)));
            }
        }
        return out;
    }

    public Series addSeries(String pattern, String note, String source) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("pattern", pattern);
        cv.put("note", note == null ? "" : note);
        cv.put("source", source);
        cv.put("created_at", now);
        cv.put("blocked_count", 0);
        long id = db.insertWithOnConflict("series", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) return new Series(id, pattern, note == null ? "" : note, source, now, 0);
        try (Cursor c = db.rawQuery(
                "SELECT id, pattern, note, source, created_at, blocked_count FROM series WHERE pattern=?",
                new String[]{pattern})) {
            if (c.moveToFirst()) {
                return new Series(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getLong(4), c.getInt(5));
            }
        }
        return null;
    }

    public void deleteSeries(long id) {
        getWritableDatabase().delete("series", "id=?", new String[]{String.valueOf(id)});
    }

    /** @return false if another series already uses the new pattern. */
    public boolean updateSeries(long id, String pattern, String note) {
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor c = db.rawQuery("SELECT id FROM series WHERE pattern=? AND id<>?",
                new String[]{pattern, String.valueOf(id)})) {
            if (c.moveToFirst()) return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("pattern", pattern);
        cv.put("note", note == null ? "" : note);
        db.update("series", cv, "id=?", new String[]{String.valueOf(id)});
        return true;
    }

    public int countBySource(String source) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM series WHERE source=?", new String[]{source})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * Longest (most specific) matching pattern wins. Prefix matching runs in SQL
     * (LIKE pattern || '%') so large cloud lists stay cheap to screen against;
     * patterns are digit-only, so LIKE has no wildcard pitfalls.
     */
    public Series findMatch(String local, String full) {
        String sql = "SELECT id, pattern, note, source, created_at, blocked_count FROM series"
                + " WHERE (LENGTH(pattern) <= 10 AND ? LIKE pattern || '%')"
                + " OR (LENGTH(pattern) > 10 AND ? LIKE pattern || '%')"
                + " ORDER BY LENGTH(pattern) DESC LIMIT 1";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{local, full})) {
            if (c.moveToFirst()) {
                return new Series(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getLong(4), c.getInt(5));
            }
        }
        return null;
    }

    public void recordBlocked(String number, String pattern) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("number", number);
        cv.put("pattern", pattern);
        cv.put("ts", System.currentTimeMillis());
        db.insert("blocked_calls", null, cv);
        db.execSQL("UPDATE series SET blocked_count = blocked_count + 1 WHERE pattern=?",
                new Object[]{pattern});
    }

    public List<BlockedCall> recentBlocked(int limit) {
        List<BlockedCall> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, number, pattern, ts FROM blocked_calls ORDER BY ts DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                out.add(new BlockedCall(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
            }
        }
        return out;
    }

    public int clearBlockedLog() {
        return getWritableDatabase().delete("blocked_calls", null, null);
    }
}
