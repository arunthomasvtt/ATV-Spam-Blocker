package com.atv.spamblocker;

public class Series {
    public final long id;
    public final String pattern;
    public final String note;
    public final String source;
    public final long createdAt;
    public int blockedCount;

    public Series(long id, String pattern, String note, String source, long createdAt, int blockedCount) {
        this.id = id;
        this.pattern = pattern;
        this.note = note;
        this.source = source;
        this.createdAt = createdAt;
        this.blockedCount = blockedCount;
    }

    public String sourceLabel() {
        switch (source == null ? "manual" : source) {
            case "call_log":  return "From call log";
            case "suggested": return "Spam suggestion";
            case "builtin":   return "Built-in";
            case "cloud":     return "Cloud spam list";
            default:          return "Added manually";
        }
    }
}
