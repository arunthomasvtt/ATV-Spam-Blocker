package com.atv.spamblocker;

public final class NumUtil {

    private NumUtil() {}

    public static String digits(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Reduce to the local (10-digit) form for Indian numbers:
     * +91XXXXXXXXXX / 91XXXXXXXXXX -> XXXXXXXXXX, 0XXXXXXXXXX -> XXXXXXXXXX.
     */
    public static String toLocal(String raw) {
        String d = digits(raw);
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        else if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        return d;
    }

    /**
     * A series matches when the local number starts with it. Patterns longer
     * than 10 digits are compared against the full number including country code.
     */
    public static boolean matches(String pattern, String local, String full) {
        if (pattern == null || pattern.isEmpty()) return false;
        if (local.startsWith(pattern)) return true;
        return pattern.length() > 10 && full.startsWith(pattern);
    }

    public static String cleanPattern(String raw) {
        return digits(raw);
    }
}
