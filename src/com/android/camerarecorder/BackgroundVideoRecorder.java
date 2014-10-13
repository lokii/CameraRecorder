package com.android.camerarecorder;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StatFs;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: dk
 * Date: 14-10-9
 */
public class BackgroundVideoRecorder extends Service implements SurfaceHolder.Callback, Camera.PreviewCallback, MediaRecorder.OnInfoListener {

    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private Camera camera = null;
    private MediaRecorder mediaRecorder = null;
    private SurfaceHolder mSurfaceHolder;

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
                480, 640,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        surfaceView.getHolder().addCallback(this);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // Method called right after Surface created (initializing and starting MediaRecorder)
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
/*        mSurfaceHolder = surfaceHolder;

        mediaRecorder = new MediaRecorder();
        initRecorder();

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(getBaseContext(), "Recording Started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            mediaRecorder.release();
        }*/

    }

    int mCount;
    private void initRecorder() {
        if (null == camera) {
            camera = Camera.open();
            Camera.Size size = getCameraPreviewSize( camera );
            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize( size.width, size.height );
            camera.setParameters( params );
            try {
                camera.setPreviewDisplay(mSurfaceHolder);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
//            camera.setPreviewCallback(this);
        } else {
            camera.lock();
            try {
                camera.setPreviewDisplay(mSurfaceHolder);
            } catch (IOException e) {
                camera.release();
                e.printStackTrace();
            }
//            camera.setPreviewCallback(this);
        }

        if (camera.getParameters().isVideoSnapshotSupported()) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    camera.takePicture(null, null, new Camera.PictureCallback() {

                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            Camera.Parameters ps = camera.getParameters();
                            if (ps.getPictureFormat() == PixelFormat.JPEG) {
                                //存储拍照获得的图片
                                String path = save(data);
                                //将图片交给Image程序处理
/*                                Uri uri = Uri.fromFile(new File(path));
                                Intent intent = new Intent();
                                intent.setAction("android.intent.action.VIEW");
                                intent.setDataAndType(uri, "image/jpeg");
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);*/
                            }
                        }
                    });
                }
            }, 1000);
        }
        camera.unlock();
        camera.startPreview();
//        mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mediaRecorder.setAudioEncoder(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mediaRecorder.setCaptureRate(30);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(4800000);
        mediaRecorder.setVideoSize(1280, 720);
//        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
/*        mediaRecorder.setOutputFile(
                Environment.getExternalStorageDirectory()+"/"+
                        DateFormat.format("yyyy-MM-dd_kk-mm-ss", new Date().getTime())+
                        ".mp4"
        );*/

        String filePath = String.format("/sdcard/record%d.mp4", mCount++);
        mediaRecorder.setOutputFile(filePath);
        mediaRecorder.setMaxDuration(5000);
        mediaRecorder.setOnInfoListener(this);
    }
    private String save(byte[] data){
        String path = "/sdcard/"+System.currentTimeMillis()+".jpg";
        try {
            //判断是否装有SD卡
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                //判断SD卡上是否有足够的空间
                String storage = Environment.getExternalStorageDirectory().toString();
                StatFs fs = new StatFs(storage);
                long available = fs.getAvailableBlocks()*fs.getBlockSize();
                if(available<data.length){
                    //空间不足直接返回空
                    return null;
                }
                File file = new File(path);
                if(!file.exists())
                    //创建文件
                    file.createNewFile();
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return path;
    }

    private Camera.Size getCameraPreviewSize( Camera camera ) {

        Camera.Parameters params = camera.getParameters();
        List<Camera.Size> supportedSizes = params.getSupportedPreviewSizes();

//        Rect frame = mSurfaceHolder.getSurfaceFrame();
        int width = 480;//frame.width();
        int height = 640;//frame.height();

        for (Camera.Size size : supportedSizes) {
            if ( size.width >= width || size.height >= height ) {
                return size;
            }
        }

        return supportedSizes.get( 0 );
    }

    // Stop recording and remove SurfaceView
    @Override
    public void onDestroy() {

        Toast.makeText(getBaseContext(), "Recording Stopped", Toast.LENGTH_SHORT).show();
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();

        camera.lock();
        camera.release();

        windowManager.removeView(surfaceView);

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        mSurfaceHolder = surfaceHolder;

        mediaRecorder = new MediaRecorder();
        initRecorder();

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(getBaseContext(), "Recording Started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            mediaRecorder.release();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (null != data) {

        }
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED == what) {
            mr.stop();
            mr.reset();

            initRecorder();
            try {
                mr.prepare();
                mr.start();
                Toast.makeText(this, "Record Again.", Toast.LENGTH_SHORT);
            } catch (IOException e) {
                e.printStackTrace();
                mediaRecorder.release();
            }
        }
    }
}
