package com.atv.spamblocker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public final class BlockedNotifier {

    public static final String CHANNEL = "blocked_calls";
    public static final String PREFS = "atv_spam_blocker";

    private BlockedNotifier() {}

    public static void ensureChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Blocked calls",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Shown when a call is rejected");
            nm.createNotificationChannel(ch);
        }
    }

    public static void notifyBlocked(Context ctx, String number, String pattern) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("notify_enabled", true)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(ctx);
        try {
            Intent i = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE);
            StringBuilder body = new StringBuilder();
            if (number != null && !number.isEmpty()) body.append(number);
            if (pattern != null && !pattern.isEmpty()) body.append("  (series ").append(pattern).append(")");
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_shield)
                    .setContentTitle("Call blocked")
                    .setContentText(body.length() == 0 ? "Private number" : body.toString())
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(1001, n);
        } catch (Exception ignored) {
        }
    }
}
