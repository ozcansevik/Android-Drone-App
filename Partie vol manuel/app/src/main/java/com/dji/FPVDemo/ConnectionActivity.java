package com.dji.FPVDemo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import dji.sdk.base.DJIBaseProduct;
import dji.sdk.products.DJIAircraft;

public class ConnectionActivity extends AppCompatActivity implements View.OnClickListener
{

    //region Attributs Graphiques

    // Texte sur l'état actuel de la connexion au drone.
    private TextView textview_EtatConnexion;

    // Texte sur l'état actuel du produit.
    private TextView textview_EtatProduit;

    // Bouton permettant d'accéder à la vue suivante une fois connecté.
    private Button button_Ouvrir;

    //endregion

    //region Attributs

    protected BroadcastReceiver mReceiver;

    //endregion

    // region Cycle de vie Android

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Selon certaines versions, le SDK peut nécessiter des permissions supplémentaires.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
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

        setContentView(R.layout.activity_connection);

        // Méthode d'initialisation de l'affichage.
        initUI();

        // Le Receiver mettra à jour l'affichage quand nécessaire.
        mReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                updateUI();
            }
        };

        // On s'enregistre pour attendre de savoir quand le drone se connecte grâce à FPVDemoApplication.
        IntentFilter filter = new IntentFilter();
        filter.addAction(FPVDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public void onReturn(View view){
        this.finish();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
    //endregion

    //region Méthodes

    /**
     * Méthode d'initialisation des éléments graphiques.
     */
    private void initUI() {

        textview_EtatConnexion = (TextView) findViewById(R.id.text_connection_status);
        textview_EtatProduit = (TextView) findViewById(R.id.text_product_info);
        button_Ouvrir = (Button) findViewById(R.id.btn_open);

        button_Ouvrir.setOnClickListener(this);

        // Au début le bouton est désactivé car aucun drone n'est encore connecté / détecté.
        button_Ouvrir.setEnabled(false);

    }


    /**
     * Méthode d'actualisation des éléments graphiques.
     */
    private void updateUI() {

        // On récupère le produit maintenant qu'on sait qu'il est connecté.
        DJIBaseProduct mProduct = FPVDemoApplication.getProductInstance();

        if (mProduct != null && mProduct.isConnected())
        {

            // On active le bouton permettant d'accéder à la vue suivante.
            button_Ouvrir.setEnabled(true);

            // On met à jour les différents affichages avec les informations de l'appareil.
            String mTypeProduit = mProduct instanceof DJIAircraft ? "DJIAircraft" : "DJIHandHeld";
            textview_EtatConnexion.setText("Status: " + mTypeProduit + " connecté.");
            if (mProduct.getModel() != null) {
                textview_EtatProduit.setText(mProduct.getModel().getDisplayName());
            } else {
                textview_EtatProduit.setText(R.string.product_information);
            }

        }
        // Si néanmoins on ne peut accéder au produit connecté.
        else
        {
            button_Ouvrir.setEnabled(false);
            textview_EtatProduit.setText(R.string.product_information);
            textview_EtatConnexion.setText(R.string.connection_loose);
        }

    }

    /**
     * Méthode gérant les différents clics sur la vue.
     * @param v
     */
    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case R.id.btn_open: {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                break;
            }
            default:
                break;
        }
    }

    //endregion
}
