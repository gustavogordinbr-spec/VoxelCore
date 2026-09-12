package com.zapagenda.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(9,13,11), SURFACE=Color.rgb(19,27,23), SURFACE2=Color.rgb(27,37,32), TEXT=Color.rgb(241,246,243), MUTED=Color.rgb(155,170,161), GREEN=Color.rgb(37,211,102), DANGER=Color.rgb(255,107,107);
    private LinearLayout root,listBox,logBox; private EditText phone,msg,fixedReply,allowList,cooldown,endpoint,apiKey,model,systemPrompt; private TextView dateText,timeText,status,botStatus; private CheckBox daily,auto,botEnabled,aiMode,ignoreGroups; private Spinner appSpinner; private Calendar selected=Calendar.getInstance();
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} private int parseInt(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}}

    @Override protected void onCreate(Bundle b){super.onCreate(b);NotificationHelper.channel(this);selected.add(Calendar.MINUTE,2);build();loadBot();askNotification();}
    @Override protected void onResume(){super.onResume();refreshStatus();refreshList();refreshLogs();}

    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private TextView label(String s){TextView t=text(s,13,MUTED);t.setPadding(0,dp(14),0,dp(6));return t;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(100,115,107));e.setTextSize(15);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(bg(SURFACE2,14));return e;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.rgb(6,30,17):TEXT);b.setBackground(bg(primary?GREEN:SURFACE2,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(8),0,0);b.setLayoutParams(lp);return b;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(16));c.setBackground(bg(SURFACE,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));c.setLayoutParams(lp);return c;}
    private TextView sectionTitle(String title,String subtitle){TextView t=text(title+"\n"+subtitle,18,TEXT);t.setTypeface(null,Typeface.BOLD);t.setLineSpacing(dp(3),1f);t.setPadding(0,0,0,dp(6));return t;}

    private void build(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(36));root.setBackgroundColor(BG);sv.addView(root);setContentView(sv);
        TextView title=text("ZapAgenda",30,TEXT);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        TextView sub=text("Agendamentos + respostas automáticas inteligentes",14,MUTED);sub.setPadding(0,dp(3),0,dp(14));root.addView(sub);
        status=text("",13,TEXT);status.setPadding(dp(14),dp(11),dp(14),dp(11));status.setBackground(bg(SURFACE,15));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.setMargins(0,0,0,dp(14));root.addView(status,slp);
        buildScheduler();buildBot();buildAi();buildHistory();
    }

    private void buildScheduler(){
        LinearLayout c=card();c.addView(sectionTitle("Agendar mensagem","Escolha contato, horário e se o envio deve ser automático."));
        c.addView(label("Número com DDI e DDD"));phone=input("Ex.: 5531999999999");phone.setInputType(InputType.TYPE_CLASS_PHONE);c.addView(phone);
        c.addView(label("Mensagem"));msg=input("Digite a mensagem");msg.setMinLines(3);msg.setGravity(Gravity.TOP);c.addView(msg);
        c.addView(label("Aplicativo"));appSpinner=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"WhatsApp","WhatsApp Business"});appSpinner.setAdapter(a);c.addView(appSpinner);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);Button bd=button("Data",false),bt=button("Hora",false);row.addView(bd,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,dp(50),1);gap.setMargins(dp(8),0,0,0);row.addView(bt,gap);c.addView(row);
        dateText=text("",13,MUTED);timeText=text("",13,MUTED);dateText.setPadding(0,dp(8),0,0);c.addView(dateText);c.addView(timeText);updateDT();
        bd.setOnClickListener(v->new DatePickerDialog(this,(d,y,m,day)->{selected.set(y,m,day);updateDT();},selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show());
        bt.setOnClickListener(v->new TimePickerDialog(this,(d,h,min)->{selected.set(Calendar.HOUR_OF_DAY,h);selected.set(Calendar.MINUTE,min);selected.set(Calendar.SECOND,0);updateDT();},selected.get(Calendar.HOUR_OF_DAY),selected.get(Calendar.MINUTE),true).show());
        daily=new CheckBox(this);daily.setText("Repetir todos os dias");daily.setTextColor(TEXT);c.addView(daily);
        auto=new CheckBox(this);auto.setText("Enviar automaticamente pela Acessibilidade");auto.setTextColor(TEXT);c.addView(auto);
        Button save=button("Agendar mensagem",true);save.setOnClickListener(v->saveSchedule());c.addView(save);Button test=button("Testar agora",false);test.setOnClickListener(v->testNow());c.addView(test);
        TextView h=text("Próximos agendamentos",16,TEXT);h.setTypeface(null,Typeface.BOLD);h.setPadding(0,dp(18),0,dp(8));c.addView(h);listBox=new LinearLayout(this);listBox.setOrientation(LinearLayout.VERTICAL);c.addView(listBox);root.addView(c);
    }

    private void buildBot(){
        LinearLayout c=card();c.addView(sectionTitle("Auto responder","Responde mensagens recebidas diretamente pela notificação do WhatsApp."));
        botStatus=text("",13,MUTED);botStatus.setPadding(dp(12),dp(10),dp(12),dp(10));botStatus.setBackground(bg(SURFACE2,12));c.addView(botStatus);
        botEnabled=new CheckBox(this);botEnabled.setText("Ativar auto-respostas");botEnabled.setTextColor(TEXT);c.addView(botEnabled);
        aiMode=new CheckBox(this);aiMode.setText("Gerar a resposta usando IA");aiMode.setTextColor(TEXT);c.addView(aiMode);
        ignoreGroups=new CheckBox(this);ignoreGroups.setText("Ignorar grupos");ignoreGroups.setTextColor(TEXT);c.addView(ignoreGroups);
        c.addView(label("Resposta fixa (usada quando IA está desligada)"));fixedReply=input("Ex.: Oi! Vi sua mensagem. Já te respondo.");c.addView(fixedReply);
        c.addView(label("Responder somente a estes contatos (opcional)"));allowList=input("Ex.: Ana, João, Mãe — se vazio, aceita qualquer contato");c.addView(allowList);
        c.addView(label("Intervalo mínimo por contato, em segundos"));cooldown=input("20");cooldown.setInputType(InputType.TYPE_CLASS_NUMBER);c.addView(cooldown);
        Button access=button("Dar acesso às notificações",false);access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));c.addView(access);
        Button acc=button("Configurar Acessibilidade do agendador",false);acc.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));c.addView(acc);
        Button exact=button("Permitir alarmes exatos",false);exact.setOnClickListener(v->AlarmScheduler.requestExactPermission(this));c.addView(exact);
        root.addView(c);
    }

    private void buildAi(){
        LinearLayout c=card();c.addView(sectionTitle("API de IA","Compatível com APIs que aceitam o formato Chat Completions. A chave fica salva somente no aparelho."));
        c.addView(label("Endpoint HTTPS"));endpoint=input("https://.../v1/chat/completions");endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);c.addView(endpoint);
        c.addView(label("Modelo"));model=input("Nome do modelo da sua API");c.addView(model);
        c.addView(label("Chave da API"));apiKey=input("sk-... / chave do provedor");apiKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);c.addView(apiKey);
        c.addView(label("Personalidade / instruções do bot"));systemPrompt=input("Como a IA deve responder");systemPrompt.setMinLines(4);systemPrompt.setGravity(Gravity.TOP);c.addView(systemPrompt);
        Button save=button("Salvar configurações",true);save.setOnClickListener(v->{saveBot();Toast.makeText(this,"Configurações salvas.",Toast.LENGTH_SHORT).show();refreshStatus();});c.addView(save);
        Button test=button("Testar IA",false);test.setOnClickListener(v->testAi());c.addView(test);root.addView(c);
    }

    private void buildHistory(){
        LinearLayout c=card();c.addView(sectionTitle("Histórico do bot","Últimas tentativas e respostas automáticas."));logBox=new LinearLayout(this);logBox.setOrientation(LinearLayout.VERTICAL);c.addView(logBox);Button clear=button("Limpar histórico",false);clear.setOnClickListener(v->{BotSettings.clearLogs(this);refreshLogs();});c.addView(clear);root.addView(c);
    }

    private void loadBot(){botEnabled.setChecked(BotSettings.enabled(this));aiMode.setChecked(BotSettings.aiMode(this));ignoreGroups.setChecked(BotSettings.ignoreGroups(this));fixedReply.setText(BotSettings.fixedReply(this));allowList.setText(BotSettings.allowList(this));cooldown.setText(String.valueOf(BotSettings.cooldown(this)));endpoint.setText(BotSettings.endpoint(this));apiKey.setText(BotSettings.apiKey(this));model.setText(BotSettings.model(this));systemPrompt.setText(BotSettings.systemPrompt(this));}
    private void saveBot(){BotSettings.save(this,botEnabled.isChecked(),aiMode.isChecked(),ignoreGroups.isChecked(),fixedReply.getText().toString(),allowList.getText().toString(),parseInt(cooldown.getText().toString(),20),endpoint.getText().toString(),apiKey.getText().toString(),model.getText().toString(),systemPrompt.getText().toString());}
    private void testAi(){saveBot();String sample=msg.getText().toString().trim();if(sample.isEmpty())sample="Oi, tudo bem?";Toast.makeText(this,"Consultando a API…",Toast.LENGTH_SHORT).show();AiClient.generate(this,"Teste",sample,(reply,error)->runOnUiThread(()->new AlertDialog.Builder(this).setTitle(error==null?"Resposta da IA":"Erro da API").setMessage(error==null?reply:error).setPositiveButton("OK",null).show()));}

    private void updateDT(){SimpleDateFormat d=new SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()),t=new SimpleDateFormat("HH:mm",Locale.getDefault());dateText.setText("Data: "+d.format(selected.getTime()));timeText.setText("Hora: "+t.format(selected.getTime()));}
    private String pkg(){return appSpinner.getSelectedItemPosition()==1?"com.whatsapp.w4b":"com.whatsapp";}
    private void saveSchedule(){String p=WhatsAppUtil.cleanPhone(phone.getText().toString()),m=msg.getText().toString().trim();if(p.length()<10||m.isEmpty()){Toast.makeText(this,"Preencha número e mensagem.",Toast.LENGTH_SHORT).show();return;}long when=selected.getTimeInMillis();if(when<System.currentTimeMillis()+30000){Toast.makeText(this,"Escolha um horário futuro.",Toast.LENGTH_SHORT).show();return;}long id=System.currentTimeMillis();ScheduleStore.Item x=new ScheduleStore.Item(id,p,m,when,daily.isChecked(),auto.isChecked(),pkg());ScheduleStore.add(this,x);boolean ok=AlarmScheduler.schedule(this,x);if(!ok){AlarmScheduler.requestExactPermission(this);Toast.makeText(this,"Salvo. Ative Alarmes e lembretes.",Toast.LENGTH_LONG).show();}else Toast.makeText(this,"Mensagem agendada.",Toast.LENGTH_SHORT).show();refreshList();}
    private void testNow(){String p=WhatsAppUtil.cleanPhone(phone.getText().toString()),m=msg.getText().toString().trim();if(p.length()<10||m.isEmpty()){Toast.makeText(this,"Preencha número e mensagem.",Toast.LENGTH_SHORT).show();return;}ScheduleStore.Item x=new ScheduleStore.Item(System.currentTimeMillis(),p,m,System.currentTimeMillis(),false,auto.isChecked(),pkg());if(x.autoSend)ScheduleStore.markPending(this,x);WhatsAppUtil.launch(this,x);}

    private boolean accessibilityOn(){String e=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return e!=null&&e.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));}
    private boolean notificationAccessOn(){String e=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return e!=null&&e.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));}
    private void refreshStatus(){boolean exact=true;if(Build.VERSION.SDK_INT>=31){AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);exact=am.canScheduleExactAlarms();}boolean acc=accessibilityOn(),notif=notificationAccessOn();status.setText("● Alarmes "+(exact?"OK":"bloqueados")+"    ● Acessibilidade "+(acc?"ON":"OFF")+"    ● Bot "+(notif?"ON":"OFF"));status.setTextColor(exact&&notif?TEXT:MUTED);if(botStatus!=null)botStatus.setText(notif?"Acesso às notificações ativo. O bot pode responder quando o WhatsApp oferecer a ação Responder.":"Acesso às notificações desativado. Ative para o auto-responder funcionar.");}

    private void refreshList(){if(listBox==null)return;listBox.removeAllViews();List<ScheduleStore.Item> l=ScheduleStore.all(this);SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault());if(l.isEmpty()){TextView e=text("Nenhum agendamento.",13,MUTED);listBox.addView(e);return;}Collections.sort(l,(a,b)->Long.compare(a.when,b.when));for(ScheduleStore.Item x:l){LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setBackground(bg(SURFACE2,12));TextView t=text("+"+x.phone+"\n"+f.format(new Date(x.when))+(x.daily?" • diário":"")+(x.autoSend?" • automático":"")+"\n"+x.message,14,TEXT);item.addView(t);Button del=button("Cancelar",false);del.setTextColor(DANGER);del.setOnClickListener(v->{AlarmScheduler.cancel(this,x.id);ScheduleStore.remove(this,x.id);refreshList();});item.addView(del);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));listBox.addView(item,lp);}}
    private void refreshLogs(){if(logBox==null)return;logBox.removeAllViews();List<String> logs=BotSettings.logs(this);if(logs.isEmpty()){logBox.addView(text("Ainda não há respostas automáticas.",13,MUTED));return;}for(String s:logs){TextView t=text(s,13,TEXT);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setBackground(bg(SURFACE2,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));logBox.addView(t,lp);}}
    private void askNotification(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},100);}
}
