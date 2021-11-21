package com.fwk.sdk.hvac;

import android.os.IBinder;
import android.os.RemoteException;

import com.fwk.sdk.HvacCallback;
import com.fwk.sdk.HvacInterface;
import com.fwk.sdk.base.BaseConnectManager;
import com.fwk.sdk.utils.Remote;
import com.fwk.sdk.utils.SdkLogUtils;

import java.util.ArrayList;
import java.util.List;

public class HvacManager extends BaseConnectManager<HvacInterface> {

    private static final String TAG = SdkLogUtils.TAG_FWK + HvacManager.class.getSimpleName();

    private static volatile HvacManager sHvacManager;

    public static final String SERVICE_PACKAGE = "com.fwk.service";
    public static final String SERVICE_CLASSNAME = "com.fwk.service.SimpleService";
    private static final long RETRY_TIME = 5000L;

    private final List<IHvacCallback> mCallbacks = new ArrayList<>();

    private final HvacCallback.Stub mSampleCallback = new HvacCallback.Stub() {
        @Override
        public void onTemperatureChanged(double temperature) throws RemoteException {
            SdkLogUtils.logV(TAG, "[onTemperatureChanged] " + temperature);
            getMainHandler().post(() -> {
                for (IHvacCallback callback : mCallbacks) {
                    callback.onTemperatureChanged(temperature);
                }
            });
        }
    };

    public static HvacManager getInstance() {
        if (sHvacManager == null) {
            synchronized (HvacManager.class) {
                if (sHvacManager == null) {
                    sHvacManager = new HvacManager();
                }
            }
        }
        return sHvacManager;
    }

    @Override
    protected String getServicePkgName() {
        return SERVICE_PACKAGE;
    }

    @Override
    protected String getServiceClassName() {
        return SERVICE_CLASSNAME;
    }

    @Override
    protected HvacInterface asInterface(IBinder service) {
        return HvacInterface.Stub.asInterface(service);
    }

    @Override
    protected long getRetryBindTimeMill() {
        return RETRY_TIME;
    }

    /******************/

    public void requestTemperature() {
        Remote.tryExec(() -> {
            if (isServiceConnected(true)) {
                getProxy().requestTemperature();
            } else {
                getTaskQueue().offer(this::requestTemperature);
            }
        });
    }

    public void setTemperature(int temperature) {
        Remote.tryExec(() -> {
            if (isServiceConnected(true)) {
                getProxy().requestTemperature();
            } else {
                getTaskQueue().offer(() -> {
                    setTemperature(temperature);
                });
            }
        });
    }

    public boolean registerCallback(IHvacCallback callback) {
        return Remote.exec(() -> {
            if (isServiceConnected(true)) {
                boolean result = getProxy().registerCallback(mSampleCallback);
                if (result) {
                    mCallbacks.remove(callback);
                    mCallbacks.add(callback);
                }
                return result;
            } else {
                getTaskQueue().offer(() -> {
                    registerCallback(callback);
                });
                return false;
            }
        });
    }

    public boolean unregisterCallback(IHvacCallback callback) {
        return Remote.exec(() -> {
            if (isServiceConnected(true)) {
                boolean result = getProxy().unregisterCallback(mSampleCallback);
                if (result) {
                    mCallbacks.remove(callback);
                }
                return result;
            } else {
                getTaskQueue().offer(() -> {
                    unregisterCallback(callback);
                });
                return false;
            }
        });
    }
}
