package com.atv.spamblocker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

/**
 * Android 8/9 fallback: the CallScreeningService API only exists from Android 10,
 * so on older devices we end ringing calls via ITelephony after a series match.
 */
public class PhoneStateReceiver extends BroadcastReceiver {

    private static String lastNumber = null;
    private static long lastAt = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 29) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) return;
        if (number == null || number.trim().isEmpty()) return;

        long now = SystemClock.elapsedRealtime();
        if (number.equals(lastNumber) && now - lastAt < 5000) return;
        lastNumber = number;
        lastAt = now;

        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String local = NumUtil.toLocal(number);
        String full = NumUtil.digits(number);
        Series m = new Db(context).findMatch(local, full);
        if (m == null) return;

        try {
            Db db = new Db(context);
            db.recordBlocked(number, m.pattern);
            BlockedNotifier.notifyBlocked(context, number, m.pattern);
        } catch (Exception ignored) {
        }
        endCallTelephony(context);
    }

    private static void endCallTelephony(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            Method get = tm.getClass().getDeclaredMethod("getITelephony");
            get.setAccessible(true);
            Object it = get.invoke(tm);
            Method end = it.getClass().getDeclaredMethod("endCall");
            end.invoke(it);
        } catch (Throwable ignored) {
        }
    }
}
