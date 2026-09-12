package com.zapagenda.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class AiClient {
    public interface Callback { void done(String reply, String error); }
    private static final ExecutorService EXEC = Executors.newCachedThreadPool();

    public static void generate(Context c, String sender, String incoming, Callback cb){
        final String endpoint = BotSettings.endpoint(c);
        final String key = BotSettings.apiKey(c);
        final String model = BotSettings.model(c);
        final String prompt = BotSettings.systemPrompt(c);
        if(endpoint.isEmpty() || key.isEmpty() || model.isEmpty()){
            cb.done(null, "Configure endpoint, modelo e chave da API.");
            return;
        }
        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                if(!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Use um endpoint HTTPS.");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(25000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + key);

                JSONObject body = new JSONObject();
                body.put("model", model);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", prompt));
                String user = "Contato: " + (sender == null ? "desconhecido" : sender) + "\nMensagem recebida: " + incoming + "\nResponda somente com o texto que deve ser enviado no WhatsApp.";
                messages.put(new JSONObject().put("role", "user").put("content", user));
                body.put("messages", messages);
                body.put("temperature", 0.7);
                body.put("max_tokens", 220);

                try(OutputStream os = conn.getOutputStream()){
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String response = read(is);
                if(code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + trim(response, 220));
                JSONObject json = new JSONObject(response);
                String reply = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "").trim();
                if(reply.isEmpty()) throw new IOException("A API retornou uma resposta vazia.");
                cb.done(trim(reply, 1200), null);
            } catch(Exception e){
                cb.done(null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally { if(conn != null) conn.disconnect(); }
        });
    }

    private static String read(InputStream in) throws IOException {
        if(in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while((n=in.read(b))!=-1) out.write(b,0,n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
    private static String trim(String s, int max){ if(s==null)return ""; return s.length()<=max?s:s.substring(0,max); }
}
