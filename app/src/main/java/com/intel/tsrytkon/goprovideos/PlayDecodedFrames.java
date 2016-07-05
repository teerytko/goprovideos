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


package com.intel.tsrytkon.goprovideos;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;


/**
 */
public class PlayDecodedFrames {
    private static final String TAG = "PlayDecodedFrames";
    private static final boolean VERBOSE = true;           // lots of logging

    // where to find files (note: requires WRITE_EXTERNAL_STORAGE permission)
    private static final File FILES_DIR = Environment.getExternalStorageDirectory();
    private static final int MAX_FRAMES = 10;       // stop extracting after this many
    private PlayDecodedFramesThread mWorkerThread;
    private String mInputFile = "N/A";
    private MediaCodec decoder = null;
    private MediaExtractor extractor = null;
    private long duration = 0;
    private int trackIndex;
    boolean outputDone = false;
    boolean inputDone = false;
    long mSeeking = -1;
    ByteBuffer[] mDecoderInputBuffers;
    ByteBuffer[] mDecoderOutputBuffers;
    long mPrevFrame[] = {0, 0};
    private Surface mSurface = null;
    private MediaFormat mFormat;
    private SurfaceTexture mSurfaceTexture = null;
    private PlayDecodedFramesCallback mCb = null;


    public PlayDecodedFrames(String inputFile, Surface surface, PlayDecodedFramesCallback cb) {
        mInputFile = inputFile;
        mSurface = surface;
        mCb = cb;
        initializeExtractor();
        mWorkerThread = new PlayDecodedFramesThread(this);
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

    public void pause() { mWorkerThread.mClock.stop(); }

    public void next() throws Throwable {
        if (!mWorkerThread.mClock.mRun)
            mWorkerThread.nextFrame.release();
    }

    public void seekTo(float pos) {
        synchronized (this) {
            if (mSeeking == -1) {
                mSeeking = (long)(duration * pos);
                Log.i(TAG, "seekTo "+mSeeking);
                extractor.seekTo(mSeeking, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                decoder.flush();
                mWorkerThread.nextFrame.release(1);
            }
        }
    }

    public void seekToPrecise(long seekPos) {
        synchronized (this) {
            if (mSeeking == -1) {
                mSeeking = seekPos;
                Log.i(TAG, "seekTo "+mSeeking);
                extractor.seekTo(mSeeking, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                decoder.flush();
                mWorkerThread.nextFrame.release(1);
            }
        }
    }

    public void prev() throws Throwable {
        if (!mWorkerThread.mClock.mRun && mSeeking == -1) {
            Log.d(TAG, "Seek to PrevFrame at  " + mPrevFrame[0] +", " + mPrevFrame[1]);
            // The possible situation where the previous
            // sync is done on sync frame, so we don't have info about previous frame
            if (mPrevFrame[0] == 0 && mPrevFrame[1] > 0)
                mPrevFrame[0] = mPrevFrame[1] - 100;
            mPrevFrame[1] = 0;
            this.seekToPrecise(mPrevFrame[0]);

        }
    }

    public void reset() {
        this.pause();
        decoder.stop();
        initializeExtractor();
        mWorkerThread = new PlayDecodedFramesThread(this);
        mWorkerThread.start();
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

            mCb.setMaxProgress((int)duration);
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
        do {
            synchronized (this) {
                if (VERBOSE) Log.d(TAG, "loop mSeeking="+mSeeking);
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
                                        chunkSize + " sampleTime: " + presentationTimeUs);
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
                        if (VERBOSE) Log.i(TAG, "no output from decoder available");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not important for us, since we're using Surface
                        if (VERBOSE) Log.i(TAG, "decoder output buffers changed");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoder.getOutputFormat();
                        if (VERBOSE) Log.i(TAG, "decoder output format changed: " + newFormat);
                    } else if (decoderStatus < 0) {
                        Log.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                    } else { // decoderStatus >= 0
                        ByteBuffer outputBuf = decoder.getOutputBuffer(decoderStatus);
                        if (VERBOSE) Log.i(TAG, "surface decoder given buffer " + decoderStatus +
                                " (time=" + info.presentationTimeUs + ")");
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            if (VERBOSE) Log.d(TAG, "output EOS");
                            outputDone = true;
                            mCb.onEndOfStream();

                        }
                        boolean doRender = (info.size != 0);

                        if (mSeeking != -1)
                            if (info.presentationTimeUs < mSeeking)
                                doRender = false;
                            else
                                mSeeking = -1;

                        if (doRender && mCb != null)
                            mCb.onProgress((int) info.presentationTimeUs);

                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        mPrevFrame[0] = mPrevFrame[1];
                        mPrevFrame[1] = info.presentationTimeUs;
                    }
                }
            }
        } while (mSeeking > 0);
   }
    /**
     */
    private static class PlayDecodedFramesThread implements Runnable {
        private Throwable mThrowable;
        private PlayDecodedFrames mDecoder;
        public float mSpeed = 1;
        public final Semaphore nextFrame = new Semaphore(1, true);
        public long sleepTime = 100;
        public ExtractClock mClock;
        private Thread mThread = null;
        private boolean mRun = true;

        private static class ExtractClock implements Runnable {
            PlayDecodedFramesThread mExtractThread;
            private boolean mRun = true;
            private Thread mThread;

            private ExtractClock(PlayDecodedFramesThread extractThread) {
                mExtractThread = extractThread;
            }

            public void start() {
                mRun = true;
                mThread = new Thread(this, "extract clock");
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
                        if (VERBOSE) Log.i(TAG, "Running clock!! "+mExtractThread.sleepTime*mExtractThread.mSpeed);
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

        private PlayDecodedFramesThread(PlayDecodedFrames decoder) {
            mDecoder = decoder;
            mDecoder.outputDone = false;
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
                    if (VERBOSE) Log.i(TAG, "Main loop");
                    nextFrame.acquire();
                    if (VERBOSE) Log.i(TAG, "Main loop - next frame");
                    mDecoder.doExtract(mDecoder.extractor,
                            mDecoder.trackIndex,
                            mDecoder.decoder,
                            info);
                    if (VERBOSE) Log.i(TAG, "Main loop - frame done");
                    sleepTime = (info.presentationTimeUs - prevTime) / 1000;
                    if (VERBOSE) Log.i(TAG, "sleepTime: " + sleepTime);
                    prevTime = info.presentationTimeUs;
                }
                Log.i(TAG, "Exit WorkThread");
                mClock.stop();
            } catch (Throwable th) {
                th.printStackTrace();
                Log.e(TAG, String.valueOf(th));
                mThrowable = th;
            }
            finally {
                mClock.stop();
                //mDecoder.doCleanup();
            }
        }
    }
}