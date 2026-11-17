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
            ch.setDescription("Shown when a call is rejected or looks like spam");
            nm.createNotificationChannel(ch);
        }
    }

    public static void notifyBlocked(Context ctx, String number, String pattern) {
        StringBuilder body = new StringBuilder();
        if (number != null && !number.isEmpty()) body.append(number);
        if (pattern != null && !pattern.isEmpty()) body.append("  (series ").append(pattern).append(")");
        post(ctx, 1001, "Call blocked",
                body.length() == 0 ? "Private number" : body.toString());
    }

    public static void notifySuspected(Context ctx, String number, String reason) {
        String body = (number == null || number.isEmpty() ? "Unknown number" : number)
                + (reason == null || reason.isEmpty() ? "" : " \u2014 " + reason);
        post(ctx, 1002, "Suspicious call", body);
    }

    private static void post(Context ctx, int id, String title, String text) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("notify_enabled", true)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(ctx);
        try {
            Intent i = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_shield)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, n);
        } catch (Exception ignored) {
        }
    }
}
