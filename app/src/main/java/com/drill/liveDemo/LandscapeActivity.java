package com.drill.liveDemo;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.Toast;
import android.view.WindowManager;
import android.util.Log;
import android.os.Build;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.Poi;
import com.baidu.location.PoiRegion;
import com.drill.liveDemo.ui.MultiToggleImageButton;
import com.drill.liveDemo.ui.GPSService;
import com.laifeng.sopcastsdk.camera.CameraListener;
import com.laifeng.sopcastsdk.configuration.AudioConfiguration;
import com.laifeng.sopcastsdk.configuration.CameraConfiguration;
import com.laifeng.sopcastsdk.configuration.VideoConfiguration;
import com.laifeng.sopcastsdk.entity.Watermark;
import com.laifeng.sopcastsdk.entity.WatermarkPosition;
import com.laifeng.sopcastsdk.stream.packer.rtmp.RtmpPacker;
import com.laifeng.sopcastsdk.stream.sender.rtmp.RtmpSender;
import com.laifeng.sopcastsdk.ui.CameraLivingView;
import com.laifeng.sopcastsdk.utils.SopCastLog;
import com.laifeng.sopcastsdk.video.effect.GrayEffect;
import com.laifeng.sopcastsdk.video.effect.NullEffect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.io.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Message;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.*;
import org.apache.http.impl.client.*;

import android.content.pm.PackageManager;

import com.drill.liveDemo.baiduGps.LocationService;

import android.app.Application;
import android.app.Service;
import android.os.Vibrator;

import static android.net.NetworkInfo.State.CONNECTED;
import static com.laifeng.sopcastsdk.constant.SopCastConstant.TAG;

public class LandscapeActivity extends Activity {
    private CameraLivingView mLFLiveView;
    private MultiToggleImageButton mFlashBtn;
    private MultiToggleImageButton mFaceBtn;
    private MultiToggleImageButton midBtn;
    private MultiToggleImageButton mgpsBtn;
    private ImageButton mbackBtn;
    private Switch mOrientationSwitch;
    private GestureDetector mGestureDetector;
    private GrayEffect mGrayEffect;
    private NullEffect mNullEffect;
    private ImageButton mRecordBtn;
    private boolean isGray;
    private boolean isRecording;
    private ProgressBar mProgressConnecting;
    private RtmpSender mRtmpSender;
    private VideoConfiguration mVideoConfiguration;
    private int mCurrentBps;
    private Dialog mUploadDialog;
    private EditText mAddressET;
    private EditText msolution;
    private String mid;
    private String mresolution;
    private boolean mProtait;
    private String mPublishUrl;

    private EditText mipEditText;
    private String mip;
    //final String defaultIP = "123.124.164.142";
    final String defaultIP = "drli.urthe1.xyz";

    private String mdeviceID;
    private String mStatus;
    private String mNetWorkInfo;
    private int mbattery;

    //动态配置信息
    private int mInterval;//上报间隔时间

    private ScheduledExecutorService scheduleExecutor;
    private ScheduledFuture<?> scheduleManager;
    private Runnable timeTask;

    private Handler cameraHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0://切换摄像头
                    mLFLiveView.switchCamera();
                    break;
                case 1://切换推拉流状态
                    if (isRecording) {
                        stopLive();
                    } else {
                        startLive();
                    }
                    break;
                case 2://间隔时间
                    changeInterval(msg.arg1);
                    break;
                case 3://车牌号
                    changeCarId((String)msg.obj);
                    break;
                case 4://清晰度
                    changeResolution((String)msg.obj);
                    loadLiveViewConfig();
                    break;
                case 5:
                    changeIp((String)msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mStatus = "未推流";//当前状态


        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //获取预设信息
        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        boolean PORTRAIT = pref.getBoolean("portrait", false);
        if (!PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setContentView(R.layout.activity_landscape);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setContentView(R.layout.activity_portrait);
        }

        init();

        Intent intent = getIntent();
        mdeviceID = intent.getStringExtra("deviceID");
        mbattery = intent.getIntExtra("battery",0);
        mNetWorkInfo = intent.getStringExtra("networkInfo");
    }

    private void init(){
        initEffects();
        initViews();
        initListeners();
        initLiveView();
        initRtmpAddressDialog();
        loadLiveViewConfig();

        //初始化推流地址
        SharedPreferences pref = getSharedPreferences("data",MODE_PRIVATE);
        mid = pref.getString("id","");
        mPublishUrl = pref.getString("url","rtmp://"+mip+"/live_540/");
        if(TextUtils.isEmpty(mid)) {
            mUploadDialog.setCanceledOnTouchOutside(false);
            mUploadDialog.show();
        }

        //根据远端状态来判断
        createSchedulePool();

        //资源上报池
        createUploadPool();
    }

    private void initEffects() {
        mGrayEffect = new GrayEffect(this);
        mNullEffect = new NullEffect(this);
    }


    private void initViews() {
        mLFLiveView = (CameraLivingView) findViewById(R.id.liveView);
        mFlashBtn = (MultiToggleImageButton) findViewById(R.id.camera_flash_button);
        mFaceBtn = (MultiToggleImageButton) findViewById(R.id.camera_switch_button);
        midBtn = (MultiToggleImageButton) findViewById(R.id.id_button);
//        mgpsBtn = (MultiToggleImageButton) findViewById(R.id.id_gps);
        mRecordBtn = (ImageButton) findViewById(R.id.btnRecord);
        mbackBtn = (ImageButton) findViewById(R.id.backBtn);
        mProgressConnecting = (ProgressBar) findViewById(R.id.progressConnecting);
    }

    private void createUploadPool(){
        scheduleExecutor = Executors.newScheduledThreadPool(5);
        timeTask = new Runnable() {
            @Override
            public void run() {

                uploadInfo();
            }
        };
        scheduleManager = scheduleExecutor.scheduleAtFixedRate(timeTask, 1, mInterval, TimeUnit.SECONDS);
    }


    private void createSchedulePool(){
        ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(5);
        scheduledThreadPool.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                //单设备控制接口
                String jsonStr = httpGet("http://drli.urthe1.xyz/api/getClientStatus");
                //解析json文件
                Log.d("camera",jsonStr);
                try {
                        JSONArray jsonArray = new JSONArray(jsonStr);
                        for(int i=0;i<jsonArray.length();i++) {
                            JSONObject jsonObject=(JSONObject)jsonArray.get(i);
                            String id=jsonObject.getString("deviceID");
                            if(id.compareTo(mdeviceID)==0){//通过设备标识符找到
                                //摄像头控制
                                int facing=jsonObject.getInt("cameraPosition");
                                int cameraNow = mLFLiveView.getCameraData().cameraFacing;
                                Log.d("camera",String.format("cameraid:%d",cameraNow));
                                if(facing != cameraNow && facing!=0){
                                    cameraHandler.sendEmptyMessage(0);
                                }
                                //推流状态
                                boolean cRecord = jsonObject.getBoolean("pushStatus");
                                if(cRecord != isRecording)
                                    cameraHandler.sendEmptyMessage(1);
                                //车牌号
                                if(!jsonObject.isNull("streamID")){
                                    String carId = jsonObject.getString("streamID");
                                    if(carId != mid){
                                        Message msg= new Message();
                                        msg.what = 3;
                                        msg.obj = carId;
                                        cameraHandler.sendMessage(msg);
                                    }
                                }
                                //清晰度
                                if(!jsonObject.isNull("streamDefinition")){
                                    String resolution = jsonObject.getString("streamDefinition");
                                    if(resolution.compareTo("540P")==0){
                                        resolution = "540";
                                    }else if(resolution.compareTo(" 720P")==0){
                                        resolution = "720";
                                    }else if(resolution.compareTo(" 1080P")==0){
                                        resolution = "1080";
                                    }
                                    if(resolution != mresolution){
                                        Message msg= new Message();
                                        msg.what = 4;
                                        msg.obj = resolution;
                                        cameraHandler.sendMessage(msg);
                                    }
                                }
                                //推流地址
                                if(!jsonObject.isNull("ip")){
                                    String ip = jsonObject.getString("ip");
                                    if(ip != mip){
                                        Message msg= new Message();
                                        msg.what = 5;
                                        msg.obj = ip;
                                        cameraHandler.sendMessage(msg);
                                    }
                                }
                                break;
                            }
                        }
                    }catch (JSONException e) {
                    e.printStackTrace();
                }
                //全局控制接口
                jsonStr = httpGet("http://drli.urthe1.xyz/api/settings");
                try {
                        JSONObject jsonObject=new JSONObject(jsonStr);
                        //间隔时间
                        if(!jsonObject.isNull("interval")) {
                            int reportInterval = jsonObject.getInt("interval");
                            if (reportInterval != mInterval) {
                                Message msg = new Message();
                                msg.what = 2;
                                msg.arg1 = reportInterval;
                                cameraHandler.sendMessage(msg);
                            }
                        }
                    }catch (JSONException e) {
                    e.printStackTrace();
                }
         }
        }, 1, 3, TimeUnit.SECONDS);
    }

    private void initListeners() {
        mFlashBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mLFLiveView.switchTorch();
            }
        });
        mFaceBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, int state) {
                mLFLiveView.switchCamera();
            }
        });
        midBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener(){
            public void stateChanged(View view, int state) {
                mUploadDialog.setCanceledOnTouchOutside(false);
                mAddressET.setText(mid);
                msolution.setText(mresolution);
                mOrientationSwitch.setChecked(mProtait);
                mipEditText.setText(mip);
                mUploadDialog.show();

            }
        });
//        mgpsBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
//            @Override
//            public void stateChanged(View view, int state) {
//                if(!mGpsStarted) {
////                    Intent it = new Intent(LandscapeActivity.this,GPSService.class);
////                    startService(it);
//                    mlocationService.start();// 定位SDK
//                    mGpsStarted = true;
//                    Toast.makeText(LandscapeActivity.this, "GPS 上报打开!", Toast.LENGTH_SHORT).show();
//
//                }else{
////                    Intent it2 = new Intent(LandscapeActivity.this,GPSService.class);
////                    stopService(it2);
//                    mlocationService.stop();// 定位SDK
//                    mGpsStarted = false;
//                    Toast.makeText(LandscapeActivity.this, "GPS 上报关闭!", Toast.LENGTH_SHORT).show();
//
//                }
//            }
//        });
        mRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isRecording) {
                    stopLive();
                } else {
                    startLive();
                }
            }
        });
        mbackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LandscapeActivity.this.finish();
            }
        });
    }

    private void initRtmpAddressDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View playView = inflater.inflate(R.layout.address_dialog,(ViewGroup) findViewById(R.id.dialog));
        mAddressET = (EditText) playView.findViewById(R.id.address);
        msolution = (EditText) playView.findViewById(R.id.resolution);
        mOrientationSwitch = (Switch) playView.findViewById(R.id.switchOrientation);
        mipEditText = (EditText) playView.findViewById(R.id.ip);
        Button okBtn = (Button) playView.findViewById(R.id.ok);
        Button cancelBtn = (Button) playView.findViewById(R.id.cancel);
        AlertDialog.Builder uploadBuilder = new AlertDialog.Builder(this);
        uploadBuilder.setTitle("请输入车号ID:");
        uploadBuilder.setView(playView);
        mUploadDialog = uploadBuilder.create();

        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mid = mAddressET.getText().toString();
                if(TextUtils.isEmpty(mid)) {
                    Toast.makeText(LandscapeActivity.this, "车号ID不为空!", Toast.LENGTH_SHORT).show();
                    return;
                }

                mresolution = msolution.getText().toString();
                if(TextUtils.isEmpty(mresolution)) {
                    mresolution = "540";
                }

                mip = mipEditText.getText().toString();
                if(TextUtils.isEmpty(mip)) {
                    mip = defaultIP;
                }

                if(!mOrientationSwitch.isChecked())
                {
                    mProtait = false;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }else{
                    mProtait = true;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }

                //持久化
                SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
                editor.putString("id",mid);
                editor.putString("ip",mip);
                editor.putString("resolution",mresolution);
                editor.putBoolean("portrait",mOrientationSwitch.isChecked());
                editor.apply();

                //这里需要重新导入数据
                loadLiveViewConfig();

                editor = getSharedPreferences("data",MODE_PRIVATE).edit();
                editor.putString("url",mPublishUrl);
                editor.apply();

                LandscapeActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Log.i("Dialog","dialog dismiss");
                        mUploadDialog.dismiss();
                    }
                });
            }
        });
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LandscapeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("Dialog","dialog dismiss");
                        mUploadDialog.dismiss();
                    }
                });                //开启状态查询
            }
        });
    }

    private void changeInterval(int newValue){
        mInterval = newValue;
        if (scheduleManager!= null)
        {
            scheduleManager.cancel(true);
        }
        scheduleManager = scheduleExecutor.scheduleAtFixedRate(timeTask, 1, mInterval, TimeUnit.SECONDS);
    }

    private void changeCarId(String carID){
        mid = carID;
        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("id",mid);
        editor.apply();
        Toast.makeText(LandscapeActivity.this, "修改车牌ID为:"+mid, Toast.LENGTH_SHORT).show();
    }
    private void changeResolution(String resolution){
        mresolution = resolution;
        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("resolution",mresolution);
        editor.apply();
    }
    private void changeIp(String ip){
        mip = ip;
        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("ip",mresolution);
        editor.apply();
    }
    private void stopLive(){
        mProgressConnecting.setVisibility(View.GONE);
        Toast.makeText(LandscapeActivity.this, "停止直播！", Toast.LENGTH_SHORT).show();
        mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
        mLFLiveView.stop();
        isRecording = false;
    }
    private void startLive(){
        if(TextUtils.isEmpty(mid)) {
            Toast.makeText(LandscapeActivity.this, "mid未赋值，无法推流", Toast.LENGTH_SHORT).show();
            return;
        }
        String uploadUrl = mPublishUrl+mid;
        Log.i("mid","url:"+uploadUrl);
        Toast.makeText(LandscapeActivity.this,uploadUrl, Toast.LENGTH_SHORT).show();
        mRtmpSender.setAddress(uploadUrl);
        mProgressConnecting.setVisibility(View.VISIBLE);
        Toast.makeText(LandscapeActivity.this, "start connecting", Toast.LENGTH_SHORT).show();
        mRecordBtn.setBackgroundResource(R.mipmap.ic_record_stop);
        mRtmpSender.connect();
        isRecording = true;
        mStatus = "正常";
    }

    private void initLiveView() {
        SopCastLog.isOpen(true);
        mLFLiveView.init();
        CameraConfiguration.Builder cameraBuilder = new CameraConfiguration.Builder();
        if(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        {
            cameraBuilder.setOrientation(CameraConfiguration.Orientation.LANDSCAPE);
        }
        else if(getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        {
            cameraBuilder.setOrientation(CameraConfiguration.Orientation.PORTRAIT);
        }
        cameraBuilder.setFacing(CameraConfiguration.Facing.BACK);
        CameraConfiguration cameraConfiguration = cameraBuilder.build();
        mLFLiveView.setCameraConfiguration(cameraConfiguration);

//        //设置水印
//        Bitmap watermarkImg = BitmapFactory.decodeResource(getResources(), R.mipmap.watermark);
//        Watermark watermark = new Watermark(watermarkImg, 50, 25, WatermarkPosition.WATERMARK_ORIENTATION_BOTTOM_RIGHT, 8, 8);
//        mLFLiveView.setWatermark(watermark);

        //设置预览监听
        mLFLiveView.setCameraOpenListener(new CameraListener() {
            @Override
            public void onOpenSuccess() {
                Toast.makeText(LandscapeActivity.this, "camera open success", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onOpenFail(int error) {
                Toast.makeText(LandscapeActivity.this, "camera open fail", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCameraChange() {
                Toast.makeText(LandscapeActivity.this, "camera switch", Toast.LENGTH_LONG).show();
            }
        });

        //设置手势识别
        mGestureDetector = new GestureDetector(this, new GestureListener());
        mLFLiveView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mGestureDetector.onTouchEvent(event);
                return false;
            }
        });

        //初始化flv打包器
        RtmpPacker packer = new RtmpPacker();
        packer.initAudioParams(AudioConfiguration.DEFAULT_FREQUENCY, 16, false);
        mLFLiveView.setPacker(packer);
        //设置发送器
        mRtmpSender = new RtmpSender();
        mRtmpSender.setAudioParams(AudioConfiguration.DEFAULT_FREQUENCY, 16, false);
        mRtmpSender.setSenderListener(mSenderListener);
        mLFLiveView.setSender(mRtmpSender);
        mLFLiveView.setLivingStartListener(new CameraLivingView.LivingStartListener() {
            @Override
            public void startError(int error) {
                //直播失败
                Toast.makeText(LandscapeActivity.this, "start living fail", Toast.LENGTH_SHORT).show();
                mLFLiveView.stop();
            }

            @Override
            public void startSuccess() {
                //直播成功
                Toast.makeText(LandscapeActivity.this, "开始直播,id号:"+mid+",地址:"+mPublishUrl, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLiveViewConfig(){
        SharedPreferences pref = getSharedPreferences("data",MODE_PRIVATE);
        mProtait = pref.getBoolean("portrait",false);
        mip = pref.getString("ip",defaultIP);
        mresolution  = pref.getString("resolution","540");

        if(!mProtait)
        {
            if(mresolution.compareTo("1080")==0){
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(1920, 1080).setBps(900,1800);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);
                mRtmpSender.setVideoParams(1920, 1080);

                mPublishUrl = "rtmp://"+mip+"/live_landscape_1080p/";
            }else if (mresolution.compareTo("720")==0){
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(1280, 720).setBps(600,1600);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(1280, 720);

                mPublishUrl = "rtmp://"+mip+"/live_720_convert/";
            }else{
                Toast.makeText(LandscapeActivity.this, "默认用540", Toast.LENGTH_SHORT).show();
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(960, 540).setBps(450,1200);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(960, 540);

                mPublishUrl = "rtmp://"+mip+"/live_540/";
            }
        }else{
            if(mresolution.compareTo("1080")==0){
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(1080, 1920).setBps(900,1800);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(1080, 1920);

                mPublishUrl = "rtmp://"+mip+"/live_portrait_1080p/";
            }else if (mresolution.compareTo("720")==0){
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(720, 1280).setBps(600,1600);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(720, 1280);

                mPublishUrl = "rtmp://"+mip+"/live_portrait_720p/";
            }else{
                Toast.makeText(LandscapeActivity.this, "默认用540", Toast.LENGTH_SHORT).show();
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(540, 960).setBps(450,1200);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(540, 960);

                mPublishUrl = "rtmp://"+mip+"/live_540/";
            }
        }
    }

    private RtmpSender.OnSenderListener mSenderListener = new RtmpSender.OnSenderListener() {
        @Override
        public void onConnecting() {

        }

        @Override
        public void onConnected() {
            mProgressConnecting.setVisibility(View.GONE);
            mLFLiveView.start();
            mCurrentBps = mVideoConfiguration.maxBps;
        }

        @Override
        public void onDisConnected() {
            mProgressConnecting.setVisibility(View.GONE);
            Toast.makeText(LandscapeActivity.this, "fail to live", Toast.LENGTH_SHORT).show();
            mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
            mLFLiveView.stop();
            isRecording = false;
        }

        @Override
        public void onPublishFail() {
            mProgressConnecting.setVisibility(View.GONE);
            Toast.makeText(LandscapeActivity.this, "fail to publish stream", Toast.LENGTH_SHORT).show();
            mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
            isRecording = false;
        }

        @Override
        public void onNetGood() {
            if (mCurrentBps + 50 <= mVideoConfiguration.maxBps){
                SopCastLog.d(TAG, "BPS_CHANGE good up 50");
                int bps = mCurrentBps + 50;
                if(mLFLiveView != null) {
                    boolean result = mLFLiveView.setVideoBps(bps);
                    if(result) {
                        mCurrentBps = bps;
                    }
                }
            } else {
                SopCastLog.d(TAG, "BPS_CHANGE good good good");
            }
            SopCastLog.d(TAG, "Current Bps: " + mCurrentBps);
        }

        @Override
        public void onNetBad() {
            if (mCurrentBps - 100 >= mVideoConfiguration.minBps){
                SopCastLog.d(TAG, "BPS_CHANGE bad down 100");
                int bps = mCurrentBps - 100;
                if(mLFLiveView != null) {
                    boolean result = mLFLiveView.setVideoBps(bps);
                    if(result) {
                        mCurrentBps = bps;
                    }
                }
            } else {
                SopCastLog.d(TAG, "BPS_CHANGE bad down 100");
            }
            SopCastLog.d(TAG, "Current Bps: " + mCurrentBps);
        }
    };

    public class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1.getX() - e2.getX() > 100
                    && Math.abs(velocityX) > 200) {
                // Fling left
                Toast.makeText(LandscapeActivity.this, "Fling Left", Toast.LENGTH_SHORT).show();
            } else if (e2.getX() - e1.getX() > 100
                    && Math.abs(velocityX) > 200) {
                // Fling right
                Toast.makeText(LandscapeActivity.this, "Fling Right", Toast.LENGTH_SHORT).show();
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mLFLiveView!=null)
            mLFLiveView.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(mLFLiveView!=null){
            mLFLiveView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLFLiveView.stop();
        mLFLiveView.release();
    }

    public String httpGet( String httpUrl ){
        String result = "" ;
        try {
            BufferedReader reader = null;
            StringBuffer sbf = new StringBuffer() ;

            URL url  = new URL( httpUrl ) ;
            HttpURLConnection connection = (HttpURLConnection) url.openConnection() ;
            //设置超时时间 10s
            connection.setConnectTimeout(10000);
            //设置请求方式
            connection.setRequestMethod( "GET" ) ;
            connection.connect();
            InputStream is = connection.getInputStream() ;
            reader = new BufferedReader(new InputStreamReader( is , "UTF-8" )) ;
            String strRead = null ;
            while ((strRead = reader.readLine()) != null) {
                sbf.append(strRead);
                sbf.append("\r\n");
            }
            reader.close();
            result = sbf.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    private void uploadInfo()
    {
        new Thread(new Runnable() {
            public void run() {
                String uriAPI = "http://drli.urthe1.xyz/api/updateDevicesStatus?deviceID=" + mdeviceID+"&streamID="+mid;
                if(!TextUtils.isEmpty(mStatus)){
                    uriAPI += String.format("&appStatus=%s",mStatus);
                }
                if(!TextUtils.isEmpty(mNetWorkInfo)){
                    uriAPI += String.format("&networkType=%s",mNetWorkInfo);
                }
                if(mbattery >= 0){
                    uriAPI += String.format("&battery=%d",mbattery);
                }

                HttpClient postClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(uriAPI);
                List<NameValuePair> params = new ArrayList<NameValuePair>();

                UrlEncodedFormEntity entity;
                HttpResponse response;
                try {
                    entity = new UrlEncodedFormEntity(params, "utf-8");
                    httpPost.setEntity(entity);
                    response = postClient.execute(httpPost);

                    if (response.getStatusLine().getStatusCode() == 200) {

                    }

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ;
            }
        }).start();

    }

    protected void onPause(){
        super.onPause();
        Log.e("active","active:pause");
        mStatus = "注意，设备已经切后台！！！";
    }

    protected void onResume(){
        super.onResume();
        Log.e("active","active:resume");
    }
}
