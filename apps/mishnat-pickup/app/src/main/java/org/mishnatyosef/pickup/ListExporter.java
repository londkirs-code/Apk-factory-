package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ייצוא רשימות לשיתוף, בפורמטים שהאפליקציה יודעת לייבא בחזרה (ImportParser):
 * JSON (mishnat-pickup-list), TXT (שורה לכל מוצר, "## שם" לכל רשימה) ו-ZIP (list.json + images/).
 * נשלחים המוצרים ושם המשפחה של הרשימה (אם יש), בלי פרטי התחברות ובלי נתיבים מקומיים.
 * בטקסט ובקוד QR כותרת הרשימה היא "## שם הרשימה | שם המשפחה" (בלי משפחה: "## שם הרשימה").
 */
final class ListExporter {
    private ListExporter() {}

    static final int MAX_LISTS = 20;

    /** רשימה אחת לייצוא: שם + המוצרים השמורים שלה (products של רשימה שמורה). */
    static final class Export {
        final String title, family; final JSONArray products;
        Export(String title, JSONArray products) { this(title, "", products); }
        Export(String title, String family, JSONArray products) {
            this.title = title; this.family = family == null ? "" : family.trim(); this.products = products;
        }
    }

    /** כותרת רשימה בטקסט: "## שם | משפחה". | בתוך השמות מוחלף ב-/. */
    private static String header(JSONObject list) {
        String name = cell(list.getJSONObject("order").optString("name", ""));
        String family = familyOf(list);
        return "## " + (name.isEmpty() ? "רשימה" : name) + (family.isEmpty() ? "" : " | " + cell(family));
    }

    /** קובץ JSON. כש-images לא null, מוצרים עם תמונה שמורה מקבלים "image": "images/..." והקובץ נרשם במפה. */
    static JSONObject json(List<Export> exports, Map<String, File> images) throws Exception {
        if (exports == null || exports.isEmpty()) throw new Exception("לא נבחרו רשימות לשיתוף.");
        if (exports.size() > MAX_LISTS) throw new Exception("אפשר לשתף עד " + MAX_LISTS + " רשימות בקובץ אחד.");
        JSONArray lists = new JSONArray();
        long stamp = System.currentTimeMillis();
        for (int l = 0; l < exports.size(); l++) {
            Export export = exports.get(l);
            JSONArray items = items(export.products, images, l);
            if (items.length() == 0) continue;
            String id = "share-" + stamp + "-" + l;
            lists.put(new JSONObject()
                    .put("account", new JSONObject().put("id", id).put("name",
                            export.family.isEmpty() ? "רשימה משותפת" : limit(oneLine(export.family), 200)))
                    .put("order", new JSONObject().put("id", id).put("name", title(export.title)))
                    .put("items", items));
        }
        if (lists.length() == 0) throw new Exception("אין ברשימות שנבחרו מוצרים לשיתוף.");
        return new JSONObject().put("format", ImportParser.FORMAT).put("version", ImportParser.VERSION).put("lists", lists);
    }

    private static JSONArray items(JSONArray products, Map<String, File> images, int listIndex) throws Exception {
        JSONArray items = new JSONArray();
        Set<String> ids = new HashSet<>();
        if (products == null) return items;
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            String name = limit(p.optString("name", ""), 200);
            if (name.isEmpty()) continue;
            String id = limit(p.optString("key", ""), 64);
            if (id.isEmpty()) id = "item" + i;
            String base = id; int suffix = 2;
            while (!ids.add(id)) id = base + "_" + (suffix++);
            double quantity = Quantities.parse(p.optString("amount", "1"));
            if (quantity <= 0) quantity = 1;
            JSONObject item = new JSONObject().put("id", id).put("name", name).put("quantity", quantity);
            String note = limit(p.optString("note", ""), 300), category = limit(p.optString("category", ""), 100);
            String url = p.optString("image_url", "");
            if (!note.isEmpty()) item.put("note", note);
            if (!category.isEmpty()) item.put("category", category);
            if (url.toLowerCase(Locale.ROOT).startsWith("https://") && url.length() <= 2048) item.put("imageUrl", url);
            if (images != null) {
                File file = new File(p.optString("image_file", ""));
                if (file.isFile() && file.length() > 0 && file.length() <= 5L * 1024 * 1024) {
                    String imageName = "img_" + listIndex + "_" + i + ".jpg";
                    images.put(imageName, file);
                    item.put("image", "images/" + imageName);
                }
            }
            items.put(item);
        }
        return items;
    }

    /** קובץ טקסט: "## שם" לכל רשימה, ושורה לכל מוצר: שם | כמות | | הערה | קטגוריה. */
    static String text(List<Export> exports) throws Exception {
        JSONObject json = json(exports, null);
        StringBuilder out = new StringBuilder("# משנת אוסף: רשימות איסוף. אפשר לייבא את הקובץ הזה באפליקציה.\n");
        JSONArray lists = json.getJSONArray("lists");
        for (int l = 0; l < lists.length(); l++) {
            JSONObject list = lists.getJSONObject(l);
            out.append('\n').append(header(list)).append('\n');
            JSONArray items = list.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                out.append(cell(item.optString("name"))).append(" | ")
                        .append(Quantities.format(item.optDouble("quantity", 1))).append(" | | ")
                        .append(cell(item.optString("note", ""))).append(" | ")
                        .append(cell(item.optString("category", ""))).append('\n');
            }
        }
        return out.toString();
    }

    /** קובץ ZIP: list.json ותיקיית images עם התמונות השמורות במכשיר. */
    static void zip(List<Export> exports, File target) throws Exception {
        Map<String, File> images = new LinkedHashMap<>();
        JSONObject json = json(exports, images);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target))) {
            zip.putNextEntry(new ZipEntry("list.json"));
            zip.write(json.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (!images.isEmpty()) {
                zip.putNextEntry(new ZipEntry("images/"));
                zip.closeEntry();
                byte[] buffer = new byte[8192];
                for (Map.Entry<String, File> image : images.entrySet()) {
                    zip.putNextEntry(new ZipEntry("images/" + image.getKey()));
                    try (InputStream in = new FileInputStream(image.getValue())) {
                        int n; while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    /**
     * חלוקה לקודי QR - זהה לגמרי ל-toQrChunks בתוסף (browser-extension/src/core.js), כדי שקודים
     * מהטלפון ומהמחשב ייסרקו באותה צורה. כל קוד הוא טקסט שהייבוא מקבל:
     * "#משנת אוסף 1/3", "## הזמנה | משפחה", "### קטגוריה", "שם | כמות | | הערה".
     */
    static List<String> qrChunks(List<Export> exports, int maxBytes) throws Exception {
        int limit = maxBytes > 0 ? maxBytes : 900;
        JSONArray lists = json(exports, null).getJSONArray("lists");
        List<String> chunks = new java.util.ArrayList<>();
        List<String> lines = new java.util.ArrayList<>();
        int[] size = {0};
        for (int l = 0; l < lists.length(); l++) {
            JSONObject list = lists.getJSONObject(l);
            String title = header(list);
            String category = null;
            if (size[0] + utf8(title) + 40 > limit) flush(chunks, lines, size);
            push(lines, size, title);
            JSONArray items = list.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String itemCategory = oneLine(item.optString("category", ""));
                String line = cell(item.optString("name")) + " | " + Quantities.format(item.optDouble("quantity", 1));
                String note = item.optString("note", "");
                if (!note.isEmpty()) line += " | | " + cell(note);
                String catLine = itemCategory.equals(category == null ? "" : category) ? null : "### " + (itemCategory.isEmpty() ? "-" : itemCategory);
                int need = utf8(line) + 1 + (catLine != null ? utf8(catLine) + 1 : 0);
                if (size[0] + need > limit - 24) {
                    // קוד חדש: מתחיל בשם ההזמנה ובקטגוריה של המוצר הבא.
                    flush(chunks, lines, size);
                    category = null;
                    push(lines, size, title);
                    catLine = itemCategory.isEmpty() ? null : "### " + itemCategory;
                }
                if (catLine != null) { category = itemCategory.isEmpty() ? null : itemCategory; push(lines, size, catLine); }
                push(lines, size, line);
            }
        }
        flush(chunks, lines, size);
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) out.add("#משנת אוסף " + (i + 1) + "/" + chunks.size() + "\n" + chunks.get(i));
        return out;
    }

    /** שם המשפחה של רשימה בקובץ (ריק כשזה השם הכללי). */
    private static String familyOf(JSONObject list) {
        JSONObject account = list.optJSONObject("account");
        String name = account == null ? "" : oneLine(account.optString("name", ""));
        return "רשימה משותפת".equals(name) ? "" : name;
    }

    private static void push(List<String> lines, int[] size, String line) {
        lines.add(line);
        size[0] += utf8(line) + 1;
    }

    private static void flush(List<String> chunks, List<String> lines, int[] size) {
        if (!lines.isEmpty()) {
            StringBuilder joined = new StringBuilder();
            for (String line : lines) joined.append(joined.length() == 0 ? "" : "\n").append(line);
            chunks.add(joined.toString());
        }
        lines.clear();
        size[0] = 0;
    }

    private static int utf8(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }

    private static String title(String value) {
        String t = limit(value, 200);
        return t.isEmpty() ? "רשימה משותפת" : t;
    }

    private static String cell(String value) { return oneLine(value).replace('|', '/'); }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String limit(String value, int max) {
        String v = value == null ? "" : value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
