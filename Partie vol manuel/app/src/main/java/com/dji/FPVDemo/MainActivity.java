package com.dji.FPVDemo;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.TextureView.SurfaceTextureListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.battery.DJIBatteryState;
import dji.common.camera.CameraSystemState;
import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIFlightControllerCurrentState;
import dji.common.flightcontroller.DJIFlightControllerDataType;
import dji.common.flightcontroller.DJIVirtualStickFlightControlData;
import dji.common.flightcontroller.DJIVirtualStickFlightCoordinateSystem;
import dji.common.flightcontroller.DJIVirtualStickRollPitchControlMode;
import dji.common.flightcontroller.DJIVirtualStickVerticalControlMode;
import dji.common.flightcontroller.DJIVirtualStickYawControlMode;
import dji.common.product.Model;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.battery.DJIBattery;
import dji.sdk.camera.DJICamera;
import dji.sdk.camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.flightcontroller.DJIFlightControllerDelegate;


public class MainActivity extends Activity implements SurfaceTextureListener,OnClickListener{

    //region Attributs graphiques

    // Retour vidéo.
    protected TextureView mVideoSurface = null;

    // Médias.
    private Button mCaptureBtn, mShootPhotoModeBtn, mRecordVideoModeBtn;
    private ToggleButton mRecordBtn;
    private TextView recordingTime;

    private Button mdecollerBtn;
    private Button matterrirBtn;
    private Button mretourBaseBtn;
    private Button mswitchControlBtn;

    private TextView mbattery_level;
    private TextView mgps;

    // Joysticks.
    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    // Touches séquentielles.
    private Button mleftUp;
    private Button mleftLeft;
    private Button mleftRight;
    private Button mleftDown;
    private Button mrightUp;
    private Button mrightLeft;
    private Button mrightRight;
    private Button mrightDown;
    private Button mArret;

    //endregion

    //region Attributs

    // Listeners des sticks.
    private OnScreenJoystickListener mScreenJoystickRightListener;
    private OnScreenJoystickListener mScreenJoystickLeftListener;

    private boolean isJoystickVisible = true;

    private static final String TAG = MainActivity.class.getName();

    // Retour de la caméra.
    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;

    // Codec pour la vidéo
    protected DJICodecManager mCodecManager = null;

    // Informations des joysticks
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    // Buffers pour afficher GPS, altitude et batterie.
    protected StringBuffer mBatteryStringBuffer;
    protected StringBuffer mGpsStringBuffer;

    // Pour gérer l'envoi des infos des sticks au drone.
    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    // Gère l'affichage GPS, altitude et batterie.
    protected static final int CHANGE_TEXT_VIEW = 0;
    protected Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case CHANGE_TEXT_VIEW :
                    mbattery_level.setText(mBatteryStringBuffer.toString());
                    mgps.setText(mGpsStringBuffer.toString());
                    break;

                default:
                    break;
            }
            return false;
        }
    });

    //endregion

    //region Cycle de vie Android

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // On met la vue en plein écran.
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        else
        {
            View decorView = getWindow().getDecorView();

            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            ActionBar actionBar = getActionBar();
            if(actionBar != null)
                actionBar.hide();
        }

        setContentView(R.layout.activity_main);

        // Initialisation de la vue.
        initUI();

        mBatteryStringBuffer = new StringBuffer();
        mGpsStringBuffer = new StringBuffer();

        //  Callback qui reçoit les données de la caméra en H264
        mReceivedVideoDataCallBack = new CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                if(mCodecManager != null){
                    // On envoie le flux au codec manager pour être décodé
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }else {
                    Log.e(TAG, "pas de codec manager");
                }
            }
        };


        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {

            camera.setDJICameraUpdatedSystemStateCallback(new DJICamera.CameraUpdatedSystemStateCallback() {
                /**
                 * Retour de la caméra, si on filme, qui actualise le timer d'enregistrement.
                 * @param cameraSystemState
                 */
                @Override
                public void onResult(CameraSystemState cameraSystemState) {
                    if (cameraSystemState != null) {

                        int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                        int minutes = (recordTime % 3600) / 60;
                        int seconds = recordTime % 60;

                        final String timeString = String.format("%02d:%02d", minutes, seconds);
                        final boolean isVideoRecording = cameraSystemState.isRecording();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                recordingTime.setText(timeString);

                                if (isVideoRecording){
                                    recordingTime.setVisibility(View.VISIBLE);
                                }else
                                {
                                    recordingTime.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                    }
                }
            });

        }

        try {
            /**
             * On veut actualiser notre textview à chaque changement d'état de la batterie.
             */
            FPVDemoApplication.getProductInstance().getBattery().setBatteryStateUpdateCallback(
                    new DJIBattery.DJIBatteryStateUpdateCallback() {
                        @Override
                        public void onResult(DJIBatteryState djiBatteryState) {
                            mBatteryStringBuffer.delete(0, mBatteryStringBuffer.length());

                            mBatteryStringBuffer.append("Battery: ").
                                    append(djiBatteryState.getBatteryEnergyRemainingPercent()).
                                    append("%\n");

                            mHandler.sendEmptyMessage(CHANGE_TEXT_VIEW);
                        }
                    }
            );

            /**
             * On veut actualiser notre textview à chaque changement d'état de la position.
             */
            FPVDemoApplication.getAircraftInstance().getFlightController().setUpdateSystemStateCallback(new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {
                @Override
                public void onResult(DJIFlightControllerCurrentState djiFlightControllerCurrentState) {
                    mGpsStringBuffer.delete(0, mGpsStringBuffer.length());

                    mGpsStringBuffer.append("Altitude : ").
                            append(djiFlightControllerCurrentState.getAircraftLocation().getAltitude()).
                            append("m. ").
                            append("Latitude : ").
                            append(String.format("%.5f",djiFlightControllerCurrentState.getAircraftLocation().getLatitude())).
                            append("Longitude :").
                            append(String.format("%.5f",djiFlightControllerCurrentState.getAircraftLocation().getLongitude()));


                            mHandler.sendEmptyMessage(CHANGE_TEXT_VIEW);
                }


            });



        } catch (Exception exception) {

        }

        // On active les sticks.
        activerJoysticks();

    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        onProductChange();

        if(mVideoSurface == null) {
            Log.e(TAG, "pas de surface vidéo");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
    }

    //endregion

    //region Méthodes

    /**
     * En cas de changement de connexion on réinitialise la vue.
     */
    protected void onProductChange() {
        initPreviewer();
    }

    /**
     * Méthode d'initialisation de la vue.
     */
    private void initUI() {

        // Retour vidéo
        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

        // Bouttons médias
        recordingTime = (TextView) findViewById(R.id.timer);
        mCaptureBtn = (Button) findViewById(R.id.btn_capture);
        mRecordBtn = (ToggleButton) findViewById(R.id.btn_record);

        /*
        mShootPhotoModeBtn = (Button) findViewById(R.id.btn_shoot_photo_mode);
        mRecordVideoModeBtn = (Button) findViewById(R.id.btn_record_video_mode);
        */

        mdecollerBtn = (Button) findViewById(R.id.btn_decoller);
        matterrirBtn = (Button) findViewById(R.id.btn_atterrir);
        mretourBaseBtn = (Button) findViewById(R.id.btn_home);

        mleftDown = (Button) findViewById(R.id.button_left_down);
        mleftRight = (Button) findViewById(R.id.button_left_right);
        mleftLeft = (Button) findViewById(R.id.button_left_left);
        mleftUp = (Button) findViewById(R.id.button_left_up);
        mrightDown = (Button) findViewById(R.id.button_right_down);
        mrightLeft = (Button) findViewById(R.id.button_right_left);
        mrightUp = (Button) findViewById(R.id.button_right_up);
        mrightRight = (Button) findViewById(R.id.button_right_right);
        mArret = (Button) findViewById(R.id.button_arret);


        mswitchControlBtn = (Button) findViewById(R.id.switchControlBtn);

        mbattery_level = (TextView) findViewById(R.id.battery_level);
        mgps = (TextView) findViewById(R.id.gps);

        mCaptureBtn.setOnClickListener(this);
        mRecordBtn.setOnClickListener(this);
        mdecollerBtn.setOnClickListener(this);
        matterrirBtn.setOnClickListener(this);
        mretourBaseBtn.setOnClickListener(this);
        mswitchControlBtn.setOnClickListener(this);
        mleftDown.setOnClickListener(this);
        mleftLeft.setOnClickListener(this);
        mleftRight.setOnClickListener(this);
        mleftUp.setOnClickListener(this);
        mrightDown.setOnClickListener(this);
        mrightLeft.setOnClickListener(this);
        mrightRight.setOnClickListener(this);
        mrightUp.setOnClickListener(this);
        mArret.setOnClickListener(this);
        mrightUp.setVisibility(View.INVISIBLE);
        mrightRight.setVisibility(View.INVISIBLE);
        mrightLeft.setVisibility(View.INVISIBLE);
        mrightDown.setVisibility(View.INVISIBLE);
        mleftLeft.setVisibility(View.INVISIBLE);
        mleftRight.setVisibility(View.INVISIBLE);
        mleftDown.setVisibility(View.INVISIBLE);
        mleftUp.setVisibility(View.INVISIBLE);
        mArret.setVisibility(View.INVISIBLE);
       /*
       mShootPhotoModeBtn.setOnClickListener(this);
       mRecordVideoModeBtn.setOnClickListener(this);
        */

        recordingTime.setVisibility(View.INVISIBLE);

        mRecordBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startRecord();
                } else {
                    stopRecord();
                }
            }
        });

        // Joysticks
        mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);

        /**
         * On attribue le listener au stick droit.
         */
        mScreenJoystickRightListener = new OnScreenJoystickListener() {

            /**
             * Méthode qui sera appelée à chaque appui sur le stick.
             * @param joystick
             * @param pX
             * @param pY
             */
            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {

                // Si le stick n'est que très légèrement non centré ( non voulu ) on considère
                // qu'il est centré.
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }

                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }

                // On récupère les valeurs maximales pour le drone de chaque type de controle.
                float pitchJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;
                float rollJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickRollPitchControlMaxVelocity;

                // On transforme les coordonnées du stick en valeurs pour le drone.
                mPitch = (float) (pitchJoyControlMaxSpeed * pY);
                mRoll = (float) (rollJoyControlMaxSpeed * pX);

                // On envoie ces valeurs au drone.
                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();

                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }



            }
        };
        mScreenJoystickRight.setJoystickListener(mScreenJoystickRightListener);

        // Même fonctionnement que pour le stick droit.
        mScreenJoystickLeftListener = new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {

                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }

                float verticalJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickVerticalControlMaxVelocity;
                float yawJoyControlMaxSpeed = DJIFlightControllerDataType.DJIVirtualStickYawControlMaxAngularVelocity;

                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);


                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();

                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);

                }




            }
        };
        mScreenJoystickLeft.setJoystickListener(mScreenJoystickLeftListener);


    }

    /**
     * Méthode d'initialisation du retour vidéo.
     */
    private void initPreviewer() {

        DJIBaseProduct product = FPVDemoApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (mVideoSurface != null) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UnknownAircraft)) {
                DJICamera camera = product.getCamera();
                if (camera != null){
                    camera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }

    /**
     * Désatribution du retour vidéo.
     */
    private void uninitPreviewer() {
        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null){
            FPVDemoApplication.getCameraInstance().setDJICameraReceivedVideoDataCallback(null);
        }
    }

    /**
     * Comportement quand une surface est disponible.
     * @param surface
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        // on crée un codec manager s'il le faut.
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    /**
     * Comportement à la destruction de la surface.
     * @param surface
     * @return
     */
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    /**
     * Méthode facilitant l'utilisation des toast dans le code.
     * @param msg
     */
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


    /**
     * Méthode de gestion des clics des boutons sur la vue.
     * @param v
     */
    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_capture:{
                captureAction();
                break;
            }
            case R.id.btn_decoller:{
                decollerAction();
                break;
            }
            case R.id.btn_atterrir:{
                atterrirAction();
                break;
            }
            case R.id.btn_home:{
                retourBaseAction();
                break;
            }
            case R.id.switchControlBtn:{
                switchControl();
                break;
            }

            // Pour toutes les touches séquentielles, nous émulons une utilisation des sticks.
            case R.id.button_arret:{
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,0);
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,0);
                break;
            }
            case R.id.button_left_up:{
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,0);
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,1);
                break;
            }
            case R.id.button_left_down:{
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,0);
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,-1);
                break;
            }
            case R.id.button_left_left:{
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,0);
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,-1,0);
                break;
            }
            case R.id.button_left_right:{
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,0);
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,1,0);
                break;
            }
            case R.id.button_right_up:{
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,0);
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,1);
                break;
            }
            case R.id.button_right_down:{
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,0);
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,0,-1);
                break;
            }
            case R.id.button_right_right:{
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,0);
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,1,0);
                break;
            }
            case R.id.button_right_left:{
                mScreenJoystickLeftListener.onTouch(mScreenJoystickLeft,0,0);
                mScreenJoystickRightListener.onTouch(mScreenJoystickRight,-1,0);
                break;
            }

            /*
            case R.id.btn_shoot_photo_mode:{
                switchCameraMode(DJICameraSettingsDef.CameraMode.ShootPhoto);
                break;
            }
            case R.id.btn_record_video_mode:{
                switchCameraMode(DJICameraSettingsDef.CameraMode.RecordVideo);
                break;
            }*/
            default:
                break;
        }
    }


    /**
     * Méthode permettant le décollage automatique du drone.
     */
    private void decollerAction()
    {
        if(FPVDemoApplication.isFlightControllerAvailable()){
            FPVDemoApplication.getAircraftInstance().getFlightController().takeOff(
                    new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        showToast(djiError.getDescription());
                    } else {
                        showToast("Décollage : succès !");
                    }
                }
            });
        }
        else{
            showToast("Erreur lors de l'acquisition du FLightController.");
        }
    }

    /**
     * Méthode permettant l'atterissage sur place du drone.
     */
    private void atterrirAction()
    {
        if(FPVDemoApplication.isFlightControllerAvailable()){
            FPVDemoApplication.getAircraftInstance().getFlightController().autoLanding(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("Atterrissage : succès !");
                            }
                        }
                    });
        }
        else{
            showToast("Erreur lors de l'acquisition du FlightController.");
        }
    }

    /**
     * Méthode permettant l'atterissage au point de départ du drone.
     */
    private void retourBaseAction()
    {
        if(FPVDemoApplication.isFlightControllerAvailable()){
            FPVDemoApplication.getAircraftInstance().getFlightController().goHome(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("Retour base : succès !");
                            }
                        }
                    });
        }
        else{
            showToast("Erreur lors de l'acquisition du FlightController.");
        }
    }


    /**
     * Méthode permettant d'acitver le contrôle du drone par les joysticks.
     */
    private void activerJoysticks()
    {
        if(FPVDemoApplication.isFlightControllerAvailable())
        {

            // On attribue toutes les caractéristiques de controle jugées durant la phase d'analyse comme idéales.
            FPVDemoApplication.getAircraftInstance().getFlightController().setHorizontalCoordinateSystem(DJIVirtualStickFlightCoordinateSystem.Body);
            FPVDemoApplication.getAircraftInstance().getFlightController().setVerticalControlMode(DJIVirtualStickVerticalControlMode.Velocity);
            FPVDemoApplication.getAircraftInstance().getFlightController().setRollPitchControlMode(DJIVirtualStickRollPitchControlMode.Velocity);
            FPVDemoApplication.getAircraftInstance().getFlightController().setYawControlMode(DJIVirtualStickYawControlMode.AngularVelocity);

            // Cette vérification est normalement nécessaire mais nous renvoyait parfois false, bien que derrière enableVirtualStick fonctionnait ?!
            //if(FPVDemoApplication.getAircraftInstance().getFlightController().isVirtualStickControlModeAvailable()){

                FPVDemoApplication.getAircraftInstance().getFlightController().enableVirtualStickControlMode(
                        new DJICommonCallbacks.DJICompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    showToast("Activation des Sticks : succès !");
                                }
                            }
                        }
                );

            //}
        }
        else
        {
            showToast("Erreur : Joysticks non activés.");
        }
    }


    // Méthode non utilisée à l'avancé du projet actuelle
    private void switchCameraMode(DJICameraSettingsDef.CameraMode cameraMode)
    {

        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            camera.setCameraMode(cameraMode, new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {

                    if (error == null) {
                        showToast("Mode de caméra changé.");
                    } else {
                        showToast(error.getDescription());
                    }
                }
            });
            }

    }

    /**
     *  Méthode de prise de photo intégrée mais non testée en profondeur.
     *  Les photos se prennent sur la carte SD du drone et ne sont pas récupérées
     *  sur le téléphone.
     */
    private void captureAction()
    {

        DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.ShootPhoto;

        final DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {

            DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.CameraShootPhotoMode.Single;
            camera.startShootPhoto(photoMode, new DJICommonCallbacks.DJICompletionCallback() {

                @Override
                public void onResult(DJIError error) {
                    if (error == null) {
                        showToast("Photo enregistrée.");
                    } else {
                        showToast(error.getDescription());
                    }
                }

            });
        }
    }

    /**
     * Methode de prise de vidéo intégrée mais non testée en profondeur.
     * L'enregistrement se fait sur carte SD du drone mais affiche parfois
     * un message d'erreur.
     */
    private void startRecord()
    {
        DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.RecordVideo;
        final DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            camera.startRecordVideo(new DJICommonCallbacks.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error)
                {
                    if (error == null) {
                        showToast("Début enregistrement.");
                    }else {
                        showToast(error.getDescription());
                    }
                }
            });
        }
    }

    /**
     * Arrêt de l'enregistrement vidéo.
     */
    private void stopRecord()
    {
        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            camera.stopRecordVideo(new DJICommonCallbacks.DJICompletionCallback(){

                @Override
                public void onResult(DJIError error)
                {
                    if(error == null) {
                        showToast("Vidéo capturée.");
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the stopRecordVideo API
        }

    }

    /**
     * Méthode permettant de switcher de mode stick / touches.
     */
    private void switchControl()
    {

        if(isJoystickVisible)
        {
            mScreenJoystickLeft.setVisibility(View.INVISIBLE);
            mScreenJoystickRight.setVisibility(View.INVISIBLE);
            isJoystickVisible = false;
            mrightUp.setVisibility(View.VISIBLE);
            mrightRight.setVisibility(View.VISIBLE);
            mrightLeft.setVisibility(View.VISIBLE);
            mrightDown.setVisibility(View.VISIBLE);
            mleftLeft.setVisibility(View.VISIBLE);
            mleftRight.setVisibility(View.VISIBLE);
            mleftDown.setVisibility(View.VISIBLE);
            mleftUp.setVisibility(View.VISIBLE);
            mArret.setVisibility(View.VISIBLE);

        }
        else
        {
            mScreenJoystickLeft.setVisibility(View.VISIBLE);
            mScreenJoystickRight.setVisibility(View.VISIBLE);
            isJoystickVisible = true;
            mrightUp.setVisibility(View.INVISIBLE);
            mrightRight.setVisibility(View.INVISIBLE);
            mrightLeft.setVisibility(View.INVISIBLE);
            mrightDown.setVisibility(View.INVISIBLE);
            mleftLeft.setVisibility(View.INVISIBLE);
            mleftRight.setVisibility(View.INVISIBLE);
            mleftDown.setVisibility(View.INVISIBLE);
            mleftUp.setVisibility(View.INVISIBLE);
            mArret.setVisibility(View.INVISIBLE);
        }
    }

    //endregion

    //region Classe interne

    /**
     * Classe permettant l'envoit des données des sticks au drone.
     */
    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            if (FPVDemoApplication.isFlightControllerAvailable()) {
                FPVDemoApplication.getAircraftInstance().
                        getFlightController().sendVirtualStickFlightControlData(
                        new DJIVirtualStickFlightControlData(
                                mRoll, mPitch, mYaw, mThrottle
                        ), new DJICommonCallbacks.DJICompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        }
                );
            }
        }
    }


    //endregion

}
