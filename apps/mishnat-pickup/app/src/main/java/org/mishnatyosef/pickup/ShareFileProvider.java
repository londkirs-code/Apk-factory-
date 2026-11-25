package org.mishnatyosef.pickup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * ספק קבצים זעיר לשיתוף (בלוטות' ואפליקציות אחרות), בלי ספריות חיצוניות.
 * מגיש לקריאה בלבד קבצים מתיקיית cache/share, ורק לאפליקציה שקיבלה הרשאה זמנית לכתובת.
 */
public final class ShareFileProvider extends ContentProvider {
    static final String AUTHORITY = "org.mishnatyosef.pickup.share";
    static final String DIR = "share";

    static File shareDir(Context context) {
        File dir = new File(context.getCacheDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static Uri uriFor(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith("."))
            throw new FileNotFoundException("שם קובץ לא תקין");
        File file = new File(shareDir(getContext()), name);
        if (!file.isFile()) throw new FileNotFoundException(name);
        return file;
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "text/plain"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) throw new FileNotFoundException("קריאה בלבד");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** שם וגודל הקובץ: אפליקציית הבלוטות' צריכה אותם כדי לשלוח. */
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File file;
        try { file = fileFor(uri); } catch (FileNotFoundException e) { return null; }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
