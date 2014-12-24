package com.intel.tsrytkon.goprovid;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController;

import com.intel.tsrytkon.goprovid.R;

import java.lang.reflect.Field;


/**
 * Created by tsrytkon on 11/24/14.
 */

/**
 * Custom MediaController that fixes issue with controls appearing offset on pre 4.3 devices
 * and shows how to add additional functionality such as fullscreen button
 */
public class CustomMediaController extends MediaController {

    public static interface OnMediaControllerInteractionListener {
        void onRequestNextFrame();
        void onRequestPrevFrame();
    }

    Context mContext;
    private OnMediaControllerInteractionListener mListener;

    public CustomMediaController(Context context) {
        super(context);
        mContext = context;
    }

    public void setListener(OnMediaControllerInteractionListener listener) {
        mListener = listener;
    }

    @Override
    public void setAnchorView(View view) {
        super.setAnchorView(view);

        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        frameParams.gravity = Gravity.TOP;
        FrameLayout.LayoutParams frameParamsN = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        frameParamsN.gravity = Gravity.TOP | Gravity.RIGHT;

        Button prevButton = new Button(mContext);
        prevButton.setText("Prev");

        Button nextButton = new Button(mContext);
        nextButton.setText("Next");
        //nextButton.

        nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if(mListener != null) {
                    mListener.onRequestNextFrame();
                }
            }
        });
        prevButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if(mListener != null) {
                    mListener.onRequestPrevFrame();
                }
            }
        });

        addView(prevButton, frameParams);
        addView(nextButton, frameParamsN);
    }

    @Override
    public void show(int timeout) {
        super.show(timeout);
        // fix pre Android 4.3 strange positioning when used in Fragments
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                Field field1 = MediaController.class.getDeclaredField("mAnchor");
                field1.setAccessible(true);
                View mAnchor = (View)field1.get(this);

                Field field2 = MediaController.class.getDeclaredField("mDecor");
                field2.setAccessible(true);
                View mDecor = (View)field2.get(this);

                Field field3 = MediaController.class.getDeclaredField("mDecorLayoutParams");
                field3.setAccessible(true);
                WindowManager.LayoutParams mDecorLayoutParams = (WindowManager.LayoutParams)field3.get(this);

                Field field4 = MediaController.class.getDeclaredField("mWindowManager");
                field4.setAccessible(true);
                WindowManager mWindowManager = (WindowManager)field4.get(this);

                // NOTE: this appears in its own Window so co-ordinates are screen co-ordinates
                int [] anchorPos = new int[2];
                mAnchor.getLocationOnScreen(anchorPos);

                // we need to know the size of the controller so we can properly position it
                // within its space
                mDecor.measure(MeasureSpec.makeMeasureSpec(mAnchor.getWidth(), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(mAnchor.getHeight(), MeasureSpec.AT_MOST));

                mDecor.setPadding(0,0,0,0);

                WindowManager.LayoutParams p = mDecorLayoutParams;
                p.verticalMargin = 0;
                p.horizontalMargin = 0;
                p.width = mAnchor.getWidth();
                p.gravity = Gravity.LEFT|Gravity.TOP;
                p.x = anchorPos[0];// + (mAnchor.getWidth() - p.width) / 2;
                p.y = anchorPos[1] + mAnchor.getHeight() - mDecor.getMeasuredHeight();
                mWindowManager.updateViewLayout(mDecor, mDecorLayoutParams);


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}