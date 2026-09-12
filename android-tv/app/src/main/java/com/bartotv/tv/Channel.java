package com.bartotv.tv;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class Channel {
    public long id;
    public String name = "";
    public String url = "";
    public String type = "";
    public String category = "GENERAL";
    public String logo = "";
    public String ua = "";
    public String referer = "";
    public Map<String, String> headers = new HashMap<>();
    public Map<String, String> drmKeys = new HashMap<>();
    public String drmServer = "";

    public static Channel fromJson(JSONObject o) {
        Channel c = new Channel();
        c.id = o.optLong("id", System.currentTimeMillis());
        c.name = o.optString("name", "");
        c.url = o.optString("url", "");
        c.type = o.optString("type", "");
        c.category = o.optString("category", "GENERAL");
        c.logo = o.optString("logo", "");
        c.ua = o.optString("ua", "");
        c.referer = o.optString("referer", "");
        JSONObject h = o.optJSONObject("headers");
        if (h != null) {
            for (String k : new String[]{"Authorization", "X-Forwarded-For", "Origin", "Referer", "Cookie"}) {
                if (h.has(k)) c.headers.put(k, h.optString(k));
            }
            // Resto de headers genéricos
            java.util.Iterator<String> it = h.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!c.headers.containsKey(k)) c.headers.put(k, h.optString(k));
            }
        }
        JSONObject dk = o.optJSONObject("drm_keys");
        if (dk != null) {
            java.util.Iterator<String> it = dk.keys();
            while (it.hasNext()) {
                String k = it.next();
                c.drmKeys.put(k, dk.optString(k));
            }
        }
        // Compat: algunos canales usan drm_server, otros license
        c.drmServer = o.optString("drm_server", o.optString("license", ""));
        return c;
    }

    public boolean isHls() {
        if ("m3u8".equalsIgnoreCase(type) || "externo".equalsIgnoreCase(type)) return true;
        return url != null && url.toLowerCase().contains(".m3u8");
    }
}
