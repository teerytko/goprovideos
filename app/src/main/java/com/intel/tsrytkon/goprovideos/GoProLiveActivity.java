package com.intel.tsrytkon.goprovideos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private String command_power_on = "http://10.5.5.9/bacpac/PW?t=nokiantorpedo&p=%01";
    private String command_power_off = "http://10.5.5.9/bacpac/PW?t=nokiantorpedo&p=%00";
    private String command_get_state = "http://10.5.5.9/camera/se?t=nokiantorpedo";

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
    private boolean mConnected = false;
    private WifiStateReceiver m_stateReceiver;
    /**
     *
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        Log.d(TAG, "onCreate");
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
        m_stateReceiver = new WifiStateReceiver();
        registerReceiver(m_stateReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        if (ipAddress.compareTo("10.5.5.109") == 0) {
            Log.d(TAG, "Connected to GoPro");
            Message msg = Message.obtain();
            msg.what = 1001;
            _handler.sendMessage(msg);
        }
        else
            this.scanWifi(c);
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
            }
        }
        else {
            Log.e(TAG, "Unknown dialog "+dialog);
            dialog.dismiss();
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

    public void scanWifi(Context context) {
        Log.d(TAG, "Got wifi manager " + mWifiManager);
        Log.d(TAG, "Wifi state " + mWifiManager.getWifiState());
        WifiScanReceiver wifiReceiver = new WifiScanReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager.startScan();
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
                mWifiConfig.BSSID = selected.BSSID;
                mWifiManager.enableNetwork(mWifiConfig.networkId, true);
                //playVideo("http://10.5.5.9:8080/live/amba.m3u8");
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
        builder.setPositiveButton("OK", this);
        mWifiPasswordDlg = builder.create();
        mWifiPasswordDlg.show();
        return true;
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
        if (m_stateReceiver != null) {
            unregisterReceiver(m_stateReceiver);
            m_stateReceiver = null;
        }
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
                mMediaPlayer = new MediaPlayer(); // MediaPlayer.create(this, myuri);
                mMediaPlayer.setOnBufferingUpdateListener(this);
                mMediaPlayer.setOnCompletionListener(this);
                mMediaPlayer.setOnPreparedListener(this);
                mMediaPlayer.setOnVideoSizeChangedListener(this);
                mMediaPlayer.setOnErrorListener(this);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM);
                mMediaPlayer.setLooping(false);
                mMediaPlayer.setDisplay(holder);
                mMediaPlayer.setDataSource(this, myuri);
                mMediaPlayer.prepare();
            }
        } catch (Exception e) {
            Log.e(TAG, "error: " + e.getMessage(), e);
        }
    }

    /*
     * SurfaceHolder callbacks
     */
    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "surfaceChanged called");

    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");
        if (mConnected) {
            playVideo("http://10.5.5.9:8080/live/amba.m3u8");
        }

    }

    /*
     * callbacks
     */
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
    public Handler _handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, String.format("Handler.handleMessage(): msg=%s", msg));
            if (msg.what == 1001) // Connected to GoPro
            {
                Log.d(TAG, "Connected to GoPro");
                mConnected = true;
                new HttpCommandTask(1002).execute(command_get_state);
                //playVideo("http://10.5.5.9:8080/live/amba.m3u8");
            }
            else if (msg.what == 1002) // Connected to GoPro
            {
                Log.d(TAG, "Got GoPro state");

            }
            super.handleMessage(msg);
        }
    };

    private class HttpCommandTask extends AsyncTask<String, Void, String> {
        int m_command;

        public HttpCommandTask(int commandId) {
            super();
            m_command = commandId;
        }
        @Override
        protected String doInBackground(String... command_url) {
            try {
                return connectUrl(command_url[0]);
            }
            catch (IOException e) {
                Log.e(TAG, "Error in http command "+e);
                return "Error in http command "+e;
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "GoPro command ret: "+result);
            Message msg = Message.obtain();
            msg.what = m_command;
            //msg.setData((Bundle) result);
            _handler.sendMessage(msg);
        }
        // Http connection
        public String connectUrl(String command_url) throws IOException {

            InputStream is = null;
            int len = 500;

            // params comes from the execute() call: params[0] is the url.
            try {
                URL url = new URL(command_url);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int response = conn.getResponseCode();
                Log.d(TAG, "The response is: " + response);
                is = conn.getInputStream();

                // Convert the InputStream into a string
                String contentAsString = readIt(is, len);
                return contentAsString;
            }
            finally {
                if (is != null) {
                    is.close();
                }
            }
        }
        // Reads an InputStream and converts it to a String.
        public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
            Reader reader = null;
            reader = new InputStreamReader(stream, "UTF-8");
            char[] buffer = new char[len];
            reader.read(buffer);
            return new String(buffer);
        }
    }

    class WifiStateReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            Log.d(TAG, "onReceive state! "+intent);
            NetworkInfo nwInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(NetworkInfo.State.CONNECTED.equals(nwInfo.getState())){
                //This implies the WiFi connection is through
                String bssid = intent.getStringExtra(WifiManager.EXTRA_BSSID);
                Log.d(TAG, "Test "+mWifiConfig);
                Log.d(TAG, "Connected to "+bssid);
                if (mWifiConfig != null)
                    Log.d(TAG, "Test BSSID "+mWifiConfig.BSSID);
                if (mWifiConfig != null && bssid != null && mWifiConfig.BSSID.compareTo(bssid) == 0) {
                    Log.d(TAG, "Connected to selected!");
                    Message msg = Message.obtain();
                    msg.what = 1001;
                    GoProLiveActivity act = (GoProLiveActivity) c;
                    act._handler.sendMessage(msg);
                }

            }
        }
    }
}