

package com.intel.tsrytkon.goprovideos;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;

public class FramePlayerVideoActivity extends Activity implements
        SurfaceHolder.Callback, View.OnClickListener, View.OnTouchListener,
        TextureView.SurfaceTextureListener, PlayDecodedFramesCallback {

    private static final int END_OF_STREAM = 1;
    private boolean VERBOSE = true;
    private static final String TAG = "FramePlayerVideo";
    private int mVideoWidth;
    private int mVideoHeight;
    private TextureView mPreview;
    private ProgressBar mProgress;
    private ImageButton mPlay;
    private ImageButton mNext;
    private ImageButton mPrev;
    private Button mSpeed;
    private Bundle extras;
    private static final String MEDIA = "media";
    private PlayDecodedFrames mPlayer;
    private SurfaceTexture mSurfaceTexture = null;
    private Handler mHandler = null;
    private String mCurPath;
    private Surface mSurface = null;
    private int curSpeed = 0;

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
        mProgress = (ProgressBar) findViewById(com.intel.tsrytkon.goprovideos.R.id.progress_bar);
        mProgress.setOnTouchListener(this);
        mPlay = (ImageButton) findViewById(com.intel.tsrytkon.goprovideos.R.id.action_play);
        mPlay.setOnClickListener(this);
        mNext = (ImageButton) findViewById(com.intel.tsrytkon.goprovideos.R.id.action_next);
        mNext.setOnClickListener(this);
        mPrev = (ImageButton) findViewById(com.intel.tsrytkon.goprovideos.R.id.action_prev);
        mPrev.setOnClickListener(this);
        mSpeed = (Button) findViewById(com.intel.tsrytkon.goprovideos.R.id.action_speed);
        mSpeed.setOnClickListener(this);
        extras = getIntent().getExtras();

        // Defines a Handler object that's attached to the UI thread
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    // The decoding is done
                    case END_OF_STREAM:
                        System.out.println("EOS reached! Replay");
                        mPlay.setSelected(false);
                        mPlayer.reset();
                        mPlayer.setSpeed(curSpeed);
                        break;
                    default:
                        super.handleMessage(inputMessage);
                }
            }
        };
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        // There's a short delay between the start of the activity and the initialization
        // of the SurfaceTexture that backs the TextureView.  We don't want to try to
        // send a video stream to the TextureView before it has initialized, so we disable
        // the "play" button until this callback fires.
        Log.d(TAG, "SurfaceTexture ready (" + width + "x" + height + ")");
        playVideo(extras.getString(MEDIA));
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        Log.d(TAG, "SurfaceTexture destroyed");
        mPlayer.pause();
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
            if (v == mPlay) {
                Log.i(TAG, "User clicked Play/pause");
                    if (v.isSelected()) {
                        v.setSelected(false);
                        mPlayer.pause();
                    }
                    else {
                        v.setSelected(true);
                        mPlayer.play();
                    }
            }
            else if (v == mNext) {
                Log.i(TAG, "User clicked Next");
                mPlayer.next();
            }
            else if (v == mPrev) {
                Log.i(TAG, "User clicked Prev");
                mPlayer.prev();
            }
            else if (v == mSpeed) {
                curSpeed = Character.getNumericValue(mSpeed.getText().charAt(2));
                Log.i(TAG, "User clicked Speed = "+mSpeed.getText());
                curSpeed = curSpeed * 2;
                if (curSpeed > 10)
                    curSpeed = 1;
                Log.i(TAG, "Set Speed = "+curSpeed);
                mPlayer.setSpeed(curSpeed);
                mSpeed.setText("1/"+curSpeed);
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
            mPlayer.seekTo(pos);
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
        Log.i(TAG, "playVideo: " + path);
        mCurPath = path;
        mProgress.setIndeterminate(false);
        mSurfaceTexture = mPreview.getSurfaceTexture();
        mSurface = new Surface(mSurfaceTexture);
        mPlayer = new PlayDecodedFrames(path, mSurface, this);
        adjustAspectRatio(
                mPlayer.getVideoWidth(),
                mPlayer.getVideoHeight(),
                mPlayer.getVideoRotation());
        try {
            mPlayer.play();
            mPlay.setSelected(true);

        }
        catch (Throwable e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    private void rePlayVideo() {
        doCleanUp();
        Log.i(TAG, "rePlayVideo: " + mCurPath);
        mProgress.setIndeterminate(false);
        mPlayer = new PlayDecodedFrames(mCurPath, mSurface, this);
        try {
            mPlayer.play();
            mPlay.setSelected(true);
        }
        catch (Throwable e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    private void adjustAspectRatio(int videoWidth, int videoHeight, int rotation) {
        int viewWidth = mPreview.getWidth();
        int viewHeight = mPreview.getHeight();
        boolean reverse = false;
        double aspectRatio = (double) videoHeight / videoWidth;
        if (rotation == 90 || rotation == 270) {
            aspectRatio =(double) videoWidth / videoHeight;
        }

        int newWidth, newHeight;
        Log.v(TAG, "viewHeight="+viewHeight+" viewWidth="+viewWidth);
        if (viewHeight > (int) (viewWidth)) {
            // limited by narrow width; restrict height
            Log.v(TAG, "limited by narrow width; restrict height");
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);

        } else {
            // limited by short height; restrict width
            Log.v(TAG, "limited by short height; restrict width");
            newHeight = viewHeight;
            newWidth = (int) (viewHeight / aspectRatio);
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;
        Log.v(TAG, "video=" + videoWidth + "x" + videoHeight +
                " aspect=" + aspectRatio +
                " view=" + viewWidth + "x" + viewHeight +
                " newView=" + newWidth + "x" + newHeight +
                " off=" + xoff + "," + yoff +
                " rotation="+rotation);

        Matrix txform = new Matrix();
        mPreview.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        txform.postTranslate(xoff, yoff);
        mPreview.setTransform(txform);
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "surfaceChanged called");
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Display display = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        Log.d(TAG, "surfaceCreated called "+rotation);
        playVideo(extras.getString(MEDIA));
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        //holder.setFixedSize(mVideoWidth, mVideoHeight);
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

    public void setMaxProgress(int maxProgress) {
        mProgress.setMax(maxProgress);

    }

    public void onProgress(int current) {
        mProgress.setProgress(current);
    }

    public void onEndOfStream() {
        Message m = Message.obtain(mHandler, END_OF_STREAM);
        mHandler.sendMessage(m);
    }

}
