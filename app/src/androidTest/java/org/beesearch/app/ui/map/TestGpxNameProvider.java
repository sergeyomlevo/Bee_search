package org.beesearch.app.ui.map;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/** Test-APK provider runs in its own process, without the target APK's Kotlin runtime. */
public class TestGpxNameProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        String mode = uri.getQueryParameter("mode");
        if ("failure".equals(mode)) throw new IllegalArgumentException("provider unavailable");
        if ("missing".equals(mode)) {
            MatrixCursor cursor = new MatrixCursor(new String[]{"other_column"});
            cursor.addRow(new Object[]{"x"});
            return cursor;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{uri.getQueryParameter("name")});
        return cursor;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
