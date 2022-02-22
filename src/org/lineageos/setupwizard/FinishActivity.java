/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2017-2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.setupwizard;

import static android.os.Binder.getCallingUserHandle;

import static org.lineageos.setupwizard.Manifest.permission.FINISH_SETUP;
import static org.lineageos.setupwizard.SetupWizardApp.ACTION_SETUP_COMPLETE;
import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.ImageView;

import com.android.settingslib.display.DisplayDensityConfiguration;

import com.google.android.setupcompat.util.SystemBarHelper;
import com.google.android.setupcompat.util.WizardManagerHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
//import java.util.HashMap;

import org.lineageos.setupwizard.util.SetupWizardUtils;

/*
  Remember: "You should give a battery optimization exception to Google Play services for features
             like push notifications to work properly in the background. It isn't needed for the
             other 2 apps."

             [ https://grapheneos.org/usage#sandboxed-google-play ]

  I likely want to automate this either on install or elsewhere in the system, to make that package
    ineligible for battery optimization.
 */

public class FinishActivity extends BaseSetupWizardActivity {
    private static final String ACTION_INSTALL = "com.darshancomputing.install";

    private SetupWizardApp mSetupWizardApp;

    // Match order for these two, and keep in Graphene's recommended install order
    private static final String[] apkBasenames = {
        "com_google_android_gsf",
        "com_google_android_gms"
    };
    private static final int[] apkResIds = {
        R.raw.com_google_android_gsf,
        R.raw.com_google_android_gms
    };
    // private static final HashMap<String, Integer> fileRes;
    // static {
    //     fileRes = new HashMap<String, Integer>();
    //     fileRes.put("com_google_android_gsf", R.raw.com_google_android_gsf);
    //     fileRes.put("com_google_android_gms", R.raw.com_google_android_gms);
    // }



    /*

Okay, install-multiple is possible too:

In such a case that multiple packages need to be committed simultaneously, multiple sessions can be referenced by a single multi-package session. This session is created with no package name and calling SessionParams#setMultiPackage(). The individual session IDs can be added with addChildSessionId(int) and commit of the multi-package session will result in all child sessions being committed atomically.

     */

    private final BroadcastReceiver pmInstallerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            // For now, just log what we got.
            // Ultimately I may wait to call onNavigateNext() until success, and show error on failure.
            System.out.println("darshanos: pmInstallerReceiver got intent: " + intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=" + savedInstanceState);
        }
        mSetupWizardApp = (SetupWizardApp) getApplication();

        ContentResolver cr = getContentResolver();

        getSystemService(UiModeManager.class).setNightModeActivated(true);
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).setTimeZone("America/Los_Angeles");
        DisplayDensityConfiguration.setForcedDisplayDensity(Display.DEFAULT_DISPLAY, 460);
        Settings.Secure.putInt(cr, Settings.Secure.DOZE_ALWAYS_ON, 1);
        //Settings.Secure.putInt(cr, Settings.Secure.WAKE_GESTURE_ENABLED, 0);
        getSystemService(ColorDisplayManager.class).setColorMode(ColorDisplayManager.COLOR_MODE_NATURAL);
        Settings.Secure.putInt(cr, Settings.Secure.DOZE_PICK_UP_GESTURE, 0);
        Settings.Secure.putInt(cr, Settings.Secure.DOZE_ENABLED, 0);
        Settings.Secure.putInt(cr, Settings.Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED, 1);


        registerReceiver(pmInstallerReceiver, new IntentFilter(ACTION_INSTALL));

        // Maybe wait for completion of each before initiating install of next?

        for (int i = 0; i < apkResIds.length; i++)
            installPackage(i);
        //installPackage(getApkStream(i), apkBasenames[i].replace('_', '.') + ".apk");
        //installApk(i);

        onNavigateNext();
    }

    @Override
    protected int getLayoutResId() {
        return -1; // No layout
    }

    @Override
    public void finish() {
        super.finish();
    }

    @Override
    public void onNavigateNext() {
        applyForwardTransition(TRANSITION_ID_NONE);
        startFinishSequence();
    }

    private void startFinishSequence() {
        Intent i = new Intent(ACTION_SETUP_COMPLETE);
        i.setPackage(getPackageName());
        sendBroadcastAsUser(i, getCallingUserHandle(), FINISH_SETUP);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        SystemBarHelper.hideSystemBars(getWindow());
        completeSetup();
    }
    private void completeSetup() {
        //final WallpaperManager wallpaperManager = WallpaperManager.getInstance(mSetupWizardApp);
        //wallpaperManager.forgetLoadedWallpaper();
        finishAllAppTasks();
        SetupWizardUtils.enableStatusBar(this);
        Intent intent = WizardManagerHelper.getNextIntent(getIntent(), Activity.RESULT_OK);
        startActivityForResult(intent, NEXT_REQUEST);
    }

    // F-Droid
    // private void doPackageStage(Uri packageURI) {
    //     final PackageManager pm = getPackageManager();
    //     final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
    //                                                                                      PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    //     final PackageInstaller packageInstaller = pm.getPackageInstaller();
    //     PackageInstaller.Session session = null;
    //     try {
    //         final int sessionId = packageInstaller.createSession(params);
    //         final byte[] buffer = new byte[65536];
    //         session = packageInstaller.openSession(sessionId);
    //         final InputStream in = getContentResolver().openInputStream(packageURI);
    //         final OutputStream out = session.openWrite("PackageInstaller", 0, -1 /* sizeBytes, unknown */);
    //         try {
    //             int c;
    //             while ((c = in.read(buffer)) != -1) {
    //                 out.write(buffer, 0, c);
    //             }
    //             session.fsync(out);
    //         } finally {
    //             IoUtils.closeQuietly(in);
    //             IoUtils.closeQuietly(out);
    //         }
    //         // Create a PendingIntent and use it to generate the IntentSender
    //         Intent broadcastIntent = new Intent(BROADCAST_ACTION_INSTALL);
    //         PendingIntent pendingIntent = PendingIntent.getBroadcast(
    //                                                                  this /*context*/,
    //                                                                  sessionId,
    //                                                                  broadcastIntent,
    //                                                                  PendingIntent.FLAG_UPDATE_CURRENT);
    //         session.commit(pendingIntent.getIntentSender());
    //     } catch (IOException e) {
    //         Log.d(TAG, "Failure", e);
    //         Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
    //     } finally {
    //         IoUtils.closeQuietly(session);
    //     }
    // }

    // Android
    // private void startInstall() {
    //     Intent newIntent = new Intent();
    //     newIntent.putExtra(PackageInstaller.PackageUtil.INTENT_ATTR_APPLICATION_INFO, mPkgInfo.applicationInfo);
    //     newIntent.setData(mPackageURI);
    //     newIntent.setClass(this, InstallAppProgress.class);
    //     newIntent.putExtra(InstallAppProgress.EXTRA_MANIFEST_DIGEST, mPkgDigest);
    //     newIntent.putExtra(InstallAppProgress.EXTRA_INSTALL_FLOW_ANALYTICS, mInstallFlowAnalytics);
    //     String installerPackageName = getIntent().getStringExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME);
    //     if (mOriginatingURI != null) {
    //         newIntent.putExtra(Intent.EXTRA_ORIGINATING_URI, mOriginatingURI);
    //     }
    //     if (mReferrerURI != null) {
    //         newIntent.putExtra(Intent.EXTRA_REFERRER, mReferrerURI);
    //     }
    //     if (mOriginatingUid != VerificationParams.NO_UID) {
    //         newIntent.putExtra(Intent.EXTRA_ORIGINATING_UID, mOriginatingUid);
    //     }
    //     if (installerPackageName != null) {
    //         newIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, installerPackageName);
    //     }
    //     if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) {
    //         newIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
    //         newIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
    //     }

    //     startActivity(newIntent);
    // }

    // https://stackoverflow.com/a/4605040/1427098
    // Nope, still uses PackageUtil, I noticed as soon as I pasted it.
    // private void installAPKs() {
    //     Intent newIntent = new Intent();
    //     newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, mPkgInfo.applicationInfo);
    //     newIntent.setData(mPackageURI);
    //     newIntent.setClass(this, InstallAppProgress.class);
    //     String installerPackageName = getIntent().getStringExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME);
    //     if (installerPackageName != null) {
    //         newIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, installerPackageName);
    //     }
    //     startActivity(newIntent);
    // }

    // Based on https://stackoverflow.com/a/37153867/1427098
    // My preference would be to catch the IOException here...
    // May need to set Wizard up to be device owner, but I'm hoping being a system app is enough.
    //private boolean installPackage(InputStream in, String packageName) throws IOException {
    private void installPackage(int p) {
        String packageName = apkBasenames[p].replace('_', '.');
        InputStream in = getApkStream(p);

        PackageInstaller pmInstaller = getPackageManager().getPackageInstaller();
        int mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(mode);
        params.setAppPackageName(packageName);

        try {
            int sid = pmInstaller.createSession(params);
            PackageInstaller.Session session = pmInstaller.openSession(sid);
            OutputStream out = session.openWrite("darshanos", 0, -1);
            byte[] buffer = new byte[65536];
            int c;

            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }

            session.fsync(out);
            in.close();
            out.close();

            PendingIntent pi = PendingIntent.getBroadcast(this, sid, new Intent(ACTION_INSTALL),
                                                          PendingIntent.FLAG_IMMUTABLE);
            session.commit(pi.getIntentSender());
        } catch (IOException ioe) {
            System.out.println("darshanos: IOException setting up installation session: " + ioe);
        }


        // Oh, I think this is an action to have called upon completion, so maybe I can skip it, or else
        //  I'll have to set something up here for that.
        //Intent i = new Intent(ACTION_INSTALL);
        //PendingIntent pi = PendingIntent.getBroadcast(this, sessionId, i, 0);
        //session.commit(pi.getIntentSender());
    }

    private void installVending() {
    }

    // private InputStream getStream(String basename) {
    //     try {
    //         return openFileInput(basename + ".apk");
    //     } catch (java.io.FileNotFoundException e) {
    //         Integer res = fileRes.get(basename);
    //         if (res != null)
    //             return c.getResources().openRawResource(res);
    //         else
    //             return null;
    //     }
    // }

    //private void installApk(int i) {
    //}

    private InputStream getApkStream(int i) {
        try {
            return openFileInput(apkBasenames[i] + ".apk");
        } catch (java.io.FileNotFoundException e) {
            return getResources().openRawResource(apkResIds[i]);
        }
    }
}
