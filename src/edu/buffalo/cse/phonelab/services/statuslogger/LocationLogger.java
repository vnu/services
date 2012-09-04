package edu.buffalo.cse.phonelab.services.statuslogger;

import java.util.Formatter;
import java.util.Locale;

import android.location.Location;
import android.util.Log;

public class LocationLogger {

	public static double mLatitude;
	public static double mLongitude;
	public static float mAccuracy;
	public static String mProvider;
	public static float mSpeed;
	public static long mLocationFixTime;

	public static void updateLocation(Location location) {
		Log.i("Status-passive", "Update Successful");
		mLatitude = location.getLatitude();
		mLongitude = location.getLongitude();
		mAccuracy = location.getAccuracy();
		mProvider = location.getProvider();
		mSpeed = location.getSpeed();
		mLocationFixTime = location.getTime();
		Log.i("Status-active", "Update Successful with Latitude : " + mLatitude + " Prov : " + mProvider);
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		Formatter formatter = new Formatter(stringBuilder, Locale.US);
		formatter.format("Location Fix: %s, Latitude: %s, Longitude: %s, Accuracy: %s, Speed:%s, Provider: %s", mLocationFixTime, mLatitude, mLongitude, mAccuracy, mSpeed,
				mProvider);
		formatter.close();
		return stringBuilder.toString();
	}

}
