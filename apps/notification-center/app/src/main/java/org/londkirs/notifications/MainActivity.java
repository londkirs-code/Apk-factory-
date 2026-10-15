package org.londkirs.notifications;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity {
    EditText titleInput, textInput, idInput, action1Input, action2Input, action3Input, progressInput, imagePathInput, groupInput, soundUriInput, ledColorInput;
    Spinner repeatSpinner, prioritySpinner;
    CheckBox soundBox, vibrateBox, ongoingBox, alertOnceBox, progressBox;
    TextView whenText, scheduledText, statusText;
    Calendar scheduledAt = Calendar.getInstance();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        scheduledAt.add(Calendar.MINUTE, 5);
        buildUi();
        requestNotificationPermission();
        refreshSchedules();
    }

    int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    TextView label(String s) { TextView v=new TextView(this); v.setText(s); v.setTextSize(15); v.setPadding(0,dp(8),0,dp(3)); return v; }
    EditText edit(String hint) { EditText e=new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setTextSize(16); return e; }
    Button button(String text, View.OnClickListener l) { Button b=new Button(this); b.setText(text); b.setOnClickListener(l); return b; }
    void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(14),dp(18),dp(28)); root.setBackgroundColor(Color.rgb(247,249,250));
        scroll.addView(root); setContentView(scroll);
        TextView head=label("מרכז התראות"); head.setTextSize(26); head.setTextColor(Color.rgb(0,105,105)); head.setPadding(0,0,0,dp(8)); root.addView(head);
        TextView desc=label("יצירה, תזמון וניהול התראות מקומיות"); desc.setTextColor(Color.DKGRAY); root.addView(desc);
        root.addView(label("כותרת")); titleInput=edit("למשל: תזכורת"); root.addView(titleInput);
        root.addView(label("תוכן ההתראה")); textInput=edit("מה להציג בהתראה"); root.addView(textInput);
        root.addView(label("מזהה (לעדכון או ביטול)")); idInput=edit("למשל: 100"); idInput.setText("100"); root.addView(idInput);
        root.addView(label("פעולה בכפתור 1 (אופציונלי)")); action1Input=edit("למשל: פתח"); root.addView(action1Input);
        root.addView(label("פעולה בכפתור 2 (אופציונלי)")); action2Input=edit("למשל: בוצע"); root.addView(action2Input);
        root.addView(label("פעולה בכפתור 3 (אופציונלי)")); action3Input=edit("למשל: דחה"); root.addView(action3Input);
        root.addView(label("תמונה גדולה — נתיב מקומי (אופציונלי)")); imagePathInput=edit("/storage/emulated/0/Pictures/image.jpg"); root.addView(imagePathInput);
        root.addView(label("קבוצת התראות (אופציונלי)")); groupInput=edit("למשל: reminders"); root.addView(groupInput);
        root.addView(label("צליל מותאם אישית — URI (אופציונלי)")); soundUriInput=edit("content://... או file://..."); root.addView(soundUriInput);
        root.addView(label("צבע נורית LED — למשל #00FFAA (אופציונלי)")); ledColorInput=edit("#00FFFF"); root.addView(ledColorInput);
        root.addView(label("סוג התראה"));
        progressBox=new CheckBox(this); progressBox.setText("התראת התקדמות"); root.addView(progressBox);
        progressInput=edit("התקדמות 0–100"); progressInput.setText("50"); root.addView(progressInput);
        root.addView(label("חשיבות"));
        prioritySpinner=new Spinner(this); prioritySpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"רגילה","נמוכה","גבוהה","דחופה"})); root.addView(prioritySpinner);
        root.addView(label("חזרה"));
        repeatSpinner=new Spinner(this); repeatSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"ללא חזרה","כל דקה","כל 5 דקות","כל שעה","כל יום","כל שבוע"})); root.addView(repeatSpinner);
        whenText=label(""); root.addView(whenText); updateWhenText();
        root.addView(button("בחירת תאריך ושעה",v->chooseDateTime()));
        soundBox=new CheckBox(this); soundBox.setText("השמעת צליל"); soundBox.setChecked(true); root.addView(soundBox);
        vibrateBox=new CheckBox(this); vibrateBox.setText("רטט"); vibrateBox.setChecked(true); root.addView(vibrateBox);
        ongoingBox=new CheckBox(this); ongoingBox.setText("התראה מתמשכת (לא ניתנת להחלקה)"); root.addView(ongoingBox);
        alertOnceBox=new CheckBox(this); alertOnceBox.setText("התרעה פעם אחת בלבד בעדכון"); root.addView(alertOnceBox);
        root.addView(button("שלח התראה עכשיו",v->sendNow()));
        root.addView(button("תזמן התראה",v->schedule()));
        root.addView(button("ביטול התראה לפי מזהה",v->cancelById()));
        root.addView(button("ביטול כל ההתראות המתוזמנות",v->cancelAll()));
        root.addView(button("הגדרות ערוץ ההתראות (צליל/חשיבות/נורית)",v->openChannelSettings()));
        statusText=label(""); statusText.setTextColor(Color.rgb(0,105,105)); root.addView(statusText);
        root.addView(label("התראות מתוזמנות")); scheduledText=label(""); root.addView(scheduledText);
        root.addView(button("רענון הרשימה",v->refreshSchedules()));
        root.addView(button("הצגת התראות פעילות במכשיר",v->showActiveNotifications()));
        root.addView(button("הרשאות התראות מדויקות",v->requestExactAlarmPermission()));
    }

    void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
    }
    void chooseDateTime() {
        DatePickerDialog d=new DatePickerDialog(this,(view,y,m,day)->{
            scheduledAt.set(Calendar.YEAR,y); scheduledAt.set(Calendar.MONTH,m); scheduledAt.set(Calendar.DAY_OF_MONTH,day);
            new TimePickerDialog(this,(tv,h,min)->{scheduledAt.set(Calendar.HOUR_OF_DAY,h);scheduledAt.set(Calendar.MINUTE,min);scheduledAt.set(Calendar.SECOND,0);updateWhenText();},scheduledAt.get(Calendar.HOUR_OF_DAY),scheduledAt.get(Calendar.MINUTE),true).show();
        },scheduledAt.get(Calendar.YEAR),scheduledAt.get(Calendar.MONTH),scheduledAt.get(Calendar.DAY_OF_MONTH)); d.show();
    }
    void updateWhenText() { if(whenText!=null) whenText.setText("מועד: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(scheduledAt.getTime())); }
    int repeatMillis() { switch(repeatSpinner.getSelectedItemPosition()){case 1:return 60000;case 2:return 300000;case 3:return 3600000;case 4:return 86400000;case 5:return 604800000;default:return 0;} }
    int priority() { switch(prioritySpinner.getSelectedItemPosition()){case 1:return -1;case 2:return 1;case 3:return 2;default:return 0;} }
    JSONObject makePayload() throws Exception {
        String title=titleInput.getText().toString().trim(), text=textInput.getText().toString().trim();
        if(title.isEmpty()) title="התראה"; if(text.isEmpty()) text=" ";
        JSONObject j=new JSONObject(); j.put("title",title); j.put("text",text); j.put("id",idInput.getText().toString().trim().isEmpty()?100:Integer.parseInt(idInput.getText().toString().trim()));
        j.put("action1",action1Input.getText().toString().trim()); j.put("action2",action2Input.getText().toString().trim()); j.put("action3",action3Input.getText().toString().trim());
        j.put("imagePath",imagePathInput.getText().toString().trim()); j.put("group",groupInput.getText().toString().trim()); j.put("soundUri",soundUriInput.getText().toString().trim()); j.put("ledColor",ledColorInput.getText().toString().trim());
        j.put("sound",soundBox.isChecked()); j.put("vibrate",vibrateBox.isChecked()); j.put("ongoing",ongoingBox.isChecked()); j.put("alertOnce",alertOnceBox.isChecked());
        j.put("priority",priority()); j.put("progress",progressBox.isChecked()); j.put("progressValue",Math.max(0,Math.min(100,Integer.parseInt(progressInput.getText().toString().trim().isEmpty()?"0":progressInput.getText().toString().trim()))));
        return j;
    }
    void sendNow() {
        try { JSONObject j=makePayload(); Intent i=new Intent(this,NotificationReceiver.class); i.setAction(NotificationReceiver.ACTION_SHOW); i.putExtra("payload",j.toString()); sendBroadcast(i); statusText.setText("ההתראה נשלחה לביצוע."); }
        catch(Exception e){ statusText.setText("בדקי את המזהה ואת אחוז ההתקדמות."); }
    }
    void schedule() {
        try {
            long at=scheduledAt.getTimeInMillis(); if(at<=System.currentTimeMillis()){statusText.setText("בחרי מועד עתידי.");return;}
            JSONObject j=makePayload(); j.put("at",at); j.put("repeat",repeatMillis()); j.put("uid",System.currentTimeMillis()+"_"+j.getInt("id"));
            JSONArray all=NotificationReceiver.readSchedules(this); all.put(j); NotificationReceiver.writeSchedules(this,all);
            NotificationReceiver.schedule(this,j);
            statusText.setText("ההתראה תוזמנה."); refreshSchedules();
        } catch(Exception e){statusText.setText("לא ניתן לתזמן. בדקי את השדות והרשאות ההתראות.");}
    }
    void cancelById() {
        String raw=idInput.getText().toString().trim(); try { int id=Integer.parseInt(raw); JSONArray old=NotificationReceiver.readSchedules(this), keep=new JSONArray(); int removed=0;
            for(int n=0;n<old.length();n++){JSONObject j=old.getJSONObject(n); if(j.optInt("id")==id){NotificationReceiver.cancel(this,j);removed++;}else keep.put(j);}
            NotificationReceiver.writeSchedules(this,keep); NotificationReceiver.cancelDisplayed(this,id); statusText.setText("בוטלו "+removed+" תזמונים וההתראה הפעילה לפי המזהה."); refreshSchedules();
        }catch(Exception e){statusText.setText("מזהה לא תקין.");}
    }
    void cancelAll() { JSONArray all=NotificationReceiver.readSchedules(this); for(int n=0;n<all.length();n++) NotificationReceiver.cancel(this,all.optJSONObject(n)); NotificationReceiver.writeSchedules(this,new JSONArray()); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll(); statusText.setText("כל התזמונים וההתראות הפעילות בוטלו."); refreshSchedules(); }
    void refreshSchedules() {
        if(scheduledText==null)return; JSONArray all=NotificationReceiver.readSchedules(this); StringBuilder s=new StringBuilder();
        for(int n=0;n<all.length();n++){JSONObject j=all.optJSONObject(n); if(j==null)continue; s.append("• ").append(j.optString("title")).append(" (ID ").append(j.optInt("id")).append(")\n").append(DateFormat.getDateTimeInstance().format(new Date(j.optLong("at"))));
            if(j.optLong("repeat")>0)s.append(" · חוזרת"); s.append("\n\n");}
        scheduledText.setText(s.length()==0?"אין התראות מתוזמנות":s.toString());
    }
    void showActiveNotifications() {
        StringBuilder out=new StringBuilder();
        if(Build.VERSION.SDK_INT>=23){ StatusBarNotification[] active=((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).getActiveNotifications(); for(StatusBarNotification n:active){out.append("• ID ").append(n.getId()).append(" — ").append(n.getPackageName()).append("\n");} }
        new AlertDialog.Builder(this).setTitle("התראות פעילות").setMessage(out.length()==0?"אין התראות פעילות":out.toString()).setPositiveButton("סגירה",null).show();
    }
    void openChannelSettings() {
        if(Build.VERSION.SDK_INT>=26){Intent i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());i.putExtra(Settings.EXTRA_CHANNEL_ID,NotificationReceiver.CHANNEL);startActivity(i);}
        else {Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));startActivity(i);}
    }
    void requestExactAlarmPermission() {
        if(Build.VERSION.SDK_INT>=31){try{Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));startActivity(i);}catch(Exception e){statusText.setText("פתחי הגדרות אפליקציה והרשי התראות מדויקות.");}}
        else statusText.setText("בגרסת Android זו אין צורך בהרשאה נפרדת.");
    }
}
