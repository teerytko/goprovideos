package com.intel.tsrytkon.goprovideos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tsrytkon on 3/8/15.
 */
public class GoProLiveActivity extends Activity implements
View.OnClickListener, TextureView.SurfaceTextureListener, DialogInterface.OnClickListener {

    private boolean VERBOSE = false;
    private static final String TAG = "GoProLiveActivity";
    private TextureView mPreview;
    private ImageButton mRecord;
    private Bundle extras;
    private WifiManager mWifiManager;
    ArrayList<String> mWifis = new ArrayList();
    WifiConfiguration mWifiConfig = null;
    List<ScanResult> mWifiScanList;
    ScanResult mSelected = null;
    AlertDialog mSelectWifiDlg = null;
    AlertDialog mWifiPasswordDlg = null;
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
        this.scanWifi(getApplicationContext());
    }


    public void scanWifi(Context context) {
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
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
                return mWifiManager.enableNetwork(mWifiConfig.networkId, true);
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

}
