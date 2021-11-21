## 前言

之前我们介绍了车载应用开发体系中如何使用Jetpack在HMI中构建MVVM架构[Android 车载应用开发与分析 （3）- 构建 MVVM 架构(Java版)](https://www.jianshu.com/p/50a2ccb805f2)，通过之前的介绍，也了解到在大多数车载系统应用架构中，一个完整的应用往往会包含三层，分别是

*   **HMI**
    Human Machine Interface，显示UI信息，进行人机交互。

*   **Service**
    在系统后台进行数据处理，监控数据状态。

*   **SDK**
    根据业务逻辑`Service`对外暴露的通信接口，其他模块通过它来完成IPC通信，通常是基于AIDL接口。

**本篇主要讲解，如何编写基于 AIDL 的 SDK 。**

## AIDL 介绍
AIDL，Android 接口定义语言，是Android开发中常用的一种进程间通信方式。关于如何使用 **AIDL** 请参考 [Android 接口定义语言 (AIDL)  |  Android 开发者  |  Android Developers](https://developer.android.google.cn/guide/components/aidl?hl=zh_cn)

这里介绍一些 **AIDL** 使用过程中容易混淆的关键字：

*   **in**
```
interface HvacInterface {

    void setData(in Hvac hvac);
}
```

单向数据流向。被in修饰的参数，会顺利传到Server端，但Servier端对实参的任何改变，都不会回调给Client端。

*   **out**
```
interface HvacInterface {

    void getData(out Hvac hvac);
}
```

单向数据流向。被out修饰的参数，只有默认值会传到Server端，Servier端对实参的改变，在调用结束后，会回调给Client端。

```
interface HvacInterface {

    void getData(inout Hvac hvac);
}
```

参数则是上面二者的结合，实参会顺利传到服务方，且服务方对实参的任何改变，在调用结束后会反应回调用方。

*   **oneway**
    AIDL 定义的接口默认是**同步调用。**举个例子：Client端调用`setData`方法，`setData`在Server端需要执行5秒钟，那么Client端调用`setData`方法的线程就会被block5秒钟。如果在`setData`方法上加上oneway，就可以避免这个问题。

```
interface HvacInterface {

    oneway void setData(in Hvac hvac);
}
```

> `oneway`不仅可以修饰方法，也可以用来修饰在interface本身，这样interface内所有的方法都隐式地带上`oneway`。被oneway修饰了的方法不可以有返回值，也不可以再用out或inout修饰参数。

### AIDL 常规用法

```
IRemoteService iRemoteService;

private ServiceConnection mConnection = new ServiceConnection() {
    // Called when the connection with the service is established
    public void onServiceConnected(ComponentName className, IBinder service) {
        // Following the example above for an AIDL interface,
        // this gets an instance of the IRemoteInterface, which we can use to call on the service
        iRemoteService = IRemoteService.Stub.asInterface(service);
    }

    // Called when the connection with the service disconnects unexpectedly
    public void onServiceDisconnected(ComponentName className) {
        Log.e(TAG, "Service has unexpectedly disconnected");
        iRemoteService = null;
    }
};

public void setData(Hvac havc){
    if (iRemoteService!=null){
        iRemoteService.setData(hvac);
    }
}

```

常规的用法中，我们需先判断Client端是否已经绑定上Server端，不仅Client端对Server端的接口调用，也要防止绑定失败导致的空指针。

车载应用中上述的常规用法不仅会使HMI开发变得繁琐，还需要处理Service异常状态下解除绑定后的状态。下面介绍如何简便的封装SDK

## 封装SDK Base类

实际开发中，我们把Client端对Service的绑定、重连、线程切换等细节隐藏到SDK中并封装成一个

`BaseConnectManager`，使用时只需要继承`BaseConnectManager`并传入Service的包名、类名和断线重连时间即可。

```
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

```

## 封装 SDK

开发中多数时候我们只有一个用于操作Service Interface，如下所示：

```
interface HvacInterface {

    oneway void setTemperature(int temperature);

    oneway void requestTemperature();

    boolean registerCallback(in HvacCallback callback);

    boolean unregisterCallback(in HvacCallback callback);

}

```

用于回调Server端处理结果的Callback

```
interface HvacCallback {

    oneway void onTemperatureChanged(double temperature);

}

```

基于`BaseConnectManager`封装一个HvacManager

```
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

```

最后，我们在SDK module的 build.gradle中加入可以编译出jar代码，

```
//makeJar
def zipFile = file('build/intermediates/aar_main_jar/release/classes.jar')
task makeJar(type: Jar) {
    from zipTree(zipFile)
    archiveBaseName =  "sdk"
    destinationDirectory = file("build/outputs/")
    manifest {
        attributes(
                'Implementation-Title': "${project.name}",
                'Built-Date': new Date().getDateTimeString(),
                'Built-With':
                        "gradle-${project.getGradle().getGradleVersion()},groovy-${GroovySystem.getVersion()}",
                'Created-By':
                        'Java ' + System.getProperty('java.version') + ' (' + System.getProperty('java.vendor') + ')')
    }
}
makeJar.dependsOn(build)

```

### 使用示例

```
public void requestTemperature() {
    LogUtils.logI(TAG, "[requestTemperature]");
    HvacManager.getInstance().requestTemperature();
}
```

实际使用时，既不需要关心Service的绑定状态，也不需要关心线程上的切换，大大简便了HMI的开发。

本文源码地址： https://github.com/linux-link/CarServerArch

## 参考资料

[Android 接口定义语言 (AIDL)  |  Android 开发者  |  Android Developers](https://developer.android.google.cn/guide/components/aidl?hl=zh_cn)
