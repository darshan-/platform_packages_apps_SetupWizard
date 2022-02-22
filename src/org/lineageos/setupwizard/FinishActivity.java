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
//import java.util.Queue;
import java.util.LinkedList;

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
    private PackageInstaller pmInstaller;

    //private static int installCount = 0;
    //private Queue sessionQueue;
    //private Queue<int> sessionQueue;
    private LinkedList<Integer> sessionQueue;

    // Match order for these two, and keep in Graphene's recommended install order
    private static final String[] apkBasenames = {
        "com_google_android_gsf",
        "com_google_android_gms"
    };
    private static final int[] apkResIds = {
        R.raw.com_google_android_gsf,
        R.raw.com_google_android_gms
    };

    // Match order for these two
    private static final String[] vapkBasenames = {
        "vending_0",
        "vending_1",
        "vending_2",
        "vending_3",
        "vending_4"
    };
    private static final int[] vapkResIds = {
        R.raw.vending_0,
        R.raw.vending_1,
        R.raw.vending_2,
        R.raw.vending_3,
        R.raw.vending_4
    };

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

            if (! ACTION_INSTALL.equals(intent.getAction()))
                return;

            //installCount--;

            //int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -99); // 0, -1 and small positive already used
            // Ahem... What if... What if we're meant to assume success?  And that's why it's 0, which of course I first
            //    typed, before it occurred to me a moment later that that was unsafe, and I should check if 0 was used,
            //    and it was... for success...  Well, let's see how this goes:
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
            System.out.println("darshanos: install status: " + status);

            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            System.out.println("darshanos: install message: " + message);

            if (status == PackageInstaller.STATUS_SUCCESS) {
                if (sessionQueue.size() > 0) {
                    System.out.println("darshanos: commiting next session because sessionQueue.size(): " + sessionQueue.size());
                    //System.out.println("darshanos: committing next session.");
                    commitSession(sessionQueue.remove());
                } else {
                    System.out.println("darshanos: all sessions commited; ending setup wizard.");
                    //onNavigateNext();
                }
            } else {
                System.out.println("darshanos: some status other than success; ending setup wizard.");
                //onNavigateNext();
            }

            //System.out.println("darshanos: installCount: " + installCount);

            //if (installCount == 0)
            //    onNavigateNext();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("darshanos: FinishActivity.onCreate");
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

        //sessionQueue = new Queue<PackageInstaller.Session>();
        //sessionQueue = new Queue<int>();
        sessionQueue = new LinkedList<Integer>();
        pmInstaller = getPackageManager().getPackageInstaller();
        try {
            for (int i = 0; i < apkResIds.length; i++)
                installPackage(i);

            installVending();
        } catch (IOException ioe) {
            System.out.println("darshanos: IOException setting up installation sessions: " + ioe);
        }

        System.out.println("darshanos: committing first session with full sessionQueue.size(): " + sessionQueue.size());
        commitSession(sessionQueue.remove());
        //installPackage(getApkStream(i), apkBasenames[i].replace('_', '.') + ".apk");
        //installApk(i);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(pmInstallerReceiver);
        new Exception("Stack trace").printStackTrace();
        System.out.println("darshanos: FinishActivity.onDestroy");
        super.onDestroy();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.finish_activity;
        //return -1; // No layout
    }

    @Override
    public void onNavigateNext() {
        System.out.println("darshanos: onNavigateNext");
        //unregisterReceiver(pmInstallerReceiver);
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
        finishAllAppTasks();
        SetupWizardUtils.enableStatusBar(this);
        Intent intent = WizardManagerHelper.getNextIntent(getIntent(), Activity.RESULT_OK);
        startActivityForResult(intent, NEXT_REQUEST);
    }

    // Based on https://stackoverflow.com/a/37153867/1427098
    private void installPackage(int p) throws IOException {
        //InputStream in = getApkStream(p);
        // int sid = makeInstallSession();
        // PackageInstaller.Session session = pmInstaller.openSession(sid);
        // addApkToSession(session, in);

        int sid = makeSession(getApkStream(p));
        //PackageInstaller.Session session = pmInstaller.openSession(sid);w
        //sessionQueue.add(session);
        sessionQueue.add(sid);
        System.out.println("darshanos: Addeded normal package p=" + p + " with sid: " + sid);
        System.out.println("darshanos: sessionQueue.size(): " + sessionQueue.size());
        //PendingIntent pi = PendingIntent.getBroadcast(this, sid, new Intent(ACTION_INSTALL), PendingIntent.FLAG_IMMUTABLE);
        //installCount++;
        //session.commit(pi.getIntentSender());
    }

    private void commitSession(int sid) {
        try {
            PackageInstaller.Session session = pmInstaller.openSession(sid);
            PendingIntent pi = PendingIntent.getBroadcast(this, sid, new Intent(ACTION_INSTALL), PendingIntent.FLAG_IMMUTABLE);
            session.commit(pi.getIntentSender());
        } catch (IOException ioe) {
            System.out.println("darshanos: IOException committing installation sessions: " + ioe);
        }
    }

    // private void installPackage(int p) throws IOException {
    //     InputStream in = getApkStream(p);
    //     //PackageInstaller.Session session = makeInstallSession();
    //     int sid = makeInstallSession();
    //     PackageInstaller.Session session = pmInstaller.openSession(sid);

    //     addApkToSession(session, in);

    //     PendingIntent pi = PendingIntent.getBroadcast(this, sid, new Intent(ACTION_INSTALL),
    //                                                   PendingIntent.FLAG_IMMUTABLE);
    //     session.commit(pi.getIntentSender());
    // }

    // private PackageInstaller.Session makeInstallSession() throws IOException {
    //     PackageInstaller pmInstaller = getPackageManager().getPackageInstaller();
    //     int mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
    //     PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(mode);
    //     int sid = pmInstaller.createSession(params);
    //     return pmInstaller.openSession(sid);
    // }

    // Call needs both Session id and Session, but we can't get an id from a Session (Session doesn't know its
    //   own id), but we *can* get a session from an id, so it's dumb, but I guess best, to return the id.
    private int makeInstallSession() throws IOException {
        PackageInstaller.SessionParams params = makeIncompleteInstallSession();
        return pmInstaller.createSession(params);
    }

    // private int makeInstallSession() throws IOException {
    //     PackageInstaller pmInstaller = getPackageManager().getPackageInstaller();
    //     int mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
    //     PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(mode);
    //     return pmInstaller.createSession(params);
    // }

    private PackageInstaller.SessionParams makeIncompleteInstallSession() throws IOException {
        int mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
        return new PackageInstaller.SessionParams(mode);
    }

    private int makeParentInstallSession() throws IOException {
        PackageInstaller.SessionParams params = makeIncompleteInstallSession();
        params.setMultiPackage();
        return pmInstaller.createSession(params);
    }

    private void addApkToSession(PackageInstaller.Session session, InputStream in) throws IOException {
        OutputStream out = session.openWrite("darshanos", 0, -1);
        byte[] buffer = new byte[65536];
        int c;

        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }

        session.fsync(out);
        in.close();
        out.close();
    }

    private int makeSession(InputStream in) throws IOException {
        int sid = makeInstallSession();
        PackageInstaller.Session session = pmInstaller.openSession(sid);

        addApkToSession(session, in);
        return sid;
    }

    // private int addVendingPackage(int p) {
    //     int sid = makeSession(getVapkStream(p));
    //     InputStream in = getVapkStream(p);
    //     int sid = makeInstallSession();
    //     PackageInstaller.Session session = pmInstaller.openSession(sid);

    //     addApkToSession(session, in);
    //     return sid;
    // }

    // private int addVendingPackage(int p) {
    //     InputStream in = getVapkStream(p);
    //     int sid = makeInstallSession();
    //     PackageInstaller.Session session = pmInstaller.openSession(sid);

    //     addApkToSession(session, in);
    //     return sid;
    // }

    private void installVending() throws IOException {
        int psid = makeParentInstallSession();
        PackageInstaller.Session psession = pmInstaller.openSession(psid);

        for (int i = 0; i < vapkResIds.length; i++) {
            int sid = makeSession(getVapkStream(i));
            psession.addChildSessionId(sid);
        }

        //PendingIntent pi = PendingIntent.getBroadcast(this, psid, new Intent(ACTION_INSTALL), PendingIntent.FLAG_IMMUTABLE);
        //installCount++;
        //psession.commit(pi.getIntentSender());
        sessionQueue.add(psid);
        System.out.println("darshanos: Addeded vending package with sid: " + psid);
        System.out.println("darshanos: sessionQueue.size(): " + sessionQueue.size());
    }

    private InputStream getApkStream(int i) {
        try {
            return openFileInput(apkBasenames[i] + ".apk");
        } catch (java.io.FileNotFoundException e) {
            return getResources().openRawResource(apkResIds[i]);
        }
    }

    private InputStream getVapkStream(int i) {
        try {
            return openFileInput(vapkBasenames[i] + ".apk");
        } catch (java.io.FileNotFoundException e) {
            return getResources().openRawResource(vapkResIds[i]);
        }
    }
}
