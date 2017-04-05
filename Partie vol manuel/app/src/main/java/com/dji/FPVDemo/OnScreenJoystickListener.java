package com.dji.FPVDemo;

import com.dji.FPVDemo.OnScreenJoystick;

public interface OnScreenJoystickListener
{
    public void onTouch(final OnScreenJoystick joystick, final float pX, final float pY);
}
