package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * פענוח תשובות השרת של משנת יוסף: בחירת ההזמנה העדכנית, מזהה הזמנה, מוצרים ומחלקות.
 * ללא תלות ב-Android (חוץ מ-org.json), ולכן נבדק בבדיקות יחידה.
 */
final class OrderParser {
    private OrderParser() {}

    static JSONObject chooseLatestPickupOrder(JSONArray orders) {
        List<JSONObject> valid = new ArrayList<>();
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null || extractOrderId(order) <= 0) continue;
            if (OrderHistoryPage.isCancelled(order)) continue;
            valid.add(order);
        }
        if (valid.isEmpty()) return null;
        Collections.sort(valid, (a, b) -> {
            long ta = orderSortValue(a), tb = orderSortValue(b);
            if (ta != tb) return Long.compare(tb, ta);
            return Long.compare(extractOrderId(b), extractOrderId(a));
        });
        return valid.get(0);
    }

    static long orderSortValue(JSONObject order) {
        String[] dateKeys = {"created_at", "createdAt", "order_date", "orderDate", "date", "updated_at", "updatedAt"};
        for (String key : dateKeys) {
            String value = first(order, key);
            if (value.isEmpty()) continue;
            long parsed = parseDate(value);
            if (parsed > 0) return parsed;
            try { return Long.parseLong(value); } catch (Exception ignored) { }
        }
        return extractOrderId(order);
    }

    static long parseDate(String value) {
        String[] formats = {"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(value);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) { }
        }
        return 0;
    }

    static JSONObject unwrapOrder(JSONObject response) {
        JSONObject order = response.optJSONObject("order");
        if (order != null) return order;
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            order = data.optJSONObject("order");
            return order != null ? order : data;
        }
        return response;
    }

    static List<ProductRow> parseProducts(JSONObject order) {
        List<ProductRow> result = new ArrayList<>();
        JSONArray orderProducts = order.optJSONArray("products");
        JSONArray orderItems = order.optJSONArray("items");
        if (orderProducts == null) orderProducts = orderItems;
        if (orderProducts == null) return result;
        for (int i = 0; i < orderProducts.length(); i++) {
            JSONObject row = orderProducts.optJSONObject(i);
            if (row == null) continue;
            JSONObject data = row.optJSONObject("data");
            JSONObject item = findMatchingItem(orderItems, row);
            JSONObject saleProduct = nestedProduct(item);
            // The catalog product ID is stable across orders and accounts. Line IDs
            // (item_salesID / order-item id) are not suitable for cross-account aggregation.
            String catalogProductId = first(saleProduct, "id", "product_id", "productID");
            if (catalogProductId.isEmpty()) {
                JSONObject itemSale = item == null ? null : item.optJSONObject("item_sale");
                catalogProductId = first(itemSale, "product_id", "productID");
            }
            String lineId = first(row, "item_salesID", "item_sales_id", "itemID", "id");
            if (lineId.isEmpty() && data != null) lineId = first(data, "item_salesID", "item_sales_id", "itemID", "id");
            String key = !catalogProductId.isEmpty() ? catalogProductId
                    : (lineId.isEmpty() ? "line" : lineId) + "_" + i;
            String name = first(data, "full_name", "name", "product_name");
            if (name.isEmpty()) name = first(row, "full_name", "name", "product_name");
            if (name.isEmpty()) name = first(saleProduct, "full_name", "name", "title");
            if (name.isEmpty()) name = "מוצר ללא שם";
            String image = first(data, "featured_image", "image", "image_url");
            if (image.isEmpty()) image = first(row, "featured_image", "image", "image_url");
            if (image.isEmpty()) image = first(saleProduct, "featured_image", "image", "image_url");
            if (!image.isEmpty() && !image.startsWith("http://") && !image.startsWith("https://")) {
                image = "https://images.mishnatyosef.org/images/items/" + image;
            }
            String amount = first(row, "amount", "quantity", "qty");
            if (amount.isEmpty() && data != null) amount = first(data, "amount", "quantity", "qty");
            if (amount.isEmpty()) amount = "1";
            String note = first(saleProduct, "note");
            // מבנה השרת: items[].item_sale.product.category = {name, order, main_category}.
            String category = findCategory(saleProduct, data, row,
                    item == null ? null : item.optJSONObject("item_sale"), item);
            JSONObject categoryObject = saleProduct == null ? null : saleProduct.optJSONObject("category");
            int categoryOrder = categoryObject == null ? 0 : categoryObject.optInt("order", 0);
            result.add(new ProductRow(key, name, image, amount, note, "", category, categoryOrder));
        }
        return result;
    }

    static JSONObject findMatchingItem(JSONArray items, JSONObject product) {
        if (items == null) return null;
        String id = first(product, "item_salesID", "item_sales_id", "itemID", "id");
        if (id.isEmpty()) id = first(product.optJSONObject("data"), "item_salesID", "item_sales_id", "itemID", "id");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(first(item, "item_salesID", "item_sales_id", "itemID", "id"))) return item;
        }
        return null;
    }

    static JSONObject nestedProduct(JSONObject item) {
        if (item == null) return null;
        JSONObject itemSale = item.optJSONObject("item_sale");
        return itemSale == null ? null : itemSale.optJSONObject("product");
    }

    static long extractOrderId(JSONObject order) {
        if (order == null) return -1;
        long id = order.optLong("id", order.optLong("order_id", order.optLong("orderID", -1)));
        if (id <= 0 && order.optJSONObject("order") != null) id = extractOrderId(order.optJSONObject("order"));
        if (id <= 0 && order.optJSONObject("data") != null) id = extractOrderId(order.optJSONObject("data"));
        return id;
    }

    static String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
        }
        return "";
    }

    static final String[] CATEGORY_KEYS = {"category", "category_name", "categoryName", "cat_name",
            "catName", "category_title", "department", "department_name", "dept", "group_name"};

    /** מחלקת מוצר מתוך נתוני השורה, הפריט והמוצר. בודק גם שדות מקוננים (למשל category.name). */
    static String findCategory(JSONObject... sources) {
        for (JSONObject source : sources) {
            String found = findCategory(source, 0);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    static String findCategory(JSONObject source, int depth) {
        if (source == null || depth > 2) return "";
        for (String key : CATEGORY_KEYS) {
            Object value = source.opt(key);
            if (value instanceof JSONObject) {
                String nested = first((JSONObject) value, "name", "title", "category_name", "week");
                if (!nested.isEmpty()) return nested;
            } else if (value instanceof String) {
                String text = ((String) value).trim();
                // מזהה מספרי אינו שם מחלקה.
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text) && !text.matches("\\d+")) return text;
            }
        }
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            Object value = source.opt(keys.next());
            if (value instanceof JSONObject) {
                String nested = findCategory((JSONObject) value, depth + 1);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    /**
     * ממיין לפי מחלקות. סדר המחלקות לפי category.order שמגיע מהשרת; כשאין, לפי רשימת המחלקות
     * של המכירה (sale.categories), ואחרת לפי א״ב. מוצרים בלי מחלקה בסוף. בתוך מחלקה נשמר הסדר המקורי.
     */
    static void sortByCategory(List<ProductRow> rows, JSONObject pickupInfo) {
        if (rows == null || rows.size() < 2) return;
        final List<String> order = new ArrayList<>();
        JSONObject sale = pickupInfo == null ? null : pickupInfo.optJSONObject("sale");
        JSONArray listed = sale == null ? null : sale.optJSONArray("categories");
        if (listed != null) for (int i = 0; i < listed.length(); i++) order.add(listed.optString(i, "").trim());
        final java.text.Collator collator = java.text.Collator.getInstance(new Locale("he"));
        Collections.sort(rows, (a, b) -> {
            boolean aEmpty = a.category.isEmpty(), bEmpty = b.category.isEmpty();
            if (aEmpty || bEmpty) return aEmpty == bEmpty ? 0 : (aEmpty ? 1 : -1);
            // סדר המחלקות כפי שהוא באתר משנת יוסף (category.order).
            if (a.categoryOrder > 0 && b.categoryOrder > 0 && a.categoryOrder != b.categoryOrder)
                return a.categoryOrder < b.categoryOrder ? -1 : 1;
            if (a.category.equals(b.category)) return 0;
            int ra = order.indexOf(a.category), rb = order.indexOf(b.category);
            if (ra < 0) ra = Integer.MAX_VALUE;
            if (rb < 0) rb = Integer.MAX_VALUE;
            if (ra != rb) return ra < rb ? -1 : 1;
            return ra == Integer.MAX_VALUE ? collator.compare(a.category, b.category) : 0;
        });
    }
}
