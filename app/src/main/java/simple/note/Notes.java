package simple.note;

import android.app.Application;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public final class Notes extends Application {
    private SQLiteOpenHelper helper;
    private SQLiteStatement insert;
    private SQLiteStatement delete;
    private SQLiteStatement peek;
    private SQLiteStatement restore;
    private SQLiteStatement update;

    @Override
    public void onCreate() {
        super.onCreate();
        helper = new SQLiteOpenHelper(this, "data.db", null, 1) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE DATA(_id INTEGER PRIMARY KEY NOT NULL,CREATION_TIME_UTC INTEGER DEFAULT(CAST((JULIANDAY('now')-2440587.5)*86400000 AS INTEGER))NOT NULL,LAST_WRITE_TIME_UTC INTEGER DEFAULT(CAST((JULIANDAY('now')-2440587.5)*86400000 AS INTEGER))NOT NULL,CONTENT TEXT);");
                db.execSQL("CREATE TRIGGER AFTER UPDATE ON DATA WHEN old.LAST_WRITE_TIME_UTC=new.LAST_WRITE_TIME_UTC BEGIN UPDATE DATA SET LAST_WRITE_TIME_UTC=CAST((JULIANDAY('now')-2440587.5)*86400000 AS INTEGER)where _id=old._id;END;");
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.execSQL("DROP TABLE DATA;");
                onCreate(db);
            }
        };
        SQLiteDatabase database = getWritableDatabase();
        insert = database.compileStatement("INSERT INTO DATA(CONTENT)VALUES(?);");
        update = database.compileStatement("UPDATE DATA SET CONTENT=?WHERE _id=?;");
        restore = database.compileStatement("INSERT INTO DATA(CREATION_TIME_UTC,LAST_WRITE_TIME_UTC,CONTENT)VALUES(?,?,?);");
        delete = database.compileStatement("DELETE FROM DATA WHERE _id=?;");
        peek = database.compileStatement("SELECT CONTENT FROM DATA WHERE _id=?LIMIT 1;");
    }

    public String peek(long id) {
        try {
            peek.bindLong(1, id);
            return peek.simpleQueryForString();
        } finally {
            peek.clearBindings();
        }
    }

    public SQLiteDatabase getWritableDatabase() {
        return helper.getWritableDatabase();
    }

    public SQLiteDatabase getReadableDatabase() {
        return helper.getReadableDatabase();
    }

    public long insert(String content) {
        try {
            insert.bindString(1, content);
            return insert.executeInsert();
        } finally {
            insert.clearBindings();
        }
    }

    public Boolean backup(OutputStream stream) {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(stream));
             Cursor cursor = query()) {
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

    public Boolean restore(InputStream stream) {
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
                    restore.bindLong(1, creationTimeUTC);
                    restore.bindLong(2, lastWriteTimeUTC);
                    restore.bindString(3, content);
                    restore.executeInsert();
                } finally {
                    restore.clearBindings();
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
            delete.bindLong(1, id);
            return delete.executeUpdateDelete();
        } finally {
            delete.clearBindings();
        }
    }

    public int update(long id, String content) {
        try {
            update.bindString(1, content);
            update.bindLong(2, id);
            return update.executeUpdateDelete();
        } finally {
            update.clearBindings();
        }
    }

    public Cursor query() {
        return getReadableDatabase().rawQuery("SELECT*FROM DATA ORDER BY LAST_WRITE_TIME_UTC DESC;", new String[0]);
    }

    public Cursor query(String content) {
        return getReadableDatabase().rawQuery("SELECT*FROM DATA WHERE _id=?LIMIT 1;", new String[]{content});
    }

    public static class NotesAdapter extends CursorAdapter {
        LayoutInflater inflater;

        public NotesAdapter(Context context) {
            super(context, null, 0);
            inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
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
