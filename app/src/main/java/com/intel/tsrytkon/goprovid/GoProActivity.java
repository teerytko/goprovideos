package com.intel.tsrytkon.goprovid;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;


public class GoProActivity extends Activity {
    private static final String MEDIA = "media";
    private static String STREAM_VIDEO = "http://www.youtube.com/watch?v=vANZfQ1bTAk";
    private static String FILE_VIDEO = "file:///storage/sdcard0/DCIM/100ANDRO/VID_0008.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_pro);
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
        else if (id == R.id.action_play) {
            Intent intent = new Intent(GoProActivity.this,
                    MediaPlayerVideoActivity.class);
            intent.putExtra(MEDIA, FILE_VIDEO);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
}
