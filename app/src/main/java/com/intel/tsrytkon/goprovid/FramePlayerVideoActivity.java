

package com.intel.tsrytkon.goprovid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

public class FramePlayerVideoActivity extends Activity implements
        SurfaceHolder.Callback, View.OnClickListener, View.OnTouchListener {

    private static final String TAG = "MediaPlayerDemo";
    private int mVideoWidth;
    private int mVideoHeight;
    private SurfaceView mPreview;
    private SurfaceHolder holder;
    private CustomMediaController mcontroller;
    private ProgressBar mProgress;
    private ImageButton mPlay;
    private ImageButton mNext;
    private ImageButton mPrev;
    private Handler handler = new Handler();
    private String path;
    private Bundle extras;
    private static final String MEDIA = "media";
    ExtractMpegFrames mFrames;

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
        mProgress.setOnTouchListener(this);
        mPlay = (ImageButton) findViewById(R.id.action_play);
        mPlay.setOnClickListener(this);
        mNext = (ImageButton) findViewById(R.id.action_next);
        mNext.setOnClickListener(this);
        mPrev = (ImageButton) findViewById(R.id.action_prev);
        mPrev.setOnClickListener(this);
        holder = mPreview.getHolder();
        holder.addCallback(this);
        //holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        extras = getIntent().getExtras();
    }

    @Override
    public void onClick(View v) {
        try {
            if (v == mPlay) {
                Log.i(TAG, "User clicked Play/pause");
                    if (v.isSelected()) {
                        v.setSelected(false);
                        mFrames.pause();
                    }
                    else {
                        v.setSelected(true);
                        mFrames.play(1);
                    }
            }
            else if (v == mNext) {
                Log.i(TAG, "User clicked Next");
                mFrames.next();
            }
            else if (v == mPrev) {
                Log.i(TAG, "User clicked Prev");
                mFrames.prev();
            }

        }
        catch (Throwable t) {
            Log.e(TAG, t.toString());
        }
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v == mProgress) {
            float pos = event.getX()/v.getWidth();
            Log.i(TAG, "User touched progress - "+pos);
            mFrames.seekTo(pos);
        }
        return true;
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
            mFrames = new ExtractMpegFrames(path, mPreview, mProgress);
            try {
                mFrames.play(1);
                mPlay.setSelected(true);

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
