package com.drill.liveDemo;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClientOption;
import com.drill.liveDemo.dialog.DialogUtils;
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

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.net.NetworkInfo.State.CONNECTED;
import static com.laifeng.sopcastsdk.constant.SopCastConstant.TAG;

public class LandscapeActivity extends Activity {
    private CameraLivingView mLFLiveView;
    private Button mScanBeforeButton;
    private Button mScanAfterButton;
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
    private Dialog mCtrlDialog;

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
    private String mCtrlip = defaultIP;
    private String mdeviceID;
    private String mStatus;
    private String mNetWorkInfo;
    private int mbattery;

    private String gpsUploadUrl = "";
    private String scanUploadUrl = "";
    private String queryUrl ="";
    private String queryDeviceListsUrl ="";
    private String setScanUrl ="";


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

    private NotificationUtils mNotificationUtils;
    private Notification notification;

    private PowerManager.WakeLock mWakeLock;

    String[] testData = new String[10];
    com.arlib.floatingsearchview.FloatingSearchView mSearchView;
    private String queryKeyWord = "";
    private String fidInfo = "";
    private String extraInfo = "";
    private boolean extraForbiden = false;

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
                    mDebugLiveView.setText((String) msg.obj);
                    break;
                case 8://切换横竖屏
                    changeProtrait((boolean) msg.obj);
                    break;
                case 9://打开阻断器
                    openStopTimer((boolean) msg.obj);
                    break;
                case 10://更新UI
                    mScanBeforeButton.setEnabled(true);
                    mScanBeforeButton.setBackgroundColor(Color.RED);
                    mScanAfterButton.setEnabled(true);
                    mScanAfterButton.setBackgroundColor(Color.RED);
                    break;
                case 11:
                    processFidInfo((String)msg.obj,0);
                    break;
                case 12:
                    processFidInfo((String)msg.obj,1);
                    break;
                case 13://处理查询结果
                     processQueryResult((String)msg.obj);
                    break;
                case 14://查询显示info
                    processFidInfo((String)msg.obj,2);
                    break;
                case 15:
                    Toast.makeText(LandscapeActivity.this, "获取详细信息失败，服务器无此数据！", Toast.LENGTH_SHORT).show();
                    break;
                case 16://第一次启动打开控制系统
                    startControlThread();
                    break;
                case 17:
                    Toast.makeText(LandscapeActivity.this, "查询数据不存在！", Toast.LENGTH_SHORT).show();
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

            if (premissionOk()) {
                //说明已经获取到摄像头权限了
                Log.i("MainActivity", "已经获取了权限");
                init();
            }
        } else {
//这个说明系统版本在6.0之下，不需要动态获取权限。
            Log.i("MainActivity", "这个说明系统版本在6.0之下，不需要动态获取权限。");
        }

    }

    private boolean premissionOk() {
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
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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

    private void initTestData(){
        String imageValue = "/9j/4AAQSkZJRgABAQAASABIAAD/4QBARXhpZgAATU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAqACAAQAAAABAAABfqADAAQAAAABAAABzgAAAAD/7QA4UGhvdG9zaG9wIDMuMAA4QklNBAQAAAAAAAA4QklNBCUAAAAAABDUHYzZjwCyBOmACZjs+EJ+/+IH6ElDQ19QUk9GSUxFAAEBAAAH2GFwcGwCIAAAbW50clJHQiBYWVogB9kAAgAZAAsAGgALYWNzcEFQUEwAAAAAYXBwbAAAAAAAAAAAAAAAAAAAAAAAAPbWAAEAAAAA0y1hcHBsAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAALZGVzYwAAAQgAAABvZHNjbQAAAXgAAAWcY3BydAAABxQAAAA4d3RwdAAAB0wAAAAUclhZWgAAB2AAAAAUZ1hZWgAAB3QAAAAUYlhZWgAAB4gAAAAUclRSQwAAB5wAAAAOY2hhZAAAB6wAAAAsYlRSQwAAB5wAAAAOZ1RSQwAAB5wAAAAOZGVzYwAAAAAAAAAUR2VuZXJpYyBSR0IgUHJvZmlsZQAAAAAAAAAAAAAAFEdlbmVyaWMgUkdCIFByb2ZpbGUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAG1sdWMAAAAAAAAAHwAAAAxza1NLAAAAKAAAAYRkYURLAAAALgAAAaxjYUVTAAAAJAAAAdp2aVZOAAAAJAAAAf5wdEJSAAAAJgAAAiJ1a1VBAAAAKgAAAkhmckZVAAAAKAAAAnJodUhVAAAAKAAAApp6aFRXAAAAFgAAAsJuYk5PAAAAJgAAAthjc0NaAAAAIgAAAv5oZUlMAAAAHgAAAyBpdElUAAAAKAAAAz5yb1JPAAAAJAAAA2ZkZURFAAAALAAAA4prb0tSAAAAFgAAA7ZzdlNFAAAAJgAAAth6aENOAAAAFgAAA8xqYUpQAAAAGgAAA+JlbEdSAAAAIgAAA/xwdFBPAAAAJgAABB5ubE5MAAAAKAAABERlc0VTAAAAJgAABB50aFRIAAAAJAAABGx0clRSAAAAIgAABJBmaUZJAAAAKAAABLJockhSAAAAKAAABNpwbFBMAAAALAAABQJydVJVAAAAIgAABS5hckVHAAAAJgAABVBlblVTAAAAJgAABXYAVgFhAGUAbwBiAGUAYwBuAP0AIABSAEcAQgAgAHAAcgBvAGYAaQBsAEcAZQBuAGUAcgBlAGwAIABSAEcAQgAtAGIAZQBzAGsAcgBpAHYAZQBsAHMAZQBQAGUAcgBmAGkAbAAgAFIARwBCACAAZwBlAG4A6AByAGkAYwBDHqUAdQAgAGgA7ABuAGgAIABSAEcAQgAgAEMAaAB1AG4AZwBQAGUAcgBmAGkAbAAgAFIARwBCACAARwBlAG4A6QByAGkAYwBvBBcEMAQzBDAEOwRMBD0EOAQ5ACAEPwRABD4ERAQwBDkEOwAgAFIARwBCAFAAcgBvAGYAaQBsACAAZwDpAG4A6QByAGkAcQB1AGUAIABSAFYAQgDBAGwAdABhAGwA4QBuAG8AcwAgAFIARwBCACAAcAByAG8AZgBpAGyQGnUoACAAUgBHAEIAIIJyX2ljz4/wAEcAZQBuAGUAcgBpAHMAawAgAFIARwBCAC0AcAByAG8AZgBpAGwATwBiAGUAYwBuAP0AIABSAEcAQgAgAHAAcgBvAGYAaQBsBeQF6AXVBeQF2QXcACAAUgBHAEIAIAXbBdwF3AXZAFAAcgBvAGYAaQBsAG8AIABSAEcAQgAgAGcAZQBuAGUAcgBpAGMAbwBQAHIAbwBmAGkAbAAgAFIARwBCACAAZwBlAG4AZQByAGkAYwBBAGwAbABnAGUAbQBlAGkAbgBlAHMAIABSAEcAQgAtAFAAcgBvAGYAaQBsx3y8GAAgAFIARwBCACDVBLhc0wzHfGZukBoAIABSAEcAQgAgY8+P8GWHTvZOAIIsACAAUgBHAEIAIDDXMO0w1TChMKQw6wOTA7UDvQO5A7oDzAAgA8ADwQO/A8YDrwO7ACAAUgBHAEIAUABlAHIAZgBpAGwAIABSAEcAQgAgAGcAZQBuAOkAcgBpAGMAbwBBAGwAZwBlAG0AZQBlAG4AIABSAEcAQgAtAHAAcgBvAGYAaQBlAGwOQg4bDiMORA4fDiUOTAAgAFIARwBCACAOFw4xDkgOJw5EDhsARwBlAG4AZQBsACAAUgBHAEIAIABQAHIAbwBmAGkAbABpAFkAbABlAGkAbgBlAG4AIABSAEcAQgAtAHAAcgBvAGYAaQBpAGwAaQBHAGUAbgBlAHIAaQENAGsAaQAg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";
        String json = "{\"stype\":\"设备类型\"," +
                "\"sname\":\"设备名称\"," +
                "\"smodel\":\"设备型号\"," +
                "\"sfreq\":\"频率范围\"," +
                "\"copName\":\"申请单位名称\"," +
                "\"copCont\":\"申请单位联系人\"," +
                "\"copTel\":\"申请单位\"," +
                "\"testi\":10," +
                "\"testf\":10.0"+
                "}";
        testData[0]= json;
    }

    private void init() {

        activeWeakLock();

        initTestData();

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

        String ctrlip = pref.getString("ctrlip", "");
        if(ctrlip.equals("")) {
            initControlAddressDialog();
        }
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

        startControlThread();

    }

    private void startTiggerTimer() {

        new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (currentTriggerNum < totalCount) {
                    Toast.makeText(LandscapeActivity.this, "系统故障，停止直播!", Toast.LENGTH_SHORT).show();
                    //关闭推流
                    if (isRecording)
                        stopLive();

                    //关闭上报定时器
                    if (uploaderScheduleManager != null) {
                        uploaderScheduleManager.cancel(true);
                        uploaderScheduleManager = null;
                    }
                    //关闭控制定时器
                    if (controlScheduleManager != null) {
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
                    }).sendEmptyMessageDelayed(0, recoveryDuration * 1000);
                }

                currentTriggerNum++;
                return true;
            }

            ;
        }).sendEmptyMessageDelayed(0, totalDuration * 1000);


    }

    public String updateVersionCode(Context context) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo;
        String versionCode = "";
        try {
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            versionCode = packageInfo.versionCode + "";
            TextView versionView = findViewById(R.id.version_view);
            versionView.setText("版本:" + versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    private void activeWeakLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPS");
        mWakeLock.acquire();
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
        LocationClientOption mOption = new LocationClientOption();
        mOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy); // 可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        mOption.setCoorType("bd09ll"); // 可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        mOption.setScanSpan(1000); // 可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        mOption.setIsNeedAddress(true); // 可选，设置是否需要地址信息，默认不需要
        mOption.setIsNeedLocationDescribe(false); // 可选，设置是否需要地址描述
        mOption.setNeedDeviceDirect(false); // 可选，设置是否需要设备方向结果
        mOption.setLocationNotify(true); // 可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        mOption.setIgnoreKillProcess(false); // 可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop
        mOption.setIsNeedLocationDescribe(false); // 可选，默认false，设置是否需要位置语义化结果，可以在BDLocation
        mOption.setIsNeedLocationPoiList(false); // 可选，默认false，设置是否需要POI结果，可以在BDLocation
        mOption.SetIgnoreCacheException(false); // 可选，默认false，设置是否收集CRASH信息，默认收集
        mOption.setOpenGps(true); // 可选，默认false，设置是否开启Gps定位
        mOption.setIsNeedAltitude(true); // 可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        mOption.setNeedNewVersionRgc(true);
        mOption.setWifiCacheTimeOut(1000);
        ((myApplication) getApplication()).mClient.setLocOption(mOption);
        ((myApplication) getApplication()).mClient.registerLocationListener(mListener);

        //设置后台定位
        //android8.0及以上使用NotificationUtils
        if (Build.VERSION.SDK_INT >= 26) {
            mNotificationUtils = new NotificationUtils(this);
            Notification.Builder builder2 = mNotificationUtils.getAndroidChannelNotification
                    ("适配android 8限制后台定位功能", "正在后台定位");
            notification = builder2.build();
        }
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音

        ((myApplication) getApplication()).mClient.enableLocInForeground(1, notification);
        ((myApplication) getApplication()).mClient.start();
        Log.e(TAG, "初始化,打开GPS!");
        mGpsStarted = true;

    }

    private void initDeviceID() {
        /***deviceID
         * 设备ID号
         */
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
//        if(tm.getDeviceId()!=null){
//            mdeviceID = tm.getDeviceId().toString();
//            Log.d(TAG, String.format("deviceID:%s", mdeviceID));
//        }else{
//            mdeviceID = "noMEID";
//        }

        try {
            String id = tm.getImei(0);
            if (id == null || id.equals("")) {
                mdeviceID = "noMEID";
            } else {
                mdeviceID = id;
                Log.e(TAG, "mdeviceID:" + mdeviceID);
            }
        } catch (NullPointerException ex) {
            Log.e(TAG, "获取meid空指针异常");
            return;
        }

    }

    private void fetchBattery() {

        //获取电量信息
        BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        mbattery = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Log.d("battery", String.format("battery info:%d", mbattery));
    }

    private void fetchNetType() {
        //获得网络类型
        Context context = getApplicationContext();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);        //获取所有网络连接的信息
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Network[] networks = connectivityManager.getAllNetworks();
            if (networks != null && networks.length > 0) {
                int size = networks.length;
                for (int i = 0; i < size; i++) {
                    NetworkInfo.State state = connectivityManager.getNetworkInfo(networks[i]).getState();
                    if (state == CONNECTED) {
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
        mScanBeforeButton = (Button) findViewById(R.id.id_before_button);
        mScanAfterButton = (Button) findViewById(R.id.id_after_button);
        mDebugLiveView = (TextView) findViewById(R.id.debug_live_view);
//        mFlashBtn = (MultiToggleImageButton) findViewById(R.id.camera_flash_button);
//        mFaceBtn = (MultiToggleImageButton) findViewById(R.id.camera_switch_button);
//       midBtn = (MultiToggleImageButton) findViewById(R.id.id_button);
//        mgpsBtn = (MultiToggleImageButton) findViewById(R.id.id_gps);
//        mRecordBtn = (ImageButton) findViewById(R.id.btnRecord);
//        mbackBtn = (ImageButton) findViewById(R.id.backBtn);
        mProgressConnecting = (ProgressBar) findViewById(R.id.progressConnecting);

        initSearchView();
    }

    private void initSearchView(){
        mSearchView = (com.arlib.floatingsearchview.FloatingSearchView)findViewById(R.id.floating_search_view);

        mSearchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
            @Override
            public void onSearchTextChanged(String oldQuery, final String newQuery) {

                //get suggestions based on newQuery
                queryKeyWord = newQuery;
                //pass them on to the search view

            }

        });

        mSearchView.setOnFocusChangeListener(new FloatingSearchView.OnFocusChangeListener() {
                                                 @Override
                                                 public void onFocus() {

                                                 }

                                                 @Override
                                                 public void onFocusCleared() {
                                                     getQueryDeviceListInfo(queryKeyWord);
                                                 }
        }
        );
    }

    private void displayQueryDialog(final String queryResultJson){
        String title = "查询结果";
        String cancelButton= "取消";


        Dialog myDialog = DialogUtils.createCustomDialog4(this, title,
                cancelButton, false, new DialogUtils.DialogListener() {
                    @Override
                    public void onPositiveButton1() {

                    }
                    @Override
                    public void onPositiveButton2() {
                    }

                    @Override
                    public void onNegativeButton() {


                    }
                });

        String[] data = getQueryListTitle(queryResultJson);
        ListView listview = (ListView)myDialog.findViewById(R.id.listview);
        ArrayAdapter<String> adapter1 = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,data);
        listview.setAdapter(adapter1);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                try {
                    JSONArray queryResultJsonArray = new JSONArray(queryResultJson);//转换为JSONObject
                    JSONObject result = queryResultJsonArray.getJSONObject(position);
                    String fid =result.getString("fid");
                    getQueryInfo(fid,"",2);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

        });

        if (myDialog != null && !myDialog.isShowing()) {
            myDialog.show();
        }
    }

    private String[] getQueryListTitle(String queryResultJson){
        ArrayList<String> stringArrayList = new ArrayList<String>();

        try {
            JSONArray queryResultJsonArray = new JSONArray(queryResultJson);//转换为JSONObject
            for(int i=0;i<queryResultJsonArray.length();i++){
                String DisplayInfo="";
                JSONObject object = queryResultJsonArray.getJSONObject(i);
                if(object.has("eutName")) {
                    String name = object.getString("eutName");
                    DisplayInfo += "设备名称:";
                    DisplayInfo += name;
                    DisplayInfo += "\n";
                }
                if(object.has("eutModel")){
                    String model = object.getString("eutModel");
                    DisplayInfo += "设备型号:";
                    DisplayInfo += model;
                    DisplayInfo += "\n";
                }
                if(object.has("companyName")){
                    String companyName = object.getString("companyName");
                    DisplayInfo += "用频单位:";
                    DisplayInfo += companyName;
                }
                if(!DisplayInfo.equals(""))
                    stringArrayList.add(DisplayInfo);
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return stringArrayList.toArray(new String[stringArrayList.size()]);
    }

    private void displayScanDialog(String content) {
        String title = "二维码内容";
        String cancelButton= "否";
        String doneButton1 = "是";

        LayoutInflater inflater = getLayoutInflater();
        View customView = inflater.inflate(R.layout.dialog_item_layout2,null);

        String message = content;
        TextView messageTextView = (TextView)customView.findViewById(R.id.message1);
        messageTextView.setText(message);

        ImageView imageView=(ImageView) customView.findViewById(R.id.dialog_image_view);
        imageView.setImageDrawable(getResources().getDrawable(R.drawable.duijiang));

        String message2 = "是否核验通过？";
        TextView messageTextView2 = (TextView)customView.findViewById(R.id.message2);
        messageTextView2.setText(message2);

        Dialog myDialog = DialogUtils.createCustomDialog3(this, title,customView,
                cancelButton,doneButton1, false, new DialogUtils.DialogListener() {
                    @Override
                    public void onPositiveButton1() {
                        showResultDialog("核验成功！");
                    }
                    @Override
                    public void onPositiveButton2() {
                    }

                    @Override
                    public void onNegativeButton() {
                        showResultDialog("核验失败！");
                    }
                });

        if (myDialog != null && !myDialog.isShowing()) {
            myDialog.show();
        }
    }

    private void showResultDialog(String result){
        new AlertDialog.Builder(this)
                .setTitle("核验结果")
                .setMessage(result)
                .setPositiveButton("确定", null)
                .show();
    }

    private void displayInitDialog(final String fidInfo,final String id){
        String title = "执法选择框";
        String positiveButton1= "设备入场";
        String positiveButton2 = "现场抽查";

        Dialog myDialog = DialogUtils.createCustomDialog0(this, title,
                     positiveButton1,positiveButton2, false, new DialogUtils.DialogListener() {
                        @Override
                        public void onPositiveButton1() {
                            Toast.makeText(LandscapeActivity.this, "正在请求服务器，请等待", Toast.LENGTH_SHORT).show();

                            getQueryInfo(fidInfo,id,0);
                        }
                        @Override
                        public void onPositiveButton2() {
                            Toast.makeText(LandscapeActivity.this, "正在请求服务器，请等待", Toast.LENGTH_SHORT).show();

                            getQueryInfo(fidInfo,id,1);
                        }

                        @Override
                        public void onNegativeButton() {

                        }
                    });

        if (myDialog != null && !myDialog.isShowing()) {
            myDialog.show();
        }
    }

    private void DisplayDialog(final String jsonString, int type){

        String title;
        String negativeButton = "取消";
        String positiveButton1="";
        String positiveButton2="";
        if(type ==0){
            title  ="设备信息(设备入场）";
            positiveButton1= "通过";
            positiveButton2 = "未通过";
        }else if(type ==1){
            title  ="设备信息（现场抽查）";
            positiveButton1= "提交";
            positiveButton2 = "";
        }else{
            title  ="设备信息";
        }

        Configuration mConfiguration = this.getResources().getConfiguration(); //获取设置的配置信息
        int ori = mConfiguration.orientation; //获取屏幕方向

        LayoutInflater inflater = getLayoutInflater();
        View customView = inflater.inflate(R.layout.dialog_item_layout,null);
        String message= "";
        try {
            JSONObject result = new JSONObject(jsonString);//转换为JSONObject
            Iterator<?> it = result.keys();
            String key = "";
            while(it.hasNext()){//遍历JSONObject˝
                key = (String) it.next().toString();
                Object valueObj = result.get(key);
                if(valueObj instanceof String){
                    if(key.equals("fid")){
                        continue;
                    }
                    message += key;
                    message += ":";
                    message += (String)valueObj;
                    message += "\n";
                }else if(valueObj instanceof JSONArray){
                    //图片数据
                    if(key.equals("simg")){
                        JSONArray images = (JSONArray)valueObj;
                        int n = images.length();
                        for(int i=0;i<n;i++){
                            //循环导入图片
                            LinearLayout ll_parent=(LinearLayout) customView.findViewById(R.id.ll_parent);
                            View imageDyView= inflater.inflate(R.layout.dialoag_item_image_layout, null);
                            ll_parent.addView(imageDyView);
                            //导入图片数据
                            String base64 = images.getString(i);
                            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                            ImageView imageView = imageDyView.findViewById(R.id.dialog_image_view);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            imageView.setImageBitmap(decodedByte);
                        }
                    }
                }else if(valueObj instanceof Integer
                        ||valueObj instanceof Float || valueObj instanceof Double){
                    message += key;
                    message += key;
                    message += ":";
                    String value = String.valueOf(valueObj);
                    message += value;
                    message += "\n";
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }


        //导入文本
        TextView messageTextView = (TextView)customView.findViewById(R.id.dialogJsonTextId);
        messageTextView.setText(message);

        //循环导入图片
//        for(int i=0;i<info.simg.size();i++){
//            LinearLayout ll_parent=(LinearLayout) customView.findViewById(R.id.ll_parent);
//            View imageDyView= inflater.inflate(R.layout.dialoag_item_image_layout, null);
//            ll_parent.addView(imageDyView);
//            //导入图片数据
//            String base64 = info.simg.get(i).value;
//            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
//            ImageView imageView = imageDyView.findViewById(R.id.dialog_image_view);
//            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
//            imageView.setImageBitmap(decodedByte);
//        }

        Dialog myDialog ;
        if(type == 0){
            myDialog = DialogUtils.createCustomDialog1(this, title, customView,
                    negativeButton, positiveButton1,positiveButton2, false, new DialogUtils.DialogListener() {
                        @Override
                        public void onPositiveButton1() {
                            String Body = appendInfoAfterOriginJson(jsonString,"1","");
                            setScanInfo(Body);
                        }
                        @Override
                        public void onPositiveButton2() {
                            String Body = appendInfoAfterOriginJson(jsonString,"2","");
                            setScanInfo(Body);
                        }

                        @Override
                        public void onNegativeButton() {
                            //Toast.makeText(LandscapeActivity.this, "取消", Toast.LENGTH_SHORT).show();
                        }
                    });
        }else if(type == 1){
            myDialog = DialogUtils.createCustomDialog2(this, title, customView,
                    negativeButton, positiveButton1, false, new DialogUtils.DialogListener() {
                        @Override
                        public void onPositiveButton1() {
                            String type;
                            if(extraForbiden){
                                type = "3";
                            }else{
                                type = "1";
                            }
                            String Body = appendInfoAfterOriginJson(jsonString, type,extraInfo);
                            setScanInfo(Body);
                            extraInfo = "";
                            extraForbiden = false;
                        }

                        @Override
                        public void onPositiveButton2() {
                            //Toast.makeText(LandscapeActivity.this, "未通过", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onNegativeButton() {
                            //Toast.makeText(LandscapeActivity.this, "取消", Toast.LENGTH_SHORT).show();
                        }
                    }, new DialogUtils.extraDataListener() {
                        @Override
                        public void onTextchanged(String text) {
                            extraInfo = text;
                        }

                        @Override
                        public void onRadioChecked(boolean checked) {
                            extraForbiden = checked;
                        }
                    });
        }else{
            myDialog = DialogUtils.createCustomDialog5(this, title, customView,
                    negativeButton,false, new DialogUtils.DialogListener() {
                        @Override
                        public void onPositiveButton1() {
                            //Toast.makeText(LandscapeActivity.this, "通过", Toast.LENGTH_SHORT).show();

                        }
                        @Override
                        public void onPositiveButton2() {
                            //Toast.makeText(LandscapeActivity.this, "未通过", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onNegativeButton() {
                            //Toast.makeText(LandscapeActivity.this, "取消", Toast.LENGTH_SHORT).show();
                        }
                    });
        }


        if (myDialog != null && !myDialog.isShowing()) {
            myDialog.show();
        }
    }

    private String appendInfoAfterOriginJson(String originJson,String type,String extraInfo){
        String ret="";
        try {
            JSONObject originObject = new JSONObject(originJson);//转换为JSONObject
            if(!extraInfo.equals("")){
                originObject.put("extraInfo",extraInfo);
            }
            originObject.put("deviceid",mdeviceID);
            DateFormat dateTimeformat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String time = dateTimeformat2.format(new Date());
            originObject.put("scanTime",time);
            originObject.put("type",type);
            ret = originObject.toString();
        }catch(JSONException e){
            e.printStackTrace();
        }
        return ret;
    };

    private void createUploadPool() {

        //防止多个线程启动
        if (uploaderScheduleManager != null) {
            uploaderScheduleManager.cancel(true);
        }
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


    private void createSchedulePool() {
        controlScheduleExecutor = Executors.newScheduledThreadPool(5);
        controlTimeTask = new Runnable() {

            @Override    public void run() {
                DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
                int height = dm.heightPixels;
                int width = dm.widthPixels;
                String jsonStr;
                if (height == 240 && width == 320) {
                    String url = "http://" + mCtrlip + "/api/getClientStatus?deviceType=1";
                    jsonStr = httpGet(url);
                } else {
                    String url = "http://" + mCtrlip + "/api/getClientStatus";
                    jsonStr = httpGet(url);
                }
                //解析json文件
                Log.d("camera", jsonStr);
                try {
                    JSONArray jsonArray = new JSONArray(jsonStr);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                        String id = jsonObject.getString("deviceID");
                        if (id.compareTo(mdeviceID) == 0) {//通过设备标识符找到
                            //摄像头控制
                            int facing = jsonObject.getInt("cameraPosition");
                            int cameraNow = mLFLiveView.getCameraData().cameraFacing;
                            Log.d("camera", String.format("cameraid:%d", cameraNow));
                            if (facing != cameraNow && facing != 0) {
                                cameraHandler.sendEmptyMessage(0);
                            }
                            //间隔时间
                            if (!jsonObject.isNull("uploadInterval")) {
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
                            if (cRecord != isRecording)
                                cameraHandler.sendEmptyMessage(1);
                            else {
                                cameraHandler.sendEmptyMessage(10);
                            }
                            //车牌号
                            if (!jsonObject.isNull("streamID")) {
                                String carId = jsonObject.getString("streamID");
                                if (!carId.equals(mid)) {
                                    Message msg = new Message();
                                    msg.what = 3;
                                    msg.obj = carId;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //清晰度
                            if (!jsonObject.isNull("streamDefinition")) {
                                String resolution = jsonObject.getString("streamDefinition");
                                if (resolution.compareTo("540P") == 0) {
                                    resolution = "540";
                                } else if (resolution.compareTo(" 720P") == 0) {
                                    resolution = "720";
                                } else if (resolution.compareTo(" 1080P") == 0) {
                                    resolution = "1080";
                                }
                                if (!resolution.equals(mresolution)) {
                                    Message msg = new Message();
                                    msg.what = 4;
                                    msg.obj = resolution;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //是否打开GPS上报
                            if (!jsonObject.isNull("gpsEnable")) {
                                boolean gpsEnable = jsonObject.getBoolean("gpsEnable");
                                if (mGpsStarted != gpsEnable) {
                                    Message msg = new Message();
                                    msg.what = 6;
                                    msg.obj = gpsEnable;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //是否切换横竖屏
                            if (!jsonObject.isNull("isLandscape")) {
                                boolean landscape = jsonObject.getBoolean("isLandscape");
                                boolean protait = !landscape;
                                if (mProtait != protait) {
                                    Message msg = new Message();
                                    msg.what = 8;
                                    msg.obj = landscape;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            //是否触发阻断器
                            if (!jsonObject.isNull("switcher") &&
                                    !jsonObject.isNull("totalDuration") &&
                                    !jsonObject.isNull("recoveryDuration") &&
                                    !jsonObject.isNull("totalCount")) {
                                boolean enableTrigger = jsonObject.getBoolean("switcher");
                                totalDuration = jsonObject.getInt("totalDuration");
                                recoveryDuration = jsonObject.getInt("recoveryDuration");
                                totalCount = jsonObject.getInt("totalCount");
                                if (enableTrigger != triggerEnable) {
                                    Message msg = new Message();
                                    msg.what = 9;
                                    msg.obj = enableTrigger;
                                    cameraHandler.sendMessage(msg);
                                }
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //全局控制接口
                String url = "http://" + mCtrlip + "/api/settings";
                jsonStr = httpGet(url);
                try {
                    JSONObject jsonObject = new JSONObject(jsonStr);
                    //推流地址
                    if (!jsonObject.isNull("ip")) {
                        String ip = jsonObject.getString("ip");
                        if (!ip.equals(mip)) {
                            Message msg = new Message();
                            msg.what = 5;
                            msg.obj = ip;
                            cameraHandler.sendMessage(msg);
                        }
                    }
                    //gps单独上报地址
                    if (!jsonObject.isNull("uploadUrl")) {
                        String uploadurl = jsonObject.getString("uploadUrl");
                        String[] temp = null;
                        temp = uploadurl.split("\\|");
                        if (!temp[0].equals(gpsUploadUrl)) {
                            gpsUploadUrl = temp[0];
                        }
                        if (!temp[1].equals(scanUploadUrl)) {
                            scanUploadUrl = temp[1];
                        }
                        if(temp.length > 2 && !temp[2].equals(queryUrl)){
                            queryUrl = temp[2];
                        }
                        if(temp.length > 3 && !temp[3].equals(queryDeviceListsUrl)){
                            queryDeviceListsUrl = temp[3];
                        }
                        if(temp.length > 4 && !temp[4].equals(setScanUrl)){
                            setScanUrl = temp[4];
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        controlScheduleManager = controlScheduleExecutor.scheduleAtFixedRate(controlTimeTask, 1, 10, TimeUnit.SECONDS);

    }

    private void initListeners() {
        mScanBeforeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mScanBeforeButton.setEnabled(true);
                mScanBeforeButton.setBackgroundColor(Color.RED);
                //停止直播
                if (isRecording)
                    stopLive();

                //释放view
                if (mLFLiveView != null) {
                    mLFLiveView.release();
                }

                //关闭上报定时器
                if (uploaderScheduleManager != null) {
                    uploaderScheduleManager.cancel(true);
                    uploaderScheduleManager = null;
                }
                //关闭控制定时器
                if (controlScheduleManager != null) {
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
                    }

                    ;
                }).sendEmptyMessageDelayed(0, 1000);//表示延迟1秒发送任务

            }
        });
        mScanAfterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mScanAfterButton.setEnabled(true);
                mScanAfterButton.setBackgroundColor(Color.RED);
                //停止直播
                if (isRecording)
                    stopLive();

                //释放view
                if (mLFLiveView != null) {
                    mLFLiveView.release();
                }

                //关闭上报定时器
                if (uploaderScheduleManager != null) {
                    uploaderScheduleManager.cancel(true);
                    uploaderScheduleManager = null;
                }
                //关闭控制定时器
                if (controlScheduleManager != null) {
                    controlScheduleManager.cancel(true);
                    controlScheduleManager = null;
                }

                new Handler(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        //实现页面跳转
                        Intent intent = new Intent(LandscapeActivity.this, CaptureActivity.class);
                        intent.setAction(Intents.Scan.ACTION);
                        startActivityForResult(intent, 112);
                        return true;
                    }

                    ;
                }).sendEmptyMessageDelayed(0, 1000);//表示延迟1秒发送任务

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

    private void initControlAddressDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View playView = inflater.inflate(R.layout.control_address_dialog,(ViewGroup) findViewById(R.id.dialog));
        final EditText ipEditText = (EditText) playView.findViewById(R.id.ip);
        Button okBtn = (Button) playView.findViewById(R.id.ok);
        Button cancelBtn = (Button) playView.findViewById(R.id.cancel);
        AlertDialog.Builder uploadBuilder = new AlertDialog.Builder(this);
        uploadBuilder.setTitle("请设置控制台ip信息:");
        uploadBuilder.setView(playView);
        mCtrlDialog = uploadBuilder.create();

        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mCtrlip = ipEditText.getText().toString();
                if(TextUtils.isEmpty(mCtrlip)) {
                    mCtrlip = defaultIP;
                }
                //持久化
                SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
                editor.putString("ctrlip",mCtrlip);
                editor.apply();

                //发送设置指令
                Message msg = new Message();
                msg.what = 16;
                cameraHandler.sendMessage(msg);


                LandscapeActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Log.i("Dialog","dialog dismiss");
                        mCtrlDialog.dismiss();
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

                        SharedPreferences.Editor editor = getSharedPreferences("data",MODE_PRIVATE).edit();
                        editor.putString("ctrlip",mCtrlip);
                        editor.apply();

                        //发送设置指令
                        Message msg = new Message();
                        msg.what = 16;
                        cameraHandler.sendMessage(msg);

                        Log.i("Dialog","dialog dismiss");
                        mCtrlDialog.dismiss();
                    }
                });
            }
        });

        mCtrlDialog.show();
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

    private void changeInterval(int newValue) {
        mInterval = newValue;
        if (uploaderScheduleManager != null) {
            uploaderScheduleManager.cancel(true);
        }
        uploaderScheduleManager = uploaderScheduleExecutor.scheduleAtFixedRate(uploaderTimeTask, 1, mInterval, TimeUnit.SECONDS);
    }

    private void changeCarId(String carID) {
        mid = carID;
        SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
        editor.putString("id", mid);
        editor.apply();
        Toast.makeText(LandscapeActivity.this, "车牌ID改为:" + mid, Toast.LENGTH_SHORT).show();
    }

    private void changeResolution(String resolution) {
        mresolution = resolution;
        Toast.makeText(LandscapeActivity.this, "清晰度改为:" + mresolution, Toast.LENGTH_SHORT).show();
        SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
        editor.putString("resolution", mresolution);
        editor.apply();
    }

    private void changeIp(String ip) {
        mip = ip;
        Toast.makeText(LandscapeActivity.this, "ip改为:" + mip, Toast.LENGTH_SHORT).show();
        SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
        editor.putString("ip", mip);
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


    private void changeProtrait(boolean landscape) {
        mProtait = !landscape;
        if (mProtait) {
            setTopApp(this);
            Toast.makeText(LandscapeActivity.this, "切换为竖屏", Toast.LENGTH_SHORT).show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setTopApp(this);
            Toast.makeText(LandscapeActivity.this, "切换为横屏", Toast.LENGTH_SHORT).show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
        editor.putBoolean("portrait", mProtait);
        editor.apply();
    }

    private void processFidInfo(String fidInfo,int type){

        if(fidInfo.equals("")){
            Toast.makeText(LandscapeActivity.this, "数据获取失败，请检查接口！", Toast.LENGTH_SHORT).show();

            return;
        }

        if(type ==0 ){
            DisplayDialog(fidInfo,0);
        }else if(type ==1){
            DisplayDialog(fidInfo,1);
        }else if(type ==2){
            DisplayDialog(fidInfo,2);
        }
    }

    private void startControlThread(){
        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        String ctrlip = pref.getString("ctrlip", "");
        if(!ctrlip.equals("")){
            //根据远端状态来判断
            createSchedulePool();
            //资源上报池
            createUploadPool();
            Intent startIntent = new Intent(this, BackgroundService.class);
            startService(startIntent);
        }
    }

    private void processQueryResult(String query){
        displayQueryDialog(query);
    }

    private void openStopTimer(boolean enable) {
        triggerEnable = enable;
        if (triggerEnable) {
            startTiggerTimer();
        }
    }

    private void openGps(boolean gpsEnable) {
        if (gpsEnable) {
            if (!mGpsStarted) {//打开GPS
                ((myApplication) getApplication()).mClient.registerLocationListener(mListener);
                ((myApplication) getApplication()).mClient.enableLocInForeground(1, notification);
                ((myApplication) getApplication()).mClient.start();
                mGpsStarted = true;
                Log.e(TAG, "开关打开GPS!");
                Toast.makeText(LandscapeActivity.this, "打开GPS!", Toast.LENGTH_SHORT).show();
            }
        } else {

            if(mGpsStarted){
                ((myApplication) getApplication()).mClient.disableLocInForeground(true);
                ((myApplication) getApplication()).mClient.unRegisterLocationListener(mListener);
                ((myApplication) getApplication()).mClient.stop();
                mGpsStarted = false;
                mDescribe = "远程关闭GPS，请在控制台打开";
                Log.e(TAG, "开关关闭GPS!");
                Toast.makeText(LandscapeActivity.this, "关闭GPS!", Toast.LENGTH_SHORT).show();
            }


        }
        //sendRefreshMessage();
    }

    private void stopLive() {
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

    private void startLive() {
        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        mProtait = pref.getBoolean("portrait", false);
        mid = pref.getString("id", "");
        mip = pref.getString("ip", defaultIP);
        mresolution = pref.getString("resolution", "");
        if (TextUtils.isEmpty(mid)) {
            Toast.makeText(LandscapeActivity.this, "mid未赋值，等待...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(mip)) {
            Toast.makeText(LandscapeActivity.this, "推流地址未赋值，等待...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(mresolution)) {
            Toast.makeText(LandscapeActivity.this, "清晰度未赋值，等待...", Toast.LENGTH_SHORT).show();
            return;
        }

        loadLiveViewConfig();
        String uploadUrl = mPublishUrl + mid;
        Log.i("mid", "url:" + uploadUrl);
        mRtmpSender.setAddress(uploadUrl);
        mProgressConnecting.setVisibility(View.VISIBLE);
        Toast.makeText(LandscapeActivity.this, "准备开始直播", Toast.LENGTH_SHORT).show();
//        mRecordBtn.setBackgroundResource(R.mipmap.ic_record_stop);
        mRtmpSender.connect();
        mStatus = "正常";
    }

    private void restartLive() {

        if (isRecording) {
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

    private void refreshLiveInfo() {
        StringBuffer sb = new StringBuffer(256);
        sb.append("状态: ");
        if (isRecording) {
            sb.append("正在推流");
        } else {
            sb.append("停止推流");
        }

        sb.append("\n");
        Message msg = new Message();
        msg.what = 7;
        msg.obj = sb.toString();
        cameraHandler.sendMessage(msg);
    }

    private void initLiveView() {
        SopCastLog.isOpen(true);
        mLFLiveView.init();
        CameraConfiguration.Builder cameraBuilder = new CameraConfiguration.Builder();
        DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
        int height = dm.heightPixels;
        int width = dm.widthPixels;
        if ((height == 240 && width == 320) || (height == 320 && width == 240)) {
            cameraBuilder.setPreview(height, width);
        }

        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            cameraBuilder.setOrientation(CameraConfiguration.Orientation.LANDSCAPE);
        } else if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
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
                mScanBeforeButton.setEnabled(true);
                mScanBeforeButton.setBackgroundColor(Color.RED);
                mScanAfterButton.setEnabled(true);
                mScanAfterButton.setBackgroundColor(Color.RED);
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
                Toast.makeText(LandscapeActivity.this, "开播失败,error:" + error, Toast.LENGTH_SHORT).show();
                mLFLiveView.stop();
                isRecording = false;
                refreshLiveInfo();
                mScanBeforeButton.setEnabled(true);
                mScanBeforeButton.setBackgroundColor(Color.RED);
                mScanAfterButton.setEnabled(true);
                mScanAfterButton.setBackgroundColor(Color.RED);
            }

            @Override
            public void startSuccess() {
                //直播成功
                Toast.makeText(LandscapeActivity.this, "开始直播,id号:" + mid + ",地址:" + mPublishUrl, Toast.LENGTH_SHORT).show();
                isRecording = true;
                refreshLiveInfo();
                mScanBeforeButton.setEnabled(true);
                mScanBeforeButton.setBackgroundColor(Color.RED);
                mScanAfterButton.setEnabled(true);
                mScanAfterButton.setBackgroundColor(Color.RED);
            }
        });
    }

    private void loadLiveViewConfig() {
        if (!mProtait) {
            if (mresolution.compareTo("1080") == 0) {
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(1920, 1080).setBps(900, 1800);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);
                mRtmpSender.setVideoParams(1920, 1080);

                mPublishUrl = "rtmp://" + mip + "/live_landscape_1080p/";
            } else if (mresolution.compareTo("720") == 0) {
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(1280, 720).setBps(600, 1600);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(1280, 720);

                mPublishUrl = "rtmp://" + mip + "/live_720_convert/";
            } else {
                //Toast.makeText(LandscapeActivity.this, "默认用540", Toast.LENGTH_SHORT).show();
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(960, 540).setBps(450, 1200);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(960, 540);

                mPublishUrl = "rtmp://" + mip + "/live_540/";
            }
        } else {
            if (mresolution.compareTo("1080") == 0) {
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(1080, 1920).setBps(900, 1800);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(1080, 1920);

                mPublishUrl = "rtmp://" + mip + "/live_portrait_1080p/";
            } else if (mresolution.compareTo("720") == 0) {
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(720, 1280).setBps(600, 1600);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(720, 1280);

                mPublishUrl = "rtmp://" + mip + "/live_portrait_720p/";
            } else {
                //Toast.makeText(LandscapeActivity.this, "默认用540", Toast.LENGTH_SHORT).show();
                VideoConfiguration.Builder videoBuilder = new VideoConfiguration.Builder();
                videoBuilder.setSize(540, 960).setBps(450, 1200);
                mVideoConfiguration = videoBuilder.build();
                mLFLiveView.setVideoConfiguration(mVideoConfiguration);

                mRtmpSender.setVideoParams(540, 960);

                mPublishUrl = "rtmp://" + mip + "/live_540/";
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
            mScanBeforeButton.setEnabled(true);
            mScanBeforeButton.setBackgroundColor(Color.RED);
            mScanAfterButton.setEnabled(true);
            mScanAfterButton.setBackgroundColor(Color.RED);
            refreshLiveInfo();
        }

        @Override
        public void onPublishFail() {
            mProgressConnecting.setVisibility(View.GONE);
            Toast.makeText(LandscapeActivity.this, "发布失败", Toast.LENGTH_SHORT).show();
//            mRecordBtn.setBackgroundResource(R.mipmap.ic_record_start);
            isRecording = false;
            mScanBeforeButton.setEnabled(true);
            mScanBeforeButton.setBackgroundColor(Color.RED);
            mScanAfterButton.setEnabled(true);
            mScanAfterButton.setBackgroundColor(Color.RED);
            refreshLiveInfo();
        }

        @Override
        public void onNetGood() {
            if (mCurrentBps + 50 <= mVideoConfiguration.maxBps) {
                SopCastLog.d(TAG, "BPS_CHANGE good up 50");
                int bps = mCurrentBps + 50;
                if (mLFLiveView != null) {
                    boolean result = mLFLiveView.setVideoBps(bps);
                    if (result) {
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
            if (mCurrentBps - 100 >= mVideoConfiguration.minBps) {
                SopCastLog.d(TAG, "BPS_CHANGE bad down 100");
                int bps = mCurrentBps - 100;
                if (mLFLiveView != null) {
                    boolean result = mLFLiveView.setVideoBps(bps);
                    if (result) {
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
        if (mLFLiveView != null)
            mLFLiveView.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLFLiveView != null) {
            mLFLiveView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLFLiveView != null) {
            mLFLiveView.stop();
            mLFLiveView.release();
        }

        // 关闭前台定位服务
        ((myApplication) getApplication()).mClient.disableLocInForeground(true);
        // 取消之前注册的 BDAbstractLocationListener 定位监听函数
        ((myApplication) getApplication()).mClient.unRegisterLocationListener(mListener);
        //停止定位服务
        if (mGpsStarted) {
            ((myApplication) getApplication()).mClient.stop();
            mGpsStarted = false;

        }

        //关闭上报定时器
        if (uploaderScheduleManager != null) {
            uploaderScheduleManager.cancel(true);
            uploaderScheduleManager = null;
        }
        //关闭控制定时器
        if (controlScheduleManager != null) {
            controlScheduleManager.cancel(true);
            controlScheduleManager = null;
        }

        mWakeLock.release();
    }

    public String httpGet(String httpUrl) {
        String result = "";
        try {
            BufferedReader reader = null;
            StringBuffer sbf = new StringBuffer();

            URL url = new URL(httpUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            //设置超时时间 10s
            connection.setConnectTimeout(10000);
            //设置请求方式
            connection.setRequestMethod("GET");
            connection.connect();
            InputStream is = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String strRead = null;
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


    private void getQueryInfo(final String fid,final String id,final int type) {

        new Thread(new Runnable() {
       @Override
            public void run() {

               String url;
               if(!id.equals("")){
                   url = queryUrl+"?fid="+fid+"&id="+id;
               }else{
                   url = queryUrl+"?fid="+fid;
               }

           String fidInfo = httpGet(url);
               if(fidInfo.contains("系统错误")){
                   Message msg = new Message();
                   msg.what = 15;
                   msg.obj = fidInfo;
                   cameraHandler.sendMessage(msg);
                   return;
               }

                if(type == 0){
                    Message msg = new Message();
                    msg.what = 11;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                }else if(type == 1){
                    Message msg = new Message();
                    msg.what = 12;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                }else{//查询列表
                    Message msg = new Message();
                    msg.what = 14;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                }

            }
        }).start();
    }

    private void getQueryInfo2(final int type) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                String url;
                url = queryUrl+"?fid="+mScanContent;

                String fidInfo = httpGet(url);

                if(fidInfo.contains("系统错误")||fidInfo.equals("")){
                    Message msg = new Message();
                    msg.what = 15;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                    return;
                }

                if(type == 0){
                    Message msg = new Message();
                    msg.what = 11;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                }else if(type == 1){
                    Message msg = new Message();
                    msg.what = 12;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                }else{//查询列表
                    Message msg = new Message();
                    msg.what = 14;
                    msg.obj = fidInfo;
                    cameraHandler.sendMessage(msg);
                }

            }
        }).start();
    }

    private void getQueryDeviceListInfo(final String queryKeyWord) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                String url = queryDeviceListsUrl+"?search="+queryKeyWord;

                String searchList = httpGet(url);

                Message msg;
                if(searchList.equals("")){
                    msg = new Message();
                    msg.what = 17;
                }else{
                    msg = new Message();
                    msg.what = 13;
                    msg.obj = searchList;
                }
                cameraHandler.sendMessage(msg);

            }
        }).start();
    }

    private void setScanInfo(final String Body) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                HttpClient postClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(setScanUrl);
                httpPost.addHeader("Content-Type", "application/json");


                UrlEncodedFormEntity entity;
                HttpResponse response;
                try {
                    httpPost.setEntity(new StringEntity(Body));

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
        }
        ).start();
    }

    private void uploadInfo() {
                //发送到服务器
                if (!gpsUploadUrl.isEmpty()) {
                    sendDirectToServer();
                }
                String uriAPI = "http://" + mCtrlip + "/api/updateDevicesStatus";

                HttpClient postClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(uriAPI);
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("deviceID", mdeviceID));
                if (!TextUtils.isEmpty(mStatus)) {
                    params.add(new BasicNameValuePair("appStatus", mStatus));
                    params.add(new BasicNameValuePair("streamStatus", String.format("%b", isRecording)));
                }
                if (!TextUtils.isEmpty(mNetWorkInfo)) {
                    params.add(new BasicNameValuePair("networkType", mNetWorkInfo));
                }
                if (mbattery >= 0) {
                    params.add(new BasicNameValuePair("battery", String.format("%d", mbattery)));
                }
                if (mlongitude > 0) {
                    params.add(new BasicNameValuePair("longitude", String.format("%f", mlongitude)));
                }
                if (mlatitude > 0) {
                    params.add(new BasicNameValuePair("latitude", String.format("%f", mlatitude)));
                }
                if (!TextUtils.isEmpty(mDescribe)) {
                    params.add(new BasicNameValuePair("locationType", mDescribe));
                }
                if (mDirection > 0) {
                    params.add(new BasicNameValuePair("direction", String.format("%f", mDirection)));
                }
                if (!TextUtils.isEmpty(mdeviceTime)) {
                    params.add(new BasicNameValuePair("deviceTime", mdeviceTime));
                }
                DisplayMetrics dm = getApplicationContext().getResources().getDisplayMetrics();
                int height = dm.heightPixels;
                int width = dm.widthPixels;
                if ((height == 240 && width == 320) || (height == 320 && width == 240)) {
                    params.add(new BasicNameValuePair("deviceType", String.format("%d", 1)));
                } else {
                    params.add(new BasicNameValuePair("deviceType", String.format("%d", 0)));
                }

                UrlEncodedFormEntity entity;
                HttpResponse response;
                try {
                    entity = new UrlEncodedFormEntity(params, "utf-8");
                    httpPost.setEntity(entity);
                    response = postClient.execute(httpPost);

                    if (response.getStatusLine().getStatusCode() == 200) {

                    }
                    Log.e(TAG, String.format("gps: sendtoManager result:%d,mlongitude:%f,mlatitude:%f", response.getStatusLine().getStatusCode(), mlongitude,mlatitude));

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                };

    }

    private void sendDirectToServer() {

        HttpClient postClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(gpsUploadUrl);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("deviceid", mdeviceID));
        if (mlongitude > 0) {
            params.add(new BasicNameValuePair("longitude", String.format("%f", mlongitude)));
        }
        if (mlatitude > 0) {
            params.add(new BasicNameValuePair("latitude", String.format("%f", mlatitude)));
        }
        if (!TextUtils.isEmpty(mdeviceTime)) {
            params.add(new BasicNameValuePair("createDate", mdeviceTime));
        }
        if (mSpeed >= 0) {
            params.add(new BasicNameValuePair("speed", String.format("%f", mSpeed)));
        }
        if (mtype > -1) {
            params.add(new BasicNameValuePair("code", String.format("%d", mtype)));
        }

        UrlEncodedFormEntity entity;
        HttpResponse response;
        try {
            entity = new UrlEncodedFormEntity(params, "utf-8");
            httpPost.setEntity(entity);
            response = postClient.execute(httpPost);

            if (response.getStatusLine().getStatusCode() == 200) {
            }
            Log.e(TAG, String.format("gps: sendDirectToServer result:%d,mlongitude:%f,mlatitude:%f,speed:%f", response.getStatusLine().getStatusCode(), mlongitude,mlatitude,mSpeed));

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ;
    }

    protected void onPause() {
        super.onPause();
        Log.e("active", "active:pause");
        mStatus = "注意，设备已经切后台！！！";
    }

    protected void onResume() {
        super.onResume();
        mStatus = "正常";
        Log.e("active", "active:resume");
    }

    private BDAbstractLocationListener mListener = new BDAbstractLocationListener() {

        /**
         * 定位请求回调函数
         * @param location 定位结果
         */
        @Override
        public void onReceiveLocation(BDLocation location) {
            // TODO Auto-generated method stub
            Log.e(TAG, String.format("-->>>>onReceiveLocation :gps:serverType:%d", location.getLocType()));
            mtype = location.getLocType();
            if (null != location && location.getLocType() != BDLocation.TypeServerError) {
                mlongitude = location.getLongitude();
                mlatitude = location.getLatitude();
                mRadius = location.getRadius();
                mSpeed = location.getSpeed();
                maddr = location.getAddrStr();
                if (location.getLocType() == BDLocation.TypeGpsLocation) {
                    mAltitude = location.getAltitude();
                    mSatellite = location.getSatelliteNumber();
                    mDescribe = "GPS定位成功 卫星：" + mSatellite + " long:" + location.getLongitude() + " lat:" + location.getLatitude() + " time:" + System.currentTimeMillis();
                } else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {
                    mDescribe = "基站定位";
                } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {
                    mDescribe = "离线定位成功";
                }
                mDirection = location.getDirection();
            } else {
                Log.e(TAG, "gps type serverError!");
            }
            Log.e(TAG, "-->>>>onReceiveLocation mtype=" + mDescribe + " mSatellite:" + mSatellite);
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
            Log.e(TAG, sb.toString());
            //mDescribsb.toString();scan
            //sendRefreshMessage();
        }
    };


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        {
            if (data != null) {

                mScanContent = data.getStringExtra(Intents.Scan.RESULT);
                Log.d("", String.format("结果为:%s", mScanContent));

                if(mScanContent.contains("设备类型")){
                    displayScanDialog(mScanContent);
                }

//                if(mScanContent.contains("fid")){
//
//                    try{
//                        String fid ="";
//                        String id ="";
//
//                        JSONObject scanContent = new JSONObject(mScanContent);//转换为JSONObject
//                        Iterator<?> it = scanContent.keys();
//                        String key = "";
//                        while(it.hasNext()) {//遍历JSONObject
//                            key = (String) it.next().toString();
//                            if(key.equals("fid")){
//                                fid = scanContent.getString("fid");
//                            }else if(key.equals("id")){
//                                id = scanContent.getString("id");
//                            }
//                        }
//                        Toast.makeText(LandscapeActivity.this, "正在请求服务器，请等待", Toast.LENGTH_SHORT).show();
//                        if(requestCode == 111){
//                            getQueryInfo(fid,id,0);
//                        }else if(requestCode == 112){
//                            getQueryInfo(fid,id,1);
//                        }
//                    }catch (JSONException e){
//                        e.printStackTrace();
//                    }
//
//                }

                Toast.makeText(LandscapeActivity.this, "正在请求服务器，请等待", Toast.LENGTH_SHORT).show();
                if(requestCode == 111){
                    getQueryInfo2(0);
                }else if(requestCode == 112){
                    getQueryInfo2(1);
                }



//                new Thread(new Runnable() {
//                    public void run() {
//                        String uriAPI = "http://" + mCtrlip + "/api/newScanningMessage?deviceID=" + mdeviceID;
//                        HttpClient postClient = new DefaultHttpClient();
//                        HttpPost httpPost = new HttpPost(uriAPI);
//                        List<NameValuePair> params = new ArrayList<NameValuePair>();
//                        if (!TextUtils.isEmpty(mScanContent)) {
//                            params.add(new BasicNameValuePair("content", mScanContent));
//                        }
//                        long time = System.currentTimeMillis();//long now = android.os.SystemClock.uptimeMillis();
//                        params.add(new BasicNameValuePair("scanningTime", String.format("%d", time)));
//                        UrlEncodedFormEntity entity;
//                        HttpResponse response;
//                        try {
//                            entity = new UrlEncodedFormEntity(params, "utf-8");
//                            httpPost.setEntity(entity);
//                            response = postClient.execute(httpPost);
//
//                            if (response.getStatusLine().getStatusCode() == 200) {
//                                runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        // 在这里更新UI
//                                        Toast.makeText(LandscapeActivity.this, "二维码扫描成功，请在后台查收", Toast.LENGTH_SHORT).show();
//                                    }
//                                });
//                            }
//
//                            //
//                            if (!scanUploadUrl.isEmpty()) {
//                                sendScanResultDirectToServer();
//                            }
//
//                        } catch (UnsupportedEncodingException e) {
//                            e.printStackTrace();
//                        } catch (ClientProtocolException e) {
//                            e.printStackTrace();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                        ;
//                    }
//                }).start();
            }
            init();
        }
    }

    ;

    private void sendScanResultDirectToServer() {

        HttpClient postClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(scanUploadUrl);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("deviceid", mdeviceID));

        if (!TextUtils.isEmpty(mScanContent)) {
            params.add(new BasicNameValuePair("info", mScanContent));
        }
        long time = System.currentTimeMillis();//long now = android.os.SystemClock.uptimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date d1 = new Date(time);
        String t1 = format.format(d1);
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
        }
        ;
    }
}
