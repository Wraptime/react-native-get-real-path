package com.rngrp;

import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Date;
import java.util.Random;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.database.Cursor;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

public class GRP extends ReactContextBaseJavaModule {

  public GRP(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "GRP";
  }

  private WritableMap makeErrorPayload(Exception ex) {
    WritableMap error = Arguments.createMap();
    error.putString("message", ex.getMessage());
    return error;
  }

  @ReactMethod
  public void getRealPathFromURI(String uriString, Callback callback) {
    Uri uri = Uri.parse(uriString);
    try {
      Context context = getReactApplicationContext();
      final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
      if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        if (isMediaDocument(uri)) {
          // http://www.banbaise.com/archives/745
          final String docId = DocumentsContract.getDocumentId(uri);
          final String[] split = docId.split(":");
          final String type = split[0];

          Uri contentUri = null;
          if ("image".equals(type)) {
              contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
          } else if ("video".equals(type)) {
              contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
          } else if ("audio".equals(type)) {
              contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
          }

          final String selection = "_id=?";
          final String[] selectionArgs = new String[] { split[1] };

          callback.invoke(null, getDataColumn(context, contentUri, selection, selectionArgs));
        } else if (isDownloadsDocument(uri)) {

          final String id = DocumentsContract.getDocumentId(uri);

          if (id.startsWith("raw:")) {
            callback.invoke(null, id.replaceFirst("raw:", ""));
          } else {
            String[] contentUriPrefixesToTry = new String[]{
                    "content://downloads/public_downloads",
                    "content://downloads/my_downloads",
                    "content://downloads/all_downloads"
            };

            String path = null;
            for (String contentUriPrefix : contentUriPrefixesToTry) {
              Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));
              try {
                path = getDataColumn(context, contentUri, null, null);
                if (path != null) {
                  break;
                }
              } catch (Exception e) {}
            }

            if (path == null) {
              long millis = System.currentTimeMillis();
              String datetime = new Date().toString();
              datetime = datetime.replace(" ", "");
              datetime = datetime.replace(":", "");
              final String displayName = random() + "_" + datetime + "_" + millis;

              path = writeFile(context, uri, displayName.replace(".", ""));
            }

            callback.invoke(null, path);
          }
        } else if (isExternalStorageDocument(uri)) {
          final String docId = DocumentsContract.getDocumentId(uri);
          final String[] split = docId.split(":");
          final String type = split[0];

          if ("primary".equalsIgnoreCase(type)) {
            callback.invoke(null, Environment.getExternalStorageDirectory() + "/" + split[1]);
          } else {
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = context.getContentResolver().query(uri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();

            callback.invoke(null, path);
          }
        }
      }
      else if ("content".equalsIgnoreCase(uri.getScheme())) {
        callback.invoke(null,getDataColumn(context, uri, null, null));
      }
      else if ("file".equalsIgnoreCase(uri.getScheme())) {
        callback.invoke(null, uri.getPath());
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  public static String random() {
    Random generator = new Random();
    StringBuilder randomStringBuilder = new StringBuilder();
    int randomLength = generator.nextInt(10);
    char tempChar;
    for (int i = 0; i < randomLength; i++){
      tempChar = (char) (generator.nextInt(96) + 32);
      randomStringBuilder.append(tempChar);
    }
    return randomStringBuilder.toString();
  }

  public static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }

  public static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }
  public static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }
  public static String getDataColumn(Context context, Uri uri, String selection,
                                     String[] selectionArgs) {
    // https://github.com/hiddentao/cordova-plugin-filepath/pull/6
    Cursor cursor = null;
    final String[] projection = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME};

    try {
      /* get `_data` */
      cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
      if (cursor != null && cursor.moveToFirst()) {
        final int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        /* bingo! */
        final String filepath = cursor.getString(column_index);
        return filepath;
      }
    } catch (Exception e) {
      if (cursor != null) {
        final int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
        final String displayName = cursor.getString(column_index);

        return writeFile(context, uri, displayName);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
    return null;
  }
  public static String writeFile(Context context, Uri uri, String displayName) {
    InputStream input = null;
    try {
      input = context.getContentResolver().openInputStream(uri);
      /* save stream to temp file */
      try {
        File file = new File(context.getCacheDir(), displayName);
        OutputStream output = new FileOutputStream(file);
        try {
          byte[] buffer = new byte[4 * 1024]; // or other buffer size
          int read;

          while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
          }
          output.flush();

          final String outputPath = file.getAbsolutePath();
          return outputPath;

        } finally {
          output.close();
        }
      } catch (Exception e1a) {
        //
      } finally {
        try {
          input.close();
        } catch (IOException e1b) {
          //
        }
      }
    } catch (FileNotFoundException e2) {
      //
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e3) {
          //
        }
      }
    }

    return null;
  }
}
