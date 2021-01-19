/*
 * Copyright (C) 2020-2021 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of SmartPack Kernel Manager, which is a heavily modified version of Kernel Adiutor,
 * originally developed by Willi Ye <williye97@gmail.com>
 *
 * Both SmartPack Kernel Manager & Kernel Adiutor are free softwares: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SmartPack Kernel Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SmartPack Kernel Manager.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.smartpack.kernelmanager.utils.tools;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import androidx.core.content.FileProvider;

import com.smartpack.kernelmanager.BuildConfig;
import com.smartpack.kernelmanager.R;
import com.smartpack.kernelmanager.utils.Utils;
import com.smartpack.kernelmanager.views.dialog.Dialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on January 16, 2020
 */

public class UpdateCheck {

    private static final String LATEST_VERSION_URL = "https://raw.githubusercontent.com/SmartPack/SmartPack-Kernel-Manager/beta/app/src/main/assets/release.json";
    private static final String LATEST_VERSION_APK = Utils.getInternalDataStorage() + "/" + BuildConfig.APPLICATION_ID + ".apk";

    public static void getVersionInfo(Context context) {
        Utils.prepareInternalDataStorage();
        Utils.downloadFile(releaseInfo(context), LATEST_VERSION_URL, context);
    }

    public static int versionCode(Context context) {
        try {
            JSONObject obj = new JSONObject(getReleaseInfo(context));
            return (obj.getInt("latestVersionCode"));
        } catch (JSONException e) {
            return BuildConfig.VERSION_CODE;
        }
    }

    private static String getUrl(Context context) {
        try {
            JSONObject obj = new JSONObject(getReleaseInfo(context));
            return (obj.getString("releaseUrl"));
        } catch (JSONException e) {
            return BuildConfig.VERSION_NAME;
        }
    }

    private static String versionName(Context context) {
        try {
            JSONObject obj = new JSONObject(getReleaseInfo(context));
            return (obj.getString("latestVersion"));
        } catch (JSONException e) {
            return BuildConfig.VERSION_NAME;
        }
    }

    private static String getChangeLogs(Context context) {
        try {
            JSONObject obj = new JSONObject(getReleaseInfo(context));
            return (obj.getString("releaseNotes"));
        } catch (JSONException e) {
            return "Unavailable";
        }
    }

    public static String getChecksum(Context context) {
        try {
            JSONObject obj = new JSONObject(getReleaseInfo(context));
            return (obj.getString("sha1"));
        } catch (JSONException e) {
            return "Unavailable";
        }
    }

    private static void getLatestApp(Context context) {
        Utils.prepareInternalDataStorage();
        Utils.downloadFile(LATEST_VERSION_APK, getUrl(context), context);
    }

    public static boolean hasVersionInfo(Context context) {
        return Utils.existFile(releaseInfo(context));
    }

    public static long lastModified(Context context) {
        return new File(releaseInfo(context)).lastModified();
    }

    private static String releaseInfo(Context context) {
        return context.getFilesDir().getPath() + "/release";
    }

    private static String getReleaseInfo(Context context) {
        return Utils.readFile(releaseInfo(context));
    }

    public static void updateAvailableDialog(Context context) {
        new Dialog(context)
                .setTitle(context.getString(R.string.update_available, UpdateCheck.versionName(context)))
                .setMessage(getChangeLogs(context))
                .setCancelable(false)
                .setNegativeButton(context.getString(R.string.cancel), (dialog, id) -> {
                })
                .setPositiveButton(context.getString(R.string.get_it), (dialog, id) -> {
                    updaterTask(context);
                })
                .show();
    }

    private static void updaterTask(Context context) {
        new AsyncTask<Void, Void, Void>() {
            private ProgressDialog mProgressDialog;
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setMessage(context.getString(R.string.downloading_update, context.
                        getString(R.string.app_name) + " v" + versionName(context) + " ..."));
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }
            @Override
            protected Void doInBackground(Void... voids) {
                getLatestApp(context);
                return null;
            }
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                try {
                    mProgressDialog.dismiss();
                } catch (IllegalArgumentException ignored) {
                }
                if (Utils.existFile(LATEST_VERSION_APK) && Utils.getChecksum(LATEST_VERSION_APK).contains(getChecksum(context))) {
                    installUpdate(context);
                } else {
                    new Dialog(context)
                            .setMessage(context.getString(R.string.download_failed))
                            .setNegativeButton(context.getString(R.string.cancel), (dialog, id) -> {
                            })
                            .show();
                }
            }
        }.execute();
    }

    public static void manualUpdateCheck(Activity activity) {
        if (activity.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            getVersionInfo(activity);
            if (UpdateCheck.hasVersionInfo(activity) && BuildConfig.VERSION_CODE < UpdateCheck.versionCode(activity)) {
                updateAvailableDialog(activity);
            } else {
                new Dialog(activity)
                        .setMessage(activity.getString(R.string.updated_dialog))
                        .setNegativeButton(activity.getString(R.string.ok), (dialog, id) -> {
                        })
                        .show();
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private static void installUpdate(Context context) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uriFile;
        uriFile = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider",
                new File(LATEST_VERSION_APK));
        intent.setDataAndType(uriFile, "application/vnd.android.package-archive");
        context.startActivity(Intent.createChooser(intent, ""));
    }

    /*
     * Based on the ApkSignatureVerifier.java in https://github.com/f-droid/fdroidclient
     * Ref: https://raw.githubusercontent.com/f-droid/fdroidclient/master/app/src/main/java/org/fdroid/fdroid/installer/ApkSignatureVerifier.java
     */
    public static boolean isSignatureMatched(Context context) {
        String mKey = "[48, -126, 3, -69, 48, -126, 2, -93, -96, 3, 2, 1, 2, 2, 4, 83, 28, 18, 69, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5, 0, 48, -127, -115, 49, 14, 48, 12, 6, 3, 85, 4, 6, 19, 5, 73, 110, 100, 105, 97, 49, 15, 48, 13, 6, 3, 85, 4, 8, 19, 6, 75, 101, 114, 97, 108, 97, 49, 14, 48, 12, 6, 3, 85, 4, 7, 19, 5, 75, 111, 99, 104, 105, 49, 18, 48, 16, 6, 3, 85, 4, 10, 19, 9, 83, 109, 97, 114, 116, 80, 97, 99, 107, 49, 33, 48, 31, 6, 3, 85, 4, 11, 19, 24, 83, 109, 97, 114, 116, 80, 97, 99, 107, 45, 75, 101, 114, 110, 101, 108, 32, 109, 97, 110, 97, 103, 101, 114, 49, 35, 48, 33, 6, 3, 85, 4, 3, 19, 26, 83, 117, 110, 105, 32, 80, 97, 117, 108, 32, 77, 97, 116, 104, 101, 119, 32, 77, 101, 110, 97, 99, 104, 101, 114, 121, 48, 30, 23, 13, 49, 55, 49, 48, 50, 54, 49, 49, 49, 57, 50, 48, 90, 23, 13, 52, 50, 49, 48, 50, 48, 49, 49, 49, 57, 50, 48, 90, 48, -127, -115, 49, 14, 48, 12, 6, 3, 85, 4, 6, 19, 5, 73, 110, 100, 105, 97, 49, 15, 48, 13, 6, 3, 85, 4, 8, 19, 6, 75, 101, 114, 97, 108, 97, 49, 14, 48, 12, 6, 3, 85, 4, 7, 19, 5, 75, 111, 99, 104, 105, 49, 18, 48, 16, 6, 3, 85, 4, 10, 19, 9, 83, 109, 97, 114, 116, 80, 97, 99, 107, 49, 33, 48, 31, 6, 3, 85, 4, 11, 19, 24, 83, 109, 97, 114, 116, 80, 97, 99, 107, 45, 75, 101, 114, 110, 101, 108, 32, 109, 97, 110, 97, 103, 101, 114, 49, 35, 48, 33, 6, 3, 85, 4, 3, 19, 26, 83, 117, 110, 105, 32, 80, 97, 117, 108, 32, 77, 97, 116, 104, 101, 119, 32, 77, 101, 110, 97, 99, 104, 101, 114, 121, 48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -126, 1, 15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -93, -67, -12, 34, 23, -76, 100, -49, 117, 10, 42, 53, -19, 54, 110, -24, -109, 107, -128, -75, 80, 58, 56, 97, -65, 3, -98, -69, 111, 104, 23, 13, -40, 1, -42, 54, -1, 77, 125, 93, 85, 14, -118, -35, -71, 5, 123, -69, 23, -102, 9, -40, 52, -38, -24, -5, 85, 101, -112, -98, -71, 97, -84, -66, 76, 52, 86, 78, -55, -113, -40, 108, 110, 32, 106, -69, -107, 91, -18, -7, 59, -94, -37, -68, 97, 70, -5, 48, -22, -8, 113, 107, 96, -124, 127, 13, -61, -122, -45, -89, 3, -55, 41, -7, -89, -61, 11, -36, 9, -11, -111, -105, -5, -7, -115, 41, -67, 68, 55, 107, -19, 115, -92, -74, -116, -64, 11, -112, -75, -104, 95, 79, -106, -105, 16, 2, -79, 87, 70, 115, 73, -126, -15, 127, -92, 123, -83, -23, -107, -24, -36, -68, 6, 99, 107, -105, -1, 16, 99, 113, -82, 95, -47, 6, -95, -8, -18, -40, -104, 22, 21, 104, -26, -103, 97, 97, -19, -93, -103, -63, 61, 71, -103, -92, 95, -42, -118, 2, -99, 37, -15, -120, -84, 1, 69, -65, 6, -82, 70, -62, 86, 34, 19, -127, -109, -49, 89, 7, 46, 3, 123, -116, 127, -73, -77, 89, -22, -76, -63, 40, 123, -48, -124, -87, -93, -127, -68, 16, 43, 2, 59, 52, -63, 36, 111, 68, 119, 96, -10, 86, 7, 80, 35, 29, 28, -125, 95, 112, -101, 82, -117, 56, -45, -75, -97, 95, 2, 3, 1, 0, 1, -93, 33, 48, 31, 48, 29, 6, 3, 85, 29, 14, 4, 22, 4, 20, 57, 125, -58, -16, 30, 48, -4, -79, -11, 127, -18, -102, 77, 126, 122, 56, 118, 83, -57, 70, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5, 0, 3, -126, 1, 1, 0, -123, 24, -8, -90, 49, 117, 25, 86, -40, 42, 93, 126, -104, 89, -117, -76, -118, 92, -56, 111, 105, 65, -106, 102, -9, 110, -46, -67, 15, 0, 99, -10, 127, -113, 82, -65, 21, -71, -89, 104, 126, -113, 95, 25, -97, -12, -9, -64, -2, 83, -84, 100, -21, -84, -92, -97, 61, -17, 67, -98, -66, 79, -91, 12, -15, 106, 9, 11, -22, -112, -28, -60, -75, -73, -72, 69, -118, -5, -45, 10, -25, 86, -65, 113, 76, 112, 59, 39, -87, 5, 57, 117, 102, 82, -43, 72, 83, 50, 52, -26, 49, -120, 15, -58, 13, -91, 76, 114, 93, -36, -46, -64, 23, 85, -70, -97, 124, -75, 12, 71, -5, 44, -81, 80, 40, 50, 126, -21, -127, 127, 116, -120, 97, -55, -121, 37, -111, 102, 83, -17, 9, -108, -37, -99, 20, -3, -39, -74, -15, -91, 25, 98, 36, -116, 118, 22, 116, -114, 60, 15, 49, 105, 123, 94, 29, 114, 12, -20, -61, -62, -71, 104, 48, 91, 63, -110, -54, -18, 94, -45, -11, -51, -111, -123, -75, -8, 36, -58, 88, -15, -116, 12, 4, 95, 16, 92, 61, -22, 12, 56, 5, 37, 44, -47, 123, 104, -98, -111, 21, -114, -121, 127, -64, -58, -9, -93, 63, -116, 83, -31, 61, -99, 34, 6, 19, -94, 69, 47, -106, -61, -115, -47, -50, -97, -28, -39, -54, 41, -48, -125, 120, 93, 84, 109, -84, 9, -83, 110, 119, 68, 72, -76, -53, 63, -52, 75]";
        String mKeyAPK = Arrays.toString(Objects.requireNonNull(getSignature(context.getPackageName(), context)));
        return mKey.equals(mKeyAPK);
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static byte[] getSignature(String packageid, Context context) {
        try {
            PackageInfo pkgInfo = context.getPackageManager().getPackageInfo(packageid, PackageManager.GET_SIGNATURES);
            return signatureToBytes(pkgInfo.signatures);
        } catch (PackageManager.NameNotFoundException ignored) {}
        return null;
    }

    private static byte[] signatureToBytes(Signature[] signatures) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (Signature sig : signatures) {
            try {
                outputStream.write(sig.toByteArray());
            } catch (IOException ignored) {}
        }
        return outputStream.toByteArray();
    }

}