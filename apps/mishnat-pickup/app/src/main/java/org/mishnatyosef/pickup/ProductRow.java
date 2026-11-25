package org.mishnatyosef.pickup;

/** שורת מוצר ברשימה. מחלקה עצמאית כדי שתהיה זמינה לפענוח, לטביעת התוכן ולבדיקות. */
final class ProductRow {
    final String key, name, imageUrl, amount, note, imagePath;
    /** מחלקת המוצר (למשל "ירקות"), לצורך מיון הרשימה. אינה חלק מטביעת התוכן. */
    final String category;
    /** סדר המחלקה באתר (category.order); 0 כשאינו ידוע. */
    final int categoryOrder;
    ProductRow(String key, String name, String imageUrl, String amount, String note) {
        this(key, name, imageUrl, amount, note, "", "");
    }
    ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath) {
        this(key, name, imageUrl, amount, note, imagePath, "");
    }
    ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath,
               String category) {
        this(key, name, imageUrl, amount, note, imagePath, category, 0);
    }
    ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath,
               String category, int categoryOrder) {
        // null הופך לטקסט ריק, כדי שטביעת התוכן והתצוגה לא ייכשלו בנתונים חסרים.
        this.key = key == null ? "" : key; this.name = name == null ? "" : name;
        this.imageUrl = imageUrl == null ? "" : imageUrl; this.amount = amount == null ? "" : amount;
        this.note = note == null ? "" : note; this.imagePath = imagePath == null ? "" : imagePath;
        this.category = category == null ? "" : category.trim();
        this.categoryOrder = Math.max(0, categoryOrder);
    }
}
