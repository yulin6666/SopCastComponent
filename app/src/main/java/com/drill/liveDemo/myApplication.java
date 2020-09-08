package com.drill.liveDemo;

import android.app.Application;
import android.location.Location;

import com.baidu.location.LocationClient;
import com.drill.liveDemo.baiduGps.LocationService;

public class myApplication extends Application {
    public LocationClient mClient;

    public void onCreate() {
        super.onCreate();

        /***
         * 初始化定位sdk，建议在Application中创建
         */
        mClient = new LocationClient(getApplicationContext());

    }
}
