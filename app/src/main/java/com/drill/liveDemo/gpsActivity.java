package com.drill.liveDemo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.drill.liveDemo.baiduGps.LocationService;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class gpsActivity extends AppCompatActivity {

    private String mdeviceID;
    private boolean mGpsStarted;

    private double mlongitude;//经度
    private double mlatitude;//纬度
    private String mdeviceTime;//gps时间
    private String mlocationType;//定位类型
    private float mDirection;//方向
    private float mRadius;//半径
    private float mSpeed;//速度
    private int mSatellite;//卫星数目
    private double mAltitude;//海拔高度
    private String maddr;//地址信息
    private String mDescribe;//描述信息
    private int mInterval;//上报间隔时间
    private int mbattery;
    private String mNetWorkInfo;

    private ScheduledExecutorService scheduleExecutor;
    private ScheduledFuture<?> scheduleManager;
    private Runnable timeTask;

    private gpsAdapter mAdapter;

    private Handler cameraHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1://屏幕刷新内容
                    mAdapter.notifyDataSetChanged();
                    break;
                case 2://间隔时间
                    changeInterval(msg.arg1);
                    break;
                case 6:
                    openGps((boolean)msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((myApplication) getApplication()).mlocationService.stop();
                finish();
            }
        });

        GridView grid = (GridView) findViewById(R.id.grid_gps);
        mAdapter = new gpsActivity.gpsAdapter();
        grid.setAdapter(mAdapter);

        Intent intent = getIntent();
        mdeviceID = intent.getStringExtra("deviceID");
        mbattery = intent.getIntExtra("battery",0);
        mNetWorkInfo = intent.getStringExtra("networkInfo");

        mGpsStarted = false;
        mlongitude =0;//经度
        mlatitude = 0;//纬度
        mdeviceTime ="";//gps时间
        mlocationType ="";//定位类型
        mDirection = 0;//方向
        mInterval = 10;//上报时间
        mRadius = 0.0f;
        mSpeed = 0.0f;
        mSatellite = 0;
        mAltitude =0;
        maddr ="";
        mDescribe ="";



        //获取locationservice实例，建议应用中只初始化1个location实例，然后使用，可以参考其他示例的activity，都是通过此种方式获取locationservice实例的
        ((myApplication) getApplication()).mlocationService.registerListener(mListener);
        ((myApplication) getApplication()).mlocationService.setLocationOption(((myApplication) getApplication()).mlocationService.getDefaultLocationClientOption());

        if(!mGpsStarted) {
            ((myApplication) getApplication()).mlocationService.start();// 定位SDK
            mGpsStarted = true;
        }

        init();
    }

    protected void onDestroy(){
        super.onDestroy();

        if(mGpsStarted){
            ((myApplication) getApplication()).mlocationService.stop();
             mGpsStarted = false;
        }

        ((myApplication) getApplication()).mlocationService.unregisterListener(mListener);
    }

    private void init(){
        //根据远端状态来判断
        createSchedulePool();

        //资源上报池
        createUploadPool();
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
        }, 1, 10, TimeUnit.SECONDS);
    }

    private void changeInterval(int newValue){
        mInterval = newValue;
        if (scheduleManager!= null)
        {
            scheduleManager.cancel(true);
        }
        scheduleManager = scheduleExecutor.scheduleAtFixedRate(timeTask, 1, mInterval, TimeUnit.SECONDS);
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

    private void createUploadPool(){
        scheduleExecutor = Executors.newScheduledThreadPool(5);
        timeTask = new Runnable() {
            @Override
            public void run(){
                //GPS信息上报
                uploadInfo();
            }
        };
        scheduleManager = scheduleExecutor.scheduleAtFixedRate(timeTask, 1, mInterval, TimeUnit.SECONDS);
    }

    private void uploadInfo()
    {
        new Thread(new Runnable() {
            public void run() {
                String uriAPI = "http://drli.urthe1.xyz/api/updateDevicesStatus?deviceID=" + mdeviceID;
                if(mlongitude >0){
                    uriAPI += String.format("&longitude=%f",mlongitude);
                }
                if(mlatitude >0){
                    uriAPI += String.format("&latitude=%f",mlatitude);
                }
                if(!TextUtils.isEmpty(mlocationType)){
                    uriAPI += String.format("&locationType=%s",mlocationType);
                }
                if(mDirection > 0){
                    uriAPI += String.format("&direction=%f",mDirection);
                }
                if(mbattery >= 0){
                    uriAPI += String.format("&battery=%d",mbattery);
                }
                if(!TextUtils.isEmpty(mNetWorkInfo)){
                    uriAPI += String.format("&networkType=%s",mNetWorkInfo);
                }


                HttpClient postClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost(uriAPI);
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                if(!TextUtils.isEmpty(mdeviceTime)){
                    params.add(new BasicNameValuePair("deviceTime", mdeviceTime));
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
                }
                ;
            }
        }).start();

    }

    private BDAbstractLocationListener mListener = new BDAbstractLocationListener() {

        /**
         * 定位请求回调函数
         * @param location 定位结果
         */
        @Override
        public void onReceiveLocation(BDLocation location) {

            // TODO Auto-generated method stub
            if (null != location && location.getLocType() != BDLocation.TypeServerError)
            {
                mlongitude=location.getLongitude();
                mlatitude=location.getLatitude();
                DateFormat dateTimeformat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                mdeviceTime = dateTimeformat2.format(new Date());
                mRadius = location.getRadius();
                mSpeed = location.getSpeed();
                maddr =location.getAddrStr();
                if (location.getLocType() == BDLocation.TypeGpsLocation){
                    mAltitude =location.getAltitude();
                    mSatellite = location.getSatelliteNumber();
                    mDescribe="GPS定位成功";
                }else if(location.getLocType() == BDLocation.TypeNetWorkLocation){
                    mDescribe="网络定位成功";
                }else if (location.getLocType() == BDLocation.TypeOffLineLocation){
                    mDescribe="网络定位成功";
                }
                mDirection=location.getDirection();

               sendRefreshMessage();

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
            Log.d("GPS",sb.toString());
//            mDescribe = sb.toString();
//            sendRefreshMessage();
        }
    };

    private void openGps(boolean gpsEnable){
        if(gpsEnable) {
            mGpsStarted = true;
            ((myApplication) getApplication()).mlocationService.start();
        }else{
            mGpsStarted = false;
            ((myApplication) getApplication()).mlocationService.stop();
            mDescribe = "远程关闭GPS，请在控制台打开";
        }
        sendRefreshMessage();
    }


    private void sendRefreshMessage(){
//        //屏幕显示
//        StringBuffer sb = new StringBuffer(256);
//        sb.append("编号:");
//        sb.append(mdeviceID);
//        sb.append("\n");
//        sb.append("time:");
//        sb.append(mdeviceTime);
//        sb.append("\n");
//        sb.append("latitude:");
//        sb.append(mlatitude);
//        sb.append("\n");
//        sb.append("longtitude:");
//        sb.append(mlongitude);
//        sb.append("\n");
//        sb.append("radius:");
//        sb.append(mRadius);
//        sb.append("\n");
//        sb.append("speed:");
//        sb.append(mSpeed);
//        sb.append("\n");
//        sb.append("satellite:");
//        sb.append(mSatellite);
//        sb.append("\n");
//        sb.append("height:");
//        sb.append(mAltitude);
//        sb.append("\n");
//        sb.append("direction:");
//        sb.append(mDirection);
//        sb.append("\n");
//        sb.append("addr:");
//        sb.append(maddr);
//        sb.append("\n");
//        sb.append("describe:");
//        sb.append(display);
//        sb.append("\n");
//
//        String context = sb.toString();

        Message msg = new Message();
        msg.what = 1;
       // msg.obj = context;
        cameraHandler.sendMessage(msg);
    }

    public class gpsAdapter extends BaseAdapter {

        private static final int TILES_COUNT = 11;

        private final int[] DRAWABLES = {
                R.drawable.dark
        };

        @Override
        public int getCount() {
            return TILES_COUNT;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            RelativeLayout v;
            if (convertView == null) {
                v = (RelativeLayout) getLayoutInflater().inflate(R.layout.grid_gps_item, parent, false);
            } else {
                v = (RelativeLayout) convertView;
            }
            v.setBackgroundResource(DRAWABLES[0]);

            TextView textView1 = (TextView) v.findViewById(R.id.gps_textView1);
            TextView textView2 = (TextView) v.findViewById(R.id.gps_textView2);


            String string1 = "", string2 = "";
            if(position == 0) {
                string1 = "编号:";
                string2 = mdeviceID;
            } else if(position == 1) {
                string1 = "time:";
                string2 = mdeviceTime;
            } else if(position == 2) {
                string1 = "latitude:";
                string2 = String.format("%f",mlatitude);
            }else if(position ==3){
                string1 = "longtitude:";
                string2 = String.format("%f",mlongitude);
            }else if(position ==4){
                string1 = "radius:";
                string2 = String.format("%f",mRadius);
            }else if(position ==5){
                string1 = "speed:";
                string2 = String.format("%f",mSpeed);
            }else if(position ==6){
                string1 = "satellite:";
                string2 = String.format("%d",mSatellite);
            }else if(position ==7){
                string1 = "height:";
                string2 = String.format("%f",mAltitude);
            }else if(position ==8){
                string1 = "direction:";
                string2 = String.format("%f",mDirection);
            }else if(position ==9){
                string1 = "addr:";
                string2 = maddr;
            }else if(position == 10){
                string1 = "describe:";
                string2 = mDescribe;
            }

            textView1.setText(string1);
            textView2.setText(string2);

            return v;
        }
    }


}
