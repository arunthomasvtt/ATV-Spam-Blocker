package com.atv.spamblocker;

public class CallLogEntry {
    public final String number;
    public final String name;
    public final int type;
    public final long date;
    public final long duration;
    public final boolean contact;

    public CallLogEntry(String number, String name, int type, long date, long duration, boolean contact) {
        this.number = number;
        this.name = name;
        this.type = type;
        this.date = date;
        this.duration = duration;
        this.contact = contact;
    }
}
