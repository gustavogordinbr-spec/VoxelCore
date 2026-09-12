package com.zapagenda.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public final class BotSettings {
    private static final String PREFS = "zapagenda_bot";
    private static SharedPreferences p(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean enabled(Context c){ return p(c).getBoolean("enabled", false); }
    public static boolean aiMode(Context c){ return p(c).getBoolean("ai_mode", false); }
    public static boolean ignoreGroups(Context c){ return p(c).getBoolean("ignore_groups", true); }
    public static String fixedReply(Context c){ return p(c).getString("fixed_reply", "Oi! Vi sua mensagem. Assim que puder eu respondo."); }
    public static String allowList(Context c){ return p(c).getString("allow_list", ""); }
    public static int cooldown(Context c){ return Math.max(3, p(c).getInt("cooldown", 20)); }
    public static String endpoint(Context c){ return p(c).getString("endpoint", ""); }
    public static String apiKey(Context c){ return p(c).getString("api_key", ""); }
    public static String model(Context c){ return p(c).getString("model", ""); }
    public static String systemPrompt(Context c){ return p(c).getString("system_prompt", "Você responde mensagens de WhatsApp em português do Brasil. Seja natural, breve e educado. Não invente informações pessoais e não prometa coisas que não sabe."); }

    public static void save(Context c, boolean enabled, boolean aiMode, boolean ignoreGroups,
                            String fixedReply, String allowList, int cooldown,
                            String endpoint, String apiKey, String model, String systemPrompt){
        p(c).edit()
                .putBoolean("enabled", enabled)
                .putBoolean("ai_mode", aiMode)
                .putBoolean("ignore_groups", ignoreGroups)
                .putString("fixed_reply", fixedReply == null ? "" : fixedReply.trim())
                .putString("allow_list", allowList == null ? "" : allowList.trim())
                .putInt("cooldown", Math.max(3, cooldown))
                .putString("endpoint", endpoint == null ? "" : endpoint.trim())
                .putString("api_key", apiKey == null ? "" : apiKey.trim())
                .putString("model", model == null ? "" : model.trim())
                .putString("system_prompt", systemPrompt == null ? "" : systemPrompt.trim())
                .apply();
    }

    public static boolean senderAllowed(Context c, String sender){
        String raw = allowList(c).trim();
        if(raw.isEmpty()) return true;
        String who = sender == null ? "" : sender.toLowerCase(Locale.ROOT);
        for(String piece : raw.split(",")){
            String q = piece.trim().toLowerCase(Locale.ROOT);
            if(!q.isEmpty() && who.contains(q)) return true;
        }
        return false;
    }

    public static boolean canReplyNow(Context c, String sender){
        String key = "cool_" + Integer.toHexString((sender == null ? "" : sender.toLowerCase(Locale.ROOT)).hashCode());
        long last = p(c).getLong(key, 0);
        return System.currentTimeMillis() - last >= cooldown(c) * 1000L;
    }

    public static void markReply(Context c, String sender){
        String key = "cool_" + Integer.toHexString((sender == null ? "" : sender.toLowerCase(Locale.ROOT)).hashCode());
        p(c).edit().putLong(key, System.currentTimeMillis()).apply();
    }

    public static synchronized void addLog(Context c, String sender, String incoming, String reply, String state){
        try {
            JSONArray old = new JSONArray(p(c).getString("logs", "[]"));
            JSONArray out = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("time", System.currentTimeMillis());
            item.put("sender", sender == null ? "" : sender);
            item.put("incoming", incoming == null ? "" : incoming);
            item.put("reply", reply == null ? "" : reply);
            item.put("state", state == null ? "" : state);
            out.put(item);
            for(int i=0;i<old.length() && i<19;i++) out.put(old.getJSONObject(i));
            p(c).edit().putString("logs", out.toString()).apply();
        } catch(Exception ignored){}
    }

    public static List<String> logs(Context c){
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("logs", "[]"));
            SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            for(int i=0;i<a.length();i++){
                JSONObject o = a.getJSONObject(i);
                String s = f.format(new Date(o.optLong("time"))) + " • " + o.optString("sender", "") + " • " + o.optString("state", "");
                String r = o.optString("reply", "");
                if(!r.isEmpty()) s += "\n↳ " + r;
                out.add(s);
            }
        } catch(Exception ignored){}
        return out;
    }

    public static void clearLogs(Context c){ p(c).edit().remove("logs").apply(); }
}
