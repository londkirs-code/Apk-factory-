package org.mishnatyosef.pickup;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** טעינת תשובות שרת לדוגמה (על בסיס תשובות אמיתיות, בלי מידע רגיש). */
final class TestData {
    private TestData() {}

    static String text(String name) throws Exception {
        try (InputStream in = TestData.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("missing resource " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static JSONObject json(String name) throws Exception { return new JSONObject(text(name)); }

    /** ההזמנה הראשונה בעמוד ההיסטוריה לדוגמה. */
    static JSONObject firstHistoryOrder() throws Exception {
        return json("orders_page.json").getJSONArray("data").getJSONObject(0);
    }
}
