package com.dji.FPVDemo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;


import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.battery.DJIBatteryState;
import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIAircraftRemainingBatteryState;
import dji.common.flightcontroller.DJIFlightControllerCurrentState;
import dji.common.flightcontroller.DJIFlightControllerDataType;
import dji.common.flightcontroller.DJIFlightControllerSmartGoHomeStatus;
import dji.common.flightcontroller.DJIGoHomeStatus;
import dji.common.flightcontroller.DJISimulatorInitializationData;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.battery.DJIBattery;
import dji.sdk.camera.DJICamera;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.flightcontroller.DJIFlightControllerDelegate;
import dji.sdk.flightcontroller.DJIFlightLimitation;
import dji.sdk.flightcontroller.DJISimulator;
import dji.sdk.missionmanager.DJIMission;
import dji.sdk.missionmanager.DJIMissionManager;
import dji.sdk.missionmanager.DJIWaypoint;
import dji.sdk.missionmanager.DJIWaypointMission;
import dji.sdk.products.DJIAircraft;
import dji.common.util.DJICommonCallbacks.DJICompletionCallback;
import dji.sdk.sdkmanager.DJISDKManager;

public class DemoMaps extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback, DJIMissionManager.MissionProgressStatusCallback, DJICommonCallbacks.DJICompletionCallback {

    protected static final String TAG = "GSDemoActivity";

    private GoogleMap gMap;

    private Button locate, add, clear;
    private Button config, prepare, start, stop, atterrir;
    private TextView textValeurDistance;
    private TextView textValeurDistanceMax;
    private TextView textGPS;
    private Circle circle;


    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 20.0f;
    private float mSpeed = 5.0f;
    private int rotation;

    private int batteryLevel;

    private DJIWaypointMission mWaypointMission;
    private DJIMissionManager mMissionManager;
    private DJIFlightController mFlightController;

    private DJIWaypointMission.DJIWaypointMissionFinishedAction mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoHome;;
    private DJIWaypointMission.DJIWaypointMissionHeadingMode mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto ;


    private StringBuffer mGpsStringBuffer;
    private StringBuffer mDistanceMax;
    protected static final int CHANGE_TEXT_VIEW = 0;


    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
        initMissionManager();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view) {
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string) {
        DemoMaps.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(DemoMaps.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        //   locate = (Button) findViewById(R.id.locate);
        //  add = (Button) findViewById(R.id.add);
        //  clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        prepare = (Button) findViewById(R.id.prepare);
        start = (Button) findViewById(R.id.start);
        start.setEnabled(false);
        stop = (Button) findViewById(R.id.stop);
        stop.setEnabled(false);
        textValeurDistance = (TextView) findViewById(R.id.textValeurDistance);
        textGPS = (TextView) findViewById(R.id.textGPS);


        config.setOnClickListener(this);
        prepare.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_demo_maps);

        IntentFilter filter = new IntentFilter();
        filter.addAction(SdkConnection.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGpsStringBuffer = new StringBuffer();
        mDistanceMax = new StringBuffer();


    }




    private Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what) {

                case CHANGE_TEXT_VIEW :
                    textGPS.setText(mGpsStringBuffer.toString());
                    //modif du cercle en recupérent mDistanceMax si on avait pas eu de problèmes
                    // avec la methode getMaxRadiusAircraftCanFlyAndGoHome()
                    break;

            }

            return false;
        }

    });



    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();

        }
    };

    private void onProductConnectionChange()
    {
        initMissionManager();
        initFlightController();
    }

    private void initMissionManager() {
        DJIBaseProduct product = SdkConnection.getProductInstance();
        if (product == null || !product.isConnected()) {
            setResultToToast("Product Not Connected");
            mMissionManager = null;
            return;
        } else {
            setResultToToast("Product Connected");
            mMissionManager = product.getMissionManager();
            mMissionManager.setMissionProgressStatusCallback(this);
            mMissionManager.setMissionExecutionFinishedCallback(this);
        }
        mWaypointMission = new DJIWaypointMission();

    }



    private void initFlightController() {
        DJIBaseProduct product = SdkConnection.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof DJIAircraft) {
                mFlightController = ((DJIAircraft) product).getFlightController();
            }
        }
        if (mFlightController != null) {


            mFlightController.setUpdateSystemStateCallback(new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {
                @Override
                public void onResult(DJIFlightControllerCurrentState state) {
                    droneLocationLat = state.getAircraftLocation().getLatitude();
                    droneLocationLng = state.getAircraftLocation().getLongitude();
                    updateDroneLocation();

                    mGpsStringBuffer.delete(0, mGpsStringBuffer.length());

                    mGpsStringBuffer.append("Altitude : ").
                            append(state.getAircraftLocation().getAltitude()).
                            append("m. ").
                            append("Latitude : ").
                            append(String.format("%.5f",state.getAircraftLocation().getLatitude())).
                            append("Longitude :").
                            append(String.format("%.5f",state.getAircraftLocation().getLongitude()));


                    rotation = state.getAircraftHeadDirection();

                    /*PARTIE AVEC SMART GO HOME STATUS RETOURNANT SYSTEMATIQUEMENT : 0

                    DJIFlightControllerSmartGoHomeStatus djiFlightControllerSmartGoHomeStatus = state.getSmartGoHomeStatus();
                    mDistanceMax.delete(0, mGpsStringBuffer.length());
                    mDistanceMax.append(Float.toString(djiFlightControllerSmartGoHomeStatus.getMaxRadiusAircraftCanFlyAndGoHome()));
                    */

                    mHandler.sendEmptyMessage(CHANGE_TEXT_VIEW);

                }
            });

            try {
                SdkConnection.getProductInstance().getBattery().setBatteryStateUpdateCallback(
                        new DJIBattery.DJIBatteryStateUpdateCallback() {
                            @Override
                            public void onResult(DJIBatteryState djiBatteryState) {

                                batteryLevel = djiBatteryState.getBatteryEnergyRemainingPercent();

                            }
                        }
                );
            }  catch (Exception exception) {

                }

        }

    }


    /**
     * DJIMissionManager Delegate Methods
     */
    @Override
    public void missionProgressStatus(DJIMission.DJIMissionProgressStatus progressStatus) {

    }

    /**
     * DJIMissionManager Delegate Methods
     */
    @Override
    public void onResult(DJIError error) {
        setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
    }

    private void setUpMap() {

        gMap.setOnMapClickListener(this);// add the listener for click for amap object

        gMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker m) {
                m.remove();
                for (Map.Entry<Integer, Marker> entry : mMarkers.entrySet())
                {
                    if(entry.getValue().equals(m))
                        mMarkers.remove(entry.getKey());
                }
                return false;
            }
        });
    }

    @Override
    public void onMapClick(LatLng point) {

        DJIWaypoint.DJIWaypointAction action = new DJIWaypoint.DJIWaypointAction(DJIWaypoint.DJIWaypointActionType.StartTakePhoto,1);
        markWaypoint(point);
        DJIWaypoint mWaypoint = new DJIWaypoint(point.latitude, point.longitude, altitude);
        mWaypoint.addAction(action);
        //Add waypoints to Waypoint arraylist;
        if (mWaypointMission != null) {
            mWaypointMission.addWaypoint(mWaypoint);
        }


    }

    private String calculDistance(){

        String dist = "";
        float distance=0;
        Location l1 = new Location("One");
        Location l2 = new Location("Two");

        for(int i=1;i<mMarkers.size();i++) {
            LatLng point1 = new LatLng(mMarkers.get(i-1).getPosition().latitude, mMarkers.get(i-1).getPosition().longitude);
            LatLng point2 = new LatLng(mMarkers.get(i).getPosition().latitude, mMarkers.get(i).getPosition().longitude);

            l1.setLatitude(point1.latitude);
            l1.setLongitude(point1.longitude);

            l2.setLatitude(point2.latitude);
            l2.setLongitude(point2.longitude);

            distance = distance + l1.distanceTo(l2);

        }
        dist = distance + " M";

        if (distance > 1000.0f) {
            distance = distance / 1000.0f;
            dist = distance + " KM";
        }
        return dist;
    }


    public static boolean checkGpsCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);

        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }
                if (checkGpsCoordinates(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                    float zoomlevel = (float) 18.0;
                    gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(droneLocationLat, droneLocationLng), zoomlevel));
                    droneMarker.setRotation(rotation);
                }
            }
        });
    }


    private void markWaypoint(LatLng point){

        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.title("Title");
        if(mMarkers.isEmpty())
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.start));
        else
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));

        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.prepare:{
                prepareWayPointMission();
                break;
            }

            case R.id.start:{

                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            default:
                break;

        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);
        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        //RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);
        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }
        });
        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.NoAction;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoHome;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.AutoLand;
                } else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoFirstWaypoint;
                }
            }
        });
        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefault(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        //Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

    String nulltoIntegerDefault(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    private void configWayPointMission(){
        if (mWaypointMission != null){
            mWaypointMission.finishedAction = mFinishedAction;
            mWaypointMission.headingMode = mHeadingMode;
            mWaypointMission.autoFlightSpeed = mSpeed;
            if (mWaypointMission.waypointsList.size() > 0){
                for (int i=0; i< mWaypointMission.waypointsList.size(); i++){
                    mWaypointMission.getWaypointAtIndex(i).altitude = altitude;
                }
                setResultToToast("Set Waypoint altitude success");
            }
        }
    }

    private void prepareWayPointMission(){
        if (mMissionManager != null && mWaypointMission != null) {
            configWayPointMission();

            calculMaxRadius();

            for(int i=1;i<mMarkers.size();i++) {
                LatLng point1 = new LatLng(mMarkers.get(i-1).getPosition().latitude, mMarkers.get(i-1).getPosition().longitude);
                LatLng point2 = new LatLng(mMarkers.get(i).getPosition().latitude, mMarkers.get(i).getPosition().longitude);
                gMap.addPolyline(new PolylineOptions()
                        .add(point1, point2)
                        .width(5)
                        .color(Color.RED));
            }

            start.setEnabled(true);
            textValeurDistance.setText(calculDistance());

            DJIMission.DJIMissionProgressHandler progressHandler = new DJIMission.DJIMissionProgressHandler() {
                @Override
                public void onProgress(DJIMission.DJIProgressType type, float progress) {

                }
            };
            mMissionManager.prepareMission(mWaypointMission, progressHandler, new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    setResultToToast(error == null ? "Success" : error.getDescription());
                }
            });


        }

    }

    private void calculMaxRadius(){
        int maxRadius =0;

        if (batteryLevel < 30)
            maxRadius = 50;
        else if (batteryLevel < 50)
            maxRadius = 150;
        else if (batteryLevel < 70)
            maxRadius = 250;
        else if (batteryLevel <= 100)
            maxRadius = 400;

        circle.setCenter(new LatLng(droneLocationLat, droneLocationLng));
        circle.setRadius(Double.valueOf(maxRadius));
    }

    private void startWaypointMission(){

       stop.setEnabled(true);

        if (mMissionManager != null) {
                mMissionManager.startMissionExecution(new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {

                        setResultToToast("Start: " + (error == null ? "Success" : error.getDescription()));
                    }
                });
        }
    }

    private void stopWaypointMission(){
        if (mMissionManager != null) {
            mMissionManager.stopMissionExecution(new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    setResultToToast("Stop: " + (error == null ? "Success" : error.getDescription()));
                }
            });
            if (mWaypointMission != null){
                mWaypointMission.removeAllWaypoints();
            }
            
            mFlightController.goHome(new DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    Toast.makeText(DemoMaps.this, "GO HOME", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }

        circle = gMap.addCircle(new CircleOptions()
                .center(new LatLng(droneLocationLat,droneLocationLng ))
                .radius(0)
                .strokeColor(Color.BLUE)
                .fillColor(Color.TRANSPARENT));

    }


    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(DemoMaps.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
