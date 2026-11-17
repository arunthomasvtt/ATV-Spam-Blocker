package com.atv.spamblocker;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.Q)
public class ScreeningService extends CallScreeningService {

    /** TRAI transactional/service series — OTP and bank calls must always ring. */
    private static final String[] TRUSTED_PREFIXES = {"1600", "1601"};

    @Override
    public void onScreenCall(Call.Details details) {
        boolean block = false;
        boolean suspected = false;
        String suspectReason = "";
        String display = "";
        String pattern = "";

        try {
            if (details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                Uri handle = details.getHandle();
                String raw = handle == null ? null : handle.getSchemeSpecificPart();
                SharedPreferences p = getSharedPreferences(BlockedNotifier.PREFS, MODE_PRIVATE);

                if (raw == null || raw.trim().isEmpty()) {
                    if (p.getBoolean("block_private", false)) {
                        block = true;
                    }
                } else {
                    display = raw;
                    String local = NumUtil.toLocal(raw);
                    String full = NumUtil.digits(raw);
                    boolean trusted = isTrustedSeries(local);

                    Series m = new Db(this).findMatch(local, full);
                    if (m != null && !(trusted && "cloud".equals(m.source))) {
                        block = true;
                        pattern = m.pattern;
                    }

                    if (!block && !trusted
                            && p.getBoolean("block_foreign", false)
                            && isForeignNumber(full)) {
                        block = true;
                    }

                    if (!block && !trusted
                            && p.getBoolean("smart_screen", true)
                            && !CallLogReader.isInContacts(this, raw)) {
                        SpamAdvisor.Verdict v = SpamAdvisor.liveScore(this, local, full);
                        if (v.score >= 3 && p.getBoolean("auto_block_suspect", false)) {
                            block = true;
                            pattern = v.prefix;
                        } else if (v.score >= 2) {
                            suspected = true;
                            suspectReason = v.reason;
                        }
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
            } else if (suspected) {
                BlockedNotifier.notifySuspected(this, display, suspectReason);
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

    private static boolean isTrustedSeries(String local) {
        for (String t : TRUSTED_PREFIXES) {
            if (local.startsWith(t)) return true;
        }
        return false;
    }

    /** Indian numbers appear as 10 digits, 0XXXXXXXXXX or +91XXXXXXXXXX; the rest is foreign. */
    private static boolean isForeignNumber(String full) {
        int n = full.length();
        if (n == 10) return false;
        if (n == 11 && full.startsWith("0")) return false;
        return !(n == 12 && full.startsWith("91"));
    }
}
