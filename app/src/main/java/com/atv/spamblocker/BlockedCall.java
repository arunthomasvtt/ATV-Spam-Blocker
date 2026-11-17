package com.atv.spamblocker;

public class BlockedCall {
    public final long id;
    public final String number;
    public final String pattern;
    public final long ts;

    public BlockedCall(long id, String number, String pattern, long ts) {
        this.id = id;
        this.number = number;
        this.pattern = pattern;
        this.ts = ts;
    }
}
