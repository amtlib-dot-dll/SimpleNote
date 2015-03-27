package simple.note;

import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.FileNotFoundException;


public class Main extends ListActivity {
    private DatabaseHelper helper;
    private final LoaderManager.LoaderCallbacks<Cursor> callback = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(Main.this) {
                final ForceLoadContentObserver mObserver = new ForceLoadContentObserver();

                @Override
                public Cursor loadInBackground() {
                    Cursor cursor = helper.queryAllRecords();
                    if (cursor != null) {
                        cursor.getCount();
                        cursor.registerContentObserver(mObserver);
                    }
                    return cursor;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            ((CursorAdapter) getListAdapter()).swapCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            ((CursorAdapter) getListAdapter()).swapCursor(null);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        helper = DatabaseHelper.getInstance(this);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startActivityForResult(new Intent(Main.this, NoteDetailActivity.class).putExtra(BaseColumns._ID, id), 0);
            }
        });
        getListView().setMultiChoiceModeListener(new ListView.MultiChoiceModeListener() {
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.list_select_menu, menu);
                mode.setTitle(R.string.select_notes);
                return true;
            }

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.item_delete:
                        SQLiteDatabase database = helper.getWritableDatabase();
                        try {
                            database.beginTransaction();
                            for (long id : getListView().getCheckedItemIds()) {
                                helper.delete(id);
                            }
                            database.setTransactionSuccessful();
                        } finally {
                            database.endTransaction();
                        }
                        getLoaderManager().restartLoader(0, null, callback);
                        mode.finish();
                        break;
                    case R.id.item_select_all:
                        for (int i = 0; i < getListView().getCount(); i++) {
                            getListView().setItemChecked(i, true);
                        }
                        break;
                    case R.id.item_copy:
                        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(null, helper.getContentOfOneRecord(getListView().getCheckedItemIds()[0])));
                        mode.finish();
                        break;
                }
                return true;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                final int checkedCount = getListView().getCheckedItemCount();
                switch (checkedCount) {
                    case 0:
                        mode.setSubtitle(null);
                        break;
                    default:
                        mode.setSubtitle(getResources().getQuantityString(R.plurals.notes_count_subtitle, checkedCount, checkedCount));
                        break;
                }
            }
        });
        setListAdapter(new DatabaseHelper.Adapter(this));
        getLoaderManager().initLoader(0, null, callback);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case 0:
                    getLoaderManager().restartLoader(0, null, callback);
                    break;
                case 1:
                    new AsyncTask<Void, Void, Boolean>() {
                        @Override
                        protected Boolean doInBackground(Void... params) {
                            try {
                                return helper.restoreFromJSON(getApplication().getContentResolver().openInputStream(data.getData()));
                            } catch (FileNotFoundException e) {
                                throw new IllegalStateException(e);
                            }
                        }

                        @Override
                        protected void onPostExecute(Boolean result) {
                            if (result) {
                                getLoaderManager().restartLoader(0, null, callback);
                                Toast.makeText(Main.this, R.string.import_success, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(Main.this, R.string.import_fail, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }.execute();
                    break;
                case 2:
                    new AsyncTask<Void, Void, Boolean>() {
                        @Override
                        protected Boolean doInBackground(Void... params) {
                            try {
                                return helper.dumpAsJSON(getContentResolver().openOutputStream(data.getData(), "w"));
                            } catch (FileNotFoundException e) {
                                throw new IllegalStateException(e);
                            }
                        }

                        @Override
                        protected void onPostExecute(Boolean result) {
                            if (result) {
                                Toast.makeText(Main.this, R.string.export_success, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(Main.this, R.string.export_fail, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }.execute();
                    break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_note:
                Intent intent = new Intent(this, NoteDetailActivity.class);
                startActivityForResult(intent, 0);
                return true;
            case R.id.item_export:
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/json")
                        .putExtra(Intent.EXTRA_TITLE, DateUtils.formatDateTime(this,
                                System.currentTimeMillis(),
                                DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME) + ".json"), 2);
                return true;
            case R.id.item_import:
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
