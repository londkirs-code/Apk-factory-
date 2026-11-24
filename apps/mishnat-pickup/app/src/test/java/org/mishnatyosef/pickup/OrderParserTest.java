package org.mishnatyosef.pickup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class OrderParserTest {
    private List<ProductRow> sampleProducts() throws Exception {
        JSONObject order = OrderParser.unwrapOrder(TestData.json("order_detail.json"));
        return OrderParser.parseProducts(order);
    }

    @Test public void productsAreParsedFromItems() throws Exception {
        List<ProductRow> rows = sampleProducts();
        assertEquals(4, rows.size());
        ProductRow quaker = rows.get(0);
        assertEquals("421", quaker.key);
        assertEquals("קווקר הכנה מהירה", quaker.name);
        assertEquals("2", quaker.amount);
        assertEquals("https://images.mishnatyosef.org/images/items/421_a.jpeg", quaker.imageUrl);
    }

    @Test public void absoluteImageUrlsAreKept() throws Exception {
        assertEquals("https://images.example/160.jpg", sampleProducts().get(3).imageUrl);
    }

    @Test public void categoryNameAndOrderAreRead() throws Exception {
        ProductRow quaker = sampleProducts().get(0);
        assertEquals("קטניות ושימורים", quaker.category);
        assertEquals(25, quaker.categoryOrder);
    }

    @Test public void sortingFollowsTheSiteCategoryOrder() throws Exception {
        List<ProductRow> rows = sampleProducts();
        OrderParser.sortByCategory(rows, null);
        assertEquals("ירקות", rows.get(0).category);            // order 14
        assertEquals("קטניות ושימורים", rows.get(1).category);  // order 25
        assertEquals("טיפוח ונקיון", rows.get(2).category);     // order 31
        assertEquals("ביצים", rows.get(3).category);            // order 200
    }

    @Test public void productsWithoutCategoryGoLast() throws Exception {
        List<ProductRow> rows = new java.util.ArrayList<>();
        rows.add(new ProductRow("a", "בלי מחלקה", "", "1", "", "", ""));
        rows.add(new ProductRow("b", "ירק", "", "1", "", "", "ירקות", 14));
        OrderParser.sortByCategory(rows, null);
        assertEquals("b", rows.get(0).key);
        assertEquals("a", rows.get(1).key);
    }

    @Test public void latestOrderSkipsCancelled() throws Exception {
        JSONArray orders = TestData.json("orders_page.json").getJSONArray("data");
        JSONObject latest = OrderParser.chooseLatestPickupOrder(orders);
        assertNotNull(latest);
        assertEquals(26394765L, OrderParser.extractOrderId(latest));
    }

    @Test public void orderIdIsFoundInEnvelopes() throws Exception {
        assertEquals(9L, OrderParser.extractOrderId(new JSONObject("{\"data\":{\"order\":{\"id\":9}}}")));
        assertEquals(-1L, OrderParser.extractOrderId(null));
    }
}
