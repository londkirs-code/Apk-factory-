package org.mishnatyosef.mobile;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StockMonitor extends BroadcastReceiver {
    public static final String WATCHES = "stock_watch_list";
    public static final String LOG = "stock_monitor_log";
    public static final String ENABLED = "stock_monitor_enabled";
    public static final String INTERVAL = "stock_monitor_interval_minutes";
    private static final String PREFS = "mishnat_auth";
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String CHECK = "org.mishnatyosef.mobile.CHECK_STOCK_WATCHES";
    private static final int ALARM_ID = 93271;
    private static final String ALERT_CHANNEL = "stock_return_alerts";
    private static final String ORDER_CHANNEL = "stock_auto_order_results";
    private static final Object LOCK = new Object();
    private static final Map<Integer, Object> SALE_ORDER_LOCKS = new HashMap<>();
    private static Object saleOrderLock(int saleId) { synchronized (LOCK) { Object lock = SALE_ORDER_LOCKS.get(saleId); if (lock == null) { lock = new Object(); SALE_ORDER_LOCKS.put(saleId, lock); } return lock; } }

    private static final class Response {
        final int code; final String body;
        Response(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) { scheduleNextCheck(context); return; }
        if (!CHECK.equals(action)) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try { checkAll(context.getApplicationContext()); }
            catch (Exception e) { appendLog(context, "", "", "בדיקת מלאי נכשלה", safeMessage(e)); }
            finally { scheduleNextCheck(context); pending.finish(); }
        }, "mishnat-stock-monitor").start();
    }

    public static JSONArray loadWatches(Context c) {
        synchronized (LOCK) {
            try { return new JSONArray(c.getSharedPreferences(PREFS, 0).getString(WATCHES, "[]")); }
            catch (Exception e) { return new JSONArray(); }
        }
    }
    public static void saveWatches(Context c, JSONArray watches) {
        synchronized (LOCK) { c.getSharedPreferences(PREFS, 0).edit().putString(WATCHES, watches == null ? "[]" : watches.toString()).apply(); }
        scheduleNextCheck(c);
    }
    public static JSONArray loadLog(Context c) {
        synchronized (LOCK) {
            try { return new JSONArray(c.getSharedPreferences(PREFS, 0).getString(LOG, "[]")); }
            catch (Exception e) { return new JSONArray(); }
        }
    }
    public static void appendLog(Context c, String key, String product, String event, String detail) {
        synchronized (LOCK) {
            SharedPreferences p = c.getSharedPreferences(PREFS, 0);
            JSONArray old;
            try { old = new JSONArray(p.getString(LOG, "[]")); } catch (Exception e) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            try { next.put(new JSONObject().put("time", System.currentTimeMillis()).put("key", safe(key))
                    .put("product", safe(product)).put("event", safe(event)).put("detail", safe(detail))); }
            catch (Exception ignored) { }
            for (int i = Math.max(0, old.length() - 199); i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i); if (item != null) next.put(item);
            }
            p.edit().putString(LOG, next.toString()).apply();
        }
    }
    public static String keyFor(int saleId, String sid) { return saleId + ":" + safe(sid); }

    public static void scheduleNextCheck(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        boolean globalAlerts = p.getBoolean("stock_global_notify_out", false) || p.getBoolean("stock_global_notify_return", false);
        if (!p.getBoolean(ENABLED, true) || (loadWatches(c).length() == 0 && !globalAlerts)) { cancelCheck(c); return; }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, StockMonitor.class).setAction(CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_ID, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + Math.max(1, Math.min(240, p.getInt(INTERVAL, 5))) * 60000L;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
    }
    private static void cancelCheck(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, StockMonitor.class).setAction(CHECK);
        PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_ID, i, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    private void checkAll(Context c) throws Exception {
        SharedPreferences prefs = c.getSharedPreferences(PREFS, 0);
        if (prefs.getString("token", "").isEmpty()) { appendLog(c, "", "", "ממתין להתחברות", "אין התחברות שמורה; לא שונה מצב מלאי."); return; }
        JSONArray sales = getSales(c);
        if (sales == null) { appendLog(c, "", "", "בדיקת מלאי נדחתה", "רשימת המכירות לא נטענה באופן תקין."); return; }
        JSONArray watches = loadWatches(c);
        boolean globalOut = prefs.getBoolean("stock_global_notify_out", false);
        boolean globalReturn = prefs.getBoolean("stock_global_notify_return", false);
        if (watches.length() == 0 && !globalOut && !globalReturn) return;
        Map<String, JSONObject> productsByKey = new HashMap<>();
        Map<Integer, JSONObject> salesById = new HashMap<>();
        for (int i=0; i<sales.length(); i++) {
            JSONObject sale=sales.optJSONObject(i); if(sale==null)continue;
            int saleId=sale.optInt("id",-1); if(saleId<=0)continue;
            salesById.put(saleId,sale);
            long orderId=orderIdFromSale(sale);
            try {
                JSONArray rows=fetchProducts(c,orderId>0?orderId:saleId);
                if(orderId>0) {
                    JSONArray fallback=fetchProducts(c,saleId);
                    Map<String,JSONObject> merged=new LinkedHashMap<>();
                    for(int j=0;j<rows.length();j++){JSONObject p=rows.optJSONObject(j);if(p!=null)merged.put(sidOf(p),p);}
                    for(int j=0;j<fallback.length();j++){JSONObject p=fallback.optJSONObject(j);if(p!=null)merged.putIfAbsent(sidOf(p),p);}
                    rows=new JSONArray();for(JSONObject p:merged.values())rows.put(p);
                }
                for(int j=0;j<rows.length();j++){JSONObject p=rows.optJSONObject(j);if(p!=null){String sid=sidOf(p);if(!sid.isEmpty())productsByKey.put(keyFor(saleId,sid),p);}}
            } catch(Exception e) {
                appendLog(c,"","","בדיקת מלאי נדחתה","לא ניתן לטעון את כל המוצרים של מכירה "+saleId+"; מצב המלאי לא עודכן. "+safeMessage(e)); return;
            }
        }
        for (JSONObject product : productsByKey.values()) {
            if (availability(product) < 0) { appendLog(c, "", first(product, "full_name", "name"), "בדיקת מלאי נדחתה", "חסר שדה זמינות תקין; מצב המלאי לא עודכן."); return; }
        }
        JSONObject oldGlobal;
        try{oldGlobal=new JSONObject(prefs.getString("stock_global_state","{}"));}catch(Exception e){oldGlobal=new JSONObject();}
        JSONObject nextGlobal=new JSONObject();
        for(Map.Entry<String,JSONObject> entry:productsByKey.entrySet()){
            String key=entry.getKey();JSONObject product=entry.getValue();int now=availability(product);
            if(now<0){appendLog(c,key,first(product,"full_name","name"),"בדיקת מלאי נדחתה","חסר שדה זמינות תקין; מצב המלאי לא עודכן.");return;}
            JSONObject old=oldGlobal.optJSONObject(key);int before=old==null?-1:old.optInt("availability",-1);
            int colon=key.indexOf(':');int saleId=-1;try{saleId=Integer.parseInt(colon<0?"":key.substring(0,colon));}catch(Exception ignored){}
            JSONObject sale=salesById.get(saleId);String saleName=sale==null?"":first(sale,"name","title","full_name");
            String name=first(product,"full_name","name");
            try{nextGlobal.put(key,new JSONObject().put("availability",now).put("name",name).put("saleName",saleName).put("checkedAt",System.currentTimeMillis()));}catch(Exception ignored){}
            if(before>=0&&before!=now){
                if(now==0&&globalOut){notifyUser(c,"global:"+key,"מוצר אזל מהמלאי",name+(saleName.isEmpty()?"":" · "+saleName)+" · המוצר אינו זמין.",false);appendLog(c,key,name,"התראה גלובלית: אזל מהמלאי",saleName);}
                else if(now==1&&globalReturn){notifyUser(c,"global:"+key,"מוצר חזר למלאי",name+(saleName.isEmpty()?"":" · "+saleName)+" · המוצר זמין שוב.",false);appendLog(c,key,name,"התראה גלובלית: חזר למלאי",saleName);}
            }
        }
        prefs.edit().putString("stock_global_state",nextGlobal.toString()).apply();
        List<Thread> attempts=new ArrayList<>();
        for(int i=0;i<watches.length();i++){
            JSONObject w=watches.optJSONObject(i);if(w==null)continue;
            String key=w.optString("key",""),name=w.optString("name","מוצר"),sid=w.optString("sid","");
            int saleId=w.optInt("saleId",-1);JSONObject sale=salesById.get(saleId);
            if(sale==null){appendLog(c,key,name,"המכירה אינה פתוחה","לא נשלחה הזמנה; המעקב נשמר.");continue;}
            JSONObject product=productsByKey.get(keyFor(saleId,sid));
            if(product==null){appendLog(c,key,name,"המוצר לא נמצא","לא שונה מצב המעקב.");continue;}
            int now=availability(product);if(now<0){appendLog(c,key,name,"זמינות לא ידועה","לא שונה מצב המעקב.");continue;}
            int before=w.optInt("lastAvailability",-1);
            updateWatch(c,key,new JSONObject().put("lastAvailability",now).put("lastCheck",System.currentTimeMillis()));
            appendLog(c,key,name,"בדיקת מלאי",now==1?"זמין":"אזל או אינו זמין");
            if(before==1&&now==0&&prefs.getBoolean("stock_notify_out",true)){notifyUser(c,key,"המוצר אזל מהמלאי",name+" אזל במכירה "+w.optString("saleName","")+".",false);appendLog(c,key,name,"נשלחה התראת אזילה","המוצר עבר מזמין ללא זמין.");}
            if(now!=1||before!=0)continue;
            if(!"auto".equals(w.optString("mode","alert"))){
                if(prefs.getBoolean("stock_notify_return",true)){notifyUser(c,key,"המוצר חזר למלאי",name+" זמין שוב במכירה "+w.optString("saleName","")+".",false);appendLog(c,key,name,"נשלחה התראת חזרה למלאי","זמינות אושרה בתשובת שרת עדכנית.");}
                else appendLog(c,key,name,"התראת חזרה למלאי כבויה","המעקב זיהה שהמוצר זמין, אך ההתראה כבויה.");
            }else if("waiting".equals(w.optString("state","waiting"))&&w.optBoolean("consent",false)){
                updateWatch(c,key,new JSONObject().put("state","attempting").put("attemptStartedAt",System.currentTimeMillis()));
                appendLog(c,key,name,"מתחיל ניסיון הזמנה אוטומטית","כמות מבוקשת: "+Math.max(1,w.optInt("qty",1)));
                JSONObject sw=new JSONObject(w.toString()),ss=new JSONObject(sale.toString()),sp=new JSONObject(product.toString());
                Thread t=new Thread(()->performAutoOrder(c,sw,ss,sp),"mishnat-auto-order-"+saleId+"-"+sid);attempts.add(t);t.start();
            }
        }
        for(Thread t:attempts)try{t.join();}catch(InterruptedException e){Thread.currentThread().interrupt();appendLog(c,"","","בדיקת רקע הופסקה","לא הושלמו כל ניסיונות ההזמנה.");break;}
    }

    private void performAutoOrder(Context c, JSONObject watch, JSONObject oldSale, JSONObject oldProduct) {
        synchronized (saleOrderLock(watch.optInt("saleId", -1))) { performAutoOrderLocked(c, watch, oldSale, oldProduct); }
    }

    private void performAutoOrderLocked(Context c, JSONObject watch, JSONObject oldSale, JSONObject oldProduct) {
        String key = watch.optString("key", ""), name = watch.optString("name", "מוצר");
        JSONObject active = findWatch(c, key);
        if (active == null || !"auto".equals(active.optString("mode", "")) || !active.optBoolean("consent", false) || !"attempting".equals(active.optString("state", ""))) {
            appendLog(c, key, name, "ניסיון ההזמנה בוטל", "המעקב הוסר או שונה לפני ביצוע הבקשה."); return;
        }
        int saleId = watch.optInt("saleId", -1), qty = Math.max(1, watch.optInt("qty", 1));
        String sid = watch.optString("sid", "");
        String state = "uncertain", detail = "לא ניתן לאמת את תוצאת ההזמנה.";
        long orderId = -1;
        try {
            JSONArray sales = getSales(c);
            JSONObject sale = sales == null ? null : findSale(sales, saleId);
            if (sale == null) { state = "failed"; detail = "המכירה כבר אינה פתוחה."; }
            else {
                long knownOrder = orderIdFromSale(sale);
                JSONArray rows = fetchProducts(c, knownOrder > 0 ? knownOrder : saleId);
                JSONObject fresh = findProduct(rows, sid);
                if (fresh == null && knownOrder > 0) fresh = findProduct(fetchProducts(c, saleId), sid);
                if (fresh == null) { state = "failed"; detail = "המוצר לא נמצא בנתוני השרת."; }
                else if (availability(fresh) != 1) {
                    updateWatch(c, key, new JSONObject().put("state", "waiting").put("lastAvailability", availability(fresh)));
                    appendLog(c, key, name, "ההזמנה לא נשלחה", "המוצר כבר אינו זמין.");
                    return;
                } else if (maxPerMan(fresh) > 0 && qty > maxPerMan(fresh)) {
                    state = "failed"; detail = "הכמות המבוקשת (" + qty + ") חורגת מהמגבלה למוצר (" + maxPerMan(fresh) + "). לא נשלחה הזמנה.";
                } else {
                    orderId = findExistingOrder(c, sale, saleId);
                    if (orderId <= 0) {
                        try { orderId = createOrder(c, saleId); }
                        catch (Exception e) {
                            long found = findExistingOrder(c, sale, saleId);
                            if (found > 0) orderId = found;
                            else {
                                state = e instanceof java.io.IOException ? "uncertain" : "failed";
                                detail = e instanceof java.io.IOException
                                        ? "התקשורת נקטעה בפתיחת ההזמנה; לא תישלח בקשה חוזרת אוטומטית."
                                        : safeMessage(e);
                                finishOrder(c, watch, state, detail, orderId); return;
                            }
                        }
                    }
                    JSONObject item = findProduct(fetchProducts(c, orderId), sid);
                    if (item == null) item = fresh;
                    int current = (int) Math.round(number(item.opt("amount")));
                    active = findWatch(c, key);
                    if (active == null || !"auto".equals(active.optString("mode", "")) || !active.optBoolean("consent", false) || !"attempting".equals(active.optString("state", ""))) {
                        appendLog(c, key, name, "ניסיון ההזמנה בוטל לפני שליחה", "המעקב הוסר או שונה לפני בקשת ההזמנה."); return;
                    }
                    JSONObject body = new JSONObject(item.toString()).put("amount", qty);
                    if (!body.has("selected_subitems") || body.isNull("selected_subitems")) body.put("selected_subitems", new JSONArray());
                    Response mutation = null; Exception transport = null;
                    try { mutation = request(c, "POST", (current > 0 ? "/api/update_item/" : "/api/add_item/") + orderId, body); }
                    catch (Exception e) { transport = e; }
                    if (verifyQuantity(c, orderId, sid, qty)) {
                        state = "success"; detail = "אומת בהזמנה: הכמות " + qty + ".";
                    } else if (transport != null || mutation == null || mutation.code >= 500 || mutation.code == 429) {
                        state = "uncertain"; detail = "לא ניתן לאמת אם השרת קיבל את הבקשה. לא יישלח ניסיון חוזר אוטומטי.";
                    } else if (mutation.code < 200 || mutation.code >= 300) {
                        state = "failed"; detail = "השרת דחה את הבקשה (" + mutation.code + "): " + apiError(mutation.body, mutation.code);
                    } else {
                        state = "uncertain"; detail = "השרת השיב, אך הכמות לא אומתה בהזמנה.";
                    }
                }
            }
        } catch (Exception e) {
            state = e instanceof java.io.IOException ? "uncertain" : "failed";
            detail = state.equals("uncertain") ? "התקשורת נקטעה; לא יישלח ניסיון חוזר אוטומטי." : safeMessage(e);
        }
        finishOrder(c, watch, state, detail, orderId);
    }

    private void finishOrder(Context c, JSONObject w, String state, String detail, long orderId) {
        String key = w.optString("key", ""), name = w.optString("name", "מוצר");
        String sale = w.optString("saleName", "המכירה"); int qty = Math.max(1, w.optInt("qty", 1));
        try { updateWatch(c, key, new JSONObject().put("state", state).put("lastAttemptAt", System.currentTimeMillis())
                .put("lastResult", detail).put("lastOrderId", orderId)); } catch (Exception ignored) { }
        appendLog(c, key, name, "תוצאת הזמנה: " + state, detail);
        String title = "success".equals(state) ? "✔ ההזמנה האוטומטית אומתה"
                : ("uncertain".equals(state) ? "⚠ תוצאת ההזמנה אינה ודאית" : "✖ ההזמנה האוטומטית נכשלה");
        String when = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(System.currentTimeMillis()));
        notifyUser(c, key, title, name + " · " + sale + " · כמות " + qty + " · " + when + "\n" + detail, true);
    }

    private static long createOrder(Context c, int saleId) throws Exception {
        Response cr = request(c, "GET", "/api/payment_methods/cards", null);
        if (cr.code < 200 || cr.code >= 300) throw new Exception("לא ניתן לטעון אמצעי תשלום (" + cr.code + ").");
        JSONArray cards = arrayFrom(cr.body); long cardId = -1;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.optJSONObject(i);
            if (card != null && (truthy(card.opt("is_default")) || truthy(card.opt("default")))) {
                cardId = card.optLong("id", card.optLong("card_id", -1)); if (cardId > 0) break;
            }
        }
        if (cardId <= 0 && cards.length() > 0) {
            JSONObject card = cards.optJSONObject(0); if (card != null) cardId = card.optLong("id", card.optLong("card_id", -1));
        }
        if (cardId <= 0) throw new Exception("לא נמצא כרטיס תשלום שמור.");
        Response created = request(c, "POST", "/api/create_order/" + saleId, new JSONObject().put("card_id", cardId));
        if (created.code < 200 || created.code >= 300) {
            if (created.code >= 500 || created.code == 429) throw new java.io.IOException("תוצאת פתיחת ההזמנה אינה ודאית.");
            throw new Exception("פתיחת ההזמנה נדחתה: " + apiError(created.body, created.code));
        }
        long id = extractOrderId(new JSONObject(created.body));
        if (id > 0) return id;
        throw new java.io.IOException("השרת לא החזיר מזהה להזמנה.");
    }

    private static long findExistingOrder(Context c, JSONObject sale, int saleId) throws Exception {
        long nested = orderIdFromSale(sale); if (nested > 0) return nested;
        Response r = request(c, "GET", "/api/orders", null);
        if (r.code < 200 || r.code >= 300) throw new Exception("לא ניתן לבדוק הזמנות קיימות (" + r.code + "); לא תיווצר הזמנה חדשה.");
        JSONArray orders = arrayFrom(r.body);
        for (int i = 0; i < orders.length(); i++) {
            JSONObject o = orders.optJSONObject(i); if (o == null) continue;
            int sid = o.optInt("saleID", o.optInt("sale_id", -1));
            JSONObject os = o.optJSONObject("sale"); if (sid < 0 && os != null) sid = os.optInt("id", -1);
            String status = o.optString("status", o.optString("order_status", "")).toLowerCase(Locale.ROOT);
            if (sid == saleId && !status.contains("closed") && !status.contains("סגור")) {
                long id = extractOrderId(o); if (id > 0) return id;
            }
        }
        return -1;
    }

    private static boolean verifyQuantity(Context c, long orderId, String sid, int qty) {
        try {
            Response r = request(c, "GET", "/api/orders/" + orderId, null);
            if (r.code < 200 || r.code >= 300) return false;
            JSONObject root = new JSONObject(r.body), order = root.optJSONObject("order");
            if (order == null) order = root.optJSONObject("data"); if (order == null) order = root;
            JSONArray items = orderItems(order); double total = 0; boolean found = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && sid.equals(sidOf(item))) { total += number(item.opt("amount")); found = true; }
            }
            return found && Math.round(total) == qty;
        } catch (Exception ignored) { }
        return false;
    }

    private JSONArray fetchProducts(Context c, long id) throws Exception {
        JSONArray result = new JSONArray();
        for (int page = 1; page <= 100; page++) {
            Response r = request(c, "GET", "/api/get_items_for_sale/" + id + "/null?page=" + page + "&limit=100", null);
            if (r.code < 200 || r.code >= 300) throw new Exception("טעינת מוצרים נכשלה (" + r.code + ").");
            Object parsed = new JSONTokener(r.body).nextValue(); JSONArray rows; int last = -1;
            if (parsed instanceof JSONArray) rows = (JSONArray) parsed;
            else if (parsed instanceof JSONObject) {
                JSONObject o = (JSONObject) parsed; rows = o.optJSONArray("data");
                if (rows == null) rows = o.optJSONArray("items"); if (rows == null) rows = o.optJSONArray("products");
                if (rows == null) throw new Exception("תשובת המוצרים אינה מכילה רשימה תקינה.");
                if (o.has("last_page")) last = o.optInt("last_page", -1);
                JSONObject meta = o.optJSONObject("meta"); if (last < 0 && meta != null && meta.has("last_page")) last = meta.optInt("last_page", -1);
            } else throw new Exception("מבנה תשובת המוצרים אינו תקין.");
            for (int i = 0; i < rows.length(); i++) { JSONObject item = rows.optJSONObject(i); if (item != null) result.put(item); }
            boolean hasMore = last >= 0 ? page < last : rows.length() >= 100;
            if (!hasMore || rows.length() == 0) return result;
            if (page == 100) throw new Exception("סריקת המוצרים חרגה ממגבלת 100 עמודים; לא ניתן לאשר שהרשימה מלאה.");
        }
        return result;
    }

    private JSONArray getSales(Context c) throws Exception {
        Response r = request(c, "GET", "/api/profile/open_sales", null);
        if (r.code < 200 || r.code >= 300) return null;
        return arrayFrom(r.body);
    }
    private static JSONObject findSale(JSONArray sales, int id) {
        for (int i = 0; i < sales.length(); i++) { JSONObject s = sales.optJSONObject(i); if (s != null && s.optInt("id", -1) == id) return s; }
        return null;
    }
    private static JSONObject findProduct(JSONArray rows, String sid) {
        for (int i = 0; i < rows.length(); i++) { JSONObject p = rows.optJSONObject(i); if (p != null && sid.equals(sidOf(p))) return p; }
        return null;
    }
    private static int maxPerMan(JSONObject p) {
        double n = number(p == null ? null : p.opt("max_per_man"));
        JSONObject nested = p == null ? null : p.optJSONObject("product");
        if (n <= 0 && nested != null) n = number(nested.opt("max_per_man"));
        return n > 0 ? (int) Math.round(n) : 0;
    }
    private static int availability(JSONObject p) {
        Object v = p == null ? null : p.opt("available");
        if (v == null || v == JSONObject.NULL) { JSONObject nested = p == null ? null : p.optJSONObject("product"); v = nested == null ? null : nested.opt("available"); }
        double n;
        if (v instanceof Number) n = ((Number) v).doubleValue();
        else if (v instanceof String) { try { n = Double.parseDouble(((String) v).trim()); } catch (Exception e) { return -1; } }
        else return -1;
        if (n < 0) return -1;
        return n > 0 && !truthy(p.opt("disable")) ? 1 : 0;
    }
    private static String sidOf(JSONObject p) {
        String id = first(p, "item_salesID", "item_sales_id", "id", "itemID");
        if (id.isEmpty()) id = first(p.optJSONObject("product"), "item_salesID", "item_sales_id", "itemID", "id");
        return id;
    }
    private static long orderIdFromSale(JSONObject sale) { return extractOrderId(sale == null ? null : sale.optJSONObject("order")); }
    private static long extractOrderId(JSONObject o) {
        if (o == null) return -1;
        long id = o.optLong("id", o.optLong("order_id", o.optLong("orderID", -1)));
        if (id <= 0 && o.optJSONObject("order") != null) id = extractOrderId(o.optJSONObject("order"));
        if (id <= 0 && o.optJSONObject("data") != null) id = extractOrderId(o.optJSONObject("data"));
        return id;
    }
    private static JSONArray orderItems(JSONObject o) {
        String[] keys = {"products", "items", "order_items", "orderItems", "details", "lines", "order_details", "orderDetails"};
        for (String key : keys) { JSONArray a = o.optJSONArray(key); if (a != null) return a; }
        return new JSONArray();
    }
    private static JSONArray arrayFrom(String body) throws Exception {
        Object v = new JSONTokener(body).nextValue();
        if (v instanceof JSONArray) return (JSONArray) v;
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v; JSONArray a = o.optJSONArray("data");
            if (a == null) a = o.optJSONArray("items"); if (a == null) a = o.optJSONArray("products");
            if (a != null) return a;
        }
        throw new Exception("תשובת השרת אינה מכילה רשימה תקינה.");
    }
    private static Response request(Context c, String method, String path, JSONObject body) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        String token = p.getString("token", ""); if (token.isEmpty()) throw new Exception("נדרשת התחברות.");
        Response r = raw(method, path, token, body);
        if (r.code == 401) {
            Response refreshed = raw("POST", "/api/refresh", token, new JSONObject().put("remember", 1));
            if (refreshed.code == 200) {
                String next = extractToken(refreshed.body);
                if (!next.isEmpty()) { p.edit().putString("token", next).putLong("expires_at", System.currentTimeMillis() + 55L * 60000L).apply(); r = raw(method, path, next, body); }
            }
        }
        return r;
    }
    private static Response raw(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Origin", "https://mishnatyosef.org");
        c.setRequestProperty("Referer", "https://mishnatyosef.org/"); c.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        if ("POST".equals(method)) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] data = (body == null ? new JSONObject().put("remember", 1) : body).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = c.getOutputStream()) { os.write(data); }
        }
        int code = c.getResponseCode(); InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder out = new StringBuilder();
        if (stream != null) try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) out.append(line);
        }
        c.disconnect(); return new Response(code, out.toString());
    }
    private static String extractToken(String body) {
        try { JSONObject o = new JSONObject(body); String token = first(o, "access_token", "token");
            JSONObject d = o.optJSONObject("data"); if (token.isEmpty() && d != null) token = first(d, "access_token", "token"); return token;
        } catch (Exception e) { return ""; }
    }
    private static String first(JSONObject o, String... keys) {
        if (o == null) return "";
        for (String k : keys) { Object v = o.opt(k); if (v != null && v != JSONObject.NULL) { String s = String.valueOf(v).trim(); if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s; } }
        return "";
    }
    private static double number(Object v) { if (v instanceof Number) return ((Number) v).doubleValue(); try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0; } }
    private static boolean truthy(Object v) { if (v instanceof Boolean) return (Boolean) v; if (v instanceof Number) return ((Number) v).doubleValue() != 0; return v instanceof String && ("1".equals(v) || "true".equalsIgnoreCase((String) v)); }
    private static String apiError(String body, int code) {
        try { String m = first(new JSONObject(body), "message", "title", "error"); if (!m.isEmpty()) return m; } catch (Exception ignored) { }
        return "שגיאת שרת " + code;
    }
    private static String safeMessage(Exception e) {
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException) return "אין חיבור לשרת.";
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.io.IOException) return "בעיית תקשורת; לא תישלח הזמנה חוזרת אוטומטית.";
        String m = e == null ? "" : e.getMessage(); return m == null || m.trim().isEmpty() || m.startsWith("{") || m.startsWith("<") ? "לא ניתן להשלים את הפעולה." : m;
    }
    private static String safe(String s) { return s == null ? "" : s; }
    private static JSONObject findWatch(Context c, String key) {
        JSONArray a = loadWatches(c);
        for (int i = 0; i < a.length(); i++) { JSONObject w = a.optJSONObject(i); if (w != null && key.equals(w.optString("key", ""))) return w; }
        return null;
    }
    private static void updateWatch(Context c, String key, JSONObject patch) {
        synchronized (LOCK) {
            SharedPreferences p = c.getSharedPreferences(PREFS, 0); JSONArray old;
            try { old = new JSONArray(p.getString(WATCHES, "[]")); } catch (Exception e) { old = new JSONArray(); }
            JSONArray out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject w = old.optJSONObject(i); if (w == null) continue;
                if (key.equals(w.optString("key", ""))) {
                    java.util.Iterator<String> it = patch.keys();
                    while (it.hasNext()) { String k = it.next(); try { w.put(k, patch.opt(k)); } catch (Exception ignored) { } }
                }
                out.put(w);
            }
            p.edit().putString(WATCHES, out.toString()).apply();
        }
    }
    private static void notifyUser(Context c, String key, String title, String message, boolean order) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE); if (nm == null) return;
        SharedPreferences notificationPrefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean sound = notificationPrefs.getBoolean("notify_sound", true);
        boolean vibrate = notificationPrefs.getBoolean("notify_vibrate", true);
        String suffix = (sound ? "_s1" : "_s0") + (vibrate ? "_v1" : "_v0");
        String alertChannel = ALERT_CHANNEL + suffix;
        String orderChannel = ORDER_CHANNEL + suffix;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel alert = new NotificationChannel(alertChannel, "חזרה למלאי", NotificationManager.IMPORTANCE_DEFAULT);
            alert.setSound(sound ? android.provider.Settings.System.DEFAULT_NOTIFICATION_URI : null, null);
            alert.enableVibration(vibrate);
            if (vibrate) alert.setVibrationPattern(new long[]{0, 250, 150, 250});
            nm.createNotificationChannel(alert);
            NotificationChannel orderResult = new NotificationChannel(orderChannel, "תוצאות הזמנה אוטומטית", NotificationManager.IMPORTANCE_HIGH);
            orderResult.setSound(sound ? android.provider.Settings.System.DEFAULT_NOTIFICATION_URI : null, null);
            orderResult.enableVibration(vibrate);
            if (vibrate) orderResult.setVibrationPattern(new long[]{0, 250, 150, 250});
            nm.createNotificationChannel(orderResult);
        }
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            appendLog(c, key, "", "ההתראה לא הוצגה", "הרשאת ההתראות חסרה."); return;
        }
        Intent launch = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Restock alerts carry both stable IDs so the app can open the matching sale and scroll to the exact product card.
        if (!order && title != null && title.contains("חזר למלאי")) {
            String targetKey = key == null ? "" : key;
            if (targetKey.startsWith("global:")) targetKey = targetKey.substring("global:".length());
            int separator = targetKey.indexOf(':');
            if (separator > 0 && separator < targetKey.length() - 1) {
                try {
                    int saleId = Integer.parseInt(targetKey.substring(0, separator));
                    String sid = targetKey.substring(separator + 1);
                    if (saleId > 0 && !sid.trim().isEmpty()) {
                        launch.putExtra("stock_sale_id", saleId);
                        launch.putExtra("stock_product_sid", sid);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        PendingIntent pi = PendingIntent.getActivity(c, Math.abs((key + title).hashCode()), launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, order ? orderChannel : alertChannel) : new Notification.Builder(c);
        if (Build.VERSION.SDK_INT < 26) {
            int defaults = 0;
            if (sound) defaults |= Notification.DEFAULT_SOUND;
            if (vibrate) defaults |= Notification.DEFAULT_VIBRATE;
            b.setDefaults(defaults);
        }
        b.setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pi).setAutoCancel(true);
        nm.notify(40000 + Math.abs((key + title).hashCode() % 20000), b.build());
    }
}
