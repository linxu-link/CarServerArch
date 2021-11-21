package com.fwk.service.binder;

import android.os.RemoteException;
import android.util.Log;

import com.fwk.sdk.HvacCallback;
import com.fwk.sdk.HvacInterface;

public class HvacBinder extends HvacInterface.Stub {

    private static final String TAG = HvacBinder.class.getSimpleName();

    @Override
    public void setTemperature(int temperature) throws RemoteException {
        Log.e(TAG, "setTemperature: " + temperature);
    }

    @Override
    public void requestTemperature() throws RemoteException {
        Log.e(TAG, "requestTemperature: ");
    }

    @Override
    public boolean registerCallback(HvacCallback callback) throws RemoteException {
        return false;
    }

    @Override
    public boolean unregisterCallback(HvacCallback callback) throws RemoteException {
        return false;
    }
}
