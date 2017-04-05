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

    /** attributs vue **/
    private GoogleMap gMap;
    private Button config, prepare, start, stop;
    private TextView textValeurDistance;
    private TextView textGPS;
    private Circle circle;

    /** attributs drone **/
    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;
    private float altitude = 20.0f;
    private float mSpeed = 5.0f;
    private int rotation;
    private int batteryLevel;

    /** attributs DJI **/
    private DJIWaypointMission mWaypointMission;
    private DJIMissionManager mMissionManager;
    private DJIFlightController mFlightController;

    private DJIWaypointMission.DJIWaypointMissionFinishedAction mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.GoHome;;
    private DJIWaypointMission.DJIWaypointMissionHeadingMode mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto ;

    /** attributs pour echange de données **/
    private StringBuffer mGpsStringBuffer;
    private StringBuffer mDistanceMax;
    private static final int CHANGE_TEXT_VIEW = 0;


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

    /** Recuèpre les élements de la vue, et met en place les listeners **/
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


    /** recupère les données stockées et les affiche en continue **/
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


    /** detecte le changement de connection au drone **/
    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();

        }
    };

    /** appelle les méthodes permettant d'intialiser les différents composants necessaires à la mission **/
    private void onProductConnectionChange()
    {
        initMissionManager();
        initFlightController();
    }

    /** initialise l'instance de DJIwaypointMission **/
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


    /** initialise l'instance de DJIFlihtController et recupère des informations depuis le drone comme
     * la position GPS, le niveau de batterie, sa rotation et les stocke dans des buffers ou directement dans des variables **/
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

    /** initialise la carte GoogleMaps **/
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

    /**  gère les évenements de clic sur la carte qui provoque l'ajout d'un DJIWaypoint,
     * mais aussi l'appel d'une méthode: markWaypoint(LatLng point) qui
     * elle se chargera d'ajouter un marker sur la carte **/
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

    /** calule la distance du parcours programmé et la retourne sous forme d'une chaine de caractères **/
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


    /** verifie que les coordonées GPS d'une latitude et longitude passées en paramètre sont correctes **/
    public static boolean checkGpsCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    /** met à jour l'icone du drone en fonction de l'évolution des cordonnées GPS du drone,
     * s'occupe de zoomer sur l'icone,
     * de mdofier la rotation de son icone en fonction de sa direction (HeadDirection)
     */
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

    /** ajoute un marker sur la carte en fonction d'une variable de type LatLng passée en paramètre **/
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

    /** permet de gérer l'interaction de clic avec l'utilisateur,
     * sur les différents bouttons
     */
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

    /** permet d'afficher la vue de configuration et d'affecter les choix aux attributs correspondants **/
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

    /** permet de convertir une chaine de caractères nulle en un entier nul **/
    String nulltoIntegerDefault(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    /** teste si une chaine de caractère est un entier et retourne un boolean **/
    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    /** configure les différents attributs après la sélection dans la vue de configuration du vol**/
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

    /** prepare l'execution du vol programmé, execute les diffirents test de securité de DJI, altitude correct,
     * parcours ne dépassant pas la distance maximale etc,
     * fait appel à la méthode pour calculer et tracer le rayon maximale informatif
     * trace les lignes pour relier les différents points de passage
     * fait appel à la méthode pour calculer la distance du parcours programmée et l'afficher
     * rend possible le clic sur le bouton démarrer
     */
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

    /** calcul le rayon maximale informatif par rapport au niveau de batterie et actualise le cercle sur la carte pour représenter ce rayon **/
    private void calculMaxRadius(){
        int maxRadius =0;

        if (batteryLevel < 30)
            maxRadius = 50;
        else if (batteryLevel < 50)
            maxRadius = 250;
        else if (batteryLevel < 70)
            maxRadius = 350;
        else if (batteryLevel <= 100)
            maxRadius = 500;

        circle.setCenter(new LatLng(droneLocationLat, droneLocationLng));
        circle.setRadius(Double.valueOf(maxRadius));
    }

    /** démmare l'execution de la mission lors du clic sur le boutton démarrer, si aucune erreur n'est retournée
     * rend possible le clic sur le boutton revenir **/
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

    /**  arrete l'exéctuion de la mission lors du clique sur le boutton revenir et permet de lancer
     * l'action pour que le drone revienne à sa position de départ (GoHome)
     */
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

    /** lorsque l'evénement la carte est prête elle appelle une méthode setUpMap() pour l'initialiser,
     * instancie et ajoute un cercle à la carte pour l'instant pas visible
     * @param googleMap
     */
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


}
