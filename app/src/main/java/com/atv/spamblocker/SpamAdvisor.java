package com.atv.spamblocker;

import android.content.Context;
import android.provider.CallLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups recent calls by the first PREFIX_LEN digits of the local number and
 * scores each group on volume, distinct callers and missed ratio.
 */
public final class SpamAdvisor {

    public static final int PREFIX_LEN = 5;

    public static class SeriesStat {
        public final String prefix;
        public int total;
        public int missed;
        public int distinct;
        public double score;
        public String reason;

        public SeriesStat(String prefix) {
            this.prefix = prefix;
        }
    }

    /** Verdict for a number that is ringing right now. */
    public static class Verdict {
        public int score;
        public String prefix = "";
        public String reason = "";
    }

    private SpamAdvisor() {}

    public static List<SeriesStat> analyze(List<CallLogEntry> calls) {
        Map<String, SeriesStat> stats = new HashMap<>();
        Map<String, Set<String>> numbers = new HashMap<>();
        Map<String, Long> durSum = new HashMap<>();
        Map<String, Integer> answered = new HashMap<>();

        for (CallLogEntry e : calls) {
            String local = NumUtil.toLocal(e.number);
            if (local.length() < PREFIX_LEN) continue;
            String prefix = local.substring(0, PREFIX_LEN);
            SeriesStat st = stats.get(prefix);
            if (st == null) {
                st = new SeriesStat(prefix);
                stats.put(prefix, st);
                numbers.put(prefix, new HashSet<>());
                durSum.put(prefix, 0L);
                answered.put(prefix, 0);
            }
            st.total++;
            if (e.type == CallLog.Calls.MISSED_TYPE
                    || e.type == CallLog.Calls.REJECTED_TYPE
                    || e.type == CallLog.Calls.BLOCKED_TYPE) {
                st.missed++;
            } else {
                durSum.put(prefix, durSum.get(prefix) + e.duration);
                answered.put(prefix, answered.get(prefix) + 1);
            }
            numbers.get(prefix).add(local);
        }

        List<SeriesStat> out = new ArrayList<>();
        for (SeriesStat st : stats.values()) {
            st.distinct = numbers.get(st.prefix).size();
            double missedRatio = st.total == 0 ? 0 : (double) st.missed / st.total;
            double score = 0;
            if (st.total >= 3 && st.distinct >= 2 && missedRatio >= 0.5) score += 3;
            if (st.total >= 3 && st.distinct == 1 && missedRatio >= 0.5) score += 2;
            int ans = answered.get(st.prefix);
            if (st.total >= 3 && ans > 0 && durSum.get(st.prefix) / ans < 15) score += 1;
            if (st.total >= 6) score += 1;
            st.score = score;
            st.reason = st.total + " calls \u00b7 " + st.distinct
                    + (st.distinct == 1 ? " number" : " numbers")
                    + " \u00b7 " + Math.round(missedRatio * 100) + "% missed";
            if (score >= 3) out.add(st);
        }

        out.sort((a, b) -> Double.compare(b.score, a.score));
        if (out.size() > 10) out.subList(10, out.size()).clear();
        return out;
    }

    /** One-off verdict for a series the user is about to add. */
    public static String verdict(List<CallLogEntry> calls, String pattern, StringBuilder colorOut) {
        int total = 0, missed = 0, answeredN = 0;
        long durSum = 0;
        Set<String> nums = new HashSet<>();
        for (CallLogEntry e : calls) {
            String local = NumUtil.toLocal(e.number);
            if (pattern.length() > 10) {
                if (!NumUtil.digits(e.number).startsWith(pattern)) continue;
            } else {
                if (local.isEmpty() || !local.startsWith(pattern)) continue;
            }
            total++;
            nums.add(local.isEmpty() ? NumUtil.digits(e.number) : local);
            if (e.type == CallLog.Calls.MISSED_TYPE
                    || e.type == CallLog.Calls.REJECTED_TYPE
                    || e.type == CallLog.Calls.BLOCKED_TYPE) {
                missed++;
            } else {
                durSum += e.duration;
                answeredN++;
            }
        }
        if (total == 0) {
            colorOut.replace(0, colorOut.length(), "green");
            return "No calls from this series in the last 30 days";
        }
        double missedRatio = (double) missed / total;
        int avgDur = answeredN == 0 ? 0 : (int) (durSum / answeredN);
        double score = 0;
        if (total >= 3 && nums.size() >= 2 && missedRatio >= 0.5) score += 3;
        if (total >= 3 && nums.size() == 1 && missedRatio >= 0.5) score += 2;
        if (total >= 3 && answeredN > 0 && avgDur < 15) score += 1;
        if (total >= 6) score += 1;

        StringBuilder sb = new StringBuilder();
        sb.append(total).append(" call").append(total == 1 ? "" : "s")
          .append(" \u00b7 ").append(nums.size())
          .append(nums.size() == 1 ? " number" : " numbers")
          .append(" \u00b7 ").append(Math.round(missedRatio * 100)).append("% missed");
        if (answeredN > 0) sb.append(" \u00b7 avg ").append(avgDur).append("s");
        if (score >= 3) {
            colorOut.replace(0, colorOut.length(), "red");
            sb.append(" \u2014 likely spam");
        } else if (score >= 2 || (total >= 2 && missedRatio >= 0.5)) {
            colorOut.replace(0, colorOut.length(), "yellow");
            sb.append(" \u2014 suspicious");
        } else {
            colorOut.replace(0, colorOut.length(), "green");
            sb.append(" \u2014 looks normal");
        }
        return sb.toString();
    }

    /**
     * Live verdict for a number that is ringing now, from its 5-digit prefix's
     * recent call history. Indian telemarketing pools show up as bursts: many
     * distinct numbers in one prefix, mostly missed, short answered durations.
     */
    public static Verdict liveScore(Context ctx, String local, String full) {
        Verdict v = new Verdict();
        String base = local.isEmpty() ? full : local;
        if (base.length() < PREFIX_LEN) return v;
        v.prefix = base.substring(0, PREFIX_LEN);

        int total = 0, missed = 0, answeredN = 0;
        long durSum = 0;
        Set<String> nums = new HashSet<>();
        for (CallLogEntry e : CallLogReader.recentCalls(ctx, 300, false)) {
            String l = NumUtil.toLocal(e.number);
            String other = l.isEmpty() ? NumUtil.digits(e.number) : l;
            if (!other.startsWith(v.prefix)) continue;
            total++;
            nums.add(other);
            if (e.type == CallLog.Calls.MISSED_TYPE
                    || e.type == CallLog.Calls.REJECTED_TYPE
                    || e.type == CallLog.Calls.BLOCKED_TYPE) {
                missed++;
            } else {
                durSum += e.duration;
                answeredN++;
            }
        }
        if (total == 0) return v;

        double missedRatio = (double) missed / total;
        double score = 0;
        if (total >= 3 && nums.size() >= 2 && missedRatio >= 0.5) score += 3;
        if (total >= 3 && nums.size() == 1 && missedRatio >= 0.5) score += 2;
        if (total >= 3 && answeredN > 0 && durSum / answeredN < 15) score += 1;
        if (total >= 6) score += 1;
        v.score = (int) score;
        v.reason = total + " recent calls \u00b7 " + nums.size()
                + (nums.size() == 1 ? " number" : " numbers")
                + " \u00b7 " + Math.round(missedRatio * 100) + "% missed";
        return v;
    }
}
