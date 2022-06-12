package com.softhouse.blueterm3.util;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.softhouse.blueterm3.R;

public class MyPreferenceFragment extends PreferenceFragment
{
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}