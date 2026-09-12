package com.zapagenda.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

public class WhatsAppReplyService extends NotificationListenerService {
    private final Map<String, String> lastSeen = new HashMap<>();

    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(!BotSettings.enabled(this) || sbn == null) return;
        String pkg = sbn.getPackageName();
        if(!"com.whatsapp".equals(pkg) && !"com.whatsapp.w4b".equals(pkg)) return;
        Notification n = sbn.getNotification();
        if(n == null || n.extras == null) return;

        Parsed p = parse(n);
        if(p.text.isEmpty()) return;
        if(BotSettings.ignoreGroups(this) && p.group) return;
        if(!BotSettings.senderAllowed(this, p.sender)) return;
        if(!BotSettings.canReplyNow(this, p.sender)) return;

        String signature = p.sender + "\n" + p.text;
        String prior = lastSeen.get(sbn.getKey());
        if(signature.equals(prior)) return;
        lastSeen.put(sbn.getKey(), signature);

        Notification.Action replyAction = findReplyAction(n);
        if(replyAction == null){
            BotSettings.addLog(this, p.sender, p.text, "", "sem ação Responder");
            return;
        }

        if(BotSettings.aiMode(this)){
            AiClient.generate(getApplicationContext(), p.sender, p.text, (reply,error) -> {
                if(error != null){ BotSettings.addLog(getApplicationContext(), p.sender, p.text, "", "IA: " + error); return; }
                doReply(replyAction, reply, p);
            });
        } else {
            String reply = BotSettings.fixedReply(this).trim();
            if(!reply.isEmpty()) doReply(replyAction, reply, p);
        }
    }

    private void doReply(Notification.Action action, String reply, Parsed p){
        try {
            RemoteInput[] inputs = action.getRemoteInputs();
            if(inputs == null || inputs.length == 0) throw new IllegalStateException("Sem RemoteInput");
            Intent fillIn = new Intent();
            Bundle results = new Bundle();
            for(RemoteInput input : inputs) results.putCharSequence(input.getResultKey(), reply);
            RemoteInput.addResultsToIntent(inputs, fillIn, results);
            action.actionIntent.send(this, 0, fillIn);
            BotSettings.markReply(this, p.sender);
            BotSettings.addLog(this, p.sender, p.text, reply, "enviado");
        } catch(PendingIntent.CanceledException | RuntimeException e){
            BotSettings.addLog(this, p.sender, p.text, reply, "erro: " + e.getClass().getSimpleName());
        }
    }

    private Notification.Action findReplyAction(Notification n){
        if(n.actions == null) return null;
        Notification.Action fallback = null;
        for(Notification.Action a : n.actions){
            if(a == null || a.actionIntent == null || a.getRemoteInputs() == null || a.getRemoteInputs().length == 0) continue;
            if(Build.VERSION.SDK_INT >= 28 && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_REPLY) return a;
            String title = a.title == null ? "" : a.title.toString().toLowerCase(Locale.ROOT);
            if(title.contains("responder") || title.contains("reply")) return a;
            fallback = a;
        }
        return fallback;
    }

    private Parsed parse(Notification n){
        Bundle e = n.extras;
        String title = chars(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = chars(e.getCharSequence(Notification.EXTRA_TEXT));
        String conversation = chars(e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        boolean group = !conversation.isEmpty();
        String sender = title;
        try {
            android.os.Parcelable[] bundles = e.getParcelableArray(Notification.EXTRA_MESSAGES);
            if(bundles != null && bundles.length > 0){
                List<Notification.MessagingStyle.Message> msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
                if(!msgs.isEmpty()){
                    Notification.MessagingStyle.Message m = msgs.get(msgs.size()-1);
                    if(m.getText()!=null) text = m.getText().toString();
                    if(m.getSenderPerson()!=null && m.getSenderPerson().getName()!=null) sender = m.getSenderPerson().getName().toString();
                }
            }
        } catch(Exception ignored){}
        if(sender.isEmpty()) sender = conversation.isEmpty() ? "Contato" : conversation;
        return new Parsed(sender, text, group);
    }

    private String chars(CharSequence c){ return c == null ? "" : c.toString().trim(); }
    private static class Parsed { final String sender,text; final boolean group; Parsed(String s,String t,boolean g){sender=s;text=t;group=g;} }
}
