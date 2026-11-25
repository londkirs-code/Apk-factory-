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
