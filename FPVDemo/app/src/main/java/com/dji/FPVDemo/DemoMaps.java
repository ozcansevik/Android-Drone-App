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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIAircraftRemainingBatteryState;
import dji.common.flightcontroller.DJIFlightControllerCurrentState;
import dji.common.flightcontroller.DJIFlightControllerDataType;
import dji.common.flightcontroller.DJIFlightControllerSmartGoHomeStatus;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.camera.DJICamera;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.flightcontroller.DJIFlightControllerDelegate;
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

    private boolean isAdd = false;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 20.0f;
    private float mSpeed = 10.0f;

    private DJIWaypointMission mWaypointMission;
    private DJIMissionManager mMissionManager;
    private DJIFlightController mFlightController;

    private DJIWaypointMission.DJIWaypointMissionFinishedAction mFinishedAction;
    private DJIWaypointMission.DJIWaypointMissionHeadingMode mHeadingMode ;

    final public int MSG_FLIGHT_CONTROLLER_CURRENT_STATE = 1;
    final public int MSG_FLIGHT_CONTROLLER_CURRENT_STATE_ERROR = 2;
    final public int MSG_FLIGHT_CONTROLLER_CURRENT_STATE_NO_CONNECTION = 3;
    TextView m_tv_FlightControllerCurrentState;


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
        stop = (Button) findViewById(R.id.stop);
        textValeurDistance = (TextView) findViewById(R.id.textValeurDistance);
        textValeurDistanceMax = (TextView) findViewById(R.id.textValeurDistanceMax);

        // atterrir = (Button) findViewById(R.id.atterir);

        //   locate.setOnClickListener(this);
        // add.setOnClickListener(this);
        //  clear.setOnClickListener(this);
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

        mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoHome;
        mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto;
        runFlightControllerCurrentState();
    }

   /* private void runFlightControllerCurrentState() {

       // textValeurDistanceMax.setText(Float.toString(mFlightController.getCurrentState().getSmartGoHomeStatus().getMaxRadiusAircraftCanFlyAndGoHome()));

        try {
            mFlightController.setUpdateSystemStateCallback(
                    new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {

                        @Override
                        public void onResult(DJIFlightControllerCurrentState djiFlightControllerCurrentState) {

                            DJIFlightControllerSmartGoHomeStatus smartGoHomeStatus = djiFlightControllerCurrentState.getSmartGoHomeStatus();
                            textValeurDistanceMax.setText(Float.toString(smartGoHomeStatus.getMaxRadiusAircraftCanFlyAndGoHome()));
                            //Log.d("Info", "SmartGoHomeStatus_IsAircraftShouldGoHome" + smartGoHomeStatus.isAircraftShouldGoHome());

                        }
                    });
        }
        catch (Exception exception) {
            showToast("Exception") ;
        }

    }*/

    private void runFlightControllerCurrentState() {
        try {
            DJIAircraft djiAircraft = (DJIAircraft) DJISDKManager.getInstance().getDJIProduct();
            if (djiAircraft != null) {

                DJIFlightController flightController = mFlightController;

                flightController.setUpdateSystemStateCallback(
                        new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {

                            @Override
                            public void onResult(DJIFlightControllerCurrentState
                                                         djiFlightControllerCurrentState) {



                                DJIFlightControllerSmartGoHomeStatus smartGoHomeStatus = djiFlightControllerCurrentState.getSmartGoHomeStatus();

                                Message msg = Message.obtain();
                                Bundle bundle = new Bundle();


                                // Complete the more complex returned values from flight controller.

                                bundle.putInt("SmartGoHomeStatus_BatteryPercentageToGoHome", smartGoHomeStatus.getBatteryPercentageNeededToGoHome());
                                bundle.putInt("SmartGoHomeStatus_RemainingFlightTime", smartGoHomeStatus.getRemainingFlightTime());
                                bundle.putInt("SmartGoHomeStatus_TimeNeededToGoHome", smartGoHomeStatus.getTimeNeededToGoHome());
                                bundle.putInt("SmartGoHomeStatus_TimeNeededToLandFromCurrentHeight", smartGoHomeStatus.getTimeNeededToLandFromCurrentHeight());
                                bundle.putFloat("SmartGoHomeStatus_MaxRadiusAircraftCanFlyAndGoHome", smartGoHomeStatus.getMaxRadiusAircraftCanFlyAndGoHome());
                                bundle.putBoolean("SmartGoHomeStatus_IsAircraftShouldGoHome", smartGoHomeStatus.isAircraftShouldGoHome());

                                msg.what = MSG_FLIGHT_CONTROLLER_CURRENT_STATE;
                                msg.setData(bundle);
                                mHandler.sendMessage(msg);
                            }
                        });

            } else {
                Message msg = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putString("FlightControllerCurrentStateNoConnection", "Flight Controller Current State - no connection available: ");
                msg.what = MSG_FLIGHT_CONTROLLER_CURRENT_STATE_NO_CONNECTION;
                mHandler.sendMessage(msg);
            }
        } catch (Exception e) {
            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putString("FlightControllerCurrentState", "Flight Controller Current State Error: " + e.getMessage() );
            msg.what = MSG_FLIGHT_CONTROLLER_CURRENT_STATE_ERROR;
            mHandler.sendMessage(msg);
        }
    }


    private Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            String flightControllerCurrentState = "";
            switch (msg.what) {
                case MSG_FLIGHT_CONTROLLER_CURRENT_STATE:


                    Integer iRemainingBatteryState = bundle.getInt("RemainingBatteryState");
                    String stRemainingBatteryState = "";

                    Integer intSmartGoHomeStatus_BatteryPercentageToGoHome = bundle.getInt("SmartGoHomeStatus_BatteryPercentageToGoHome");
                    Integer intSmartGoHomeStatus_RemainingFlightTime = bundle.getInt("SmartGoHomeStatus_RemainingFlightTime");
                    Integer intSmartGoHomeStatus_TimeNeededToGoHome = bundle.getInt("SmartGoHomeStatus_TimeNeededToGoHome");
                    Integer intSmartGoHomeStatus_TimeNeededToLandFromCurrentHeight = bundle.getInt("SmartGoHomeStatus_TimeNeededToLandFromCurrentHeight");
                    Float flSmartGoHomeStatus_MaxRadiusAircraftCanFlyAndGoHome = bundle.getFloat("SmartGoHomeStatus_MaxRadiusAircraftCanFlyAndGoHome");
                    Boolean blSmartGoHomeStatus_IsAircraftShouldGoHome = bundle.getBoolean("SmartGoHomeStatus_IsAircraftShouldGoHome");


                    if (iRemainingBatteryState == DJIAircraftRemainingBatteryState.Normal.value()) {
                        stRemainingBatteryState = "Normal";
                    } else if (iRemainingBatteryState == DJIAircraftRemainingBatteryState.Low.value()) {
                        stRemainingBatteryState = "Low";
                    } else if (iRemainingBatteryState == DJIAircraftRemainingBatteryState.VeryLow.value()) {
                        stRemainingBatteryState = "Very Low";
                    } else if (iRemainingBatteryState == DJIAircraftRemainingBatteryState.Reserved.value()) {
                        stRemainingBatteryState = "Reserved Battery State";
                    }

                    flightControllerCurrentState += "Remaining Battery: " + stRemainingBatteryState + "\n";

                    flightControllerCurrentState += "Smart Battery  - Percentage To Go Home: " + intSmartGoHomeStatus_BatteryPercentageToGoHome  + "\n";
                    flightControllerCurrentState += "Smart Battery  - Remaining Flight Time: " + intSmartGoHomeStatus_RemainingFlightTime  + "\n";
                    flightControllerCurrentState += "Smart Battery  - Time Needed to go Home: " + intSmartGoHomeStatus_TimeNeededToGoHome  + "\n";
                    flightControllerCurrentState += "Smart Battery  - Tm Needed to land from Cur Hght: " + intSmartGoHomeStatus_TimeNeededToLandFromCurrentHeight  + "\n";
                    flightControllerCurrentState += "Smart Battery  - Max Radius Can Fly and Go Home: " + flSmartGoHomeStatus_MaxRadiusAircraftCanFlyAndGoHome + "\n";
                    flightControllerCurrentState += "Smart Battery  - Is Aircraft should go home: " + blSmartGoHomeStatus_IsAircraftShouldGoHome  + "\n";
                    break;
                case MSG_FLIGHT_CONTROLLER_CURRENT_STATE_ERROR:
                    flightControllerCurrentState += "Flight Controller Current State Error: " + bundle.getString("FlightControllerCurrentStateError") + "\n";
                    break;
                case MSG_FLIGHT_CONTROLLER_CURRENT_STATE_NO_CONNECTION:
                    flightControllerCurrentState += bundle.getString("FlightControllerCurrentStateNoConnection") + "\n";
                    break;
            }
           // m_tv_FlightControllerCurrentState.setText(flightControllerCurrentState);
            textValeurDistanceMax.setText(flightControllerCurrentState);
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

                }
            });
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
        /*if (isAdd){
            markWaypoint(point);
            DJIWaypoint mWaypoint = new DJIWaypoint(point.latitude, point.longitude, altitude);
            //Add waypoints to Waypoint arraylist;
            if (mWaypointMission != null) {
                mWaypointMission.addWaypoint(mWaypoint);
                setResultToToast("AddWaypoint");
            }
        }else{
            setResultToToast("Cannot add waypoint");
        } */

        DJIWaypoint.DJIWaypointAction action = new DJIWaypoint.DJIWaypointAction(DJIWaypoint.DJIWaypointActionType.StartTakePhoto,1);
        markWaypoint(point);
        DJIWaypoint mWaypoint = new DJIWaypoint(point.latitude, point.longitude, altitude);
        mWaypoint.addAction(action);
        //Add waypoints to Waypoint arraylist;
        if (mWaypointMission != null) {
            mWaypointMission.addWaypoint(mWaypoint);
            setResultToToast("AddWaypoint");
        }


    }

    public String calculDistance(){

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
                    //  mMarkers.put(0,droneMarker);
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
       /*     case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            } */
        /*    case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }
                });
                if (mWaypointMission != null){
                    mWaypointMission.removeAllWaypoints(); // Remove all the waypoints added to the task
                }
                break;
            }*/
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
           /* case R.id.atterir: { //atterrissage forcé
                stopWaypointMission();
                mFlightController.autoLanding(new DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        //pas de gestion d'erreur pour l'instant : peut etre faire un showDialog : Utils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;
            }*/
            default:
                break;

        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);
    }

    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
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
        /*heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");
                if (checkedId == R.id.headingNext) {
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.UsingInitialDirection;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.ControlByRemoteController;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.UsingWaypointHeading;
                }
            }
        });*/
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
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
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
            //textDistance.setText(calculDistance());



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

            textValeurDistance.setText(calculDistance());
           // runFlightControllerCurrentState();
        }
    }

    private void startWaypointMission(){



        for(int i=1;i<mMarkers.size();i++) {
            LatLng point1 = new LatLng(mMarkers.get(i-1).getPosition().latitude, mMarkers.get(i-1).getPosition().longitude);
            LatLng point2 = new LatLng(mMarkers.get(i).getPosition().latitude, mMarkers.get(i).getPosition().longitude);
            gMap.addPolyline(new PolylineOptions()
                    .add(point1, point2)
                    .width(5)
                    .color(Color.RED));
        }
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
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }

    }


    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(DemoMaps.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
