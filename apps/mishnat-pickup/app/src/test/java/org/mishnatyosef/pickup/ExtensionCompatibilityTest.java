package org.mishnatyosef.pickup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * התאימות בין תוסף הדפדפן (browser-extension) לאפליקציה: הקבצים שהתוסף מייצר
 * (test/fixtures בתיקיית התוסף) חייבים להיקלט ב-ImportParser בלי לאבד הזמנות, כמויות, קטגוריות ותמונות.
 * אם הבדיקה נכשלת, שינוי בתוסף או באפליקציה שבר את הפורמט המשותף.
 */
public class ExtensionCompatibilityTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static File fixture(String name) {
        // הבדיקות רצות מתיקיית המודול app.
        File file = new File("../browser-extension/test/fixtures/" + name);
        assertTrue("חסר קובץ דוגמה של התוסף: " + file.getAbsolutePath(), file.isFile());
        return file;
    }

    private ImportParser.Bundle parse(String name) throws Exception {
        try (InputStream in = new FileInputStream(fixture(name))) {
            return ImportParser.parse(in, new File(folder.getRoot(), name + "-work"));
        }
    }

    private static void assertOrders(ImportParser.Bundle bundle) {
        assertEquals(2, bundle.lists.size());
        ImportParser.ImportedList first = bundle.lists.get(0);
        assertEquals("וישלח תשפ\"ז", first.orderName);
        assertEquals(4, first.items.size());
        // סדר הקטגוריות כמו באתר (category.order).
        assertEquals("בטטות", first.items.get(0).name);
        assertEquals("ירקות", first.items.get(0).category);
        assertEquals("קווקר הכנה מהירה", first.items.get(1).name);
        assertEquals("2", first.items.get(1).quantity);
        assertEquals("קטניות ושימורים", first.items.get(1).category);
        assertEquals("הזמנה | שנייה לבדיקה", bundle.lists.get(1).orderName);
        assertEquals(2, bundle.lists.get(1).items.size());
    }

    @Test public void textFromExtensionImports() throws Exception {
        assertOrders(parse("export.txt"));
    }

    @Test public void jsonFromExtensionImports() throws Exception {
        ImportParser.Bundle bundle = parse("export.json");
        assertOrders(bundle);
        assertEquals("https://images.mishnatyosef.org/images/items/421_a.jpeg", bundle.lists.get(0).items.get(1).imageUrl);
        assertNotEquals(bundle.lists.get(0).accountId, bundle.lists.get(1).accountId);
    }

    /** כמה קודי QR שהודבקו יחד: חלקים של אותה הזמנה מתחברים, והקטגוריות נלקחות משורות "###". */
    @Test public void qrChunksFromExtensionImport() throws Exception {
        ImportParser.Bundle bundle = parse("export-qr.txt");
        assertOrders(bundle);
        assertEquals("ביצים", bundle.lists.get(0).items.get(3).category);
        assertEquals("קטניות ושימורים", bundle.lists.get(1).items.get(1).category);
    }

    /** קודי QR מהאפליקציה זהים לגמרי לקודים מהתוסף (אותם נתונים, אותה חלוקה). */
    @Test public void appQrChunksMatchExtension() throws Exception {
        org.json.JSONArray products = new org.json.JSONArray()
                .put(new org.json.JSONObject().put("key", "11").put("name", "בטטות").put("amount", "1").put("category", "ירקות"))
                .put(new org.json.JSONObject().put("key", "421").put("name", "קווקר הכנה מהירה").put("amount", "2").put("category", "קטניות ושימורים"))
                .put(new org.json.JSONObject().put("key", "6385").put("name", "שישיית נייר סופג").put("amount", "1").put("category", "טיפוח ונקיון"))
                .put(new org.json.JSONObject().put("key", "160").put("name", "ביצים גודל M").put("amount", "1").put("category", "ביצים"));
        org.json.JSONArray firstTwo = new org.json.JSONArray().put(products.get(0)).put(products.get(1));
        java.util.List<ListExporter.Export> exports = java.util.Arrays.asList(
                new ListExporter.Export("וישלח תשפ\"ז", products),
                new ListExporter.Export("הזמנה | שנייה\nלבדיקה", firstTwo));
        java.util.List<String> chunks = ListExporter.qrChunks(exports, 160);
        StringBuilder joined = new StringBuilder();
        for (String c : chunks) joined.append(joined.length() == 0 ? "" : "\n").append(c);
        String expected = new String(java.nio.file.Files.readAllBytes(fixture("export-qr.txt").toPath()), "UTF-8");
        assertEquals(expected, joined.toString());
    }

    @Test public void zipFromExtensionImportsWithImages() throws Exception {
        ImportParser.Bundle bundle = parse("export.zip");
        assertOrders(bundle);
        ImportParser.Item oats = bundle.lists.get(0).items.get(1);
        assertTrue(oats.image.startsWith("images/"));
        File image = bundle.imageFile(oats.image);
        assertNotNull(image);
        assertTrue(image.isFile());
        assertEquals(16, image.length());
        // תמונה מחוץ לשרת של משנת יוסף לא ירדה, ונשאר רק הקישור.
        ImportParser.Item eggs = bundle.lists.get(0).items.get(3);
        assertTrue(eggs.image == null || eggs.image.isEmpty());
    }
}
