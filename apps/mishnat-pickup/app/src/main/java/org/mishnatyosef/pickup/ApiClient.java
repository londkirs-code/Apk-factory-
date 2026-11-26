package org.mishnatyosef.pickup;

import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** בקשות HTTP לשרת משנת יוסף. אינה שומרת מצב; הטוקן מועבר בכל קריאה. */
final class ApiClient {
    private ApiClient() {}

    static final String API = "https://v2.mishnatyosef.org";

    static HttpURLConnection openConnection(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (Build.VERSION.SDK_INT < 21 && connection instanceof javax.net.ssl.HttpsURLConnection) {
            javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(
                    new Tls12SocketFactory(context.getSocketFactory()));
        }
        return connection;
    }

    static HttpResult rawRequest(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = openConnection(new URL(API + path));
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(22000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Origin", "https://mishnatyosef.org");
        connection.setRequestProperty("Referer", "https://mishnatyosef.org/");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = (body == null ? new JSONObject() : body).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
        }
        connection.disconnect();
        return new HttpResult(code, text.toString());
    }

    static void ensureSuccess(HttpResult response) throws Exception {
        if (response.code == 401) throw new Exception("AUTH_REQUIRED: ההתחברות פגה או אינה תקפה.");
        if (response.code < 200 || response.code >= 300) {
            String message = "השרת החזיר שגיאה (" + response.code + ").";
            try {
                JSONObject error = new JSONObject(response.body);
                String detail = OrderParser.first(error, "message", "error", "title");
                if (!detail.isEmpty() && detail.length() < 180) message = detail;
            } catch (Exception ignored) { }
            throw new Exception(message);
        }
    }

    static String extractToken(String body) {
        try {
            JSONObject root = new JSONObject(body);
            String token = OrderParser.first(root, "access_token", "token");
            JSONObject data = root.optJSONObject("data");
            if (token.isEmpty() && data != null) token = OrderParser.first(data, "access_token", "token");
            return token;
        } catch (Exception e) { return ""; }
    }


}
