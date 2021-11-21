package com.fwk.sdk;

interface HvacCallback {

    oneway void onTemperatureChanged(double temperature);

}