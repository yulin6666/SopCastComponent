package com.drill.liveDemo;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.util.Log;
import android.os.Build;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.google.zxing.client.android.CaptureActivity;
import com.google.zxing.client.android.Intents;
import com.laifeng.sopcastsdk.camera.CameraListener;
import com.laifeng.sopcastsdk.configuration.AudioConfiguration;
import com.laifeng.sopcastsdk.configuration.CameraConfiguration;
import com.laifeng.sopcastsdk.configuration.VideoConfiguration;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.*;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Message;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpResponse;
import org.apache.http.client.*;
import org.apache.http.impl.client.*;

import android.content.pm.PackageManager;

import static android.net.NetworkInfo.State.CONNECTED;
import static com.laifeng.sopcastsdk.constant.SopCastConstant.TAG;

public class LandscapeActivity extends Activity {
    private CameraLivingView mLFLiveView;
    private ImageButton mScanButton;
    private TextView mDebugLiveView;
    //    private MultiToggleImageButton mFlashBtn;
//    private MultiToggleImageButton mFaceBtn;
//    private MultiToggleImageButton midBtn;
//    private MultiToggleImageButton mgpsBtn;
//    private ImageButton mbackBtn;
    private Switch mOrientationSwitch;
    private GestureDetector mGestureDetector;
    private GrayEffect mGrayEffect;
    private NullEffect mNullEffect;
    //    private ImageButton mRecordBtn;
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

    //final String defaultIP = "123.124.164.142";
    //final String defaultIP = "drli.urthe1.xyz";
    final String defaultIP = "39.106.226.236";
    private String mip = defaultIP;
    private String mdeviceID;
    private String mStatus;
    private String mNetWorkInfo;
    private int mbattery;

    private String gpsUploadUrl = "";
    private String scanUploadUrl = "";

    //动态配置信息
    private int mInterval;//上报间隔时间

    private ScheduledExecutorService uploaderScheduleExecutor;
    private ScheduledFuture<?> uploaderScheduleManager;
    private Runnable uploaderTimeTask;

    private ScheduledExecutorService controlScheduleExecutor;
    private ScheduledFuture<?> controlScheduleManager;
    private Runnable controlTimeTask;

    //GPS相关信息
    private boolean mGpsStarted;
    private double mlongitude;//经度
    private double mlatitude;//纬度
    private String mdeviceTime;//gps时间
    private float mDirection;//方向
    private float mRadius;//半径
    private float mSpeed;//速度
    private int mSatellite;//卫星数目
    private double mAltitude;//海拔高度
    private String maddr;//地址信息
    private String mDescribe;//描述信息
    private int mtype;//类型

    private String mScanContent;

    private int currentTriggerNum = 0;

    private Boolean triggerEnable = false;
    private int totalCount = 3;
    private int recoveryDuration = 30;
    private int totalDuration = 60;

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
                    changeCarId((String) msg.obj);
                    restartLive();//自动重启
                    break;
                case 4://清晰度
                    changeResolution((String) msg.obj);
                    restartLive();//自动重启
                    break;
                case 5://修改IP
                    changeIp((String) msg.obj);
                    restartLive();//自动重启
                    break;
                case 6://打开GPS开关
                    openGps((boolean) msg.obj);
                    break;
                case 7://livedebug信息
                    mDebugLiveView.invalidate();
                    mDebugLiveView.setText((String)msg.obj);
                    break;
                case 8://切换横竖屏
                    changeProtrait((boolean)msg.obj);
                    break;
                case 9://打开阻断器
                    openStopTimer((boolean)msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //获取权限
        if (Build.VERSION.SDK_INT > 22) {

            if(premissionOk())
            {
                //说明已经获取到摄像头权限了
                Log.i("MainActivity", "已经获取了权限");
                init();
            }
        } else {
//这个说明系统版本在6.0之下，不需要动态获取权限。
            Log.i("MainActivity", "这个说明系统版本在6.0之下，不需要动态获取权限。");
        }

    }

    private boolean premissionOk(){
        boolean ret = true;
        if (ContextCompat.checkSelfPermission(LandscapeActivity.this,
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //先判断有没有权限 ，没有就在这里进行权限的申请
            ActivityCompat.requestPermissions(LandscapeActivity.this,
                    new String[]{
                            Manifest.permission.CAMERA
                    }, 1);
            ret = false;
        }
        if (ContextCompat.checkSelfPermission(LandscapeActivity.this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //先判断有没有权限 ，没有就在这里进行权限的申请
            ActivityCompat.requestPermissions(LandscapeActivity.this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 1);
            ret = false;
        }
        if (ContextCompat.checkSelfPermission(LandscapeActivity.this,
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //先判断有没有权限 ，没有就在这里进行权限的申请
            ActivityCompat.requestPermissions(LandscapeActivity.this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    }, 1);
            ret = false;
        }
        if (ContextCompat.checkSelfPermission(LandscapeActivity.this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //先判断有没有权限 ，没有就在这里进行权限的申请
            ActivityCompat.requestPermissions(LandscapeActivity.this,
                    new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 1);
            ret = false;
        }
        if (ContextCompat.checkSelfPermission(LandscapeActivity.this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //先判断有没有权限 ，没有就在这里进行权限的申请
            ActivityCompat.requestPermissions(LandscapeActivity.this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, 1);
            ret = false;
        }
        if (ContextCompat.checkSelfPermission(LandscapeActivity.this,
                android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            //先判断有没有权限 ，没有就在这里进行权限的申请
            ActivityCompat.requestPermissions(LandscapeActivity.this,
                    new String[]{
                            Manifest.permission.READ_PHONE_STATE
                    }, 1);
            ret = false;
        }
        return ret;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[5] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    Log.i("LandscapeActivity", "dialog权限回调");
                    init();
                } else {
                    // Permission Denied
                }

            }
            return;
        }

        // other 'case' lines to check for other
        // permissions this app might request

    }

    private void init() {

        mStatus = "初始化";//当前状态

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
            setContentView(R.layout.activity_protrait);
        }

        updateVersionCode(getApplicationContext());

        initDeviceID();
        initGps();

        fetchBattery();
        fetchNetType();

        initEffects();
        initViews();
        initListeners();
        initLiveView();
//        initRtmpAddressDialog();
//        loadLiveViewConfig();

        //初始化推流地址
//        pref = getSharedPreferences("data", MODE_PRIVATE);
//        mid = pref.getString("id", "");
//        mPublishUrl = pref.getString("url", "rtmp://" + mip + "/live_540/");
//        if (TextUtils.isEmpty(mid)) {
//            mUploadDialog.setCanceledOnTouchOutside(false);
//            mUploadDialog.show();
//        }

        //根据远端状态来判断
        createSchedulePool();

        //资源上报池
        createUploadPool();
        Intent startIntent = new Intent(this, BackgroundService.class);
        startService(startIntent);


    }

    private void startTiggerTimer(){

        new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if(currentTriggerNum < totalCount){
                    Toast.makeText(LandscapeActivity.this, "系统故障，停止直播!", Toast.LENGTH_SHORT).show();
                    //关闭推流
                    if(isRecording)
                        stopLive();

                    //关闭上报定时器
                    if (uploaderScheduleManager != null)
                    {
                        uploaderScheduleManager.cancel(true);
                        uploaderScheduleManager = null;
                    }
                    //关闭控制定时器
                    if(controlScheduleManager != null){
                        controlScheduleManager.cancel(true);
                        controlScheduleManager = null;
                    }

                    new Handler(new Handler.Callback() {
                        @Override
                        public boolean handleMessage(Message msg) {
                            Toast.makeText(LandscapeActivity.this, "恢复直播!", Toast.LENGTH_SHORT).show();
                            //重新创建控制池
                            createSchedulePool();
                            //重新创建上报池
                            createUploadPool();
                            //重新开启计数器
                            startTiggerTimer();
                            return true;
                        }

                        ;
                    }).sendEmptyMessageDelayed(0, recoveryDuration*1000);
                }

                currentTriggerNum++;
                return true;
            };
        }).sendEmptyMessageDelayed(0, totalDuration*1000);


    }

    public String updateVersionCode(Context context) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo;
        String versionCode = "";
        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            versionCode = packageInfo.versionCode + "";
            TextView versionView = findViewById(R.id.version_view);
            versionView.setText("版本:"+versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    private void initGps() {
        mGpsStarted = false;
        mlongitude = 0;//经度
        mlatitude = 0;//纬度
        mdeviceTime = "";//gps时间
        mDirection = 0;//方向
        mInterval = 10;//上报时间
        mRadius = 0.0f;
        mSpeed = 0.0f;
        mSatellite = 0;
        mAltitude = 0;
        maddr = "";
        mDescribe = "网络定位成功";
        mtype = -1;

        //获取locationservice实例，建议应用中只初始化1个location实例，然后使用，可以参考其他示例的activity，都是通过此种方式获取locationservice实例的
        ((myApplication) getApplication()).mlocationService.registerListener(mListener);
        ((myApplication) getApplication()).mlocationService.setLocationOption(((myApplication) getApplication()).mlocationService.getDefaultLocationClientOption());

        ((myApplication) getApplication()).mlocationService.start();
        Log.e(TAG, "初始化,打开GPS!");
        mGpsStarted = true;

    }

    private void initDeviceID() {
        /***deviceID
         * 设备ID号
         */
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        if(tm.getDeviceId()!=null){
            mdeviceID = tm.getDeviceId().toString();
            Log.d(TAG, String.format("deviceID:%s", mdeviceID));
        }else{
            mdeviceID = "noMEID";
        }

    }

    private void fetchBattery() {

        //获取电量信息
        BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        mbattery = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Log.d("battery", String.format("battery info:%d", mbattery));
    }

    private void fetchNetType(){
        //获得网络类型
        Context context = getApplicationContext();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);        //获取所有网络连接的信息
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Network[] networks = connectivityManager.getAllNetworks();
            if (networks != null && networks.length > 0) {
                int size = networks.length;
                for (int i=0; i<size; i++) {
                    NetworkInfo.State state = connectivityManager.getNetworkInfo(networks[i]).getState();
                    if(state == CONNECTED) {
                        Log.d("TAG", "=====类型====" + connectivityManager.getNetworkInfo(networks[i]).getTypeName());
                        mNetWorkInfo = connectivityManager.getNetworkInfo(networks[i]).getTypeName();
                    }
                }
            }
        }
    }

    private void initEffects() {
        mGrayEffect = new GrayEffect(this);
        mNullEffect = new NullEffect(this);
    }


    private void initViews() {
        mLFLiveView = (CameraLivingView) findViewById(R.id.liveView);
        mScanButton = (ImageButton)findViewById(R.id.id_scan_button);
        mDebugLiveView = (TextView)findViewById(R.id.debug_live_view);
//        mFlashBtn = (MultiToggleImageButton) findViewById(R.id.camera_flash_button);
//        mFaceBtn = (MultiToggleImageButton) findViewById(R.id.camera_switch_button);
//       midBtn = (MultiToggleImageButton) findViewById(R.id.id_button);
//        mgpsBtn = (MultiToggleImageButton) findViewById(R.id.id_gps);
//        mRecordBtn = (ImageButton) findViewById(R.id.btnRecord);
//        mbackBtn = (ImageButton) findViewById(R.id.backBtn);
        mProgressConnecting = (ProgressBar) findViewById(R.id.progressConnecting);
    }

    private void createUploadPool(){
        uploaderScheduleExecutor = Executors.newScheduledThreadPool(5);
        uploaderTimeTask = new Runnable() {
            @Override
            public void run() {
                //获取电量
                fetchBattery();
                //获取网络信息
                fetchNetType();
                //获得当前时间
                DateFormat dateTimeformat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                mdeviceTime = dateTimeformat2.format(new Date());
                uploadInfo();
            }
        };
        uploaderScheduleManager = uploaderScheduleExecutor.scheduleAtFixedRate(uploaderTimeTask, 1, mInterval, TimeUnit.SECONDS);
    }


    private void createSchedulePool(){
        controlScheduleExecutor = Executors.newScheduledThreadPool(5);
        controlTimeTask= new Runnable() {

            @Override
            public void run() {
                DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
                int height= dm.heightPixels;
                int width= dm.widthPixels;
                String jsonStr;
                if(height == 240 && width ==320){
                    String url = "http://"+mip+"/api/getClientStatus?deviceType=1";
                    jsonStr = httpGet(url);
                }else{
                    String url = "http://"+mip+"/api/getClientStatus";
                    jsonStr = httpGet(url);
                }
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
                            //间隔时间
                            if(!jsonObject.isNull("uploadInterval")) {
                                int reportInterval = jsonObject.getInt("uploadInterval");
                                if (reportInterval != mInterval) {
                                    Message msg = new Message();
                                    msg.what = 2;
                                    msg.arg1 = reportInterval;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //推流状态
                            boolean cRecord = jsonObject.getBoolean("pushStatus");
                            if(cRecord != isRecording)
                                cameraHandler.sendEmptyMessage(1);
                            //车牌号
                            if(!jsonObject.isNull("streamID")){
                                String carId = jsonObject.getString("streamID");
                                if(!carId.equals(mid)){
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
                                if(!resolution.equals(mresolution)){
                                    Message msg= new Message();
                                    msg.what = 4;
                                    msg.obj = resolution;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //是否打开GPS上报
                            if(!jsonObject.isNull("gpsEnable")){
                                boolean gpsEnable = jsonObject.getBoolean("gpsEnable");
                                if(mGpsStarted != gpsEnable){
                                    Message msg= new Message();
                                    msg.what = 6;
                                    msg.obj = gpsEnable;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //是否切换横竖屏
                            if(!jsonObject.isNull("isLandscape")){
                                boolean landscape = jsonObject.getBoolean("isLandscape");
                                boolean protait = !landscape;
                                if(mProtait != protait){
                                    Message msg= new Message();
                                    msg.what = 8;
                                    msg.obj = landscape;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //是否触发阻断器
                            if(!jsonObject.isNull("switcher")&&
                                    !jsonObject.isNull("totalDuration") &&
                                    !jsonObject.isNull("recoveryDuration") &&
                                    !jsonObject.isNull("totalCount")){
                                boolean enableTrigger = jsonObject.getBoolean("switcher");
                                totalDuration = jsonObject.getInt("totalDuration");
                                recoveryDuration = jsonObject.getInt("recoveryDuration");
                                totalCount = jsonObject.getInt("totalCount");
                                if(enableTrigger != triggerEnable){
                                    Message msg= new Message();
                                    msg.what = 9;
                                    msg.obj = enableTrigger;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            break;
                        }
                    }
                }catch (Exception e) {
                    e.printStackTrace();
                }
                //全局控制接口
                String url = "http://"+mip+"/api/settings";
                jsonStr = httpGet(url);
                try {
                    JSONObject jsonObject=new JSONObject(jsonStr);
                    //推流地址
                    if(!jsonObject.isNull("ip")){
                        String ip = jsonObject.getString("ip");
                        if(!ip.equals(mip)){
                            Message msg= new Message();
                            msg.what = 5;
                            msg.obj = ip;
                            cameraHandler.sendMessage(msg);
                        }
                    }
                    //gps单独上报地址
                    if(!jsonObject.isNull("uploadUrl")){
                        String uploadurl = jsonObject.getString("uploadUrl");
                        String[] temp = null;
                        temp = uploadurl.split("\\|");
                        if(!temp[0].equals(gpsUploadUrl)){
                            gpsUploadUrl = temp[0];
                        }
                        if(!temp[1].equals(scanUploadUrl)){
                            scanUploadUrl = temp[1];
                        }
                    }
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        controlScheduleManager = controlScheduleExecutor.scheduleAtFixedRate(controlTimeTask, 1, 10, TimeUnit.SECONDS);

    }

    private void initListeners() {
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //停止直播
                stopLive();

                //释放view
                if(mLFLiveView!=null){
                    mLFLiveView.release();
                }

                //关闭上报定时器
                if (uploaderScheduleManager != null)
                {
                    uploaderScheduleManager.cancel(true);
                    uploaderScheduleManager = null;
                }
                //关闭控制定时器
                if(controlScheduleManager != null){
                    controlScheduleManager.cancel(true);
                    controlScheduleManager = null;
                }


                new Handler(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        //实现页面跳转
                        Intent intent = new Intent(LandscapeActivity.this, CaptureActivity.class);
                        intent.setAction(Intents.Scan.ACTION);
                        startActivityForResult(intent, 111);
                        return true;
                    };
                }).sendEmptyMessageDelayed(0,300);//表示延迟1秒发送任务

            }
        });
//        mFlashBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
//            @Override
//            public void stateChanged(View view, int state) {
//                mLFLiveView.switchTorch();
//            }
//        });
//        mFaceBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener() {
//            @Override
//            public void stateChanged(View view, int state) {
//                mLFLiveView.switchCamera();
//            }
//        });
//        midBtn.setOnStateChangeListener(new MultiToggleImageButton.OnStateChangeListener(){
//            public void stateChanged(View view, int state) {
//                mUploadDialog.setCanceledOnTouchOutside(false);
//                mAddressET.setText(mid);
//                msolution.setText(mresolution);
//                mOrientationSwitch.setChecked(mProtait);
//                mipEditText.setText(mip);
//                mUploadDialog.show();
//
//            }
//        });
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
//        mRecordBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if(isRecording) {
//                    stopLive();
//                } else {
//                    startLive();
//                }
//            }
//        });
//        mbackBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                LandscapeActivity.this.finish();
//            }
//        });
    }

//    private void initRtmpAddressDialog() {
//        LayoutInflater inflater = getLayoutInflater();
//        View playView = inflater.inflate(R.layout.address_dialog,(ViewGroup) findViewById(R.id.dialog));
//        mAddressET = (EditText) playView.findViewById(R.id.address);
//        msolution = (EditText) playView.findViewById(R.id.resolution);
//        mOrientationSwitch = (Switch) playView.findViewById(R.id.switchOrientation);
//        mipEditText = (EditText) playView.findViewById(R.id.ip);
//        Button okBtn = (Button) playView.findViewById(R.id.ok);
//        Button cancelBtn = (Button) playView.findViewById(R.id.cancel);
//        AlertDialog.Builder uploadBuilder = new AlertDialog.Builder(this);
//        uploadBuilder.setTitle("请输入车号ID:");
//        uploadBuilder.setView(playView);
//        mUploadDialog = uploadBuilder.create();
//
//        okBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mid = mAddressET.getText().toString();
//                if(TextUtils.isEmpty(mid)) {
//                    //Toast.makeText(LandscapeActivity.this, "车号ID不为空!", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//
//                mresolution = msolution.getText().toString();
//                if(TextUtils.isEmpty(mresolution)) {
//                    mresolution = "540";
//                }
//
//                mip = mipEditText.getText().toString();
//                if(TextUtils.isEmpty(mip)) {
//                    mip = defaultIP;
//                }
//
//                if(!mOrientationSwitch.isChecked())
//                {
//                    mProtait = false;
//                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
//                }else{
//                    mProtait = true;
//                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
//                }
//
//                //持久化
//                SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
//                editor.putString("id",mid);
//                editor.putString("ip",mip);
//                editor.putString("resolution",mresolution);
//                editor.putBoolean("portrait",mOrientationSwitch.isChecked());
//                editor.apply();
//
//                //这里需要重新导入数据
//                loadLiveViewConfig();
//
//                editor = getSharedPreferences("data",MODE_PRIVATE).edit();
//                editor.putString("url",mPublishUrl);
//                editor.apply();
//
//                LandscapeActivity.this.runOnUiThread(new Runnable() {
//
//                    @Override
//                    public void run() {
//                        Log.i("Dialog","dialog dismiss");
//                        mUploadDialog.dismiss();
//                    }
//                });
//            }
//        });
//        cancelBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                LandscapeActivity.this.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Log.i("Dialog","dialog dismiss");
//                        mUploadDialog.dismiss();
//                    }
//                });                //开启状态查询
//            }
//        });
//    }

    private void changeInterval(int newValue){
        mInterval = newValue;
        if (uploaderScheduleManager != null)
        {
            uploaderScheduleManager.cancel(true);
        }
        uploaderScheduleManager = uploaderScheduleExecutor.scheduleAtFixedRate(uploaderTimeTask, 1, mInterval, TimeUnit.SECONDS);
    }

    private void changeCarId(String carID){
        mid = carID;
        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("id",mid);
        editor.apply();
        Toast.makeText(LandscapeActivity.this, "车牌ID改为:"+mid, Toast.LENGTH_SHORT).show();
    }
    private void changeResolution(String resolution){
        mresolution = resolution;
        Toast.makeText(LandscapeActivity.this, "清晰度改为:"+mresolution, Toast.LENGTH_SHORT).show();
        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("resolution",mresolution);
        editor.apply();
    }
    private void changeIp(String ip){
        mip = ip;
        Toast.makeText(LandscapeActivity.this, "ip改为:"+mip, Toast.LENGTH_SHORT).show();
        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("ip",mip);
        editor.apply();
    }
          /**
         * 将本应用置顶到最前端
         * 当本应用位于后台时，则将它切换到最前端
         *
         * @param context
         */
        public static void setTopApp(Context context) {
            if (!isRunningForeground(context)) {
                /**获取ActivityManager*/
                ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

                /**获得当前运行的task(任务)*/
                List<ActivityManager.RunningTaskInfo> taskInfoList = activityManager.getRunningTasks(100);
                for (ActivityManager.RunningTaskInfo taskInfo : taskInfoList) {
                    /**找到本应用的 task，并将它切换到前台*/
                    if (taskInfo.topActivity.getPackageName().equals(context.getPackageName())) {
                        activityManager.moveTaskToFront(taskInfo.id, 0);
                        break;
                    }
                }
            }
        }

        /**
         * 判断本应用是否已经位于最前端
         *
         * @param context
         * @return 本应用已经位于最前端时，返回 true；否则返回 false
         */
        public static boolean isRunningForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcessInfoList = activityManager.getRunningAppProcesses();
            /**枚举进程*/
            for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessInfoList) {
                if (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (appProcessInfo.processName.equals(context.getApplicationInfo().processName)) {
                        return true;
                    }
                }
            }
            return false;
        }


    private void changeProtrait(boolean landscape){
        mProtait = !landscape;
        if(mProtait){
            setTopApp(this);
            Toast.makeText(LandscapeActivity.this, "切换为竖屏", Toast.LENGTH_SHORT).show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        else{
            setTopApp(this);
            Toast.makeText(LandscapeActivity.this, "切换为横屏", Toast.LENGTH_SHORT).show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putBoolean("portrait",mProtait);
        editor.apply();
    }
    private void openStopTimer(boolean enable){
        triggerEnable = enable;
        if(triggerEnable){
            startTiggerTimer();
        }
    }
    private void openGps(boolean gpsEnable){
        if(gpsEnable) {
            if(!mGpsStarted){
                mGpsStarted = true;
                ((myApplication) getApplication()).mlocationService.start();
                Log.e(TAG, "开关打开GPS!");
                Toast.makeText(LandscapeActivity.this, "打开GPS!", Toast.LENGTH_SHORT).show();
            }
        }else{
            mGpsStarted = false;
            ((myApplication) getApplication()).mlocationService.stop();
            mDescribe = "远程关闭GPS，请在控制台打开";
            Log.e(TAG, "开关关闭GPS!");
            Toast.makeText(LandscapeActivity.this, "关闭GPS!", Toast.LENGTH_SHORT).show();

        }
        //sendRefreshMessage();
    }

    private void stopLive(){
        mProgressConnecting.setVisibility(View.GONE);
        Toast.makeText(LandscapeActivity.this, "停止直播！", Toast.LENGTH_SHORT).show();
//        mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
        mLFLiveView.stop();
        isRecording = false;
        refreshLiveInfo();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(LandscapeActivity.this, "按HOME返回桌面", Toast.LENGTH_SHORT).show();
    }

    private void startLive(){
        SharedPreferences pref = getSharedPreferences("data",MODE_PRIVATE);
        mProtait = pref.getBoolean("portrait",false);
        mid = pref.getString("id","");
        mip = pref.getString("ip",defaultIP);
        mresolution  = pref.getString("resolution","");
        if(TextUtils.isEmpty(mid)) {
            Toast.makeText(LandscapeActivity.this, "mid未赋值，等待...", Toast.LENGTH_SHORT).show();
            return;
        }
        if(TextUtils.isEmpty(mip)){
            Toast.makeText(LandscapeActivity.this, "推流地址未赋值，等待...", Toast.LENGTH_SHORT).show();
            return;
        }
        if(TextUtils.isEmpty(mresolution)){
            Toast.makeText(LandscapeActivity.this, "清晰度未赋值，等待...", Toast.LENGTH_SHORT).show();
            return;
        }

        loadLiveViewConfig();
        String uploadUrl = mPublishUrl+mid;
        Log.i("mid","url:"+uploadUrl);
        mRtmpSender.setAddress(uploadUrl);
        mProgressConnecting.setVisibility(View.VISIBLE);
        Toast.makeText(LandscapeActivity.this, "准备开始直播", Toast.LENGTH_SHORT).show();
//        mRecordBtn.setBackgroundResource(R.mipmap.ic_record_stop);
        mRtmpSender.connect();
        mStatus = "正常";
    }

    private void restartLive(){

        if(isRecording) {
            stopLive();

//            new Handler(new Handler.Callback() {
//                @Override
//                public boolean handleMessage(Message msg) {
//                    startLive();
//                    return true;
//                }
//
//                ;
//            }).sendEmptyMessageDelayed(0, 5000);//表示延迟5秒发送任务
        }
    }
    private void refreshLiveInfo(){
        StringBuffer sb = new StringBuffer(256);
        sb.append("状态: ");
        if(isRecording){
            sb.append("正在推流");
        }else {
            sb.append("停止推流");
        }

        sb.append("\n");
        Message msg= new Message();
        msg.what = 7;
        msg.obj = sb.toString();
        cameraHandler.sendMessage(msg);
    }

    private void initLiveView() {
        SopCastLog.isOpen(true);
        mLFLiveView.init();
        CameraConfiguration.Builder cameraBuilder = new CameraConfiguration.Builder();
        DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
        int height= dm.heightPixels;
        int width= dm.widthPixels;
        if((height == 240 && width ==320) ||(height == 320 && width ==240)){
            cameraBuilder.setPreview(height,width);
        }

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
                Toast.makeText(LandscapeActivity.this, "相机打开成功", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onOpenFail(int error) {
                Toast.makeText(LandscapeActivity.this, "相机打开失败", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCameraChange() {
                Toast.makeText(LandscapeActivity.this, "相机切换", Toast.LENGTH_LONG).show();
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
                Toast.makeText(LandscapeActivity.this, "开播失败,error:"+error, Toast.LENGTH_SHORT).show();
                mLFLiveView.stop();
                isRecording = false;
                refreshLiveInfo();
            }

            @Override
            public void startSuccess() {
                //直播成功
                Toast.makeText(LandscapeActivity.this, "开始直播,id号:"+mid+",地址:"+mPublishUrl, Toast.LENGTH_SHORT).show();
                isRecording = true;
                refreshLiveInfo();
            }
        });
    }

    private void loadLiveViewConfig(){
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
                //Toast.makeText(LandscapeActivity.this, "默认用540", Toast.LENGTH_SHORT).show();
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
                //Toast.makeText(LandscapeActivity.this, "默认用540", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(LandscapeActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
//            mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
            mLFLiveView.stop();
            isRecording = false;
            refreshLiveInfo();
        }

        @Override
        public void onPublishFail() {
            mProgressConnecting.setVisibility(View.GONE);
            Toast.makeText(LandscapeActivity.this, "发布失败", Toast.LENGTH_SHORT).show();
//            mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
            isRecording = false;
            refreshLiveInfo();
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
                //Toast.makeText(LandscapeActivity.this, "Fling Left", Toast.LENGTH_SHORT).show();
            } else if (e2.getX() - e1.getX() > 100
                    && Math.abs(velocityX) > 200) {
                // Fling right
                //Toast.makeText(LandscapeActivity.this, "Fling Right", Toast.LENGTH_SHORT).show();
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
        if(mLFLiveView!=null){
            mLFLiveView.stop();
            mLFLiveView.release();
        }

        if(mGpsStarted){
            ((myApplication) getApplication()).mlocationService.stop();
            mGpsStarted = false;

        }
        ((myApplication) getApplication()).mlocationService.unregisterListener(mListener);

        //关闭上报定时器
        if (uploaderScheduleManager != null)
        {
            uploaderScheduleManager.cancel(true);
            uploaderScheduleManager = null;
        }
        //关闭控制定时器
        if(controlScheduleManager != null){
            controlScheduleManager.cancel(true);
            controlScheduleManager = null;
        }

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
                String uriAPI = "http://"+mip+"/api/updateDevicesStatus";

                HttpClient postClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(uriAPI);
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("deviceID", mdeviceID));
                if(!TextUtils.isEmpty(mStatus)){
                    params.add(new BasicNameValuePair("appStatus", mStatus));
                    params.add(new BasicNameValuePair("streamStatus",String.format("%b",isRecording)));
                }
                if(!TextUtils.isEmpty(mNetWorkInfo)){
                    params.add(new BasicNameValuePair("networkType", mNetWorkInfo));
                }
                if(mbattery >= 0){
                    params.add(new BasicNameValuePair("battery", String.format("%d",mbattery)));
                }
                if(mlongitude >0){
                    params.add(new BasicNameValuePair("longitude", String.format("%f",mlongitude)));
                }
                if(mlatitude >0){
                    params.add(new BasicNameValuePair("latitude", String.format("%f",mlatitude)));
                }
                if(!TextUtils.isEmpty(mDescribe)){
                    params.add(new BasicNameValuePair("locationType", mDescribe));
                }
                if(mDirection > 0){
                    params.add(new BasicNameValuePair("direction", String.format("%f",mDirection)));
                }
                if(!TextUtils.isEmpty(mdeviceTime)){
                    params.add(new BasicNameValuePair("deviceTime", mdeviceTime));
                }
                DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
                int height= dm.heightPixels;
                int width= dm.widthPixels;
                if((height == 240 && width ==320)||(height== 320 && width==240)){
                    params.add(new BasicNameValuePair("deviceType", String.format("%d",1)));
                }else{
                    params.add(new BasicNameValuePair("deviceType", String.format("%d",0)));
                }

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
                };

                if(!gpsUploadUrl.isEmpty()){
                    sendDirectToServer();
                }
            }
        }).start();

    }

    private void sendDirectToServer(){

        HttpClient postClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(gpsUploadUrl);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("deviceid", mdeviceID));
        if(mlongitude >0){
            params.add(new BasicNameValuePair("longitude", String.format("%f",mlongitude)));
        }
        if(mlatitude >0){
            params.add(new BasicNameValuePair("latitude", String.format("%f",mlatitude)));
        }
        if(!TextUtils.isEmpty(mdeviceTime)){
            params.add(new BasicNameValuePair("createDate", mdeviceTime));
        }
        if(mSpeed >= 0){
            params.add(new BasicNameValuePair("speed", String.format("%f",mSpeed)));
        }
        if(mtype > -1){
            params.add(new BasicNameValuePair("code", String.format("%d",mtype)));
        }

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
        };
    }

    protected void onPause(){
        super.onPause();
        Log.e("active","active:pause");
        mStatus = "注意，设备已经切后台！！！";
    }

    protected void onResume(){
        super.onResume();
        mStatus = "正常";
        Log.e("active","active:resume");
    }

    private BDAbstractLocationListener mListener = new BDAbstractLocationListener() {

        /**
         * 定位请求回调函数
         * @param location 定位结果
         */
        @Override
        public void onReceiveLocation(BDLocation location) {

            // TODO Auto-generated method stub
            Log.e(TAG,String.format("gps:serverType:%d",location.getLocType()));
            if (null != location && location.getLocType() != BDLocation.TypeServerError)
            {
                mlongitude=location.getLongitude();
                mlatitude=location.getLatitude();
                mRadius = location.getRadius();
                mSpeed = location.getSpeed();
                maddr =location.getAddrStr();
                if (location.getLocType() == BDLocation.TypeGpsLocation){
                    mAltitude =location.getAltitude();
                    mSatellite = location.getSatelliteNumber();
                    mDescribe="GPS定位成功";
                }else if(location.getLocType() == BDLocation.TypeNetWorkLocation){
                    mDescribe="GPS定位成功";
                }else if (location.getLocType() == BDLocation.TypeOffLineLocation){
                    mDescribe="GPS定位成功";
                }
                mDirection=location.getDirection();
                mtype = location.getLocType();
                Log.d("location",String.format("location type:%d",mtype));
            }else{
                Log.e(TAG,"gps type serverError!");
            }
//            {
//                int tag = 1;
//                StringBuffer sb = new StringBuffer(256);
//                sb.append("time : ");
//                /**
//                 * 时间也可以使用systemClock.elapsedRealtime()方法 获取的是自从开机以来，每次回调的时间；
//                 * location.getTime() 是指服务端出本次结果的时间，如果位置不发生变化，则时间不变
//                 */
//                sb.append(c);
//                sb.append("\nlocType : ");// 定位类型
//                sb.append(location.getLocType());
//                sb.append("\nlocType description : ");// *****对应的定位类型说明*****
//                sb.append(location.getLocTypeDescription());
//                sb.append("\nlatitude : ");// 纬度
//                sb.append(location.getLatitude());
//                sb.append("\nlongtitude : ");// 经度
//                sb.append(location.getLongitude());
//                sb.append("\nradius : ");// 半径
//                sb.append(location.getRadius());
//                sb.append("\nCountryCode : ");// 国家码
//                sb.append(location.getCountryCode());
//                sb.append("\nProvince : ");// 获取省份
//                sb.append(location.getProvince());
//                sb.append("\nCountry : ");// 国家名称
//                sb.append(location.getCountry());
//                sb.append("\ncitycode : ");// 城市编码
//                sb.append(location.getCityCode());
//                sb.append("\ncity : ");// 城市
//                sb.append(location.getCity());
//                sb.append("\nDistrict : ");// 区
//                sb.append(location.getDistrict());
//                sb.append("\nTown : ");// 获取镇信息
//                sb.append(location.getTown());
//                sb.append("\nStreet : ");// 街道
//                sb.append(location.getStreet());
//                sb.append("\naddr : ");// 地址信息
//                sb.append(location.getAddrStr());
//                sb.append("\nStreetNumber : ");// 获取街道号码
//                sb.append(location.getStreetNumber());
//                sb.append("\nUserIndoorState: ");// *****返回用户室内外判断结果*****
//                sb.append(location.getUserIndoorState());
//                sb.append("\nDirection(not all devices have value): ");
//                sb.append(location.getDirection());// 方向
//                sb.append("\nlocationdescribe: ");
//                sb.append(location.getLocationDescribe());// 位置语义化信息
//                sb.append("\nPoi: ");// POI信息
//                if (location.getPoiList() != null && !location.getPoiList().isEmpty()) {
//                    for (int i = 0; i < location.getPoiList().size(); i++) {
//                        Poi poi = (Poi) location.getPoiList().get(i);
//                        sb.append("poiName:");
//                        sb.append(poi.getName() + ", ");
//                        sb.append("poiTag:");
//                        sb.append(poi.getTags() + "\n");
//                    }
//                }
//                if (location.getPoiRegion() != null) {
//                    sb.append("PoiRegion: ");// 返回定位位置相对poi的位置关系，仅在开发者设置需要POI信息时才会返回，在网络不通或无法获取时有可能返回null
//                    PoiRegion poiRegion = location.getPoiRegion();
//                    sb.append("DerectionDesc:"); // 获取POIREGION的位置关系，ex:"内"
//                    sb.append(poiRegion.getDerectionDesc() + "; ");
//                    sb.append("Name:"); // 获取POIREGION的名字字符串
//                    sb.append(poiRegion.getName() + "; ");
//                    sb.append("Tags:"); // 获取POIREGION的类型
//                    sb.append(poiRegion.getTags() + "; ");
//                    sb.append("\nSDK版本: ");
//                }
//                sb.append(mlocationService.getSDKVersion()); // 获取SDK版本
//                if (location.getLocType() == BDLocation.TypeGpsLocation) {// GPS定位结果
//                    sb.append("\nspeed : ");
//                    sb.append(location.getSpeed());// 速度 单位：km/h
//                    sb.append("\nsatellite : ");
//                    sb.append(location.getSatelliteNumber());// 卫星数目
//                    sb.append("\nheight : ");
//                    sb.append(location.getAltitude());// 海拔高度 单位：米
//                    sb.append("\ngps status : ");
//                    sb.append(location.getGpsAccuracyStatus());// *****gps质量判断*****
//                    sb.append("\ndescribe : ");
//                    sb.append("gps定位成功");
//                } else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {// 网络定位结果
//                    // 运营商信息
//                    if (location.hasAltitude()) {// *****如果有海拔高度*****
//                        sb.append("\nheight : ");
//                        sb.append(location.getAltitude());// 单位：米
//                    }
//                    sb.append("\noperationers : ");// 运营商信息
//                    sb.append(location.getOperators());
//                    sb.append("\ndescribe : ");
//                    sb.append("网络定位成功");
//                } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {// 离线定位结果
//                    sb.append("\ndescribe : ");
//                    sb.append("离线定位成功，离线定位结果也是有效的");
//                } else if (location.getLocType() == BDLocation.TypeServerError) {
//                    sb.append("\ndescribe : ");
//                    sb.append("服务端网络定位失败，可以反馈IMEI号和大体定位时间到loc-bugs@baidu.com，会有人追查原因");
//                } else if (location.getLocType() == BDLocation.TypeNetWorkException) {
//                    sb.append("\ndescribe : ");
//                    sb.append("网络不同导致定位失败，请检查网络是否通畅");
//                } else if (location.getLocType() == BDLocation.TypeCriteriaException) {
//                    sb.append("\ndescribe : ");
//                    sb.append("无法获取有效定位依据导致定位失败，一般是由于手机的原因，处于飞行模式下一般会造成这种结果，可以试着重启手机");
//                }
//                Log.d("GPS",sb.toString());
//            }
        }

        @Override
        public void onConnectHotSpotMessage(String s, int i) {
            super.onConnectHotSpotMessage(s, i);
        }

        /**
         * 回调定位诊断信息，开发者可以根据相关信息解决定位遇到的一些问题
         * @param locType 当前定位类型
         * @param diagnosticType 诊断类型（1~9）
         * @param diagnosticMessage 具体的诊断信息释义
         */
        @Override
        public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
            super.onLocDiagnosticMessage(locType, diagnosticType, diagnosticMessage);
            int tag = 2;
            StringBuffer sb = new StringBuffer(256);
            sb.append("诊断结果: ");
            if (locType == BDLocation.TypeNetWorkLocation) {
                if (diagnosticType == 1) {
                    sb.append("网络定位成功，没有开启GPS，建议打开GPS会更好");
                    sb.append("\n" + diagnosticMessage);
                } else if (diagnosticType == 2) {
                    sb.append("网络定位成功，没有开启Wi-Fi，建议打开Wi-Fi会更好");
                    sb.append("\n" + diagnosticMessage);
                }
            } else if (locType == BDLocation.TypeOffLineLocationFail) {
                if (diagnosticType == 3) {
                    sb.append("定位失败，请您检查您的网络状态");
                    sb.append("\n" + diagnosticMessage);
                }
            } else if (locType == BDLocation.TypeCriteriaException) {
                if (diagnosticType == 4) {
                    sb.append("定位失败，无法获取任何有效定位依据");
                    sb.append("\n" + diagnosticMessage);
                } else if (diagnosticType == 5) {
                    sb.append("定位失败，无法获取有效定位依据，请检查运营商网络或者Wi-Fi网络是否正常开启，尝试重新请求定位");
                    sb.append(diagnosticMessage);
                } else if (diagnosticType == 6) {
                    sb.append("定位失败，无法获取有效定位依据，请尝试插入一张sim卡或打开Wi-Fi重试");
                    sb.append("\n" + diagnosticMessage);
                } else if (diagnosticType == 7) {
                    sb.append("定位失败，飞行模式下无法获取有效定位依据，请关闭飞行模式重试");
                    sb.append("\n" + diagnosticMessage);
                } else if (diagnosticType == 9) {
                    sb.append("定位失败，无法获取任何有效定位依据");
                    sb.append("\n" + diagnosticMessage);
                }
            } else if (locType == BDLocation.TypeServerError) {
                if (diagnosticType == 8) {
                    sb.append("定位失败，请确认您定位的开关打开状态，是否赋予APP定位权限");
                    sb.append("\n" + diagnosticMessage);
                }
            }
            Log.e(TAG,sb.toString());
            //mDescribe = sb.toString();
            //sendRefreshMessage();
        }
    };


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 111) {
            if (data != null) {

                mScanContent = data.getStringExtra(Intents.Scan.RESULT);
                Log.d("",String.format("结果为:%s",mScanContent));

                new Thread(new Runnable() {
                    public void run() {
                        String uriAPI = "http://"+mip+"/api/newScanningMessage?deviceID=" + mdeviceID;
                        HttpClient postClient = new DefaultHttpClient();
                        HttpPost httpPost = new HttpPost(uriAPI);
                        List<NameValuePair> params = new ArrayList<NameValuePair>();
                        if(!TextUtils.isEmpty(mScanContent)){
                            params.add(new BasicNameValuePair("content", mScanContent));
                        }
                        long time=System.currentTimeMillis();//long now = android.os.SystemClock.uptimeMillis();
                        params.add(new BasicNameValuePair("scanningTime", String.format("%d",time)));
                        UrlEncodedFormEntity entity;
                        HttpResponse response;
                        try {
                            entity = new UrlEncodedFormEntity(params, "utf-8");
                            httpPost.setEntity(entity);
                            response = postClient.execute(httpPost);

                            if (response.getStatusLine().getStatusCode() == 200) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // 在这里更新UI
                                        Toast.makeText(LandscapeActivity.this, "二维码扫描成功，请在后台查收", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            //
                            if(!scanUploadUrl.isEmpty()){
                                sendScanResultDirectToServer();
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
            init();
        }
    };

    private void sendScanResultDirectToServer(){

        HttpClient postClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(scanUploadUrl);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("deviceid", mdeviceID));

        if(!TextUtils.isEmpty(mScanContent)){
            params.add(new BasicNameValuePair("info", mScanContent));
        }
        long time=System.currentTimeMillis();//long now = android.os.SystemClock.uptimeMillis();
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date d1=new Date(time);
        String t1=format.format(d1);
        params.add(new BasicNameValuePair("time", t1));

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
        };
    }
}
