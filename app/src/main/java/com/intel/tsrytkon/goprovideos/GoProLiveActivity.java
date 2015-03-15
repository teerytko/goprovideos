package com.intel.tsrytkon.goprovideos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tsrytkon on 3/8/15.
 */
public class GoProLiveActivity extends Activity implements
View.OnClickListener, DialogInterface.OnClickListener,
MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener,
MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
MediaPlayer.OnVideoSizeChangedListener, SurfaceHolder.Callback {

    private boolean VERBOSE = false;
    private static final String TAG = "GoProLiveActivity";
    private SurfaceView mPreview;
    private SurfaceHolder holder;

    private ImageButton mRecord;
    private Bundle extras;
    private WifiManager mWifiManager;
    ArrayList<String> mWifis = new ArrayList();
    WifiConfiguration mWifiConfig = null;
    List<ScanResult> mWifiScanList;
    ScanResult mSelected = null;
    AlertDialog mSelectWifiDlg = null;
    AlertDialog mWifiPasswordDlg = null;
    MediaPlayer mMediaPlayer = null;
    private int mVideoWidth;
    private int mVideoHeight;
    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;

    /**
     *
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_video_preview_full);
        mPreview = (SurfaceView) findViewById(com.intel.tsrytkon.goprovideos.R.id.fullscreen_content);
        holder = mPreview.getHolder();
        holder.addCallback(this);

        extras = getIntent().getExtras();
        Context c = getApplicationContext();
        mWifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wi = mWifiManager.getConnectionInfo();
        String ipAddress = Formatter.formatIpAddress(wi.getIpAddress());
        Log.d(TAG, "Current wifi "+ipAddress);
        if (ipAddress.compareTo("10.5.5.109") == 0) {
            Log.d(TAG, "Connected to GoPro");
        }
        else
            this.scanWifi(c);
    }


    public void scanWifi(Context context) {
        Log.d(TAG, "Got wifi manager "+mWifiManager);
        Log.d(TAG, "Wifi state "+mWifiManager.getWifiState());
        WifiScanReceiver wifiReceiver = new WifiScanReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();
    }

    public void onClick(DialogInterface dialog, int selected) {
        if (dialog == mSelectWifiDlg) {
            Log.d(TAG, "Selected " + selected + " " + mWifis.get(selected));
            this.connectToNetwork(mWifiScanList.get(selected));
            dialog.dismiss();
        }
        else if (dialog == mWifiPasswordDlg) {
            EditText password_input = (EditText) mWifiPasswordDlg.findViewById(R.id.password);
            if (mSelected.capabilities.contains("WPA")) {
                //WPA
                String sSecurityKey = password_input.getEditableText().toString();
                Log.d(TAG, "WPA Password given "+sSecurityKey);
                mWifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                mWifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                mWifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                mWifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                mWifiConfig.preSharedKey = "\""+sSecurityKey+"\"";
            }
            else if (mSelected.capabilities.contains("WEP")) {
                String sSecurityKey = password_input.getEditableText().toString();
                Log.d(TAG, "WEP Password given "+sSecurityKey);
                mWifiConfig.wepKeys[0] = sSecurityKey;
                mWifiConfig.wepTxKeyIndex = 0;
            }
            else {
                Log.e(TAG, "Unsupported wifi security");
            }
            //mWifiConfig.
            mWifiConfig.status = WifiConfiguration.Status.ENABLED;
            Log.d(TAG, "Adding new network config " + mWifiConfig.SSID);
            int netId = mWifiManager.addNetwork(mWifiConfig);
            if (netId < 0) {
                Log.e(TAG, "Adding network failed! " + mWifiConfig.SSID);
            }
            else {
                //mWifiManager.disconnect();
                Log.d(TAG, "Connecting to " + netId);
                mWifiManager.enableNetwork(netId, true);
                playVideo("http://10.5.5.9:8080/live/amba.m3u8");
            }
        }
        else {
            Log.e(TAG, "Unknown dialog "+dialog);
            dialog.dismiss();
        }
    }

    class WifiScanReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            Log.d(TAG, "onReceive!");
            unregisterReceiver(this);
            mWifiScanList = mWifiManager.getScanResults();
            String data = mWifiScanList.get(0).toString();
            for (int i = 0; i < mWifiScanList.size(); i++) {
                if (mWifiScanList.get(i).SSID != "") {
                    mWifis.add((mWifiScanList.get(i)).SSID);
                    Log.d(TAG, "Found wifi: " + i + " " + mWifiScanList.get(i).SSID + ": -->" + mWifiScanList.get(i).toString());
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(c);
            builder.setTitle("Select you GoPro device");
            String[] wifiList = new String[mWifis.size()];
            builder.setItems(mWifis.toArray(wifiList), (GoProLiveActivity) c);
            mSelectWifiDlg = builder.create();
            mSelectWifiDlg.show();
        }
    }
    public boolean connectToNetwork(ScanResult selected){
        Log.i(TAG, "Connecting to network " + selected);
        List <WifiConfiguration> listConfig = mWifiManager.getConfiguredNetworks();

        for (int i = 0; i<listConfig.size(); i++){
            mWifiConfig = listConfig.get(i);
            Log.d(TAG, "Compare to existing network "+mWifiConfig);
            String SSID = mWifiConfig.SSID.replaceAll("\"$|^\"", "");
            if (SSID.compareTo(selected.SSID) == 0){
                Log.i(TAG, "Existing network config found " + selected.BSSID);
                mWifiManager.enableNetwork(mWifiConfig.networkId, true);
                playVideo("http://10.5.5.9:8080/live/amba.m3u8");
                return true;
            }
        }
        mSelected = selected;
        mWifiConfig = new WifiConfiguration();
        mWifiConfig.BSSID = selected.BSSID;
        mWifiConfig.SSID = "\""+selected.SSID+"\"";
        mWifiConfig.priority = 1;
        mWifiConfig.hiddenSSID = false;

        Log.d(TAG, "Try to create new network config for " + selected.SSID);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Wifi "+selected.SSID+" password");
        LayoutInflater inflater = this.getLayoutInflater();
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(inflater.inflate(R.layout.dlg_ask_password_view, null));
        //password_input = new EditText(this);
        //password_input.setHint("Password");
        //password_input.setInputType(EditText);
        //builder.setView(password_input);
        builder.setPositiveButton("OK", this);
        mWifiPasswordDlg = builder.create();
        mWifiPasswordDlg.show();
        return true;
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "surfaceChanged called");

    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");
        //playVideo(extras.getString(MEDIA));
        playVideo("http://10.5.5.9:8080/live/amba.m3u8");
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: "+ what+" extra: "+extra);
        super.onPause();
        releaseMediaPlayer();
        doCleanUp();
        return true;
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        releaseMediaPlayer();
        doCleanUp();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        releaseMediaPlayer();
        doCleanUp();
    }

    private void playVideo(String path) {
        doCleanUp();
        try {
            String tpath = URLEncoder.encode(path, "UTF-8");
            Log.i(TAG, "playVideo path " + path);
            if (path != "") {
                // Create a new media player and set the listeners
                Uri myuri = Uri.parse(path);
                Log.i(TAG, "playVideo myuri " + myuri);
                mMediaPlayer = MediaPlayer.create(this, myuri);
                mMediaPlayer.setOnBufferingUpdateListener(this);
                mMediaPlayer.setOnCompletionListener(this);
                mMediaPlayer.setOnPreparedListener(this);
                mMediaPlayer.setOnVideoSizeChangedListener(this);
                mMediaPlayer.setOnErrorListener(this);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setLooping(false);
                mMediaPlayer.setDisplay(holder);
            }
        } catch (Exception e) {
            Log.e(TAG, "error: " + e.getMessage(), e);
        }
    }
    public void onBufferingUpdate(MediaPlayer arg0, int percent) {
        Log.d(TAG, "onBufferingUpdate percent:" + percent);

    }

    public void onCompletion(MediaPlayer arg0) {
        Log.d(TAG, "onCompletion called");
    }
    private void doCleanUp() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        mIsVideoReadyToBePlayed = false;
        mIsVideoSizeKnown = false;
    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        Log.v(TAG, "onVideoSizeChanged called");
        if (width == 0 || height == 0) {
            Log.e(TAG, "invalid video width(" + width + ") or height(" + height
                    + ")");
            return;
        }
        mIsVideoSizeKnown = true;
        mVideoWidth = width;
        mVideoHeight = height;
        Log.i(TAG, "Video width(" + width + ") or height(" + height + ")");
        if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
            startVideoPlayback();
        }

    }
    private void startVideoPlayback() {
        Log.v(TAG, "startVideoPlayback");
        holder.setFixedSize(mVideoWidth, mVideoHeight);
        mMediaPlayer.start();
    }

    public void onPrepared(MediaPlayer mediaplayer) {
        Log.d(TAG, "onPrepared called");
        mIsVideoReadyToBePlayed = true;
        if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
            startVideoPlayback();
        }
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

}
