package com.fwk.sdk;

import com.fwk.sdk.HvacCallback;

interface HvacInterface {

    oneway void setTemperature(int temperature);

    oneway void requestTemperature();

    boolean registerCallback(in HvacCallback callback);

    boolean unregisterCallback(in HvacCallback callback);

}