package com.pratham.pradigikids.util;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

abstract public class HotspotUtils {
    private static final String TAG = "HotspotUtils";

    private static HotspotUtils mInstance = null;
    private static Handler mHandler;

    private WifiManager mWifiManager;

    private HotspotUtils(Context context) {
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public static HotspotUtils getInstance(Context context, final Handler handler) {
        mHandler = handler;
        if (mInstance == null)
            mInstance = Build.VERSION.SDK_INT >= 26
                    ? new OreoAPI(context)
                    : new HackAPI(context);

        return mInstance;
    }

    private static Object invokeSilently(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "exception in invoking methods: " + method.getName() + "(): " + e.getMessage());
        }

        return null;
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 26 || HackAPI.supported();
    }

    public static int getAllowedKeyManagement(WifiConfiguration wifiConfiguration) {
        String keyManagement = wifiConfiguration.allowedKeyManagement.toString();

        try {
            return Integer.valueOf(keyManagement.substring(1, keyManagement.length() - 1));
        } catch (Exception ignored) {
        }

        return -1;
    }

    abstract public boolean disable();

    abstract public boolean enable();

    abstract public boolean enableConfigured(String apName, String passKeyWPA2);

    abstract public WifiConfiguration getConfiguration();

    abstract public WifiConfiguration getPreviousConfig();

    abstract public boolean isEnabled();

    abstract public boolean isStarted();

    abstract public boolean unloadPreviousConfig();

    WifiManager getWifiManager() {
        return mWifiManager;
    }

    @RequiresApi(26)
    public static class OreoAPI extends HotspotUtils {
        private WifiManager.LocalOnlyHotspotReservation mHotspotReservation;
        private WifiManager.LocalOnlyHotspotCallback mCallback;

        private OreoAPI(Context context) {
            super(context);
        }

        @Override
        public boolean disable() {
            if (mHotspotReservation == null)
                return false;

            mHotspotReservation.close();
            mHotspotReservation = null;

            return true;
        }

        @Override
        public boolean enable() {
            try {
                getWifiManager().startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                    @Override
                    public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                        super.onStarted(reservation);
                        mHotspotReservation = reservation;
                        PD_Constant.HOTSPOT_SSID = reservation.getWifiConfiguration().SSID;
                        PD_Constant.HOTSPOT_PASSWORD = reservation.getWifiConfiguration().preSharedKey;
                        PD_Constant.FTP_HOTSPOT_KEYMGMT = getAllowedKeyManagement(reservation.getWifiConfiguration());
                        Message msg = mHandler.obtainMessage(PD_Constant.ApCreateApSuccess);
                        mHandler.sendMessage(msg);

                        if (mCallback != null)
                            mCallback.onStarted(reservation);
                    }

                    @Override
                    public void onStopped() {
                        super.onStopped();
                        mHotspotReservation = null;

                        if (mCallback != null)
                            mCallback.onStopped();
                    }

                    @Override
                    public void onFailed(int reason) {
                        super.onFailed(reason);
                        mHotspotReservation = null;

                        if (mCallback != null)
                            mCallback.onFailed(reason);
                    }
                }, new Handler(Looper.myLooper()));

                return true;
            } catch (Throwable ignored) {
            }

            return false;
        }

        @Override
        public WifiConfiguration getConfiguration() {
            if (mHotspotReservation == null)
                return null;

            return mHotspotReservation.getWifiConfiguration();
        }

        @Override
        public WifiConfiguration getPreviousConfig() {
            return getConfiguration();
        }

        @Override
        public boolean enableConfigured(String apName, String passKeyWPA2) {
            return enable();
        }

        @Override
        public boolean isEnabled() {
            return HackAPI.enabled(getWifiManager());
        }

        @Override
        public boolean isStarted() {
            return mHotspotReservation != null;
        }

        public void setSecondaryCallback(WifiManager.LocalOnlyHotspotCallback callback) {
            mCallback = callback;
        }

        @Override
        public boolean unloadPreviousConfig() {
            return mHotspotReservation != null;
        }
    }

    public static class HackAPI extends HotspotUtils {
        private static Method getWifiApConfiguration;
        private static Method getWifiApState;
        private static Method isWifiApEnabled;
        private static Method setWifiApEnabled;
        private static Method setWifiApConfiguration;

        static {
            for (Method method : WifiManager.class.getDeclaredMethods()) {
                switch (method.getName()) {
                    case "getWifiApConfiguration":
                        getWifiApConfiguration = method;
                        break;
                    case "getWifiApState":
                        getWifiApState = method;
                        break;
                    case "isWifiApEnabled":
                        isWifiApEnabled = method;
                        break;
                    case "setWifiApEnabled":
                        setWifiApEnabled = method;
                        break;
                    case "setWifiApConfiguration":
                        setWifiApConfiguration = method;
                        break;
                }
            }
        }

        private WifiConfiguration mPreviousConfig;

        private HackAPI(Context context) {
            super(context);
        }

        static boolean enabled(WifiManager wifiManager) {
            Object result = invokeSilently(isWifiApEnabled, wifiManager);

            if (result == null)
                return false;

            return (Boolean) result;
        }

        static boolean supported() {
            return getWifiApState != null
                    && isWifiApEnabled != null
                    && setWifiApEnabled != null
                    && getWifiApConfiguration != null;
        }

        public boolean disable() {
            unloadPreviousConfig();
            return setHotspotEnabled(mPreviousConfig, false);
        }

        public boolean enable() {
            getWifiManager().setWifiEnabled(false);
            return setHotspotEnabled(getConfiguration(), true);
        }

        public boolean enableConfigured(String apName, String passKeyWPA2) {
            getWifiManager().setWifiEnabled(false);

            if (mPreviousConfig == null)
                mPreviousConfig = getConfiguration();

            WifiConfiguration wifiConfiguration = new WifiConfiguration();

            wifiConfiguration.SSID = apName;

            if (passKeyWPA2 != null && passKeyWPA2.length() >= 8) {
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wifiConfiguration.preSharedKey = passKeyWPA2;
            } else
                wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            PD_Constant.FTP_HOTSPOT_KEYMGMT = getAllowedKeyManagement(wifiConfiguration);
            return setHotspotEnabled(wifiConfiguration, true);
        }

        @Override
        public boolean isEnabled() {
            return enabled(getWifiManager());
        }

        @Override
        public boolean isStarted() {
            return getPreviousConfig() != null;
        }

        public WifiConfiguration getConfiguration() {
            return (WifiConfiguration) invokeSilently(getWifiApConfiguration, getWifiManager());
        }

        public WifiConfiguration getPreviousConfig() {
            return mPreviousConfig;
        }

        private void setHotspotConfig(WifiConfiguration config) {
            Object result = invokeSilently(setWifiApConfiguration, getWifiManager(), config);

            if (result == null) {
            }

        }

        private boolean setHotspotEnabled(WifiConfiguration config, boolean enabled) {
            Object result = invokeSilently(setWifiApEnabled, getWifiManager(), config, enabled);
            if (result == null)
                return false;
            if ((Boolean) result) {
                Message msg = mHandler.obtainMessage(PD_Constant.ApCreateApSuccess);
                mHandler.sendMessage(msg);
            }
            return (Boolean) result;
        }

        public boolean unloadPreviousConfig() {
            if (mPreviousConfig == null)
                return false;

            setHotspotConfig(mPreviousConfig);

            mPreviousConfig = null;

            return true;
        }
    }
}
