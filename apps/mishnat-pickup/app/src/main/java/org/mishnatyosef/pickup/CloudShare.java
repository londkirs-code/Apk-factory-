package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * שיתוף רשימות דרך שרת Apps Script (ראו cloud/Code.gs).
 * העלאה מחזירה מספר אקראי בין 1 ל-1000 שבתוקף 7 ימים; הבאה לפי המספר מחזירה קובץ רשימה
 * בפורמט mishnat-pickup-list, שעובר דרך מסלול הייבוא הרגיל של האפליקציה.
 * נשלחים רק המוצרים: בלי שמות חשבונות, בלי משפחות ובלי שום פרט התחברות.
 */
final class CloudShare {
    private CloudShare() {}

    /** כתובת ברירת המחדל של השרת (הפריסה של Apps Script). אפשר לעקוף בהגדרות. */
    static final String DEFAULT_URL =
            "https://script.google.com/macros/s/AKfycbxZF2NiDL80aY8mTf4pYmeyvmliWT_sj67RAOkc_emJL3Xcz4fk2wXc4mpMPZlcvpshSQ/exec";
    static final int MIN_NUMBER = 1, MAX_NUMBER = 1000;

    static final class Uploaded {
        final int number; final long expiresAt;
        Uploaded(int number, long expiresAt) { this.number = number; this.expiresAt = expiresAt; }
    }

    static final class Fetched {
        final int number, itemCount; final String title; final long createdAt, expiresAt; final JSONObject list;
        Fetched(int number, String title, int itemCount, long createdAt, long expiresAt, JSONObject list) {
            this.number = number; this.title = title; this.itemCount = itemCount;
            this.createdAt = createdAt; this.expiresAt = expiresAt; this.list = list;
        }
    }

    /**
     * בונה קובץ רשימה לשיתוף מתוך מוצרים שמורים (products של רשימה שמורה).
     * רק שם, כמות, הערה, מחלקה וקישור https לתמונה. כמות לא תקינה הופכת ל-1.
     */
    static JSONObject buildPayload(String title, JSONArray products) throws Exception {
        JSONArray items = new JSONArray();
        Set<String> ids = new HashSet<>();
        if (products != null) for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("name", "").trim();
            if (name.isEmpty()) continue;
            if (name.length() > 200) name = name.substring(0, 200);
            String id = p.optString("key", "").trim();
            if (id.isEmpty()) id = "item" + i;
            if (id.length() > 64) id = id.substring(0, 64);
            String base = id; int suffix = 2;
            while (!ids.add(id)) id = base + "_" + (suffix++);
            double quantity = Quantities.parse(p.optString("amount", "1"));
            if (quantity <= 0) quantity = 1;
            String image = p.optString("image_url", "");
            if (!image.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) image = "";
            JSONObject item = new JSONObject().put("id", id).put("name", name).put("quantity", quantity);
            String note = limit(p.optString("note", ""), 300), category = limit(p.optString("category", ""), 100);
            if (!note.isEmpty()) item.put("note", note);
            if (!category.isEmpty()) item.put("category", category);
            if (!image.isEmpty()) item.put("imageUrl", image);
            items.put(item);
        }
        if (items.length() == 0) throw new Exception("אין ברשימה מוצרים לשיתוף.");
        String cleanTitle = limit(title == null || title.trim().isEmpty() ? "רשימה משותפת" : title.trim(), 80);
        return new JSONObject().put("format", ImportParser.FORMAT).put("version", ImportParser.VERSION)
                .put("lists", new JSONArray().put(new JSONObject()
                        .put("account", new JSONObject().put("id", "cloud").put("name", "רשימה מהענן"))
                        .put("order", new JSONObject().put("id", "cloud").put("name", cleanTitle))
                        .put("items", items)));
    }

    /**
     * מתאים קובץ שהגיע מהשרת לייבוא: מזהה חשבון ייחודי לכל העלאה (כדי שהבאה חוזרת של אותה
     * רשימה תעדכן אותה, ורשימות שונות לא ידרסו זו את זו), ושם שכולל את המספר.
     */
    static JSONObject prepareForImport(Fetched fetched) throws Exception {
        JSONObject list = new JSONObject(fetched.list.toString());
        JSONArray lists = list.optJSONArray("lists");
        if (lists == null || lists.length() != 1 || lists.optJSONObject(0) == null)
            throw new Exception("הרשימה שהתקבלה מהשרת אינה תקינה.");
        JSONObject entry = lists.getJSONObject(0);
        String uploadId = "cloud-" + fetched.number + "-" + fetched.createdAt;
        String name = "רשימה " + fetched.number + " מהענן";
        String title = fetched.title == null || fetched.title.isEmpty() ? name : fetched.title;
        entry.put("account", new JSONObject().put("id", uploadId).put("name", name));
        entry.put("order", new JSONObject().put("id", uploadId).put("name", title));
        return list;
    }

    static Uploaded upload(String serverUrl, JSONObject payload, String device) throws Exception {
        JSONObject body = new JSONObject().put("action", "upload").put("list", payload).put("device", device);
        JSONObject response = call(serverUrl, body);
        int number = response.optInt("number", 0);
        if (number < MIN_NUMBER || number > MAX_NUMBER) throw new Exception("השרת לא החזיר מספר רשימה.");
        return new Uploaded(number, response.optLong("expiresAt", 0));
    }

    static Fetched fetch(String serverUrl, int number) throws Exception {
        if (number < MIN_NUMBER || number > MAX_NUMBER)
            throw new Exception("המספר צריך להיות בין " + MIN_NUMBER + " ל-" + MAX_NUMBER + ".");
        JSONObject response = call(serverUrl, new JSONObject().put("action", "get").put("number", number));
        JSONObject list = response.optJSONObject("list");
        if (list == null) throw new Exception("הרשימה שהתקבלה מהשרת ריקה.");
        return new Fetched(number, response.optString("title", ""), response.optInt("itemCount", 0),
                response.optLong("createdAt", 0), response.optLong("expiresAt", 0), list);
    }

    /** קריאה לשרת ופענוח התשובה. שגיאה מהשרת ({ok:false,error}) נזרקת כהודעה בעברית. */
    static JSONObject call(String serverUrl, JSONObject body) throws Exception {
        if (serverUrl == null || !serverUrl.trim().startsWith("https://"))
            throw new Exception("שרת הענן עוד לא הוגדר. אפשר להגדיר אותו בהגדרות ← ייבוא ונתונים.");
        String text = post(serverUrl.trim(), body.toString());
        JSONObject response;
        try { response = new JSONObject(text); }
        catch (Exception e) { throw new Exception("תשובה לא צפויה משרת הענן. בדקו את כתובת השרת."); }
        if (!response.optBoolean("ok", false)) {
            String error = response.optString("error", "");
            throw new Exception(error.isEmpty() ? "שרת הענן החזיר שגיאה." : error);
        }
        return response;
    }

    /**
     * Apps Script עונה ל-POST בהפניה (302) לכתובת אחרת של גוגל, שאותה צריך לקרוא ב-GET.
     * ההפניה מטופלת ידנית, כי HttpURLConnection לא עובר בעצמו בין שרתים.
     */
    private static String post(String serverUrl, String body) throws Exception {
        HttpURLConnection connection = ApiClient.openConnection(new URL(serverUrl));
        connection.setRequestMethod("POST");
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
        int code = connection.getResponseCode();
        for (int hops = 0; code >= 300 && code < 400 && hops < 5; hops++) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.isEmpty()) throw new Exception("שרת הענן החזיר הפניה ריקה.");
            connection = ApiClient.openConnection(new URL(new URL(serverUrl), location));
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            code = connection.getResponseCode();
        }
        String text = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (code < 200 || code >= 300) throw new Exception("שרת הענן לא זמין (" + code + ").");
        return text;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096]; int n;
            while ((n = reader.read(buffer)) != -1) {
                text.append(buffer, 0, n);
                if (text.length() > 2_000_000) throw new Exception("התשובה משרת הענן גדולה מדי.");
            }
        }
        return text.toString();
    }

    private static String limit(String value, int max) {
        String v = value == null ? "" : value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
