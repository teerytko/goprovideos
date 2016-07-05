package com.intel.tsrytkon.goprovideos;

/**
 * Created by tsrytkon on 3/20/16.
 */
public interface PlayDecodedFramesCallback {

    public void setMaxProgress(int maxProgress);
    public void onProgress(int current);
    public void onEndOfStream();

}
