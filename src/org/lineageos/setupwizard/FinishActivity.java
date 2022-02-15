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
import android.app.UiModeManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.ImageView;

import com.android.settingslib.display.DisplayDensityConfiguration;

import com.google.android.setupcompat.util.SystemBarHelper;
import com.google.android.setupcompat.util.WizardManagerHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class FinishActivity extends BaseSetupWizardActivity {
    public static final String TAG = FinishActivity.class.getSimpleName();
    private SetupWizardApp mSetupWizardApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=" + savedInstanceState);
        }
        mSetupWizardApp = (SetupWizardApp) getApplication();

        getSystemService(UiModeManager.class).setNightModeActivated(true);
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).setTimeZone("America/Los_Angeles");
        DisplayDensityConfiguration.setForcedDisplayDensity(Display.DEFAULT_DISPLAY, 460);

        setNextText(R.string.start);

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
}
