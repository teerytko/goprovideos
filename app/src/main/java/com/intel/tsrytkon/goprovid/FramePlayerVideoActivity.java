

package com.intel.tsrytkon.goprovid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ProgressBar;

public class FramePlayerVideoActivity extends Activity implements
        SurfaceHolder.Callback {

    private static final String TAG = "MediaPlayerDemo";
    private int mVideoWidth;
    private int mVideoHeight;
    private SurfaceView mPreview;
    private SurfaceHolder holder;
    private CustomMediaController mcontroller;
    private ProgressBar mProgress;
    private Handler handler = new Handler();
    private String path;
    private Bundle extras;
    private static final String MEDIA = "media";
    private static final int LOCAL_AUDIO = 1;
    private static final int STREAM_AUDIO = 2;
    private static final int RESOURCES_AUDIO = 3;
    private static final int LOCAL_VIDEO = 4;
    private static final int STREAM_VIDEO = 5;
    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;

    /**
     *
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_video_playback_full);
        mPreview = (SurfaceView) findViewById(R.id.fullscreen_content);
        mProgress = (ProgressBar) findViewById(R.id.progress_bar);
        holder = mPreview.getHolder();
        holder.addCallback(this);
        //holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        extras = getIntent().getExtras();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        /*
         * the MediaController will hide after 3 seconds - tap the screen to
         * make it appear again
         */
        //mcontroller.show();
        return false;
    }

    private void playVideo(String path) {
        doCleanUp();
        try {
            mProgress.setIndeterminate(false);
            ExtractMpegFrames frames = new ExtractMpegFrames(path, mPreview, mProgress);
            try {
                frames.startExtractMpegFrames();
            }
            catch (Throwable e) {
                System.out.println(e);
            }
        } catch (Exception e) {
            Log.e(TAG, "error: " + e.getMessage(), e);
        }
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "surfaceChanged called");
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");
        playVideo(extras.getString(MEDIA));
    }

    @Override
    protected void onPause() {
        super.onPause();
        doCleanUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doCleanUp();
    }

    private void doCleanUp() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        mIsVideoReadyToBePlayed = false;
        mIsVideoSizeKnown = false;
    }

    private void startVideoPlayback() {
        Log.v(TAG, "startVideoPlayback");
        holder.setFixedSize(mVideoWidth, mVideoHeight);
    }

    //mediacontroller implemented methods
    public void onRequestNextFrame() {
        int curpos = 0; //getCurrentPosition();
        int newpos = curpos + 16;
        System.out.println("onRequestNextFrame: cur pos: " + curpos);
    }

    public void onRequestPrevFrame() {
        System.out.println("onRequestPrevFrame: cur pos:"+0);
    }

}
