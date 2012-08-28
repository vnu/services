package edu.buffalo.phonelab.cse.services.statuslogger;

import android.location.Location;
import android.util.Log;

public class LocationLogger {
	
	public static String mLatitude;
	public static String mLongitude;
	public static String mAccuracy;
	public static String mProvider;
	public static String mSpeed;
	public static String mLocationFixTime;
	
	public static void updateLocation(Location location) {
		Log.i("Status-passive","Update Successful");
		mLatitude = String.valueOf(location.getLatitude());
		mLongitude = String.valueOf(location.getLongitude());
		mAccuracy = String.valueOf(location.getAccuracy());
		mProvider = location.getProvider();
		mSpeed = String.valueOf(location.getSpeed());
		mLocationFixTime = String.valueOf(location.getTime());
		Log.i("Status-active","Update Successful with Latitude : "+mLatitude+" Prov : "+mProvider);

	}

}
