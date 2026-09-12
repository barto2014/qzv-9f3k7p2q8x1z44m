package com.bartotv.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Interfaz espejo de index.html: top-bar con tabs, reloj, guía por categorías e info-bar.
public class MainActivity extends Activity {

    private TextView status;
    private TextView clockTime;
    private TextView clockDate;
    private TextView infoCat;
    private TextView infoName;
    private ImageView infoLogo;
    private LinearLayout tabs;
    private RecyclerView list;

    private final List<Channel> all = new ArrayList<>();
    private final List<Object> visible = new ArrayList<>(); // String header o Channel
    private final List<String> categories = new ArrayList<>();
    private String activeCategory = null;
    private Adapter adapter;
    private long lastLoad = 0;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            try {
                Date n = new Date();
                clockTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(n));
                clockDate.setText(new SimpleDateFormat("EEE d MMM", new Locale("es")).format(n).toUpperCase());
            } catch (Exception ignored) {}
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.init(getApplicationContext());
        String prev = CrashLog.readPrevious(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        clockTime = findViewById(R.id.clock_time);
        clockDate = findViewById(R.id.clock_date);
        infoCat = findViewById(R.id.info_cat);
        infoName = findViewById(R.id.info_name);
        infoLogo = findViewById(R.id.info_logo);
        tabs = findViewById(R.id.tabs);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
        clockHandler.post(clockTick);
        if (prev != null) {
            // Mostrar el crash anterior para copiarlo. Tocar el texto reintenta.
            status.setText("CRASH ANTERIOR (sacale foto y pasamelo):\n" + prev);
            status.setOnClickListener(v -> load());
        } else {
            load();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (System.currentTimeMillis() - lastLoad > 30000) load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick);
    }

    private void load() {
        lastLoad = System.currentTimeMillis();
        status.setText("Cargando canales…");
        new Thread(() -> {
            try {
                DataRepository.Data d = DataRepository.load();
                runOnUiThread(() -> {
                    all.clear();
                    all.addAll(d.channels);
                    buildCategories();
                    rebuildVisible();
                    status.setText(all.size() + " canales · " + Config.OWNER + "/" + Config.REPO);
                    preview(firstChannel());
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void buildCategories() {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Channel c : all) {
            if (c.category != null && !seen.containsKey(c.category)) seen.put(c.category, c.category);
        }
        categories.clear();
        categories.addAll(seen.keySet());
        tabs.removeAllViews();
        tabs.addView(makeTab("TODOS", null));
        for (String cat : categories) tabs.addView(makeTab(cat, cat));
    }

    private View makeTab(String label, final String cat) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setPadding(24, 10, 24, 10);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        boolean active = (cat == null && activeCategory == null)
                || (cat != null && cat.equals(activeCategory));
        t.setTextColor(active ? 0xFFFFFFFF : 0xFF6a89a8);
        t.setBackgroundColor(active ? 0xFF0057b8 : 0x80002850);
        t.setOnClickListener(v -> {
            activeCategory = cat;
            buildCategories();
            rebuildVisible();
        });
        t.setOnFocusChangeListener((v, f) -> {
            if (f) {
                activeCategory = cat;
                buildCategories();
                rebuildVisible();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 12, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void rebuildVisible() {
        visible.clear();
        Map<String, List<Channel>> byCat = new LinkedHashMap<>();
        for (Channel c : all) {
            if (activeCategory != null && !activeCategory.equals(c.category)) continue;
            if (!byCat.containsKey(c.category)) byCat.put(c.category, new ArrayList<>());
            byCat.get(c.category).add(c);
        }
        for (Map.Entry<String, List<Channel>> e : byCat.entrySet()) {
            visible.add(e.getKey());
            visible.addAll(e.getValue());
        }
        adapter.notifyDataSetChanged();
        if (!visible.isEmpty()) list.post(() -> {
            int firstRow = -1;
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i) instanceof Channel) { firstRow = i; break; }
            }
            if (firstRow >= 0) {
                list.scrollToPosition(firstRow);
                RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(firstRow);
                if (vh != null) vh.itemView.requestFocus();
            }
        });
    }

    private Channel firstChannel() {
        for (Object o : visible) if (o instanceof Channel) return (Channel) o;
        return null;
    }

    private void preview(Channel c) {
        if (c == null) {
            infoCat.setText("BARTO TV");
            infoName.setText("Selecciona un canal");
            infoLogo.setImageBitmap(null);
            return;
        }
        infoCat.setText(c.category == null ? "" : c.category);
        infoName.setText(c.name == null ? "" : c.name);
        ImageLoader.load(c.logo, infoLogo);
    }

    private void open(Channel c) {
        if ("youtube".equalsIgnoreCase(c.type)) {
            Intent y = new Intent(this, YoutubeActivity.class);
            y.putExtra("videoId", extractYoutubeId(c.url));
            startActivity(y);
            return;
        }
        if ("web".equalsIgnoreCase(c.type) || "redirect".equalsIgnoreCase(c.type)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(c.url)));
            } catch (Exception e) {
                status.setText("No se pudo abrir: " + c.url);
            }
            return;
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("name", c.name);
        i.putExtra("url", c.url);
        i.putExtra("type", c.type);
        i.putExtra("ua", c.ua == null ? "" : c.ua);
        i.putExtra("referer", c.referer == null ? "" : c.referer);
        i.putExtra("headers", new JSONObject(c.headers).toString());
        i.putExtra("drmKeys", new JSONObject(c.drmKeys).toString());
        i.putExtra("drmServer", c.drmServer == null ? "" : c.drmServer);
        startActivity(i);
    }

    private static String extractYoutubeId(String url) {
        if (url == null) return "";
        int v = url.indexOf("v=");
        if (v >= 0) {
            String s = url.substring(v + 2);
            int amp = s.indexOf('&');
            return amp >= 0 ? s.substring(0, amp) : s;
        }
        String[] parts = url.split("/");
        return parts.length == 0 ? url : parts[parts.length - 1];
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int T_HEADER = 0;
        private static final int T_ROW = 1;

        @Override
        public int getItemViewType(int pos) {
            return visible.get(pos) instanceof Channel ? T_ROW : T_HEADER;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == T_HEADER) {
                return new HH(inf.inflate(R.layout.section_header, parent, false));
            }
            return new VH(inf.inflate(R.layout.item_channel, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            Object o = visible.get(pos);
            if (o instanceof String) {
                ((HH) h).title.setText((String) o);
                return;
            }
            Channel c = (Channel) o;
            VH vh = (VH) h;
            vh.name.setText(c.name);
            vh.sub.setText(c.category + " · " + (c.type == null ? "" : c.type.toUpperCase()));
            ImageLoader.load(c.logo, vh.logo);
            vh.itemView.setOnClickListener(v -> open(c));
            vh.itemView.setOnFocusChangeListener((v, f) -> {
                v.setScaleX(f ? 1.02f : 1f);
                v.setScaleY(f ? 1.02f : 1f);
                v.setAlpha(f ? 1f : 0.9f);
                if (f) preview(c);
            });
        }

        @Override
        public int getItemCount() { return visible.size(); }
    }

    private static class HH extends RecyclerView.ViewHolder {
        TextView title;
        HH(View v) { super(v); title = v.findViewById(R.id.title); }
    }

    private static class VH extends RecyclerView.ViewHolder {
        ImageView logo;
        TextView name;
        TextView sub;
        VH(View v) {
            super(v);
            logo = v.findViewById(R.id.logo);
            name = v.findViewById(R.id.name);
            sub = v.findViewById(R.id.sub);
        }
    }
}
