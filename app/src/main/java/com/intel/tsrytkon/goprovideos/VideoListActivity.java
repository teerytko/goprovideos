/**
 * main activity
 *
 * TODO: Try MX Player
 */

package com.intel.tsrytkon.goprovideos;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.loader.app.LoaderManager;

import java.io.File;
import java.util.ArrayList;


public class VideoListActivity extends FragmentActivity implements View.OnClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {
    static final int REQUEST_VIDEO_CAPTURE = 1;
    private static final String MEDIA = "media";
    private Button mRecordButton;
    private ListView mListView;
    private ArrayList mPaths = new ArrayList();
    private static String GOPRO_LIVE = "http://10.5.5.9:8080/live/amba.m3u8";
    private static String TAG = "GOPRO";
    // This is the Adapter being used to display the list's data
    private SimpleCursorAdapter mAdapter;

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Log.i(TAG, "onCreateLoader");
        String[] projection = {MediaStore.Video.Media._ID,
                               MediaStore.Video.Media.DISPLAY_NAME,
                               MediaStore.Video.Media.DATE_ADDED,
                               MediaStore.Video.Media.DATA};
        return new CursorLoader(this, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                null, // Return all rows
                null,
                "date_added DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Log.i(TAG, "onLoadFinished");
        if (cursor.getCount() > 0)
        {
            cursor.moveToFirst();
            int index = 0;
            do {
                Log.i(TAG, cursor.getString(0) + " " + cursor.getString(1)+" " + cursor.getString(3));
                mPaths.add(index++, cursor.getString(3));
            } while(cursor.moveToNext());
            cursor.moveToFirst();
        }
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
        setContentView(com.intel.tsrytkon.goprovideos.R.layout.activity_main_list_view);
        // get reference to the views
        mRecordButton = (Button) findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(this);

        mListView = (ListView) findViewById(com.intel.tsrytkon.goprovideos.R.id.listView);
        Log.i(TAG, "Create main view!!");
        // For the cursor adapter, specify which columns go into which views
        String[] fromColumns = {MediaStore.Video.Media.DISPLAY_NAME,
                                MediaStore.Video.Media.DATE_ADDED};
        int[] toViews = {android.R.id.text1, android.R.id.text2}; // The TextView in simple_list_item_2

        // Create an empty adapter we will use to display the loaded data.
        // We pass null for the cursor, then update it in onLoadFinished()
        mAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2, null,
                fromColumns, toViews, 0);

        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
                String videopath = (String) mPaths.get(position);
                Toast.makeText(VideoListActivity.this, videopath, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(VideoListActivity.this,
                        FramePlayerVideoActivity.class);
                intent.putExtra(MEDIA, videopath);
                startActivity(intent);
            }
        });
        getSupportLoaderManager().initLoader(1, null, this);
        registerForContextMenu(mListView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(com.intel.tsrytkon.goprovideos.R.menu.videolist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == com.intel.tsrytkon.goprovideos.R.id.action_settings) {
            return true;
        }
        else if (id == com.intel.tsrytkon.goprovideos.R.id.action_connect) {
            Intent intent = new Intent(VideoListActivity.this,
                    GoProLiveActivity.class);
            intent.putExtra(MEDIA, GOPRO_LIVE);
            startActivity(intent);
        }
        else if (id == com.intel.tsrytkon.goprovideos.R.id.action_record) {
            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
            }
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onClick(View v) {
        try {
            if (v == mRecordButton) {
                Log.i(TAG, "User clicked record");
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
                }
            }

        }
        catch (Throwable t) {
            t.printStackTrace();
            Log.e(TAG, t.toString());
        }
    }
    /**
     * Context menu for video list
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId()== com.intel.tsrytkon.goprovideos.R.id.listView) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(com.intel.tsrytkon.goprovideos.R.menu.video_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        String videopath = (String) mPaths.get(info.position);
        final File vf = new File(videopath);
        switch(item.getItemId()) {
            case com.intel.tsrytkon.goprovideos.R.id.add:
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
                }
                return true;
            case com.intel.tsrytkon.goprovideos.R.id.rename:
                // edit stuff here
                Log.d(TAG, "Rename file"+videopath);
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Rename");

                final EditText input = new EditText(this);
                input.setText(vf.getName());
                alert.setView(input);

                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String newName = input.getEditableText().toString();
                        Log.d(TAG, "Rename to "+newName);
                        File nf = new File(vf.getPath(), newName);
                        Boolean ret = vf.renameTo(nf);
                        if (!ret) {
                            Toast.makeText(VideoListActivity.this, "Could not rename!", Toast.LENGTH_SHORT).show();
                            Toast.makeText(VideoListActivity.this, "Permissions? " + vf.getPath(), Toast.LENGTH_LONG).show();
                        }
                        else {
                            // update media
                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(vf)));
                            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(nf)));
                        }
                    }
                });

                alert.setNegativeButton("CANCEL",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alertDialog = alert.create();
                alertDialog.show();
                return false;
                //return true;
            case com.intel.tsrytkon.goprovideos.R.id.delete:
                // remove stuff here
                Log.d(TAG, "Delete file " + vf.getAbsoluteFile());
                boolean d = vf.getAbsoluteFile().delete();
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(vf)));
                return d;
            default:
                return super.onContextItemSelected(item);
        }
    }


}
