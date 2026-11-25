package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * מידע איסוף להזמנה: מקום, מועד, שעות, אחראי והערות.
 *
 * המידע נלקח מאובייקט sale שברשומת ההזמנה (GET /api/orders) או בפרטי ההזמנה.
 * נשמרים רק שדות שנבחרו מראש (רשימה לבנה), כך שפרטי תשלום, טוקנים ושדות פנימיים
 * של השרת אינם נשמרים במכשיר לעולם.
 */
final class PickupInfo {
    private PickupInfo() {}

    private static final String[] ORDER_KEYS = {"id", "time", "created_at", "total", "total_list"};
    private static final String[] SALE_KEYS = {"id", "custom_name", "begin", "end", "supply_begin", "supply_end",
            "startHour", "endHour", "general_start", "general_end", "men_start", "men_end",
            "women_start", "women_end", "msg", "special_msg_txt", "prep_note", "times_info",
            "sale_process", "tip", "did_you_know", "remember", "next_week",
            "closed_for_orders", "reason_for_closing", "special_address", "categories"};
    private static final String[] PARENT_KEYS = {"week", "description", "week_start", "sunday_day",
            "monday_day", "tuesday_day", "wednesday_day", "thursday_day"};
    private static final String[] SITE_KEYS = {"name", "city", "sub_city", "street", "address",
            "address_description", "neighborhood", "lat", "lng", "admin_name", "admin_phone",
            "note", "prep_note", "hours", "distribution_day", "general_start_hour", "general_end_hour",
            "men_start_hour", "men_end_hour", "women_start_hour", "women_end_hour", "mishloach", "start"};

    /** בונה מידע איסוף מרשומת ההיסטוריה, ואם חסר בה - מפרטי ההזמנה. מחזיר null כשאין מידע. */
    static JSONObject extract(JSONObject historyRow, JSONObject orderDetail) {
        try {
            JSONObject order = firstWithSale(historyRow, orderDetail);
            if (order == null) return null;
            JSONObject sale = order.optJSONObject("sale");
            if (sale == null) return null;
            JSONObject result = new JSONObject();
            result.put("order", copy(order, ORDER_KEYS));
            result.put("sale", copy(sale, SALE_KEYS));
            JSONObject parent = sale.optJSONObject("parent");
            if (parent == null) parent = order.optJSONObject("parent");
            if (parent != null) result.put("parent", copy(parent, PARENT_KEYS));
            JSONObject site = sale.optJSONObject("site");
            if (site == null) site = order.optJSONObject("site");
            if (site != null) result.put("site", copy(site, SITE_KEYS));
            String name = OrderHistoryPage.saleName(order);
            if (!name.isEmpty()) result.put("sale_name", name);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject firstWithSale(JSONObject... sources) {
        for (JSONObject source : sources) {
            if (source == null) continue;
            if (source.optJSONObject("sale") != null) return source;
            JSONObject inner = source.optJSONObject("order");
            if (inner != null && inner.optJSONObject("sale") != null) return inner;
            JSONObject data = source.optJSONObject("data");
            if (data != null && data.optJSONObject("sale") != null) return data;
            JSONObject dataOrder = data == null ? null : data.optJSONObject("order");
            if (dataOrder != null && dataOrder.optJSONObject("sale") != null) return dataOrder;
        }
        return null;
    }

    private static JSONObject copy(JSONObject from, String[] keys) throws Exception {
        JSONObject to = new JSONObject();
        for (String key : keys) {
            Object value = from.opt(key);
            if (value == null || value == JSONObject.NULL || value instanceof JSONObject) continue;
            if (value instanceof JSONArray) { to.put(key, value); continue; }
            to.put(key, value);
        }
        return to;
    }

    // ---------------------------------------------------------------------
    // תצוגה
    // ---------------------------------------------------------------------

    /** שורת מידע: כותרת קטע (value==null), או תווית וערך. */
    static final class Line {
        final String label, value;
        Line(String label, String value) { this.label = label; this.value = value; }
        boolean isSection() { return value == null; }
    }

    static List<Line> describe(JSONObject info) {
        List<Line> lines = new ArrayList<>();
        if (info == null) return lines;
        JSONObject order = obj(info, "order"), sale = obj(info, "sale"),
                parent = obj(info, "parent"), site = obj(info, "site");

        // מועד האיסוף - המידע החשוב ביותר, ולכן ראשון.
        List<Line> when = new ArrayList<>();
        add(when, "תאריך", pickupDate(sale, parent, site));
        String general = range(text(sale, "startHour"), text(sale, "endHour"));
        if (general.isEmpty()) general = range(text(sale, "general_start"), text(sale, "general_end"));
        if (general.isEmpty()) general = range(text(site, "general_start_hour"), text(site, "general_end_hour"));
        add(when, "שעות", general);
        String men = range(text(sale, "men_start"), text(sale, "men_end"));
        if (men.isEmpty()) men = range(text(site, "men_start_hour"), text(site, "men_end_hour"));
        add(when, "שעות לגברים", men);
        String women = range(text(sale, "women_start"), text(sale, "women_end"));
        if (women.isEmpty()) women = range(text(site, "women_start_hour"), text(site, "women_end_hour"));
        add(when, "שעות לנשים", women);
        add(when, "זמנים", text(sale, "times_info"));
        add(when, "שעות הנקודה", text(site, "hours"));
        section(lines, "מועד האיסוף", when);

        List<Line> where = new ArrayList<>();
        add(where, "נקודת איסוף", text(site, "name"));
        add(where, "כתובת", join(", ", text(site, "street"), text(site, "address"),
                text(site, "neighborhood"), text(site, "sub_city"), text(site, "city")));
        add(where, "פירוט מיקום", text(site, "address_description"));
        add(where, "כתובת מיוחדת למכירה", text(sale, "special_address"));
        section(lines, "מקום האיסוף", where);

        List<Line> contact = new ArrayList<>();
        add(contact, "אחראי הנקודה", text(site, "admin_name"));
        add(contact, "טלפון", phone(info));
        section(lines, "איש קשר", contact);

        List<Line> notes = new ArrayList<>();
        add(notes, "הודעת המכירה", text(sale, "msg"));
        add(notes, "הודעה מיוחדת", text(sale, "special_msg_txt"));
        add(notes, "הכנה לאיסוף", text(sale, "prep_note"));
        add(notes, "הערת הנקודה", text(site, "note"));
        add(notes, "הכנה בנקודה", text(site, "prep_note"));
        add(notes, "תהליך המכירה", text(sale, "sale_process"));
        add(notes, "טיפ", text(sale, "tip"));
        add(notes, "לזכור", text(sale, "remember"));
        add(notes, "הידעת?", text(sale, "did_you_know"));
        add(notes, "בשבוע הבא", text(sale, "next_week"));
        add(notes, "סיבת סגירה", text(sale, "reason_for_closing"));
        section(lines, "הערות", notes);

        List<Line> about = new ArrayList<>();
        add(about, "מכירה", text(info, "sale_name"));
        add(about, "מספר הזמנה", text(order, "id"));
        add(about, "סכום", money(text(order, "total"), text(order, "total_list")));
        add(about, "הוזמן", dateTime(text(order, "time")));
        add(about, "ההזמנות פתוחות", range(dateTime(text(sale, "begin")), dateTime(text(sale, "end"))));
        add(about, "קטגוריות", categories(sale));
        section(lines, "פרטי ההזמנה", about);
        return lines;
    }

    /** קישור למפה לפי קואורדינטות הנקודה, או לפי הכתובת. ריק כשאין מיקום. */
    static String mapUri(JSONObject info) {
        JSONObject site = obj(info, "site");
        if (site == null) return "";
        double lat = site.optDouble("lat", Double.NaN), lng = site.optDouble("lng", Double.NaN);
        String label = text(site, "name");
        if (!Double.isNaN(lat) && !Double.isNaN(lng) && (lat != 0 || lng != 0)) {
            return "geo:" + lat + "," + lng + "?q=" + lat + "," + lng
                    + (label.isEmpty() ? "" : "(" + android.net.Uri.encode(label) + ")");
        }
        String address = join(", ", text(site, "street"), text(site, "city"));
        return address.isEmpty() ? "" : "geo:0,0?q=" + android.net.Uri.encode(address);
    }

    /** מספר הטלפון של אחראי הנקודה, רק כשהוא נראה כמו מספר אמיתי. */
    static String phone(JSONObject info) {
        String value = text(obj(info, "site"), "admin_phone");
        String digits = value.replaceAll("[^0-9+]", "");
        return digits.length() >= 7 ? value : "";
    }

    static String title(JSONObject info) {
        String name = text(info, "sale_name");
        String site = text(obj(info, "site"), "name");
        if (!name.isEmpty() && !site.isEmpty()) return name + " · " + site;
        return name.isEmpty() ? site : name;
    }

    // ---------------------------------------------------------------------
    // עזרי עיצוב
    // ---------------------------------------------------------------------

    private static void section(List<Line> lines, String title, List<Line> rows) {
        if (rows.isEmpty()) return;
        lines.add(new Line(title, null));
        lines.addAll(rows);
    }

    private static void add(List<Line> lines, String label, String value) {
        if (value != null && !value.trim().isEmpty()) lines.add(new Line(label, value.trim()));
    }

    private static JSONObject obj(JSONObject parent, String key) {
        return parent == null ? null : parent.optJSONObject(key);
    }

    /** ערך טקסט נקי: ריק עבור null, אפסים, תאריכי 0000 ושעות 00:00. */
    private static String text(JSONObject object, String key) {
        if (object == null) return "";
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL || raw instanceof JSONObject || raw instanceof JSONArray) return "";
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value) || value.startsWith("0000-00-00")
                || value.equals("00:00:00") || value.equals("00:00")) return "";
        return value;
    }

    private static String join(String separator, String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            String clean = part.trim();
            if (out.indexOf(clean) >= 0) continue;
            if (out.length() > 0) out.append(separator);
            out.append(clean);
        }
        return out.toString();
    }

    private static String hour(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.length() >= 5 && value.charAt(2) == ':' ? value.substring(0, 5) : value;
    }

    private static String range(String from, String to) {
        String a = hour(from), b = hour(to);
        if (a.isEmpty() && b.isEmpty()) return "";
        if (b.isEmpty() || a.equals(b)) return a;
        if (a.isEmpty()) return b;
        return a + "–" + b;
    }

    private static String money(String total, String list) {
        String value = total.isEmpty() ? list : total;
        if (value.isEmpty() || "0".equals(value)) return "";
        return "₪" + value;
    }

    private static String categories(JSONObject sale) {
        JSONArray array = sale == null ? null : sale.optJSONArray("categories");
        if (array == null || array.length() == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (value.isEmpty()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.toString();
    }

    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
    private static final String[] DAY_KEYS = {"sunday_day", "monday_day", "tuesday_day",
            "wednesday_day", "thursday_day"};

    /** תאריך האיסוף: תחילת האספקה של המכירה, ואם אין - יום החלוקה של הנקודה בשבוע המכירה. */
    private static String pickupDate(JSONObject sale, JSONObject parent, JSONObject site) {
        Date supply = parse(text(sale, "supply_begin"));
        if (supply != null) return dayAndDate(supply);
        int day = site == null ? 0 : site.optInt("distribution_day", 0);
        if (day >= 1 && day <= DAY_KEYS.length) {
            Date date = parse(text(parent, DAY_KEYS[day - 1]));
            if (date != null) return dayAndDate(date);
            return "יום " + DAYS[day - 1];
        }
        return "";
    }

    private static String dateTime(String value) {
        Date date = parse(value);
        if (date == null) return "";
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        boolean hasTime = c.get(Calendar.HOUR_OF_DAY) != 0 || c.get(Calendar.MINUTE) != 0;
        return dayAndDate(date) + (hasTime ? " " + new SimpleDateFormat("HH:mm", Locale.US).format(date) : "");
    }

    private static String dayAndDate(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return "יום " + DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + new SimpleDateFormat("d.M.yyyy", Locale.US).format(date);
    }

    /** שעות השרת הן שעון ישראל, ולכן מפורשות כשעון מקומי (בלי המרת אזור זמן). */
    private static Date parse(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};
        for (String format : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
                parser.setLenient(false);
                String input = value.length() > format.replace("'", "").length()
                        ? value.substring(0, format.replace("'", "").length()) : value;
                Date date = parser.parse(input);
                if (date != null) return date;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
