package com.intel.tsrytkon.goprovideos;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;

import java.util.List;

/**
 * Created by tsrytkon on 3/8/15.
 */
public class GoProLiveActivity extends Activity implements
View.OnClickListener, TextureView.SurfaceTextureListener {

    private boolean VERBOSE = false;
    private static final String TAG = "GoProLiveActivity";
    private TextureView mPreview;
    private ImageButton mRecord;
    private Bundle extras;
    private static final String MEDIA = "media";
    private WifiManager mWifiManager;
    ListView list;
    String wifis[];

    /**
     *
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(com.intel.tsrytkon.goprovideos.R.layout.activity_video_playback_full);
        mPreview = (TextureView) findViewById(com.intel.tsrytkon.goprovideos.R.id.fullscreen_content);
        mPreview.setSurfaceTextureListener(this);
        mRecord = (ImageButton) findViewById(com.intel.tsrytkon.goprovideos.R.id.action_play);
        mPreview.setOnClickListener(this);
        extras = getIntent().getExtras();
        Context context = getApplicationContext();
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Log.d(TAG, "Got wifi manager "+mWifiManager);
        Log.d(TAG, "Wifi state "+mWifiManager.getWifiState());
        WifiScanReceiver wifiReceiver = new WifiScanReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        // There's a short delay between the start of the activity and the initialization
        // of the SurfaceTexture that backs the TextureView.  We don't want to try to
        // send a video stream to the TextureView before it has initialized, so we disable
        // the "play" button until this callback fires.
        Log.d(TAG, "SurfaceTexture ready (" + width + "x" + height + ")");
        //playVideo(extras.getString(MEDIA));
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        Log.d(TAG, "SurfaceTexture destroyed");
        // assume activity is pausing, so don't need to update controls
        return true;    // caller should release ST
    }
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        Log.d(TAG, "SurfaceTexture onSurfaceTextureSizeChanged");
        // ignore
    }
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (VERBOSE) Log.d(TAG, "SurfaceTexture onSurfaceTextureUpdated");
        // ignore
    }
    @Override
    public void onClick(View v) {
        try {
            if (v == mRecord) {
                Log.i(TAG, "User clicked Record/stop");
                if (v.isSelected()) {
                    v.setSelected(false);
                    //mPlayer.pause();
                }
                else {
                    v.setSelected(true);
                    //mPlayer.play();
                }
            }

        }
        catch (Throwable t) {
            Log.e(TAG, t.toString());
        }
    }

    class WifiScanReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            Log.d(TAG, "onReceive!");
            List<ScanResult> wifiScanList = mWifiManager.getScanResults();
            String data = wifiScanList.get(0).toString();
            wifis = new String[wifiScanList.size()];
            for(int i = 0; i < wifiScanList.size(); i++){
                wifis[i] = ((wifiScanList.get(i)).toString());
                Log.d(TAG, "Found wifi: "+wifis[i]);
            }

            list.setAdapter(new ArrayAdapter<String>(getApplicationContext(),
                    android.R.layout.simple_list_item_1,wifis));
        }
    }

}
