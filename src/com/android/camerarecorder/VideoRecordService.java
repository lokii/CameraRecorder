package com.android.camerarecorder;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created with IntelliJ IDEA.
 * User: dk
 * Date: 14-10-12
 */
public class VideoRecordService extends Service implements Camera.PreviewCallback,
        SurfaceHolder.Callback {
    private static final String TAG = "VideoRecordService";
    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private Camera camera = null;
    private MediaCodec mMediaCodec;
    private FileOutputStream fos;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
//    private MediaRecorder mediaRecorder = null;

    @Override
    public void onCreate() {

        // Start foreground service to avoid unexpected kill
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Background Video Recorder")
                .setContentText("")
                .setSmallIcon(R.drawable.icon)
                .build();
        startForeground(1234, notification);

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        surfaceView = new SurfaceView(this);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                320, 240,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        surfaceView.getHolder().addCallback(this);

    }

    // Method called right after Surface created (initializing and starting MediaRecorder)
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        camera = Camera.open();
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setPreviewSize(320, 240);
        camera.setParameters(parameters);
        camera.setPreviewCallback(this);
        camera.unlock();

        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        initCodec();
    }

    // Stop recording and remove SurfaceView
    @Override
    public void onDestroy() {

        Toast.makeText(getBaseContext(), "Recording Stopped", Toast.LENGTH_SHORT).show();

        if (null != mMediaCodec && null != fos) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (null != camera) {
            camera.lock();
            camera.release();
        }

        windowManager.removeView(surfaceView);

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        encode(data);
    }

    private void initCodec()
    {
        try
        {
            fos = new FileOutputStream(new File("/sdcard/test.h264"), false);
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",
                320,
                240);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        mMediaCodec.configure(mediaFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        inputBuffers = mMediaCodec.getInputBuffers();
        outputBuffers = mMediaCodec.getOutputBuffers();
    }

    private void encode(byte[] data)
    {
        inputBuffers = mMediaCodec.getInputBuffers();// here changes
        outputBuffers = mMediaCodec.getOutputBuffers();

        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0)
        {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(data);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, data.length, 0, 0);
        }
        else
        {
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do
        {
            if (outputBufferIndex >= 0)
            {
                ByteBuffer outBuffer = outputBuffers[outputBufferIndex];
                System.out.println("buffer info-->" + bufferInfo.offset + "--"
                        + bufferInfo.size + "--" + bufferInfo.flags + "--"
                        + bufferInfo.presentationTimeUs);
                byte[] outData = new byte[bufferInfo.size];
                outBuffer.get(outData);
                try
                {
                    if (bufferInfo.offset != 0)
                    {
                        fos.write(outData, bufferInfo.offset, outData.length
                                - bufferInfo.offset);
                    }
                    else
                    {
                        fos.write(outData, 0, outData.length);
                    }
                    fos.flush();
                    Log.i(TAG, "out data -- > " + outData.length);
                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo,
                            0);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            {
                outputBuffers = mMediaCodec.getOutputBuffers();
            }
            else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
            {
                MediaFormat format = mMediaCodec.getOutputFormat();
            }
        } while (outputBufferIndex >= 0);

    }
}
