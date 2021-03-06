package com.drill.liveDemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.zxing.client.android.CaptureActivity;
import com.google.zxing.client.android.Intents;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static android.net.NetworkInfo.State.CONNECTED;
import static com.laifeng.sopcastsdk.constant.SopCastConstant.TAG;


public class MainActivity extends AppCompatActivity {

    private String mdeviceID;
    private int mbattery;
    private String mNetWorkInfo;

    private String mScanContent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GridView grid = (GridView) findViewById(R.id.grid);
        grid.setAdapter(new HoloTilesAdapter());

        //获取权限
        getPermission();
    }

    void init(){
        /***
         * 设备ID号
         */
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        mdeviceID = tm.getMeid().toString();
        Log.d(TAG,String.format("deviceID:%s",mdeviceID));

        //获取电量信息
        BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        mbattery =manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Log.d("battery",String.format("battery info:%d",mbattery));

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

    /** 获取权限*/
    private void getPermission() {
        if (Build.VERSION.SDK_INT>22){
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    android.Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED){
                //先判断有没有权限 ，没有就在这里进行权限的申请
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.READ_PHONE_STATE
                        },1);
            }else {
                //说明已经获取到摄像头权限了
                Log.i("MainActivity","已经获取了权限");
                init();
            }
        }else {
//这个说明系统版本在6.0之下，不需要动态获取权限。
            Log.i("MainActivity","这个说明系统版本在6.0之下，不需要动态获取权限。");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                Log.i("MainActivity","dialog权限回调");
                init();
            }
            return;
        }

        // other 'case' lines to check for other
        // permissions this app might request

    }

    public class HoloTilesAdapter extends BaseAdapter {

        private static final int TILES_COUNT = 2;

        private final int[] DRAWABLES = {
                R.drawable.dark,
                R.drawable.dark,
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
                v = (RelativeLayout) getLayoutInflater().inflate(R.layout.grid_item, parent, false);
            } else {
                v = (RelativeLayout) convertView;
            }
            v.setBackgroundResource(DRAWABLES[position % 3]);

            TextView textView1 = (TextView) v.findViewById(R.id.textView1);

            String string1 = "", string2 = "";
            if(position == 0) {
                string1 = "二维码扫描";
            } else if(position == 1) {
                string1 = "演练直播";
            }
//            else if(position == 2) {
//                string1 = "GPS定位";
//            }
            textView1.setText(string1);

            final int currentPosition = position;
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(currentPosition == 0) {
                        goScan();
                    } else if(currentPosition == 1) {
                        golive();
                    }
//                    else if(currentPosition ==2){
//                        goGps();
//                    }
                }
            });
            return v;
        }
    }

    private void goScan() {
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        intent.setAction(Intents.Scan.ACTION);
        startActivityForResult(intent, 111);
    }

    private void golive() {
        Intent intent = new Intent(this, LandscapeActivity.class);
        intent.putExtra("deviceID", mdeviceID);
        intent.putExtra("battery", mbattery);
        intent.putExtra("networkInfo",mNetWorkInfo);
        startActivity(intent);
    }
//    private void goGps(){
//        Intent intent = new Intent(this, gpsActivity.class);
//        intent.putExtra("deviceID", mdeviceID);
//        intent.putExtra("battery", mbattery);
//        intent.putExtra("networkInfo",mNetWorkInfo);
//
//        startActivity(intent);
//    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 111) {
            if (data != null) {

                mScanContent = data.getStringExtra(Intents.Scan.RESULT);
                Log.d("",String.format("结果为:%s",mScanContent));

                new Thread(new Runnable() {
                    public void run() {
                        String uriAPI = "http://drli.urthe1.xyz/api/newScanningMessage?deviceID=" + mdeviceID;
                        HttpClient postClient = new DefaultHttpClient();
                        HttpPost httpPost = new HttpPost(uriAPI);
                        List<NameValuePair> params = new ArrayList<NameValuePair>();
                        if(!TextUtils.isEmpty(mScanContent)){
                            params.add(new BasicNameValuePair("content", mScanContent));
                        }
                        long time=System.currentTimeMillis();//long now = android.os.SystemClock.uptimeMillis();
                        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        Date d1=new Date(time);
                        String t1=format.format(d1);
                        params.add(new BasicNameValuePair("scanningTime", t1));

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
                                        Toast.makeText(MainActivity.this, "二维码扫描成功，请在后台查收", Toast.LENGTH_SHORT).show();
                                    }
                                });
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
        }
    }
}
