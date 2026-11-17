package com.atv.spamblocker;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Downloads a spam-number list and merges it into the series table with
 * source "cloud". Accepts one number per line (E.164 or plain digits,
 * optionally "number,comment"), skips blank/# comment lines.
 */
public final class SpamDbSync {

    public static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/Shalom-Karr/AI-Number-Blocklist/main/blacklist.txt";

    public static class Result {
        public int fetched;
        public int added;
        public String error;
    }

    private SpamDbSync() {}

    public static Result sync(Context ctx) {
        Result r = new Result();
        String url = ctx.getSharedPreferences(BlockedNotifier.PREFS, Context.MODE_PRIVATE)
                .getString("spam_db_url", DEFAULT_URL);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            int code = conn.getResponseCode();
            if (code != 200) {
                r.error = "HTTP " + code;
                return r;
            }
            Db db = new Db(ctx);
            int before = db.countBySource("cloud");
            Set<String> seen = new HashSet<>();
            android.database.sqlite.SQLiteDatabase sq = db.getWritableDatabase();
            sq.beginTransaction();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                    int comma = line.indexOf(',');
                    String num = comma >= 0 ? line.substring(0, comma).trim() : line;
                    String comment = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    String digits = NumUtil.digits(num);
                    // 10+ digits keeps entries exact-number shaped (national or E.164),
                    // so SQL prefix matching semantics stay predictable
                    if (digits.length() < 10 || digits.length() > 15) continue;
                    if (!seen.add(digits)) continue;
                    db.addSeries(digits,
                            comment.isEmpty() ? "Community spam list" : comment, "cloud");
                    r.fetched++;
                }
                sq.setTransactionSuccessful();
            } finally {
                sq.endTransaction();
            }
            r.added = db.countBySource("cloud") - before;
            ctx.getSharedPreferences(BlockedNotifier.PREFS, Context.MODE_PRIVATE)
                    .edit().putLong("last_sync", System.currentTimeMillis()).apply();
        } catch (Exception e) {
            r.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return r;
    }
}
