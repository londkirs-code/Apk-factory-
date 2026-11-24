package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class CloudShareTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static JSONArray products() throws Exception {
        return new JSONArray()
                .put(new JSONObject().put("key", "p1").put("name", "חלב").put("amount", "3")
                        .put("note", "1 ליטר").put("category", "מוצרי חלב")
                        .put("image_url", "https://example.org/milk.jpg").put("image_file", "/data/x.jpg"))
                .put(new JSONObject().put("key", "p1").put("name", "לחם").put("amount", "abc")
                        .put("image_url", "http://insecure.example/bread.jpg"))
                .put(new JSONObject().put("key", "p3").put("name", "  ").put("amount", "2"));
    }

    @Test public void payloadKeepsOnlyProductFields() throws Exception {
        JSONObject payload = CloudShare.buildPayload("מכירת חנוכה", products());
        assertEquals(ImportParser.FORMAT, payload.getString("format"));
        JSONObject list = payload.getJSONArray("lists").getJSONObject(0);
        assertEquals("cloud", list.getJSONObject("account").getString("id"));
        assertEquals("מכירת חנוכה", list.getJSONObject("order").getString("name"));
        JSONArray items = list.getJSONArray("items");
        assertEquals(2, items.length());                     // שם ריק לא נשלח
        JSONObject milk = items.getJSONObject(0);
        assertEquals(3.0, milk.getDouble("quantity"), 0.0001);
        assertEquals("https://example.org/milk.jpg", milk.getString("imageUrl"));
        assertFalse(milk.has("image_file"));                // נתיב מקומי לא נשלח
        JSONObject bread = items.getJSONObject(1);
        assertEquals(1.0, bread.getDouble("quantity"), 0.0001); // כמות לא תקינה -> 1
        assertFalse(bread.has("imageUrl"));                 // רק https
        assertNotEquals(milk.getString("id"), bread.getString("id")); // מזהים ייחודיים
    }

    @Test(expected = Exception.class)
    public void emptyListIsRejected() throws Exception {
        CloudShare.buildPayload("ריקה", new JSONArray());
    }

    @Test public void fetchedListPassesTheImportParser() throws Exception {
        JSONObject payload = CloudShare.buildPayload("מכירת חנוכה", products());
        CloudShare.Fetched fetched = new CloudShare.Fetched(37, "מכירת חנוכה", 2, 1700000000000L, 1700600000000L, payload);
        JSONObject ready = CloudShare.prepareForImport(fetched);
        File work = new File(folder.getRoot(), "work");
        ImportParser.Bundle bundle = ImportParser.parse(
                new ByteArrayInputStream(ready.toString().getBytes(StandardCharsets.UTF_8)), work);
        assertEquals(1, bundle.lists.size());
        ImportParser.ImportedList list = bundle.lists.get(0);
        assertEquals("cloud-37-1700000000000", list.accountId);
        assertEquals("רשימה 37 מהענן", list.accountName);
        assertEquals("מכירת חנוכה", list.orderName);
        assertEquals(2, list.items.size());
        assertEquals("3", list.items.get(0).quantity);
        assertEquals("מוצרי חלב", list.items.get(0).category);
    }

    @Test public void missingServerGivesHebrewError() {
        try { CloudShare.call("", new JSONObject()); fail(); }
        catch (Exception e) { assertTrue(e.getMessage().contains("שרת הענן עוד לא הוגדר")); }
    }
}
