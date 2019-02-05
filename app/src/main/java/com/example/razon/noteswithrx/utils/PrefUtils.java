package com.example.razon.noteswithrx.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.razon.noteswithrx.BuildConfig;
import com.example.razon.noteswithrx.app.Const;

public class PrefUtils {

    /**
     * Storing API Key in shared preferences to
     * add it in header part of every retrofit request
     */
    public PrefUtils() {
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
    }

    public static void storeApiKey(Context context, String apiKey) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putString(Const.API_KEY, apiKey);
        editor.apply();
    }

    public static String getApiKey(Context context) {
        return getSharedPreferences(context).getString(Const.API_KEY, null);
    }

}
