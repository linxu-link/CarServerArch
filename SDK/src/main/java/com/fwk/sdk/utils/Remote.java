/*
 * CONFIDENTIAL - FORD MOTOR COMPANY
 *
 * This is an unpublished work, which is a trade secret, created in
 * 2021.  Ford Motor Company owns all rights to this work and intends
 * to maintain it in confidence to preserve its trade secret status.
 * Ford Motor Company reserves the right to protect this work as an
 * unpublished copyrighted work in the event of an inadvertent or
 * deliberate unauthorized publication.  Ford Motor Company also
 * reserves its rights under the copyright laws to protect this work
 * as a published work.  Those having access to this work may not copy
 * it, use it, or disclose the information contained in it without
 * the written authorization of Ford Motor Company.
 */

package com.fwk.sdk.utils;

import android.os.RemoteException;

/**
 * Helper functions to assist remote calls.
 *
 * @author WuJia
 * @version 1.0
 * @date 2021/8/13
 */
public abstract class Remote {

    private static final String TAG = SdkLogUtils.TAG_FWK + Remote.class.getSimpleName();

    /**
     * Throwing void function.
     */
    public interface RemoteVoidFunction {
        /**
         * The actual throwing function.
         */
        void call() throws RemoteException;
    }

    /**
     * Throwing function that returns some value.
     *
     * @param <V> Return type for the function.
     */
    public interface RemoteFunction<V> {
        /**
         * The actual throwing function.
         */
        V call() throws RemoteException;
    }

    /**
     * Wraps remote function and rethrows {@link RemoteException}.
     */
    public static <V> V exec(RemoteFunction<V> func) {
        try {
            return func.call();
        } catch (RemoteException exception) {
            throw new IllegalArgumentException("Failed to execute remote call" + exception);
        }
    }

    /**
     * Wraps remote void function and logs in case of {@link RemoteException}.
     */
    public static void tryExec(RemoteVoidFunction func) {
        try {
            func.call();
        } catch (RemoteException exception) {
            SdkLogUtils.logV(TAG, exception.toString());
        }
    }
}
