package com.drill.liveDemo;

import android.app.Application;

import com.drill.liveDemo.baiduGps.LocationService;

public class myApplication extends Application {
    public LocationService mlocationService;

    public void onCreate() {
        super.onCreate();

        /***
         * 初始化定位sdk，建议在Application中创建
         */
        mlocationService = new LocationService(getApplicationContext());

    }
}
