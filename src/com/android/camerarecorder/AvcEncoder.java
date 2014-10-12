package com.android.camerarecorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created with IntelliJ IDEA.
 * User: dk
 * Date: 14-10-12
 */
public class AvcEncoder {

        private static String TAG = AvcEncoder.class.getSimpleName();

        private MediaCodec mediaCodec;
        private BufferedOutputStream outputStream;


        public AvcEncoder(String fileDir) {

            Log.d(TAG, "Thread Id: " + Thread.currentThread().getId());

            File f = new File(fileDir/*Environment.getExternalStorageDirectory()*/,
                    "/video_encoded.h264");

            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(f));
                Log.i("AvcEncoder", "outputStream initialized");
            } catch (Exception e){
                e.printStackTrace();
            }

            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 960, 720);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            //mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);

            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
        }

        public void close() throws IOException {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;

            //outputStream.flush();
            outputStream.close();
        }

        public void byteWriteTest(byte[] input) {
            try {
                outputStream.write(input, 0, input.length);
            } catch(Exception e) {
                Log.d("AvcEncoder", "Outputstream write failed");
                e.printStackTrace();
            }
            Log.i("AvcEncoder", input.length + " bytes written");
        }

        // called from Camera.setPreviewCallbackWithBuffer(...) in other class
        public void offerEncoder(byte[] input) {
            try {
                ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);

                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(input);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);

                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    try {
                        outputStream.write(outData, 0, outData.length);

                    } catch(Exception e) {
                        Log.d("AvcEncoder", "Outputstream write failed");
                        e.printStackTrace();
                    }
                    //Log.i("AvcEncoder", outData.length + " bytes written");

                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

        }
    }
