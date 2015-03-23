package simple.note;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class NoteDetailActivity extends Activity {
    private EditText text;
    private long id;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.layout_note_detail);
        text = (EditText) findViewById(android.R.id.text1);
        id = getIntent().getLongExtra(BaseColumns._ID, -1);
        if (id != -1) {
            text.setText(((Notes) getApplication()).peek(id));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (id == -1) {
                    ((Notes) getApplication()).insert(text.getText().toString());
                } else {
                    ((Notes) getApplication()).update(id, text.getText().toString());
                }
                setResult(RESULT_OK);
                finish();
                return true;
            case R.id.linkify:
                Spannable spannable = new SpannableString(text.getText());
                Linkify.addLinks(spannable, Linkify.ALL);
                URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
                final String[] urls = new String[spans.length + 1];
                urls[0] = getString(R.string.treat_all_as_link);
                for (int i = 0; i < spans.length; i++) {
                    urls[i + 1] = spans[i].getURL();
                }
                new AlertDialog.Builder(this).setItems(urls, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(text.getText().toString())));
                        } else {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls[which])));
                        }
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}