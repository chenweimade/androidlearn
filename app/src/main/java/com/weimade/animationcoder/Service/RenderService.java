package com.weimade.animationcoder.Service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.weimade.animationcoder.VideoEditor;

/**
 * Created by wei on 13/11/2016.
 */

public class RenderService extends Service {

    private static final String TAG = "HelloService";

    private boolean isRunning  = false;

    @Override
    public void onCreate() {
        Log.i(TAG, "Service onCreate"+ Thread.currentThread().getId());

        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(TAG, "Service onStartCommand"+ Thread.currentThread().getId());

        //Creating new thread for my service
        //Always write your long running tasks in a separate thread, to avoid ANR
        new Thread(new Runnable() {
            @Override
            public void run() {

                Log.i(TAG, "Service onStartCommand"+ Thread.currentThread().getId());
                VideoEditor editor = new VideoEditor();
                try {
                    editor.testExtractDecodeEditEncodeMuxAudioVideo();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
                //Your logic that service will perform will be placed here
                //In this example we are just looping and waits for 1000 milliseconds in each loop

                //Stop service once it finishes its task
                stopSelf();
            }
        }).start();

        return Service.START_STICKY;
    }


    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return null;
    }

    @Override
    public void onDestroy() {

        isRunning = false;

        Log.i(TAG, "Service onDestroy");
    }
}