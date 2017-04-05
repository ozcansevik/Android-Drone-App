package com.dji.FPVDemo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * Cette classe nous est donnée par DJI, elle permet d'avoir des joysticks fonctionnels qui
 * renvoient les informations de l'utilisateur et gèrent la partie grapique.
 * A nous par la suite de traiter les données des joysticks pour en faire ce que
 * nous souhaitons.
 */
public class OnScreenJoystick extends SurfaceView implements
        SurfaceHolder.Callback, OnTouchListener {

    // region Attributs graphiques

    private Bitmap mJoystick;
    private SurfaceHolder mHolder;
    private Rect mKnobBounds;

    //endregion

    //region Attributs

    private JoystickThread mThread;

    private int mKnobX, mKnobY;
    private int mKnobSize;
    private int mBackgroundSize;
    private float mRadius;

    private OnScreenJoystickListener mJoystickListener;

    private boolean mAutoCentering = true;

    //endregion

    // region Constructeur(s)

    /**
     * Constructeur
     * @param context
     * @param attrs
     */
    public OnScreenJoystick(Context context, AttributeSet attrs) {
        super(context, attrs);

        initGraphics(attrs);
        init();
    }

    //endregion

    //region Méthodes

    /**
     * Initialise la partie graphique via l'image en resource.
     * @param attrs
     */
    private void initGraphics(AttributeSet attrs) {
        Resources res = getContext().getResources();
        mJoystick = BitmapFactory
                .decodeResource(
                        res, R.mipmap.joystick);

    }

    /**
     * Initialise les différentes tailles / limites de la partie graphique.
     * @param pCanvas
     */
    private void initBounds(final Canvas pCanvas) {
        mBackgroundSize = pCanvas.getHeight();
        mKnobSize = Math.round(mBackgroundSize * 0.6f);

        mKnobBounds = new Rect();

        mRadius = mBackgroundSize * 0.5f;
        mKnobX = Math.round((mBackgroundSize - mKnobSize) * 0.5f);
        mKnobY = Math.round((mBackgroundSize - mKnobSize) * 0.5f);

    }

    /**
     * Initialise le fonctionnement du joystick.
     */
    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);

        mThread = new JoystickThread();

        setZOrderOnTop(true);
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        setOnTouchListener(this);
        setEnabled(true);
        setAutoCentering(true);
    }

    /**
     * Définit si le stick doit se recentrer ou non.
     * @param pAutoCentering
     */
    public void setAutoCentering(final boolean pAutoCentering) {
        mAutoCentering = pAutoCentering;
    }

    /**
     * Permet de savoir si le stick se recentre ou non.
     * @return le stick se recentre ou non.
     */
    public boolean isAutoCentering() {
        return mAutoCentering;
    }

    /**
     * Setter du OnScreenJoystickListener
     * @param pJoystickListener
     */
    public void setJoystickListener(
            final OnScreenJoystickListener pJoystickListener) {
        mJoystickListener = pJoystickListener;
    }

    /**
     * Comportement au changement de surface.
     * @param arg0
     * @param arg1
     * @param arg2
     * @param arg3
     */
    @Override
    public void surfaceChanged(final SurfaceHolder arg0, final int arg1,
                               final int arg2, final int arg3) {


    }

    /**
     * Comportement à la création de la surface.
     * @param arg0
     */
    @Override
    public void surfaceCreated(final SurfaceHolder arg0) {
        mThread = new JoystickThread();
        mThread.start();

    }

    /**
     * Comportement à la destruction de la surface.
     * @param arg0
     */
    @Override
    public void surfaceDestroyed(final SurfaceHolder arg0) {
        boolean retry = true;
        mThread.setRunning(false);

        while (retry) {
            try {

                mThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }

    }

    /**
     * Méthode qui dessine le stick sur la vue.
     * @param pCanvas
     */
    public void doDraw(final Canvas pCanvas) {
        if (mKnobBounds == null) {
            initBounds(pCanvas);
        }

        // pCanvas.drawBitmap(mJoystickBg, null, mBgBounds, null);

        mKnobBounds.set(mKnobX, mKnobY, mKnobX + mKnobSize, mKnobY + mKnobSize);
        pCanvas.drawBitmap(mJoystick, null, mKnobBounds, null);

    }

    /**
     * Comportement lors de l'appui sur un stick qui calcule les coordonnées x et y correspondantes.
     * @param arg0
     * @param pEvent
     * @return
     */
    @Override
    public boolean onTouch(final View arg0, final MotionEvent pEvent)
    {

        final float x = pEvent.getX();
        final float y = pEvent.getY();

        switch (pEvent.getAction()) {

            case MotionEvent.ACTION_UP:
                if (isAutoCentering()) {
                    mKnobX = Math.round((mBackgroundSize - mKnobSize) * 0.5f);
                    mKnobY = Math.round((mBackgroundSize - mKnobSize) * 0.5f);
                }
                break;
            default:
                if (checkBounds(x, y)) {
                    mKnobX = Math.round(x - mKnobSize * 0.5f);
                    mKnobY = Math.round(y - mKnobSize * 0.5f);
                } else {
                    final double angle = Math.atan2(y - mRadius, x - mRadius);
                    mKnobX = (int) (Math.round(mRadius
                            + (mRadius - mKnobSize * 0.5f) * Math.cos(angle)) - mKnobSize * 0.5f);
                    mKnobY = (int) (Math.round(mRadius
                            + (mRadius - mKnobSize * 0.5f) * Math.sin(angle)) - mKnobSize * 0.5f);
                }
        }

        if (mJoystickListener != null)
        {
            // On appelle la méthode onTouch du JoystickListener avec les coordonnées x et y du stick.
            // C'est dans cette méthode qu'on gérera le comportement voulu.
            mJoystickListener.onTouch(this,
                    (0.5f - (mKnobX / (mRadius * 2 - mKnobSize))) * -2,
                    (0.5f - (mKnobY / (mRadius * 2 - mKnobSize))) * 2);

        }

        return true;
    }

    /**
     * Permet de savoir si on est en dehors des limites du stick.
     * @param pX
     * @param pY
     * @return
     */
    private boolean checkBounds(final float pX, final float pY) {
        return Math.pow(mRadius - pX, 2) + Math.pow(mRadius - pY, 2) <= Math
                .pow(mRadius - mKnobSize * 0.5f, 2);
    }

    /**
     * Méthode qui envoie les informations du stick au Listener.
     */
    private void pushTouchEvent(){

        if (mJoystickListener != null) {
            mJoystickListener.onTouch(this,
                    (0.5f - (mKnobX / (mRadius * 2 - mKnobSize))) * -2,
                    (0.5f - (mKnobY / (mRadius * 2 - mKnobSize))) * 2);

        }
    }

    //endregion

    //region Classe interne

    /**
     * Classe gérant le thread dessinant le stick en temps réel.
     */
    private class JoystickThread extends Thread {

        private boolean running = false;

        @Override
        public synchronized void start() {
            running = true;
            super.start();
        }

        public void setRunning(final boolean pRunning) {
            running = pRunning;
        }

        @Override
        public void run() {
            while (running) {
                Canvas canvas = null;
                try {
                    canvas = mHolder.lockCanvas(null);
                    synchronized (mHolder) {
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        doDraw(canvas);
                    }
                }
                catch(Exception e){}
                finally {
                    if (canvas != null) {
                        mHolder.unlockCanvasAndPost(canvas);
                    }
                }

            }
        }
    }

    //endregion

}
