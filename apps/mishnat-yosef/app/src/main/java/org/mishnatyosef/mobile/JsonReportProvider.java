package org.mishnatyosef.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class JsonReportProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File resolveFile(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri == null) throw new FileNotFoundException("Provider unavailable");
        String name = uri.getLastPathSegment();
        File file;
        if ("mishnat-yosef-json-report.txt".equals(name)) {
            file = new File(new File(getContext().getCacheDir(), "json-reports"), name);
        } else if ("mishnat-yosef.apk".equals(name) || "משנת יוסף.apk".equals(name)) {
            file = new File(new File(getContext().getCacheDir(), "shared_apk"), name);
        } else {
            throw new FileNotFoundException("Unknown shared file");
        }
        if (!file.isFile() || !file.canRead() || file.length() <= 0) {
            throw new FileNotFoundException("Shared file missing or unreadable: " + name);
        }
        return file;
    }

    @Override public String getType(Uri uri) {
        String name = uri == null ? "" : uri.getLastPathSegment();
        if ("mishnat-yosef.apk".equals(name) || "משנת יוסף.apk".equals(name)) {
            return "application/vnd.android.package-archive";
        }
        if ("mishnat-yosef-json-report.txt".equals(name)) return "text/plain";
        return "application/octet-stream";
    }

    // Many share targets query OpenableColumns before opening a content URI.
    // Returning null here causes some apps to reject the shared attachment.
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = resolveFile(uri);
            String[] columns = projection == null
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                    : projection;
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            Object[] values = new Object[columns.length];
            String name = file.getName();
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = name;
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
                else values[i] = null;
            }
            cursor.addRow(values);
            return cursor;
        } catch (FileNotFoundException error) {
            return new MatrixCursor(projection == null
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                    : projection);
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolveFile(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
