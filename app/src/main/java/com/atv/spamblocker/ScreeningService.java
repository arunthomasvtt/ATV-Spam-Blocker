package com.atv.spamblocker;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.Q)
public class ScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        boolean block = false;
        String display = "";
        String pattern = "";

        try {
            if (details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                Uri handle = details.getHandle();
                String raw = handle == null ? null : handle.getSchemeSpecificPart();

                if (raw == null || raw.trim().isEmpty()) {
                    SharedPreferences p = getSharedPreferences(BlockedNotifier.PREFS, MODE_PRIVATE);
                    if (p.getBoolean("block_private", false)) {
                        block = true;
                        display = "";
                        pattern = "";
                    }
                } else {
                    display = raw;
                    String local = NumUtil.toLocal(raw);
                    String full = NumUtil.digits(raw);
                    Series m = new Db(this).findMatch(local, full);
                    if (m != null) {
                        block = true;
                        pattern = m.pattern;
                    }
                }
            }
        } catch (Exception ignored) {
            block = false;
        }

        try {
            if (block) {
                Db db = new Db(this);
                db.recordBlocked(display, pattern);
                BlockedNotifier.notifyBlocked(this, display, pattern);
            }
        } catch (Exception ignored) {
        }

        respondToCall(details, new CallScreeningService.CallResponse.Builder()
                .setDisallowCall(block)
                .setRejectCall(block)
                .setSkipCallLog(false)
                .setSkipNotification(block)
                .build());
    }
}
