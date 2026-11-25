package org.mishnatyosef.pickup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * תמונות המוצרים: מטמון גלובלי לכל האפליקציה (זיכרון + דיסק), לפי כתובת התמונה.
 * תמונה שהורדה פעם אחת לא מורדת שוב, בלי קשר לחשבון או לרשימה.
 */
final class ProductImages {
    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService imageLoader;
    private final ExecutorService cacheWorker;

    ProductImages(Context context, Handler mainHandler, ExecutorService imageLoader, ExecutorService cacheWorker) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
        this.imageLoader = imageLoader;
        this.cacheWorker = cacheWorker;
    }

    /** גודל מטמון התמונות בדיסק, בבתים. */
    long diskCacheBytes() {
        File dir = new File(context.getFilesDir(), "product_image_cache");
        File[] files = dir.listFiles();
        long total = 0;
        if (files != null) for (File file : files) total += file.length();
        return total;
    }

    /** מחיקת כל התמונות השמורות (בזיכרון ובדיסק). הן יורדו מחדש לפי הצורך. */
    void clear() {
        imageCache.evictAll();
        failedImages.clear();
        File dir = new File(context.getFilesDir(), "product_image_cache");
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
    }

    // זיכרון לתמונות: שמינית מהזיכרון הזמין לאפליקציה (לפחות 8MB).
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(
            (int) Math.max(8L * 1024 * 1024, Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8))) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    // Persistent URL-keyed image cache: rendering never waits for image network requests.
    private final Object imageTargetsLock = new Object();

    private final Set<String> imageDownloads = new HashSet<>();

    private final Map<String, List<WeakReference<ImageView>>> imageTargets = new HashMap<>();

    private boolean downloadImageToFile(String url, File destination) {
        HttpURLConnection connection = null;
        try {
            connection = ApiClient.openConnection(new URL(url));
            connection.setConnectTimeout(12000); connection.setReadTimeout(18000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return false;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192]; int n; long total = 0;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 8L * 1024L * 1024L) return false;
                    out.write(buffer, 0, n);
                }
                out.getFD().sync();
            }
            Bitmap bitmap = BitmapFactory.decodeFile(destination.getAbsolutePath());
            if (bitmap == null) return false;            bitmap.recycle();
            return destination.length() > 0;
        } catch (Exception e) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    void loadLegacy(ProductRow product, ImageView target) {
        final String imageUrl = product.imageUrl == null ? "" : product.imageUrl;
        final String viewTag = imageUrl.isEmpty() ? "legacy:" + product.imagePath : imageUrl;
        // A URL-keyed cache entry always wins over the old per-order image file.
        // Previously the legacy branch decoded that file again on every screen rebuild.
        if (!imageUrl.isEmpty()) {
            Bitmap memory = imageCache.get(imageUrl);
            if (memory != null) {
                target.setTag(imageUrl);
                target.setImageBitmap(memory);
                return;
            }
            File persistent = cacheFile(imageUrl);
            if (persistent != null && persistent.isFile() && persistent.length() > 0) {
                load(imageUrl, target);
                return;
            }
        }
        target.setImageDrawable(Ui.photoPlaceholder(context));
        target.setTag(viewTag);
        imageLoader.execute(() -> {
            File source = new File(product.imagePath);
            Bitmap bitmap = decode(source);
            if (bitmap == null) {
                if (!imageUrl.isEmpty()) mainHandler.post(() -> {
                    if (viewTag.equals(target.getTag())) load(imageUrl, target);
                });
                return;
            }
            if (!imageUrl.isEmpty()) {
                File cached = cacheFile(imageUrl);
                if (cached != null && !cached.isFile()) {
                    File dir = cached.getParentFile();
                    File temp = new File(dir, cached.getName() + ".tmp");
                    try {
                        if (!dir.exists()) dir.mkdirs();
                        try (FileInputStream in = new FileInputStream(source);
                             FileOutputStream out = new FileOutputStream(temp)) {
                            byte[] buffer = new byte[8192]; int count; long total = 0;
                            while ((count = in.read(buffer)) != -1) {
                                total += count;
                                if (total > 8L * 1024L * 1024L) throw new Exception("image too large");
                                out.write(buffer, 0, count);
                            }
                            out.getFD().sync();
                        }
                        if (temp.renameTo(cached)) imageCache.put(imageUrl, bitmap);
                        else temp.delete();
                    } catch (Exception ignored) { temp.delete(); }
                } else imageCache.put(imageUrl, bitmap);
            }
            mainHandler.post(() -> {
                if (viewTag.equals(target.getTag())) target.setImageBitmap(bitmap);
            });
        });
    }

    /**
     * מטמון התמונות גלובלי לכל האפליקציה: תיקייה אחת, לפי כתובת התמונה, בלי קשר לחשבון, למקור
     * או לרשימה. תמונה שהורדה פעם אחת נטענת מהדיסק בכל רשימה ובכל חשבון, ולא מורדת שוב.
     * המטמון אינו נמחק בהחלפת חשבון, בהתנתקות או בסנכרון.
     */
    File cacheFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        File dir = new File(context.getApplicationContext().getFilesDir(), "product_image_cache");
        return new File(dir, DataGuard.sha256(imageUrl.trim()) + ".img");
    }

    /**
     * קובץ התמונה במטמון; אם עוד לא הורדה - מוריד עכשיו (לקריאה מחוט רקע בלבד, למשל בהכנת ZIP).
     * מחזיר null כשאין תמונה או שההורדה נכשלה.
     */
    File cachedOrDownload(String imageUrl) {
        File file = cacheFile(imageUrl);
        if (file == null) return null;
        if (file.isFile() && file.length() > 0) return file;
        if (!imageUrl.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://")) return null;
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return null;
        File temp = new File(dir, file.getName() + ".zip.tmp");
        if (!downloadImageToFile(imageUrl.trim(), temp)) { temp.delete(); return null; }
        if (file.exists()) file.delete();
        if (!temp.renameTo(file)) { temp.delete(); return null; }
        return file;
    }

    /** כתובות שההורדה שלהן נכשלה לאחרונה, כדי לא לנסות שוב בכל ציור מחדש של הרשימה. */
    private final Map<String, Long> failedImages = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long IMAGE_RETRY_MS = 10 * 60 * 1000L;

    private boolean recentlyFailed(String imageUrl) {
        Long at = failedImages.get(imageUrl);
        if (at == null) return false;
        if (System.currentTimeMillis() - at > IMAGE_RETRY_MS) { failedImages.remove(imageUrl); return false; }
        return true;
    }

    /** מוריד מראש לדיסק את כל תמונות הרשימה שעוד אינן שמורות (גם בתצוגה ללא תמונות), כדי שיהיו זמינות גם בלי אינטרנט. */
    void prefetch(List<ProductRow> rows) {
        if (rows == null) return;
        final List<String> urls = new ArrayList<>();
        for (ProductRow row : rows) {
            if (row.imageUrl == null || row.imageUrl.isEmpty() || urls.contains(row.imageUrl)) continue;
            File file = cacheFile(row.imageUrl);
            if (file != null && file.isFile() && file.length() > 0) continue;
            urls.add(row.imageUrl);
        }
        if (urls.isEmpty()) return;
        cacheWorker.execute(() -> {
            for (String url : urls) {
                if (recentlyFailed(url)) continue;
                synchronized (imageTargetsLock) { if (!imageDownloads.add(url)) continue; }
                try {
                    File target = cacheFile(url);
                    if (target == null || (target.isFile() && target.length() > 0)) continue;
                    File dir = target.getParentFile();
                    if (dir != null && !dir.exists()) dir.mkdirs();
                    File temp = new File(dir, target.getName() + ".pre");
                    if (downloadImageToFile(url, temp) && temp.renameTo(target)) failedImages.remove(url);
                    else { temp.delete(); failedImages.put(url, System.currentTimeMillis()); }
                } finally {
                    // כרטיסים שחיכו לתמונה הזו בזמן ההורדה המוקדמת מקבלים אותה עכשיו מהדיסק.
                    List<WeakReference<ImageView>> waiting;
                    synchronized (imageTargetsLock) { imageDownloads.remove(url); waiting = imageTargets.remove(url); }
                    if (waiting != null && !waiting.isEmpty()) mainHandler.post(() -> {
                        for (WeakReference<ImageView> reference : waiting) {
                            ImageView view = reference.get();
                            if (view != null && url.equals(view.getTag())) load(url, view);
                        }
                    });
                }
            }
        });
    }

    Bitmap decode(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int targetPixels = Math.max(120, Ui.imageSize(context));
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetPixels
                && bounds.outHeight / (sample * 2) >= targetPixels) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    void load(String imageUrl, ImageView target) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            target.setImageDrawable(Ui.photoPlaceholder(context));
            return;
        }
        target.setTag(imageUrl);
        Bitmap cached = imageCache.get(imageUrl);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageDrawable(Ui.photoPlaceholder(context));
        File onDisk = cacheFile(imageUrl);
        if (recentlyFailed(imageUrl) && (onDisk == null || !onDisk.isFile())) return;

        // Deduplicate both disk decoding and network downloads by URL. Repeated
        // renders attach their ImageView to the same in-flight cache operation.
        synchronized (imageTargetsLock) {
            List<WeakReference<ImageView>> targets = imageTargets.get(imageUrl);
            if (targets == null) { targets = new ArrayList<>(); imageTargets.put(imageUrl, targets); }
            boolean alreadyAttached = false;
            for (WeakReference<ImageView> reference : targets) {
                if (reference.get() == target) { alreadyAttached = true; break; }
            }
            if (!alreadyAttached) targets.add(new WeakReference<>(target));
            if (!imageDownloads.add(imageUrl)) return;
        }

        imageLoader.execute(() -> {
            HttpURLConnection connection = null;
            Bitmap bitmap = null;
            File temp = null;
            File diskFile = cacheFile(imageUrl);
            try {
                // First try the permanent URL cache. Only a missing or corrupt entry
                // is allowed to trigger a network request.
                if (diskFile != null && diskFile.isFile() && diskFile.length() > 0) {
                    bitmap = decode(diskFile);
                    if (bitmap == null) diskFile.delete();
                }
                if (bitmap == null) {
                    File dir = diskFile.getParentFile();
                    if (!dir.exists() && !dir.mkdirs())
                        throw new Exception("image cache directory unavailable");
                    temp = new File(dir, diskFile.getName() + ".tmp");
                    connection = ApiClient.openConnection(new URL(imageUrl));
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(9000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setInstanceFollowRedirects(true);
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new Exception("image HTTP " + code);
                    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[8192]; int read; long total = 0;
                        while ((read = in.read(buffer)) != -1) {
                            total += read;
                            if (total > 6L * 1024L * 1024L) throw new Exception("image too large");
                            out.write(buffer, 0, read);
                        }
                        out.getFD().sync();
                    }
                    bitmap = decode(temp);
                    if (bitmap == null) throw new Exception("invalid image");
                    if (diskFile.exists()) diskFile.delete();
                    if (!temp.renameTo(diskFile)) throw new Exception("cannot publish image cache");
                    temp = null;
                }
                imageCache.put(imageUrl, bitmap);
            } catch (Exception ignored) {
                if (bitmap != null && !imageCache.snapshot().containsKey(imageUrl)) bitmap.recycle();
                if (temp != null) temp.delete();
                bitmap = null;
                failedImages.put(imageUrl, System.currentTimeMillis());
            } finally {
                if (connection != null) connection.disconnect();
                List<WeakReference<ImageView>> targets;
                synchronized (imageTargetsLock) {
                    imageDownloads.remove(imageUrl);
                    targets = imageTargets.remove(imageUrl);
                }
                final Bitmap result = bitmap;
                if (result != null && targets != null) mainHandler.post(() -> {
                    for (WeakReference<ImageView> reference : targets) {
                        ImageView view = reference.get();
                        if (view != null && imageUrl.equals(view.getTag())) view.setImageBitmap(result);
                    }
                });
            }
        });
    }
}
