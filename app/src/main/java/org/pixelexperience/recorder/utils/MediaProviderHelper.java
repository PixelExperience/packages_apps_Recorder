package org.pixelexperience.recorder.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public final class MediaProviderHelper {
    private static final String TAG = "MediaProviderHelper";

    private MediaProviderHelper() {
    }

    public static void addVideoToContentProvider(
            @Nullable ContentResolver cr,
            @Nullable File file,
            @NonNull OnContentWritten listener) {
        if (cr == null || file == null) {
            return;
        }

        final ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Video.Media.TITLE, file.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Screen records");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        final Uri uri = cr.insert(MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) {
            Log.e(TAG, "Failed to insert " + file.getAbsolutePath());
            return;
        }

        new WriterTask(file, uri, cr, listener).execute();
    }

    public static void remove(@NonNull ContentResolver cr, @NonNull Uri uri) {
        try {
            cr.delete(uri, null, null);
        } catch (Exception ignored) {

        }
    }

    public interface OnContentWritten {
        void onContentWritten(@Nullable String uri);
    }

    @RequiresApi(29)
    static class WriterTask extends AsyncTask<Void, Void, String> {
        @NonNull
        private final File file;
        @NonNull
        private final Uri uri;
        @NonNull
        private final ContentResolver cr;
        @NonNull
        private final OnContentWritten listener;

        /* synthetic */ WriterTask(@NonNull File file,
                                   @NonNull Uri uri,
                                   @NonNull ContentResolver cr,
                                   @NonNull OnContentWritten listener) {
            this.file = file;
            this.uri = uri;
            this.cr = cr;
            this.listener = listener;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                final ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "w", null);
                if (pfd == null) {
                    return null;
                }
                final FileOutputStream oStream = new FileOutputStream(pfd.getFileDescriptor());
                oStream.write(Files.readAllBytes(file.toPath()));
                oStream.close();
                pfd.close();


                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, values, null, null);

                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete tmp file");
                }

                return uri.toString();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write into MediaStore", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            listener.onContentWritten(s);
        }
    }
}