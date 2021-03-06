package com.weimade.animationcoder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.weimade.animationcoder.utils.FileUtil;
import com.weimade.animationcoder.utils.MiscUtils;
import com.weimade.animationcoder.utils.MsgUtil;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "AppCompatActivity";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    public  static String INIT_FLAG = "abcFlag";// do not change
    Button goPlayerBtn ;
    Button goCaptureBtn ;

    TextView tips;
    final   int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 100;
    SharedPreferences prefs ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        goCaptureBtn = (Button) findViewById(R.id.captureBtn);
        goCaptureBtn.setVisibility(View.INVISIBLE);
        goCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CameraCaptureActivity.class));
            }
        });

        tips = (TextView) findViewById(R.id.sample_text);
        tips.setText("初始化中，请稍后");

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(INIT_FLAG, false)) {
            // git
            initApp();
            // mark first time has runned.

        }else {
            //goCaptureBtn.setVisibility(View.VISIBLE);
            tips.setVisibility(View.INVISIBLE);
        }

        goPlayerBtn = (Button) findViewById(R.id.goPlayerBtn);
        goPlayerBtn.setVisibility(View.INVISIBLE);
        goPlayerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PlayMovieSurfaceActivity.class));
            }
        });


        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            if ( !ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                return;
            }
        }


        String[] mMovieFiles = MiscUtils.getFiles(new File(FileUtil.getPath()), "*.mp4");
        if(mMovieFiles.length > 0){//has inited
            //check the files count
            goPlayerBtn.setVisibility(View.VISIBLE);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    private void initApp(){
        //http://112.74.80.186:9999/test.zip
        //get test gif,music from server
        String url = "http://112.74.80.186:9999/mali.zip";
        final String targetFile = "mali.zip";
        final File target = new File(FileUtil.getPath(), targetFile);
        AQuery aq = new AQuery(this);
        aq.progress(R.id.progress).download(url,target ,new AjaxCallback<File>() {
            public void callback(String url, File file, AjaxStatus status) {
                if(file != null){
                    MsgUtil.toastMsg(getApplicationContext(),"测试素材文件下载成功");
                    try {
                        FileUtil.unzip(target.getAbsolutePath(),FileUtil.getPath()+"/");
                    } catch (IOException e) {
                        MsgUtil.toastMsg(getApplicationContext(),"错误：解压素材文件失败！！！");
                        e.printStackTrace();
                    }
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(INIT_FLAG, true);
                    editor.commit();
                    goPlayerBtn.setVisibility(View.VISIBLE);


                    startActivity(new Intent(MainActivity.this, PlayMovieSurfaceActivity.class));
                    //goCaptureBtn.setVisibility(View.VISIBLE);
                }else{
                    MsgUtil.toastMsg(getApplicationContext(),"错误：测试素材文件下载失败！！！");
                    //goCaptureBtn.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
