package com.dji.FPVDemo;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import dji.sdk.camera.DJICamera;
import dji.sdk.products.DJIAircraft;
import dji.sdk.products.DJIHandHeld;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseComponent.DJIComponentListener;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIBaseProduct.DJIBaseProductListener;
import dji.sdk.base.DJIBaseProduct.DJIComponentKey;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;

public class FPVDemoApplication extends Application
{

    //region Attributs

    public static final String FLAG_CONNECTION_CHANGE = "connection_change";

    // Instance unique de notre produit.
    private static DJIBaseProduct mProduct;

    private Handler mHandler;

    /**
     * Instance (obligatoire) permettant d'avoir un retour sur la connexion au sdk.
     */
    private DJISDKManager.DJISDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.DJISDKManagerCallback()
    {
        // Obtient le résultat de la connexion.
        @Override
        public void onGetRegisteredResult(DJIError error)
        {
            // Si c'est un succès.
            if(error == DJISDKError.REGISTRATION_SUCCESS)
            {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Connecté au SDK.", Toast.LENGTH_LONG).show();
                    }
                });

                DJISDKManager.getInstance().startConnectionToProduct();

            }
            // Si c'est un échec.
            else
            {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Echec de connexion au SDK", Toast.LENGTH_LONG).show();
                    }
                });

            }
            DJISDKManager.getInstance().startConnectionToProduct();
        }

        // Changement de produit.
        @Override
        public void onProductChanged(DJIBaseProduct oldProduct, DJIBaseProduct newProduct) {

            mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setDJIBaseProductListener(mDJIBaseProductListener);
            }

            notifyStatusChange();
        }
    };

    /**
     * Permet de savoir quand il y a un changement de produit.
     */
    private DJIBaseProductListener mDJIBaseProductListener = new DJIBaseProductListener()
    {

        @Override
        public void onComponentChange(DJIComponentKey key, DJIBaseComponent oldComponent, DJIBaseComponent newComponent) {

            if(newComponent != null) {
                newComponent.setDJIComponentListener(mDJIComponentListener);
            }
            // On notifie qu'il y a eu un changement.
            notifyStatusChange();
        }

        @Override
        public void onProductConnectivityChanged(boolean isConnected) {

            notifyStatusChange();
        }

    };

    /**
     * Permet de savoir quand il y a un changement de connexion.
     */
    private DJIComponentListener mDJIComponentListener = new DJIComponentListener() {

        @Override
        public void onComponentConnectivityChanged(boolean isConnected) {
            notifyStatusChange();
        }

    };

    //endregion

    //region Méthodes

        //region Vérifications

    /**
     * Obtient le statut de connexion du produit en tant qu'Aircraft.
     * @return statut de connexion du produit en tant qu'Aircraft.
     */
    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof DJIAircraft;
    }

    /**
     * Obtient le statut de connexion du produit en tant qu'HandHeld.
     * @return statut de connexion du produit en tant qu'HandHeld.
     */
    public static boolean isHandHeldConnected() {
        return getProductInstance() != null && getProductInstance() instanceof DJIHandHeld;
    }

    /**
     * Indique si le FlightController est disponible ou non.
     * @return FlightController est disponible ou non.
     */
    public static boolean isFlightControllerAvailable() {
        return isProductModuleAvailable() && isAircraft() && (FPVDemoApplication.getAircraftInstance().getFlightController() != null);
    }

    /**
     * Indique si le produit est disponible ou non.
     * @return produit est disponible ou non.
     */
    public static boolean isProductModuleAvailable() {
        return (FPVDemoApplication.getProductInstance() != null);
    }

    /**
     * Indique si le produit est de type Aircraft.
     * @return si le produit est de type Aircraft.
     */
    public static boolean isAircraft() {
        return FPVDemoApplication.getProductInstance() instanceof DJIAircraft;
    }

        //endregion

        //region Getters

    /**
     * Getter du produit.
     * @return instance de produit
     */
    public static synchronized DJIBaseProduct getProductInstance() {
        if (mProduct == null) {
            mProduct = DJISDKManager.getInstance().getDJIProduct();
        }
        return mProduct;
    }

    /**
     * Retourne l'instance de produit en tant que DJIAircraft.
     * @return instance de produit en tant que DJIAircraft.
     */
    public static synchronized DJIAircraft getAircraftInstance() {
        if(!isAircraftConnected())
            return null;
        return (DJIAircraft) getProductInstance();
    }

    /**
     * Retourne l'instance de produit en tant que DJIHandHeld.
     * @return instance de produit en tant que DJIHandHeld.
     */
    public static synchronized DJIHandHeld getHandHeldInstance() {
        if (!isHandHeldConnected())
            return null;
        return (DJIHandHeld) getProductInstance();
    }

    /**
     * Getter de la camera de notre produit.
     * @return camera du produit.
     */
    public static synchronized DJICamera getCameraInstance()
    {
        if (getProductInstance() == null)
            return null;

        DJICamera camera = null;

        if (getProductInstance() instanceof DJIAircraft){
            camera = ((DJIAircraft) getProductInstance()).getCamera();

        } else if (getProductInstance() instanceof DJIHandHeld) {
            camera = ((DJIHandHeld) getProductInstance()).getCamera();
        }

        return camera;
    }

        //endregion

    /**
     * Méthode appelée pour notifier d'un changement de connexion.
     */
    private void notifyStatusChange()
    {
        // On utilise le Handler pour envoyer un message.
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    /**
     * Est utilisé pour signaler que la connexion a changé, notamment à la ConnectionActivity.
     */
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };
    //endregion

    //region Cycle de Vie Android

    @Override
    public void onCreate() {

        super.onCreate();

        mHandler = new Handler(Looper.getMainLooper());

        // Initialise le DJISDKManager.
        DJISDKManager.getInstance().initSDKManager(this, mDJISDKManagerCallback);
    }

    //endregion

}
