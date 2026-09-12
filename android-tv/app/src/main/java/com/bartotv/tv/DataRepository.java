package com.bartotv.tv;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Lee datar.bin (base64 de JSON, mismo que usa index.html) directo de GitHub.
// Sin Vercel, sin CORS, sin mixed-content: por eso la nativa no necesita proxy.
public final class DataRepository {

    public static class Data {
        public List<Channel> channels = new ArrayList<>();
        public List<String> categoryOrder = new ArrayList<>();
    }

    public static Data load() throws Exception {
        String raw = httpGet(Config.datarUrl() + "?v=" + System.currentTimeMillis());
        String json = decode(raw);
        JSONObject root = new JSONObject(json);
        Data d = new Data();
        JSONArray arr = root.optJSONArray("channels");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Channel c = Channel.fromJson(o);
                if (c.url == null || c.url.isEmpty()) continue;
                d.channels.add(c);
            }
        }
        JSONArray order = root.optJSONArray("categoryOrder");
        if (order != null) {
            for (int i = 0; i < order.length(); i++) d.categoryOrder.add(order.optString(i));
        }
        // Ordenar por categoryOrder como la web
        if (!d.categoryOrder.isEmpty()) {
            final Map<String, Integer> pos = new LinkedHashMap<>();
            for (int i = 0; i < d.categoryOrder.size(); i++) pos.put(d.categoryOrder.get(i), i);
            d.channels.sort((a, b) -> {
                int ia = pos.getOrDefault(a.category.toUpperCase(), 999);
                int ib = pos.getOrDefault(b.category.toUpperCase(), 999);
                if (ia != ib) return ia - ib;
                return a.name.compareToIgnoreCase(b.name);
            });
        }
        return d;
    }

    private static String decode(String raw) throws Exception {
        String t = raw.trim();
        // datar.bin normal: base64(JSON)
        try {
            byte[] b = Base64.decode(t, Base64.DEFAULT);
            String s = new String(b, StandardCharsets.UTF_8).trim();
            if (s.startsWith("{")) return s;
        } catch (Exception ignored) {}
        // Fallback: raw ya es JSON
        if (t.startsWith("{")) return t;
        throw new Exception("datar.bin inválido");
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "BartoTV-Android/1.0");
        c.setRequestProperty("Cache-Control", "no-cache");
        // Repo privado: usa el token inyectado en el build (secret DATAR_TOKEN).
        try {
            String tok = com.bartotv.tv.BuildConfig.DATAR_TOKEN;
            if (tok != null && !tok.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + tok);
                c.setRequestProperty("Accept", "application/vnd.github.raw");
            }
        } catch (Exception ignored) {}
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private DataRepository() {}
}
