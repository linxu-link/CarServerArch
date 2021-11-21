package com.fwk.sdk.base;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;

import com.fwk.sdk.SdkAppGlobal;
import com.fwk.sdk.listener.IServiceConnectListener;
import com.fwk.sdk.utils.Remote;
import com.fwk.sdk.utils.SdkLogUtils;

import java.util.concurrent.LinkedBlockingQueue;

public abstract class BaseConnectManager<T extends IInterface> {

    private final String TAG = SdkLogUtils.TAG_FWK + getClass().getSimpleName();
    private static final String THREAD_NAME = "bindServiceThread";

    private final Application mApplication;
    private IServiceConnectListener mServiceListener;
    private final Handler mChildThread;
    private final Handler mMainThread;
    private final LinkedBlockingQueue<Runnable> mTaskQueue = new LinkedBlockingQueue<>();
    private final Runnable mBindServiceTask = this::bindService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SdkLogUtils.logV(TAG, "[onServiceConnected]");
            mProxy = asInterface(service);
            Remote.tryExec(() -> {
                service.linkToDeath(mDeathRecipient, 0);
            });
            if (mServiceListener != null) {
                mServiceListener.onServiceConnected();
            }
            handleTask();
            mChildThread.removeCallbacks(mBindServiceTask);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            SdkLogUtils.logV(TAG, "[onServiceDisconnected]");
            mProxy = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected();
            }
        }
    };

    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            SdkLogUtils.logV(TAG, "[binderDied]");
            if (mServiceListener != null) {
                mServiceListener.onBinderDied();
            }

            if (mProxy != null) {
                mProxy.asBinder().unlinkToDeath(mDeathRecipient, 0);
                mProxy = null;
            }

            attemptToRebindService();
        }

    };

    private T mProxy;

    public BaseConnectManager() {
        mApplication = SdkAppGlobal.getApplication();
        HandlerThread thread = new HandlerThread(THREAD_NAME, 6);
        thread.start();
        mChildThread = new Handler(thread.getLooper());
        mMainThread = new Handler(Looper.getMainLooper());
        bindService();
    }

    private void bindService() {
        if (mProxy == null) {
            SdkLogUtils.logV(TAG, "[bindService] start");
            ComponentName name = new ComponentName(getServicePkgName(), getServiceClassName());
            Intent intent = new Intent();
            if (getServiceAction() != null) {
                intent.setAction(getServiceAction());
            }
            intent.setComponent(name);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mApplication.startForegroundService(intent);
            } else {
                mApplication.startService(intent);
            }
            boolean connected = mApplication.bindService(intent, mServiceConnection,
                    Context.BIND_AUTO_CREATE);
            SdkLogUtils.logV(TAG, "[bindService] result " + connected);
            if (!connected) {
                attemptToRebindService();
            }
        } else {
            SdkLogUtils.logV(TAG, "[bindService] not need");
        }
    }

    protected void attemptToRebindService() {
        SdkLogUtils.logV(TAG, "[attemptToRebindService]");
        mChildThread.postDelayed(mBindServiceTask, getRetryBindTimeMill());
    }

    protected void handleTask() {
        Runnable task;
        while ((task = mTaskQueue.poll()) != null) {
            SdkLogUtils.logV(TAG, "[handleTask] poll task form task queue");
            mChildThread.post(task);
        }
    }

    public void init() {
        bindService();
    }

    public boolean isServiceConnected() {
        return isServiceConnected(false);
    }

    public boolean isServiceConnected(boolean tryConnect) {
        SdkLogUtils.logV(TAG, "[isServiceConnected] tryConnect " + tryConnect + ";isConnected " + (mProxy != null));
        if (mProxy == null && tryConnect) {
            attemptToRebindService();
        }
        return this.mProxy != null;
    }

    public void release() {
        SdkLogUtils.logV(TAG, "[release]");
        if (this.isServiceConnected()) {
            this.mProxy.asBinder().unlinkToDeath(this.mDeathRecipient, 0);
            this.mProxy = null;
            this.mApplication.unbindService(mServiceConnection);
        }
    }

    public void setStateListener(IServiceConnectListener listener) {
        SdkLogUtils.logV(TAG, "[setStateListener]" + listener);
        mServiceListener = listener;
    }

    public void removeStateListener() {
        SdkLogUtils.logV(TAG, "[removeStateListener]");
        mServiceListener = null;
    }

    protected T getProxy() {
        return mProxy;
    }

    protected LinkedBlockingQueue<Runnable> getTaskQueue() {
        return mTaskQueue;
    }

    public Handler getMainHandler() {
        return mMainThread;
    }

    protected abstract String getServicePkgName();

    protected abstract String getServiceClassName();

    protected String getServiceAction() {
        return null;
    }

    protected abstract T asInterface(IBinder service);

    protected abstract long getRetryBindTimeMill();

}
