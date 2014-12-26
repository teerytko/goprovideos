/**
 * main activity
 *
 * TODO: Try MX Player
 */

package com.intel.tsrytkon.goprovid;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.support.v4.app.LoaderManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class GoProActivity extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String MEDIA = "media";
    private ListView mListView;
    private TextView mStatusView;
    private static String STREAM_VIDEO = "https://www.youtube.com/watch?v=z0JebT4kIPU";
    private static String FILE_VIDEO = "/storage/sdcard0/DCIM/100ANDRO/VID_0010.mp4";
    //private static String FILE_VIDEO = "storage/extSdCard/DCIM/Camera/20141224_135804.mp4";
    private static String GOPRO_VIDEO = "http://10.5.5.9:8080/live/amba.m3u8";
    private static String TAG = "GOPRO";
    // This is the Adapter being used to display the list's data
    private SimpleCursorAdapter mAdapter;

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Log.i(TAG, "onCreateLoader");
        String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME};
        return new CursorLoader(this, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                null, // Return all rows
                null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Log.i(TAG, "onLoadFinished");
        mAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        Log.i(TAG, "onLoaderReset");
        mAdapter.swapCursor(null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_pro);
        // get reference to the views
        mListView = (ListView) findViewById(R.id.listView);
        mStatusView = (TextView) findViewById(R.id.statusView);
        Log.i(TAG, "Create main view!!");
        // For the cursor adapter, specify which columns go into which views
        String[] fromColumns = {MediaStore.Video.Media.DISPLAY_NAME};
        int[] toViews = {android.R.id.text1}; // The TextView in simple_list_item_1

        // Create an empty adapter we will use to display the loaded data.
        // We pass null for the cursor, then update it in onLoadFinished()
        mAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1, null,
                fromColumns, toViews, 0);

        mListView.setAdapter(mAdapter);
        getSupportLoaderManager().initLoader(1, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.go, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        else if (id == R.id.action_connect) {
            Intent intent = new Intent(GoProActivity.this,
                    MediaPlayerVideoActivity.class);
            intent.putExtra(MEDIA, FILE_VIDEO);
            startActivity(intent);
        }
        else if (id == R.id.action_play_file) {
            Intent intent = new Intent(GoProActivity.this,
                    FramePlayerVideoActivity.class);
            intent.putExtra(MEDIA, FILE_VIDEO);
            startActivity(intent);
        }
        else if (id == R.id.action_play_live) {
            Intent intent = new Intent(GoProActivity.this,
                    MediaPlayerVideoActivity.class);
            intent.putExtra(MEDIA, GOPRO_VIDEO);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

}
