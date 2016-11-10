/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package com.weimade.animationcoder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;
import com.thuytrinh.android.collageviews.MultiTouchListener;
import com.thuytrinh.android.collageviews.OnGifEditedListener;
import com.weimade.animationcoder.animation.CollageView;
import com.weimade.animationcoder.gles.EglCore;
import com.weimade.animationcoder.gles.WindowSurface;
import com.weimade.animationcoder.utils.FileUtil;
import com.weimade.animationcoder.utils.MiscUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import pl.droidsonroids.gif.GifDrawable;

/**
 * Play a movie from a file on disk.  Output goes to a SurfaceView.
 * <p>
 * This is very similar to PlayMovieActivity, but the output goes to a SurfaceView instead of
 * a TextureView.  There are some important differences:
 * <ul>
 *   <li> TextureViews behave like normal views.  SurfaceViews don't.  A SurfaceView has
 *        a transparent "hole" in the UI through which an independent Surface layer can
 *        be seen.  This Surface is sent directly to the system graphics compositor.
 *   <li> Because the video is being composited with the UI by the system compositor,
 *        rather than the application, it can often be done more efficiently (e.g. using
 *        a hardware composer "overlay").  This can lead to significant battery savings
 *        when playing a long movie.
 *   <li> On the other hand, the TextureView contents can be freely scaled and rotated
 *        with a simple matrix.  The SurfaceView output is limited to scaling, and it's
 *        more awkward to do.
 *   <li> DRM-protected content can't be touched by the app (or even the system compositor).
 *        We have to point the MediaCodec decoder at a Surface that is composited by a
 *        hardware composer overlay.  The only way to do the app side of this is with
 *        SurfaceView.
 * </ul>
 * <p>
 * The MediaCodec decoder requests buffers from the Surface, passing the video dimensions
 * in as arguments.  The Surface provides buffers with a matching size, which means
 * the video data will completely cover the Surface.  As a result, there's no need to
 * use SurfaceHolder#setFixedSize() to set the dimensions.  The hardware scaler will scale
 * the video to match the view size, so if we want to preserve the correct aspect ratio
 * we need to adjust the View layout.  We can use our custom AspectFrameLayout for this.
 * <p>
 * The actual playback of the video -- sending frames to a Surface -- is the same for
 * TextureView and SurfaceView.
 */
public class PlayMovieSurfaceActivity extends Activity implements OnItemSelectedListener,
        SurfaceHolder.Callback, MoviePlayer.PlayerFeedback {
    private static final String TAG = MainActivity.TAG;



    private SurfaceView mSurfaceView;
    private String[] mMovieFiles;
    private int mSelectedMovie;
    private boolean mShowStopLabel;
    private MoviePlayer.PlayTask mPlayTask;
    private boolean mSurfaceHolderReady = false;

    //add by wei
    AspectFrameLayout aspectLayout ;
    CollageView collageView;
    int gifRotation=0,gifPivotX=10,gifPivotY=10;
    float gifScale = 1;
    private FFmpeg ffmpeg ;
    private static final int ProcessImg = 1;
    private static final int ProcessAudio = 2;
    private static String collageViewTag = "collageViewTag";
    String gifPath = FileUtil.getPath()+"/mali.gif";
    TextView processTips;

    boolean hasMergeImgError = false;
    boolean hasMergeAudioError = false;

    private String mLastMovieFile;

    private Handler handler = new Handler() {

        // 处理子线程给我们发送的消息。
        @Override
        public void handleMessage(Message msg) {

            if(msg.what == ProcessImg){

            }else if(msg.what == ProcessAudio){
                System.out.println("ProcessAudio");
                HashMap<String,String> data = (HashMap<String,String>)msg.obj;

                //声音与视频合成
                final String resPath = data.get("tmpFile")+".result.mp4";
                String[] cmdMergeAudioVideo ={
                        "-i", data.get("tmpFile")+".noaudio.mp4",
                        "-itsoffset", data.get("applyTime"),
                        "-i", data.get("musicPath"),
                        "-filter_complex", "[0:a][1:a]amix",
                        "-async" ,"1",
                        resPath,
                };
                System.out.println("CMD: "+StringUtils.join(cmdMergeAudioVideo," "));

                try {

                    ffmpeg.execute(cmdMergeAudioVideo, new ExecuteBinaryResponseHandler() {

                        @Override
                        public void onStart() {
                            processTips.setText("开始处理声音");

                        }

                        @Override
                        public void onProgress(String message) {
                            processTips.setText("渲染中。。"+message);
                            System.out.println(message);
                        }

                        @Override
                        public void onFailure(String message) {
                            System.out.println(message);
                            hasMergeAudioError = true;
                            processTips.setText("音频处理失败!");

                        }

                        @Override
                        public void onSuccess(String message) {
                            System.out.println(message);
                            processTips.setText("声音处理成功!");
                        }

                        @Override
                        public void onFinish() {
                            if(!hasMergeAudioError){
                                processTips.setText("全部处理完成!");
                            }
                            collageView.setVisibility(View.INVISIBLE);
                            mLastMovieFile = resPath;

                        }
                    });
                } catch (FFmpegCommandAlreadyRunningException e) {
                    // Handle if FFmpeg is already running
                }
            }
        };
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_movie_surface);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView = (SurfaceView) findViewById(R.id.playMovie_surface);
        mSurfaceView.getHolder().addCallback(this);

        // Populate file-selection spinner.
        Spinner spinner = (Spinner) findViewById(R.id.playMovieFile_spinner);
        // Need to create one of these fancy ArrayAdapter thingies, and specify the generic layout
        // for the widget itself.
        mMovieFiles = MiscUtils.getFiles(new File(FileUtil.getPath()), "*.mp4");

        //for test start
        mMovieFiles = new String[1];
        mMovieFiles[0] = "video.mp4";
        //for test start

        processTips = (TextView) findViewById(R.id.processTips);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, mMovieFiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner.
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        Button mAnination = (Button) findViewById(R.id.anination);
        mAnination.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if(aspectLayout.findViewWithTag(collageViewTag) == null){

                        GifDrawable gifFromResource = new GifDrawable(  gifPath );
                        collageView = new CollageView(getApplicationContext());
                        collageView.setTag(collageViewTag);
                        collageView.setVisibility(View.VISIBLE);
                        collageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        //collageView.setImageResource(R.drawable.ic_camera_filter);
                        collageView.setImageDrawable(gifFromResource);
                        MultiTouchListener touchListener = new MultiTouchListener();
                        touchListener.setOnViewChangeListener(new OnGifEditedListener(){
                            @Override
                            public void onChange(float scale, float rotation) {

                                gifScale = scale;
                                gifRotation = (int) rotation;
                            }

                            @Override
                            public void onMove(float pivotX, float pivotY) {
                                System.out.println(String.format(" pivotX:%f pivotY:%f ",pivotX,pivotY));
                                gifPivotX = (int)pivotX;
                                gifPivotY = (int)pivotY;
                            }

                        });
                        collageView.setOnTouchListener(touchListener);
                        aspectLayout.addView(collageView);
                    }else{
                        collageView.setVisibility(View.VISIBLE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });


        Button mRenderBtn = (Button) findViewById(R.id.render);
        mRenderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "Test case 1 clicked!\n");
                if(collageView == null){
                    processTips.setText("请先添加一个动贴！");
                }
                String videoPath = FileUtil.getPath() + "/" + mMovieFiles[mSelectedMovie];
                File image = new File(gifPath);
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(),bmOptions);


                String musicPath = FileUtil.getPath()+"/music.m4a";
                int animationLen = 2;
                //有形变的话，这里也会发生变化。。。
                double bWidth = Math.sin(gifRotation)*bitmap.getHeight() + Math.cos(gifRotation)*bitmap.getWidth();
                double bHeight = Math.sin(gifRotation)*bitmap.getWidth() + Math.cos(gifRotation)*bitmap.getHeight();
                int realX = gifPivotX - (int) bWidth;
                int realY = gifPivotY - (int) bHeight;
                String locationStr = gifPivotX+":"+gifPivotY;

                int startPoint = 2;

                execFFmpeg(videoPath,gifPath,musicPath,animationLen,gifScale,gifRotation,locationStr,startPoint);

            }
        });



        Button playVideoBtn = (Button) findViewById(R.id.playVideoBtn);
        playVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collageView.setVisibility(View.INVISIBLE);
                if(!StringUtils.isEmpty(mLastMovieFile) ){
                    playVideo(new File(mLastMovieFile));
                }else {
                    processTips.setText("没有渲染的视频可以看呢！");

                }
            }
        });

        updateControls();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "PlayMovieSurfaceActivity onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "PlayMovieSurfaceActivity onPause");
        super.onPause();
        // We're not keeping track of the state in static fields, so we need to shut the
        // playback down.  Ideally we'd preserve the state so that the player would continue
        // after a device rotation.
        //
        // We want to be sure that the player won't continue to send frames after we pause,
        // because we're tearing the view down.  So we wait for it to stop here.
        if (mPlayTask != null) {
            stopPlayback();
            mPlayTask.waitForStop();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // There's a short delay between the start of the activity and the initialization
        // of the SurfaceHolder that backs the SurfaceView.  We don't want to try to
        // send a video stream to the SurfaceView before it has initialized, so we disable
        // the "play" button until this callback fires.
        Log.d(TAG, "surfaceCreated");
        mSurfaceHolderReady = true;
        updateControls();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // ignore
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // ignore
        Log.d(TAG, "Surface destroyed");
    }

    /*
     * Called when the movie Spinner gets touched.
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner spinner = (Spinner) parent;
        mSelectedMovie = spinner.getSelectedItemPosition();
        clickPlayStop(null);
        Log.d(TAG, "onItemSelected: " + mSelectedMovie + " '" + mMovieFiles[mSelectedMovie] + "'");
    }

    @Override public void onNothingSelected(AdapterView<?> parent) {}

    /**
     * onClick handler for "play"/"stop" button.
     */
    public void clickPlayStop(@SuppressWarnings("unused") View unused) {
        if (mShowStopLabel) {
            Log.d(TAG, "stopping movie");
            stopPlayback();
            // Don't update the controls here -- let the task thread do it after the movie has
            // actually stopped.
            //mShowStopLabel = false;
            //updateControls();
        } else {
            if (mPlayTask != null) {
                Log.w(TAG, "movie already playing");
                return;
            }
            playVideo(new File(new File(FileUtil.getPath()), mMovieFiles[mSelectedMovie]));
        }
    }

    /**
     * Requests stoppage if a movie is currently playing.
     */
    private void stopPlayback() {
        if (mPlayTask != null) {
            mPlayTask.requestStop();
        }
    }

    @Override   // MoviePlayer.PlayerFeedback
    public void playbackStopped() {
        Log.d(TAG, "playback stopped");
        mShowStopLabel = false;
        mPlayTask = null;
        updateControls();
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button play = (Button) findViewById(R.id.play_stop_button);
        if (mShowStopLabel) {
            play.setText(R.string.stop_button_text);
        } else {
            play.setText(R.string.play_button_text);
        }
        play.setEnabled(mSurfaceHolderReady);
    }

    /**
     * Clears the playback surface to black.
     */
    private void clearSurface(Surface surface) {
        // We need to do this with OpenGL ES (*not* Canvas -- the "software render" bits
        // are sticky).  We can't stay connected to the Surface after we're done because
        // that'd prevent the video encoder from attaching.
        //
        // If the Surface is resized to be larger, the new portions will be black, so
        // clearing to something other than black may look weird unless we do the clear
        // post-resize.
        EglCore eglCore = new EglCore();
        WindowSurface win = new WindowSurface(eglCore, surface, false);
        win.makeCurrent();
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        win.swapBuffers();
        win.release();
        eglCore.release();
    }


    private void execFFmpeg(final String videoPath, String gifPath, final String musicPath, int animationLen, float scale ,
                            int rotation, String locationStr, final int applyTime){

        if(StringUtils.isEmpty(videoPath) || StringUtils.isEmpty(gifPath) || StringUtils.isEmpty(musicPath) || StringUtils.isEmpty(locationStr)){
            System.out.println("videoPath gifPath musicPath locationStr can not be empty!!" );
            return;
        }

        if(scale == 0){
            scale = 1;
        }


        ffmpeg = FFmpeg.getInstance(getApplicationContext());
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {

                @Override
                public void onStart() {
                    System.out.println("load");
                }

                @Override
                public void onFailure() {}

                @Override
                public void onSuccess() {
                    System.out.println("loadBinary success----");
                }

                @Override
                public void onFinish() {}
            });
        } catch (FFmpegNotSupportedException e) {
            // Handle if FFmpeg is not supported by device
        }

        //gif与视频合成
        // to execute "ffmpeg -version" command you just need to pass "-version"
        ArrayList filterList = new ArrayList<String>();
        //face in,out...
        int duration = applyTime + animationLen;
        filterList.add("fade=t=in:st="+applyTime+":d=0.1,fade=t=out:st="+duration+":d=0.1");
        //rotate
        filterList.add("rotate="+rotation+"*PI/180:c=none:ow=rotw(iw):oh=roth(ih)");
        //scale
        filterList.add("scale=w="+scale+"*iw:h="+scale+"*ih");
        String filterStr = TextUtils.join(",",filterList);
        String filter_complex = "[1:v] "+filterStr+" [ov]; [0:v][ov] overlay="+locationStr+" [v]";
        // example "[1:v] fade=t=in:st=1:d=0.1,fade=t=out:st=2:d=0.1,rotate=-30*PI/180:c=none:ow=rotw(iw):oh=roth(ih),scale=w=0.2*iw:h=0.5*ih [ov]; [0:v][ov] overlay=200:10 [v]",


        final String tmpFile = videoPath+(System.currentTimeMillis());
        String[] cmdMergeGifVideo ={
                "-i", videoPath,
                "-itsoffset", String.valueOf(applyTime),
                "-i", gifPath,
                "-filter_complex", filter_complex,
                "-map","[v]",
                "-map" ,"0:a",
                "-c:v", "libx264",
                "-c:a", "copy",
                "-shortest",
                "-async" ,"1",
                tmpFile+".noaudio.mp4",
        };
        System.out.println("CMD: "+StringUtils.join(cmdMergeGifVideo," "));
        try {

            ffmpeg.execute(cmdMergeGifVideo, new ExecuteBinaryResponseHandler() {

                @Override
                public void onStart() {}

                @Override
                public void onProgress(String message) {
                    System.out.println(message);
                    processTips.setText("渲染中。。"+message);
                }

                @Override
                public void onFailure(String message) {
                    System.out.println(message);
                    hasMergeImgError = true;
                    processTips.setText("图像处理失败！！");
                }

                @Override
                public void onSuccess(String message) {
                    System.out.println(message);
                    processTips.setText("图像处理成功！");
                    HashMap map = new HashMap<String,String>();
                    map.put("videoPath",videoPath);
                    map.put("tmpFile",tmpFile);
                    map.put("applyTime",String.valueOf(applyTime));
                    map.put("musicPath",musicPath);

                    Message msg = Message.obtain();
                    msg.obj = map;
                    msg.what = ProcessAudio;
                    handler.sendMessage(msg);
                }

                @Override
                public void onFinish() {
                    System.out.println("graph finish!");
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
        }
    }

    private void playVideo(File file){
        if (mPlayTask != null) {
            Log.w(TAG, "movie already playing");
            return;
        }

        Log.d(TAG, "starting movie");
        SpeedControlCallback callback = new SpeedControlCallback();
        SurfaceHolder holder = mSurfaceView.getHolder();
        Surface surface = holder.getSurface();

        // Don't leave the last frame of the previous video hanging on the screen.
        // Looks weird if the aspect ratio changes.
        clearSurface(surface);

        MoviePlayer player = null;
        try {
            player = new MoviePlayer(file, surface, callback);
        } catch (IOException ioe) {
            Log.e(TAG, "Unable to play movie", ioe);
            surface.release();
            return;
        }

        aspectLayout = (AspectFrameLayout) findViewById(R.id.playMovie_afl);
        int width = player.getVideoWidth();
        int height = player.getVideoHeight();
        aspectLayout.setAspectRatio((double) width / height);
        //holder.setFixedSize(width, height);

        mPlayTask = new MoviePlayer.PlayTask(player, this);

        mShowStopLabel = true;
        updateControls();
        mPlayTask.execute();
    }


}
