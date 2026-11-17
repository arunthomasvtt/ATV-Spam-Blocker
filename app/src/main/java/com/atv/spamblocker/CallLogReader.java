package com.atv.spamblocker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallLogReader {

    private CallLogReader() {}

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Incoming/missed/rejected/blocked calls of the last 30 days, newest first. */
    public static List<CallLogEntry> recentCalls(Context ctx, int maxEntries) {
        List<CallLogEntry> out = new ArrayList<>();
        if (!hasPermission(ctx)) return out;
        long since = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
        String[] proj = {
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_LOOKUP_URI
        };
        try (Cursor c = ctx.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                proj,
                CallLog.Calls.DATE + ">=?",
                new String[]{String.valueOf(since)},
                CallLog.Calls.DATE + " DESC")) {
            if (c == null) return out;
            int iNum = c.getColumnIndex(CallLog.Calls.NUMBER);
            int iName = c.getColumnIndex(CallLog.Calls.CACHED_NAME);
            int iType = c.getColumnIndex(CallLog.Calls.TYPE);
            int iDate = c.getColumnIndex(CallLog.Calls.DATE);
            int iDur = c.getColumnIndex(CallLog.Calls.DURATION);
            int iLookup = c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI);
            if (iNum < 0 || iType < 0 || iDate < 0) return out;
            Map<String, Boolean> contactCache = new HashMap<>();
            while (c.moveToNext() && out.size() < maxEntries) {
                String number = c.getString(iNum);
                if (number == null || number.trim().isEmpty()) continue;
                int type = c.getInt(iType);
                if (type == CallLog.Calls.OUTGOING_TYPE || type == CallLog.Calls.VOICEMAIL_TYPE) continue;
                String name = iName >= 0 ? c.getString(iName) : null;
                String key = NumUtil.digits(number);
                Boolean contact = contactCache.get(key);
                if (contact == null) {
                    String lookup = iLookup >= 0 ? c.getString(iLookup) : null;
                    contact = (lookup != null && !lookup.trim().isEmpty())
                            || isInContacts(ctx, number);
                    contactCache.put(key, contact);
                }
                out.add(new CallLogEntry(
                        number,
                        name == null ? "" : name,
                        type,
                        c.getLong(iDate),
                        iDur >= 0 ? c.getLong(iDur) : 0,
                        contact));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean isInContacts(Context ctx, String number) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return false;
        if (number == null || NumUtil.digits(number).length() < 4) return false;
        try (Cursor c = ctx.getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(number)),
                new String[]{ContactsContract.PhoneLookup._ID},
                null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }
}
