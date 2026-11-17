package com.atv.spamblocker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.app.role.RoleManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 1;
    private static final int REQ_ROLE = 2;

    private static final int COLOR_SAFE = 0xFF00E5A0;
    private static final int COLOR_WARN = 0xFFFFCC00;
    private static final int COLOR_SPAM = 0xFFFF3B5C;
    private static final int COLOR_MUTED = 0xFF6B6B8A;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fmtDateTime =
            new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
    private final SimpleDateFormat fmtDate =
            new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

    private Db db;
    private List<CallLogEntry> lastCalls = new ArrayList<>();

    private LinearLayout pageSeries, pageCalls, pageBlocked;
    private TextView tvBanner, tvSeriesEmpty, tvCallsEmpty, tvBlockedEmpty;
    private ListView listSeries, listCalls, listBlockedLog;
    private SeriesAdapter seriesAdapter;
    private CallLogAdapter callLogAdapter;
    private BlockedCallAdapter blockedAdapter;
    private int lastSeriesCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new Db(this);

        pageSeries = findViewById(R.id.pageSeries);
        pageCalls = findViewById(R.id.pageCalls);
        pageBlocked = findViewById(R.id.pageBlocked);
        tvBanner = findViewById(R.id.tvBanner);
        Button btnBanner = findViewById(R.id.btnBanner);
        listSeries = findViewById(R.id.listSeries);
        listCalls = findViewById(R.id.listCalls);
        listBlockedLog = findViewById(R.id.listBlockedLog);
        tvSeriesEmpty = findViewById(R.id.tvSeriesEmpty);
        tvCallsEmpty = findViewById(R.id.tvCallsEmpty);
        tvBlockedEmpty = findViewById(R.id.tvBlockedEmpty);

        seriesAdapter = new SeriesAdapter();
        callLogAdapter = new CallLogAdapter();
        blockedAdapter = new BlockedCallAdapter();
        listSeries.setAdapter(seriesAdapter);
        listCalls.setAdapter(callLogAdapter);
        listBlockedLog.setAdapter(blockedAdapter);

        listSeries.setOnItemLongClickListener((parent, view, pos, id) -> {
            confirmDelete(seriesAdapter.getItem(pos));
            return true;
        });
        listCalls.setOnItemClickListener((parent, view, pos, id) ->
                callRowClicked(callLogAdapter.getItem(pos)));

        findViewById(R.id.btnAddSeries).setOnClickListener(v -> showAddSeriesDialog());
        findViewById(R.id.btnMenu).setOnClickListener(this::showOverflow);
        btnBanner.setOnClickListener(v -> bannerAction());

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_shield) showTab(0);
            else if (id == R.id.nav_calls) showTab(1);
            else showTab(2);
            return true;
        });
        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences p = prefs();
        if (!p.getBoolean("asked_perms", false)) {
            p.edit().putBoolean("asked_perms", true).apply();
            requestNeededPerms();
        }
        refreshAll();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(BlockedNotifier.PREFS, MODE_PRIVATE);
    }

    // ---------- tabs ----------

    private void showTab(int idx) {
        pageSeries.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        pageCalls.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        pageBlocked.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        refreshAll();
    }

    private void refreshAll() {
        refreshSeries();
        refreshCalls();
        refreshBlocked();
        syncBanner();
    }

    // ---------- permissions / role ----------

    private boolean granted(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPerms() {
        List<String> need = new ArrayList<>();
        if (!granted(Manifest.permission.READ_CALL_LOG)) need.add(Manifest.permission.READ_CALL_LOG);
        if (!granted(Manifest.permission.READ_CONTACTS)) need.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT < 29 && !granted(Manifest.permission.READ_PHONE_STATE))
            need.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        } else {
            maybeAskRole();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshAll();
        maybeAskRole();
    }

    private boolean roleHeld() {
        if (Build.VERSION.SDK_INT < 29) {
            return granted(Manifest.permission.READ_PHONE_STATE);
        }
        RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private void maybeAskRole() {
        if (Build.VERSION.SDK_INT < 29) return;
        if (roleHeld()) return;
        SharedPreferences p = prefs();
        if (p.getBoolean("asked_role", false)) return;
        p.edit().putBoolean("asked_role", true).apply();
        requestRole();
    }

    private void requestRole() {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                toast("Call screening role is not available on this device");
                return;
            }
            startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
        } catch (Exception e) {
            toast("Could not open the screening role request");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            refreshAll();
            if (roleHeld()) toast("Call screening enabled");
        }
    }

    // ---------- banner ----------

    private void syncBanner() {
        boolean logPerm = granted(Manifest.permission.READ_CALL_LOG);
        boolean screen = roleHeld();
        Button btn = findViewById(R.id.btnBanner);
        if (screen && logPerm) {
            tvBanner.setText("Protection active \u2014 " + lastSeriesCount
                    + (lastSeriesCount == 1 ? " series" : " series") + " blocked");
            tvBanner.setTextColor(COLOR_SAFE);
            btn.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder();
            if (!logPerm) sb.append("Call log permission missing. ");
            if (!screen) sb.append(Build.VERSION.SDK_INT >= 29
                    ? "Call screening is off \u2014 calls will not be blocked."
                    : "Phone state permission missing \u2014 calls will not be blocked.");
            tvBanner.setText(sb.toString().trim());
            tvBanner.setTextColor(COLOR_WARN);
            btn.setVisibility(View.VISIBLE);
            btn.setText(!logPerm ? R.string.banner_grant : R.string.banner_enable);
        }
    }

    private void bannerAction() {
        if (!granted(Manifest.permission.READ_CALL_LOG)) {
            requestNeededPerms();
        } else if (!roleHeld()) {
            requestRole();
        }
    }

    // ---------- data refresh ----------

    private void refreshSeries() {
        exec.execute(() -> {
            final List<Series> list = db.getSeries();
            ui.post(() -> {
                lastSeriesCount = list.size();
                seriesAdapter.setData(list);
                tvSeriesEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                syncBanner();
            });
        });
    }

    private void refreshCalls() {
        exec.execute(() -> {
            final List<CallLogEntry> calls = CallLogReader.recentCalls(this, 300);
            final List<SpamAdvisor.SeriesStat> suggestions = new ArrayList<>();
            if (CallLogReader.hasPermission(this)) {
                List<String> blockedPatterns = new ArrayList<>();
                for (Series s : db.getSeries()) blockedPatterns.add(s.pattern);
                List<CallLogEntry> forAnalysis = new ArrayList<>();
                for (CallLogEntry e : calls) if (!e.contact) forAnalysis.add(e);
                for (SpamAdvisor.SeriesStat st : SpamAdvisor.analyze(forAnalysis)) {
                    boolean covered = false;
                    for (String p : blockedPatterns) {
                        if (NumUtil.matches(p, st.prefix, st.prefix)) {
                            covered = true;
                            break;
                        }
                    }
                    if (!covered) suggestions.add(st);
                }
            }
            ui.post(() -> {
                lastCalls = calls;
                callLogAdapter.setData(suggestions, calls);
                if (!CallLogReader.hasPermission(this)) {
                    tvCallsEmpty.setText(R.string.calls_need_permission);
                    tvCallsEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvCallsEmpty.setVisibility(calls.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private void refreshBlocked() {
        exec.execute(() -> {
            final List<BlockedCall> list = db.recentBlocked(200);
            ui.post(() -> {
                blockedAdapter.setData(list);
                tvBlockedEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ---------- actions ----------

    private void addPattern(String pattern, String note, String source) {
        exec.execute(() -> {
            final Series s = db.addSeries(pattern, note, source);
            ui.post(() -> {
                if (s != null) {
                    toast("Series " + s.pattern + " blocked");
                    refreshSeries();
                    refreshCalls();
                }
            });
        });
    }

    private void confirmDelete(Series s) {
        new AlertDialog.Builder(this)
                .setTitle("Remove series " + s.pattern + "?")
                .setMessage("Calls from this series will ring again.")
                .setPositiveButton("Remove", (d, w) -> exec.execute(() -> {
                    db.deleteSeries(s.id);
                    ui.post(this::refreshAll);
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddSeriesDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_series, null);
        EditText et = v.findViewById(R.id.etPattern);
        TextView tvStats = v.findViewById(R.id.tvStats);

        Runnable update = () -> {
            String p = NumUtil.cleanPattern(et.getText().toString());
            if (p.length() < 3) {
                tvStats.setText(R.string.add_hint);
                tvStats.setTextColor(COLOR_MUTED);
                return;
            }
            StringBuilder color = new StringBuilder("green");
            String verdict = SpamAdvisor.verdict(lastCalls, p, color);
            tvStats.setText(verdict);
            switch (color.toString()) {
                case "red":    tvStats.setTextColor(COLOR_SPAM); break;
                case "yellow": tvStats.setTextColor(COLOR_WARN); break;
                default:       tvStats.setTextColor(COLOR_SAFE);
            }
        };
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { update.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_title)
                .setView(v)
                .setPositiveButton(R.string.block, (d, w) -> {
                    String p = NumUtil.cleanPattern(et.getText().toString());
                    if (p.length() < 3 || p.length() > 13) {
                        toast("Enter a prefix of 3-13 digits");
                        return;
                    }
                    addPattern(p, "Added manually", "manual");
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        update.run();
    }

    private void callRowClicked(Object row) {
        if (!(row instanceof CallLogEntry)) {
            if (row instanceof SpamAdvisor.SeriesStat) {
                confirmSuggestion((SpamAdvisor.SeriesStat) row);
            }
            return;
        }
        final CallLogEntry e = (CallLogEntry) row;
        String local = NumUtil.toLocal(e.number);
        String full = NumUtil.digits(e.number);
        final String base = local.isEmpty() ? full : local;
        if (base.length() < 3) {
            toast("This number cannot be blocked");
            return;
        }

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_block_call, null);
        TextView tvNumber = v.findViewById(R.id.tvNumber);
        TextView tvName = v.findViewById(R.id.tvName);
        TextView tvPreview = v.findViewById(R.id.tvPreview);
        Spinner sp = v.findViewById(R.id.spMode);
        tvNumber.setText(e.number);
        tvName.setVisibility(e.name.isEmpty() ? View.GONE : View.VISIBLE);
        tvName.setText(e.name);

        final List<Integer> lens = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String shown = base.length() > 10 ? base.substring(0, 10) + "\u2026" : base;
        labels.add("Exact number (" + shown + ")");
        lens.add(Math.min(base.length(), 10));
        for (int L : new int[]{4, 5, 6, 7, 8}) {
            if (L < base.length()) {
                labels.add("Series: first " + L + " digits (" + base.substring(0, L) + ")");
                lens.add(L);
            }
        }
        if (base.length() > 10) {
            labels.add("Series: first " + base.length() + " digits");
            lens.add(base.length());
        }
        ArrayAdapter<String> spAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(spAdapter);

        Runnable preview = () -> {
            int pos = sp.getSelectedItemPosition() < 0 ? 0 : sp.getSelectedItemPosition();
            String p = base.substring(0, lens.get(pos));
            tvPreview.setText("Will reject any number starting with " + p);
        };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                preview.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.block_dialog_title)
                .setView(v)
                .setPositiveButton(R.string.block, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            int pos = sp.getSelectedItemPosition() < 0 ? 0 : sp.getSelectedItemPosition();
            String p = base.substring(0, lens.get(pos));
            String note = e.name.isEmpty() ? "From call log" : "From " + e.name;
            addPattern(p, note, "call_log");
            dialog.dismiss();
        });
        preview.run();
    }

    private void confirmSuggestion(SpamAdvisor.SeriesStat st) {
        new AlertDialog.Builder(this)
                .setTitle("Block series " + st.prefix + "?")
                .setMessage("Every number starting with " + st.prefix
                        + " will be rejected automatically.\n\n" + st.reason)
                .setPositiveButton("Block series", (d, w) ->
                        addPattern(st.prefix, "Spam suggestion", "suggested"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOverflow(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenuInflater().inflate(R.menu.main_menu, pm.getMenu());
        pm.getMenu().findItem(R.id.action_notify)
                .setChecked(prefs().getBoolean("notify_enabled", true));
        pm.getMenu().findItem(R.id.action_private)
                .setChecked(prefs().getBoolean("block_private", false));
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_notify) {
                boolean now = !prefs().getBoolean("notify_enabled", true);
                prefs().edit().putBoolean("notify_enabled", now).apply();
                item.setChecked(now);
                toast(now ? "Blocked-call notifications on" : "Blocked-call notifications off");
                return true;
            } else if (id == R.id.action_private) {
                boolean now = !prefs().getBoolean("block_private", false);
                prefs().edit().putBoolean("block_private", now).apply();
                item.setChecked(now);
                toast(now ? "Private-number blocking on" : "Private-number blocking off");
                return true;
            } else if (id == R.id.action_clear) {
                new AlertDialog.Builder(this)
                        .setTitle("Clear blocked-call log?")
                        .setPositiveButton("Clear", (d, w) -> exec.execute(() -> {
                            db.clearBlockedLog();
                            ui.post(this::refreshBlocked);
                        }))
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
            return false;
        });
        pm.show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- adapters ----------

    private class SeriesAdapter extends BaseAdapter {
        private List<Series> data = new ArrayList<>();

        void setData(List<Series> d) {
            data = d;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }
        @Override public Series getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return data.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView == null
                    ? getLayoutInflater().inflate(R.layout.item_series, parent, false)
                    : convertView;
            Series s = getItem(position);
            TextView tvPattern = row.findViewById(R.id.tvPattern);
            TextView tvSub = row.findViewById(R.id.tvSub);
            TextView tvCount = row.findViewById(R.id.tvCount);
            tvPattern.setText(s.pattern);
            String note = s.note == null || s.note.isEmpty() ? "" : s.note + " \u00b7 ";
            tvSub.setText(note + s.sourceLabel() + " \u00b7 " + fmtDate.format(new Date(s.createdAt)));
            tvCount.setText(s.blockedCount + " blocked");
            tvCount.setVisibility(s.blockedCount > 0 ? View.VISIBLE : View.GONE);
            return row;
        }
    }

    private class CallLogAdapter extends BaseAdapter {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_SUGGESTION = 1;
        private static final int TYPE_CALL = 2;

        private final List<Object> rows = new ArrayList<>();

        void setData(List<SpamAdvisor.SeriesStat> suggestions, List<CallLogEntry> calls) {
            rows.clear();
            rows.add("SUSPECTED SPAM SERIES");
            if (suggestions.isEmpty()) rows.add("none_found");
            for (SpamAdvisor.SeriesStat st : suggestions) rows.add(st);
            rows.add("RECEIVED CALLS \u00b7 LAST 30 DAYS");
            for (CallLogEntry e : calls) rows.add(e);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int position) {
            Object o = rows.get(position);
            if (o instanceof SpamAdvisor.SeriesStat) return TYPE_SUGGESTION;
            if (o instanceof CallLogEntry) return TYPE_CALL;
            return TYPE_HEADER;
        }
        @Override public boolean isEnabled(int position) {
            return getItemViewType(position) != TYPE_HEADER;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int type = getItemViewType(position);
            if (type == TYPE_HEADER) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : null;
                if (tv == null) {
                    tv = new TextView(MainActivity.this);
                    tv.setTextColor(COLOR_MUTED);
                    tv.setTextSize(13);
                    tv.setPadding(dp(16), dp(10), dp(16), dp(4));
                }
                Object o = rows.get(position);
                tv.setText("none_found".equals(o) ? "No spam patterns found" : o.toString());
                return tv;
            }
            View row = convertView == null
                    ? getLayoutInflater().inflate(
                        type == TYPE_SUGGESTION ? R.layout.item_suggestion : R.layout.item_call,
                        parent, false)
                    : convertView;
            if (type == TYPE_SUGGESTION) {
                SpamAdvisor.SeriesStat st = (SpamAdvisor.SeriesStat) rows.get(position);
                ((TextView) row.findViewById(R.id.tvPrefix)).setText("Series " + st.prefix);
                ((TextView) row.findViewById(R.id.tvReason)).setText(st.reason);
            } else {
                CallLogEntry e = (CallLogEntry) rows.get(position);
                ((TextView) row.findViewById(R.id.tvNumber)).setText(e.number);
                String name = e.name.isEmpty() ? "" : e.name + " \u00b7 ";
                ((TextView) row.findViewById(R.id.tvSub))
                        .setText(name + fmtDateTime.format(new Date(e.date)));
                TextView tvType = row.findViewById(R.id.tvType);
                tvType.setText(typeLabel(e.type));
                int[] badge = typeBadge(e.type);
                tvType.setBackgroundResource(badge[0]);
                tvType.setTextColor(badge[1]);
            }
            return row;
        }

        private String typeLabel(int type) {
            switch (type) {
                case CallLog.Calls.INCOMING_TYPE: return "Incoming";
                case CallLog.Calls.MISSED_TYPE:   return "Missed";
                case CallLog.Calls.REJECTED_TYPE: return "Rejected";
                case CallLog.Calls.BLOCKED_TYPE:  return "Blocked";
                default:                          return "Call";
            }
        }

        private int[] typeBadge(int type) {
            switch (type) {
                case CallLog.Calls.INCOMING_TYPE: return new int[]{R.drawable.bg_badge_safe, COLOR_SAFE};
                case CallLog.Calls.MISSED_TYPE:   return new int[]{R.drawable.bg_badge_spam, COLOR_SPAM};
                case CallLog.Calls.REJECTED_TYPE: return new int[]{R.drawable.bg_badge_spam, COLOR_SPAM};
                case CallLog.Calls.BLOCKED_TYPE:  return new int[]{R.drawable.bg_badge_spam, COLOR_SPAM};
                default:                          return new int[]{R.drawable.bg_badge_warn, COLOR_WARN};
            }
        }
    }

    private class BlockedCallAdapter extends BaseAdapter {
        private List<BlockedCall> data = new ArrayList<>();

        void setData(List<BlockedCall> d) {
            data = d;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }
        @Override public BlockedCall getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return data.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView == null
                    ? getLayoutInflater().inflate(R.layout.item_blocked_call, parent, false)
                    : convertView;
            BlockedCall b = getItem(position);
            ((TextView) row.findViewById(R.id.tvNumber)).setText(
                    b.number == null || b.number.isEmpty() ? "Private number" : b.number);
            ((TextView) row.findViewById(R.id.tvDetail)).setText(
                    "series " + b.pattern + " \u00b7 " + fmtDateTime.format(new Date(b.ts)));
            return row;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
