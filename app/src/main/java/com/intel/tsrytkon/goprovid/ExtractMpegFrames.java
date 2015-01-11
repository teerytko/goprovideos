/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.intel.tsrytkon.goprovid;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.ProgressBar;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.Semaphore;

//20131122: minor tweaks to saveFrame() I/O
//20131205: add alpha to EGLConfig (huge glReadPixels speedup); pre-allocate pixel buffers;
//          log time to run saveFrame()
//20140123: correct error checks on glGet*Location() and program creation (they don't set error)
//20140212: eliminate byte swap

/**
 * Extract frames from an MP4 using MediaExtractor, MediaCodec, and GLES.  Put a .mp4 file
 * in "/sdcard/source.mp4" and look for output files named "/sdcard/frame-XX.png".
 * <p>
 * This uses various features first available in Android "Jellybean" 4.1 (API 16).
 * <p>
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 */
public class ExtractMpegFrames {
    private static final String TAG = "ExtractMpegFrames";
    private static final boolean VERBOSE = true;           // lots of logging

    // where to find files (note: requires WRITE_EXTERNAL_STORAGE permission)
    private static final File FILES_DIR = Environment.getExternalStorageDirectory();
    private static final int MAX_FRAMES = 10;       // stop extracting after this many
    private ProgressBar mProgress = null;
    private ExtractMpegFramesThread mWorkerThread;
    private String mInputFile = "N/A";
    private MediaCodec decoder = null;
    private MediaExtractor extractor = null;
    private int saveWidth = 640;
    private int saveHeight = 480;
    private long duration = 0;
    private int trackIndex;
    boolean outputDone = false;
    boolean inputDone = false;
    long mSeeking = -1;
    ByteBuffer[] mDecoderInputBuffers;
    private Surface mSurface = null;
    private MediaFormat mFormat;

    public ExtractMpegFrames(String inputFile, Surface surface, ProgressBar progress) {
        mInputFile = inputFile;
        mSurface = surface;
        mProgress = progress;
        initializeExtractor();
        mWorkerThread = new ExtractMpegFramesThread(this);
        mWorkerThread.start();
    }

    private void doCleanup() {
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
    }
    public void setSpeed(float speed) {
        mWorkerThread.mSpeed = speed;
    }

    public void play() throws Throwable {
        mWorkerThread.mClock.start();
    }

    public void pause() throws Throwable {
        mWorkerThread.mClock.stop();
    }

    public void next() throws Throwable {
        mWorkerThread.nextFrame.release();
    }

    public void seekTo(float pos) {
        synchronized (this) {
            if (mSeeking == -1) {
                mSeeking = (long)(duration * pos);
                Log.i(TAG, "seekTo "+mSeeking);
                extractor.seekTo(mSeeking, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                decoder.flush();
                mWorkerThread.nextFrame.release(10);
            }
        }
    }
    public void prev() throws Throwable {
        //mWrapper.pause();
    }

    public int getVideoWidth() {
        return mFormat.getInteger(MediaFormat.KEY_WIDTH);
    }
    public int getVideoHeight() {
        return mFormat.getInteger(MediaFormat.KEY_HEIGHT);
    }
    public int getVideoRotation() {
        Log.d(TAG, "getVideoRotation");
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        reader.setDataSource(mInputFile);
        String rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        Log.d(TAG, "getVideoRotation: "+rotation);
        reader.release();
        return Integer.parseInt(rotation);
    }

    /**
     * Selects the video track, if any.
     *
     * @return the track index, or -1 if no video track is found.
     */
    private int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected tracks " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }
        return -1;
    }

    /**
     */
    private void initializeExtractor() {
        try {
            System.out.println("extractMpegFrames input: "+mInputFile);
            File inputFile = new File(mInputFile);   // must be an absolute path
            // The MediaExtractor error messages aren't very useful.  Check to see if the input
            // file exists so we can throw a better one if it's not there.
            if (!inputFile.canRead()) {
                throw new FileNotFoundException("Unable to read " + inputFile);
            }
            extractor = new MediaExtractor();
            extractor.setDataSource(inputFile.toString());
            trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + inputFile);
            }
            extractor.selectTrack(trackIndex);
            mFormat = extractor.getTrackFormat(trackIndex);
            duration = mFormat.getLong(MediaFormat.KEY_DURATION);
            mProgress.setMax((int)duration);
            Log.d(TAG, "Video size is " + mFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                    mFormat.getInteger(MediaFormat.KEY_HEIGHT)+" duration: "+duration);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }
    private void initializeDecoder() throws IOException {
        try {
            // Create a MediaCodec decoder, and configure it with the MediaFormat from the
            // extractor.  It's very important to use the format from the extractor because
            // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
            String mime = mFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(mFormat, mSurface, null, 0);
            decoder.start();
            mDecoderInputBuffers = decoder.getInputBuffers();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    /**
     * Work loop.
     */
    void doExtract(MediaExtractor extractor,
                   int trackIndex,
                   MediaCodec decoder,
                   MediaCodec.BufferInfo info
                   ) throws IOException {
        final int TIMEOUT_USEC = 10000;
        int inputChunk = 0;
        int decodeCount = 0;
        synchronized (this) {
            if (VERBOSE) Log.d(TAG, "loop");
            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = mDecoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        if (extractor.getSampleTrackIndex() != trackIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " +
                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize + " sampleTime: "+presentationTimeUs);
                        }
                        inputChunk++;
                        extractor.advance();
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }
            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Log.i(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.i(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.i(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    Log.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else { // decoderStatus >= 0
                    if (VERBOSE) Log.i(TAG, "surface decoder given buffer " + decoderStatus +
                            " (time=" + info.presentationTimeUs + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        outputDone = true;
                    }
                    if (mSeeking > 0)
                        mSeeking = -1;

                    boolean doRender = (info.size != 0);
                    if (mProgress != null)
                        mProgress.setProgress((int)info.presentationTimeUs);
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                }
            }
        }
   }
    /**
     */
    private static class ExtractMpegFramesThread implements Runnable {
        private Throwable mThrowable;
        private ExtractMpegFrames mDecoder;
        public float mSpeed = 1;
        public final Semaphore nextFrame = new Semaphore(1, true);
        public long sleepTime = 100;
        public ExtractClock mClock;
        private Thread mThread = null;
        private boolean mRun = true;

        private static class ExtractClock implements Runnable {
            ExtractMpegFramesThread mExtractThread;
            private boolean mRun = true;
            private Thread mThread;

            private ExtractClock(ExtractMpegFramesThread extractThread) {
                mExtractThread = extractThread;
            }

            public void start() {
                mRun = true;
                mThread = new Thread(this, "codec clock");
                mThread.start();
            }
            public void stop() {
                mRun = false;
                try {
                    mThread.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void run() {
                try {
                    while (mRun) {
                        Log.i(TAG, "Running clock!! "+mExtractThread.sleepTime*mExtractThread.mSpeed);
                        // Make sure that the sleep time is something more than 0
                        // To avoid crazy loops
                        if (mExtractThread.sleepTime>0)
                            Thread.sleep((int)(mExtractThread.sleepTime*mExtractThread.mSpeed));
                        else
                            Thread.sleep(50);
                        mExtractThread.nextFrame.release();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private ExtractMpegFramesThread(ExtractMpegFrames decoder) {
            mDecoder = decoder;
            mClock = new ExtractClock(this);
        }
        public void start() {
            mThread = new Thread(this, "codec clock");
            mThread.start();
        }
        public void stop() {
            mRun = false;
            try {
                mThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                mDecoder.initializeDecoder();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long frameSaveTime = 0;
                long prevTime = 0;

                while (!mDecoder.outputDone && mRun) {
                    Log.i(TAG, "Main loop");
                    nextFrame.acquire();
                    Log.i(TAG, "Main loop - next frame");
                    mDecoder.doExtract(mDecoder.extractor,
                            mDecoder.trackIndex,
                            mDecoder.decoder,
                            info);
                    Log.i(TAG, "Main loop - frame done");
                    sleepTime = (info.presentationTimeUs - prevTime) / 1000;
                    prevTime = info.presentationTimeUs;
                }
                mClock.stop();
            } catch (Throwable th) {
                Log.e(TAG, String.valueOf(th));
                mThrowable = th;
            }
            finally {
                mClock.stop();
                mDecoder.doCleanup();
            }
        }
    }
}