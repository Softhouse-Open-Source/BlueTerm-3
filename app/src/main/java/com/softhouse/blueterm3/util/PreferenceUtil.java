package com.softhouse.blueterm3.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by Abid on 15/03/2022.
 */

public class PreferenceUtil  {


    public static final String AGREE_PRIVACY_POLICY = "agree_privacy_policy";
    public static final String PREFERENCE_NAME = "PREFERENCE_NAME";


    private static PreferenceUtil instance;

    private SharedPreferences sPref;

    private PreferenceUtil(Context context) {
        sPref = context.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);
    }

    public static PreferenceUtil getInstance(Context context) {
        if (instance == null) {
            instance = new PreferenceUtil(context);
        }
        return instance;
    }

    public boolean isAgreePrivacyPolicy() {
        return sPref.getBoolean(AGREE_PRIVACY_POLICY, false);
    }

    public void setAgreePrivacyPolicy(boolean value) {
        SharedPreferences.Editor editor = sPref.edit();
        editor.putBoolean(AGREE_PRIVACY_POLICY, value);
        editor.apply();
    }




}
