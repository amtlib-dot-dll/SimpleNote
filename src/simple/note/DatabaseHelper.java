package simple.note;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.text.format.DateUtils;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import java.io.*;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static DatabaseHelper instance;
    private SQLiteStatement insertStatement;
    private SQLiteStatement deleteStatement;
    private SQLiteStatement getContentOfOneRecordStatement;
    private SQLiteStatement restoreStatement;
    private SQLiteStatement updateStatement;

    private DatabaseHelper(Context context) {
        super(context, "data.db", null, 1);
        SQLiteDatabase database = getWritableDatabase();
        insertStatement = database.compileStatement("INSERT INTO DATA (CONTENT) VALUES (?);");
        updateStatement = database.compileStatement("UPDATE DATA\n" +
                "SET CONTENT = ?\n" +
                "WHERE _id = ?;");
        restoreStatement = database.compileStatement("INSERT INTO DATA (CREATION_TIME_UTC, LAST_WRITE_TIME_UTC, CONTENT) VALUES (?, ?, ?);");
        deleteStatement = database.compileStatement("DELETE FROM DATA\n" +
                "WHERE _id = ?;");
        getContentOfOneRecordStatement = database.compileStatement("SELECT CONTENT\n" +
                "FROM DATA\n" +
                "WHERE _id = ?\n" +
                "LIMIT 1;");
    }

    public static DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE DATA (\n" +
                "  _id                 INTEGER PRIMARY KEY NOT NULL,\n" +
                "  CREATION_TIME_UTC   INTEGER DEFAULT (CAST((JULIANDAY('now') - 2440587.5) * 86400000 AS INTEGER)) NOT NULL,\n" +
                "  LAST_WRITE_TIME_UTC INTEGER DEFAULT (CAST((JULIANDAY('now') - 2440587.5) * 86400000 AS INTEGER)) NOT NULL,\n" +
                "  CONTENT             TEXT\n" +
                ");");
        db.execSQL("CREATE VIRTUAL TABLE [INDEX] USING fts4(content=DATA, CONTENT);");
        db.execSQL("CREATE TRIGGER [AUTO UPDATE TIME] AFTER UPDATE ON DATA\n" +
                "  WHEN old.LAST_WRITE_TIME_UTC = new.LAST_WRITE_TIME_UTC\n" +
                "BEGIN\n" +
                "  UPDATE DATA\n" +
                "  SET LAST_WRITE_TIME_UTC = CAST((JULIANDAY('now') - 2440587.5) * 86400000 AS INTEGER)\n" +
                "  WHERE DATA._id = new._id;\n" +
                "END;");
        db.execSQL("CREATE TRIGGER [SYNC INDEX BEFORE UPDATE] BEFORE UPDATE ON DATA BEGIN\n" +
                "  DELETE FROM [INDEX]\n" +
                "  WHERE ROWID = old.rowid;\n" +
                "END;");
        db.execSQL("CREATE TRIGGER [SYNC INDEX BEFORE DELETE] BEFORE DELETE ON DATA BEGIN\n" +
                "  DELETE FROM [INDEX]\n" +
                "  WHERE ROWID = old.rowid;\n" +
                "END;");
        db.execSQL("CREATE TRIGGER [SYNC INDEX AFTER UPDATE] AFTER UPDATE ON DATA BEGIN\n" +
                "  INSERT INTO [INDEX] (ROWID, CONTENT) VALUES (new.ROWID, new.CONTENT);\n" +
                "END;");
        db.execSQL("CREATE TRIGGER [SYNC INDEX AFTER INSERT] AFTER INSERT ON DATA BEGIN\n" +
                "  INSERT INTO [INDEX] (ROWID, CONTENT) VALUES (new.ROWID, new.CONTENT);\n" +
                "END;");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS DATA;");
        db.execSQL("DROP TABLE IF EXISTS [INDEX];");
        db.execSQL("DROP VIEW IF EXISTS [ALL DATA];");
        db.execSQL("DROP TABLE IF EXISTS [TEXT DATA];");
        db.execSQL("DROP TABLE IF EXISTS METADATA;");
        onCreate(db);
    }

    public String getContentOfOneRecord(long id) {
        try {
            getContentOfOneRecordStatement.bindLong(1, id);
            return getContentOfOneRecordStatement.simpleQueryForString();
        } finally {
            getContentOfOneRecordStatement.clearBindings();
        }
    }

    public long insert(String content) {
        try {
            insertStatement.bindString(1, content);
            return insertStatement.executeInsert();
        } finally {
            insertStatement.clearBindings();
        }
    }

    public Boolean dumpAsJSON(OutputStream stream) {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(stream));
             Cursor cursor = queryAllRecords()) {
            writer.beginArray();
            while (cursor.moveToNext()) {
                writer.beginObject();
                writer.name("creation_time_utc");
                writer.value(cursor.getLong(1));
                writer.name("last_write_time_utc");
                writer.value(cursor.getLong(2));
                writer.name("content");
                writer.value(cursor.getString(3));
                writer.endObject();
            }
            writer.endArray();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Boolean restoreFromJSON(InputStream stream) {
        SQLiteDatabase database = getWritableDatabase();
        try (JsonReader reader = new JsonReader(new InputStreamReader(stream))) {
            database.beginTransaction();
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                long creationTimeUTC = 0;
                long lastWriteTimeUTC = 0;
                String content = null;
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "creation_time_utc":
                            creationTimeUTC = reader.nextLong();
                            break;
                        case "last_write_time_utc":
                            lastWriteTimeUTC = reader.nextLong();
                            break;
                        case "content":
                            content = reader.nextString();
                            break;
                        default:
                            reader.skipValue();
                    }
                }
                try {
                    restoreStatement.bindLong(1, creationTimeUTC);
                    restoreStatement.bindLong(2, lastWriteTimeUTC);
                    restoreStatement.bindString(3, content);
                    restoreStatement.executeInsert();
                } finally {
                    restoreStatement.clearBindings();
                }
                reader.endObject();
            }
            reader.endArray();
            database.setTransactionSuccessful();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            database.endTransaction();
        }
    }

    public int delete(long id) {
        try {
            deleteStatement.bindLong(1, id);
            return deleteStatement.executeUpdateDelete();
        } finally {
            deleteStatement.clearBindings();
        }
    }

    public int update(long id, String content) {
        try {
            updateStatement.bindLong(2, id);
            updateStatement.bindString(1, content);
            return updateStatement.executeUpdateDelete();
        } finally {
            updateStatement.clearBindings();
        }
    }

    public Cursor queryAllRecords() {
        return getReadableDatabase().rawQuery("SELECT\n" +
                "  _id,\n" +
                "  CREATION_TIME_UTC,\n" +
                "  LAST_WRITE_TIME_UTC,\n" +
                "  CONTENT\n" +
                "FROM DATA\n" +
                "ORDER BY LAST_WRITE_TIME_UTC\n" +
                "  DESC;", null);
    }

    public Cursor search(String query) {
        return getReadableDatabase().rawQuery("SELECT\n" +
                "  _id,\n" +
                "  CREATION_TIME_UTC,\n" +
                "  LAST_WRITE_TIME_UTC,\n" +
                "  DATA.CONTENT\n" +
                "FROM DATA, [INDEX]\n" +
                "WHERE DATA._id = [INDEX].ROWID AND [INDEX].CONTENT MATCH ?\n" +
                "ORDER BY LAST_WRITE_TIME_UTC\n" +
                "  DESC;", new String[]{query + '*'});
    }

    public static class Adapter extends CursorAdapter {
        LayoutInflater inflater;

        public Adapter(Context context) {
            super(context, null, 0);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View result = inflater.inflate(R.layout.record, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.text = (TextView) result.findViewById(R.id.text);
            holder.timeStamp = (TextView) result.findViewById(R.id.time_stamp);
            holder.number = (TextView) result.findViewById(R.id.number);
            result.setTag(holder);
            return result;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();
            holder.timeStamp.setText(DateUtils.formatDateTime(context, cursor.getLong(1), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
            holder.text.setText(cursor.getString(3));
            holder.number.setVisibility(View.GONE);
        }

        private class ViewHolder {
            public TextView text;
            public TextView timeStamp;
            public TextView number;
        }
    }
}
