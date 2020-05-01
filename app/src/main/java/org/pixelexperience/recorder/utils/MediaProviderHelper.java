package org.pixelexperience.recorder.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;

public final class MediaProviderHelper {
    private static final String TAG = "MediaProviderHelper";

    private MediaProviderHelper() {
    }

    public static String addVideoToContentProvider(
            @Nullable ContentResolver cr,
            @Nullable File tempFile,
            @Nullable File file) {
        if (cr == null || file == null) {
            return null;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Video.Media.TITLE, file.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Screen records");
        values.put(MediaStore.Audio.Media.IS_PENDING, 0);

        final Uri uri = cr.insert(MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY), values);

        if (uri == null) {
            Log.e(TAG, "Failed to insert " + file.getAbsolutePath());
            return null;
        }

        try (OutputStream os = cr.openOutputStream(uri, "w")) {
            assert os != null;
            Files.copy(tempFile.toPath(), os);
            tempFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to insert " + file.getAbsolutePath());
            return null;
        }

        values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, values, null, null);

        return uri.toString();
    }

    public static void remove(@NonNull ContentResolver cr, @NonNull Uri uri) {
        try {
            cr.delete(uri, null, null);
        } catch (Exception ignored) {

        }
    }
}