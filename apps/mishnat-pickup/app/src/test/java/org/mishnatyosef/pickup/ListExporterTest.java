package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** כל פורמט ייצוא חייב לחזור דרך ImportParser בלי לאבד רשימות, שמות, כמויות וקטגוריות. */
public class ListExporterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private List<ListExporter.Export> twoLists(File image) throws Exception {
        JSONArray first = new JSONArray()
                .put(new JSONObject().put("key", "a").put("name", "חלב | 3%").put("amount", "3")
                        .put("note", "שורה\nשנייה").put("category", "מוצרי חלב")
                        .put("image_url", "https://example.org/milk.jpg")
                        .put("image_file", image == null ? "" : image.getAbsolutePath()))
                .put(new JSONObject().put("key", "b").put("name", "לחם").put("amount", "1.5"));
        JSONArray second = new JSONArray()
                .put(new JSONObject().put("key", "c").put("name", "ביצים").put("amount", "12").put("category", "ביצים"));
        return Arrays.asList(new ListExporter.Export("מכירת חנוכה", first), new ListExporter.Export("שבת", second));
    }

    private ImportParser.Bundle parse(byte[] bytes) throws Exception {
        return ImportParser.parse(new ByteArrayInputStream(bytes), new File(folder.getRoot(), "work" + System.nanoTime()));
    }

    @Test public void jsonRoundTrip() throws Exception {
        JSONObject json = ListExporter.json(twoLists(null), null);
        ImportParser.Bundle bundle = parse(json.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(2, bundle.lists.size());
        assertEquals("מכירת חנוכה", bundle.lists.get(0).orderName);
        assertEquals("שבת", bundle.lists.get(1).orderName);
        assertEquals("3", bundle.lists.get(0).items.get(0).quantity);
        assertEquals("1.5", bundle.lists.get(0).items.get(1).quantity);
        assertEquals("מוצרי חלב", bundle.lists.get(0).items.get(0).category);
        assertNotEquals(bundle.lists.get(0).accountId, bundle.lists.get(1).accountId);
    }

    @Test public void textRoundTripKeepsEveryList() throws Exception {
        String text = ListExporter.text(twoLists(null));
        assertTrue(text.contains("## מכירת חנוכה"));
        ImportParser.Bundle bundle = parse(text.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, bundle.lists.size());
        ImportParser.ImportedList first = bundle.lists.get(0);
        assertEquals("מכירת חנוכה", first.orderName);
        assertEquals(2, first.items.size());
        assertEquals("חלב / 3%", first.items.get(0).name);     // | לא שובר עמודות
        assertEquals("3", first.items.get(0).quantity);
        assertEquals("שורה שנייה", first.items.get(0).note);   // ירידת שורה לא שוברת שורות
        assertEquals("מוצרי חלב", first.items.get(0).category);
        assertEquals("1.5", first.items.get(1).quantity);
        assertEquals("שבת", bundle.lists.get(1).orderName);
        assertEquals("12", bundle.lists.get(1).items.get(0).quantity);
    }

    @Test public void plainTextWithoutListHeadersStillImportsOneList() throws Exception {
        ImportParser.Bundle bundle = parse("חלב | 2\nלחם\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, bundle.lists.size());
        assertEquals(2, bundle.lists.get(0).items.size());
    }

    @Test public void zipRoundTripCarriesImages() throws Exception {
        File image = folder.newFile("cached_image");
        try (FileOutputStream out = new FileOutputStream(image)) {
            out.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4});
        }
        File zip = new File(folder.getRoot(), "lists.zip");
        ListExporter.zip(twoLists(image), zip);
        ImportParser.Bundle bundle;
        try (FileInputStream in = new FileInputStream(zip)) {
            bundle = ImportParser.parse(in, new File(folder.getRoot(), "zipwork"));
        }
        assertEquals(2, bundle.lists.size());
        ImportParser.Item milk = bundle.lists.get(0).items.get(0);
        assertEquals("images/img_0_0.jpg", milk.image);
        File extracted = bundle.imageFile(milk.image);
        assertNotNull(extracted);
        assertTrue(extracted.isFile());
        assertEquals(image.length(), extracted.length());
    }

    @Test(expected = Exception.class)
    public void emptyExportIsRejected() throws Exception {
        ListExporter.json(Arrays.asList(new ListExporter.Export("ריקה", new JSONArray())), null);
    }
}
