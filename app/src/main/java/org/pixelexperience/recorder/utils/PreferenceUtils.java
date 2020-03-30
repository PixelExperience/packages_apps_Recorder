package org.pixelexperience.recorder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

public class PreferenceUtils {

    public static final String PREFS = "preferences";
    public static final String PREF_SHOW_TOUCHES = "show_touches";
    public static final Boolean PREF_SHOW_TOUCHES_DEFAULT = false;

    SharedPreferences mSharedPrefs;
    Context mContext;

    public PreferenceUtils(Context context) {
        mContext = context;
        mSharedPrefs = context.getSharedPreferences(PREFS, 0);
    }

    public boolean canControlShowTouches() {
        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        return userManager.isAdminUser();
    }

    public boolean getShouldShowTouches() {
        return mSharedPrefs.getBoolean(PREF_SHOW_TOUCHES, PREF_SHOW_TOUCHES_DEFAULT);
    }

    public void setShouldShowTouches(boolean value) {
        mSharedPrefs.edit().putBoolean(PREF_SHOW_TOUCHES, value).apply();
    }
}
