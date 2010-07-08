package com.corner23.android.findbooks;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Settings extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.getPreferenceManager().setSharedPreferencesName("Findbooks");
		addPreferencesFromResource(R.xml.preferences);
	}
}
