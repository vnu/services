package edu.buffalo.cse.phonelab.launcher;

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import edu.buffalo.cse.phonelab.manifest.ManifestInterface;
import edu.buffalo.cse.phonelab.manifest.ManifestService;
import edu.buffalo.cse.phonelab.manifest.ManifestService.ManifestBinder;

public class LauncherService extends Service implements ManifestInterface {
	
	private String TAG = "LauncherService";
	
	ManifestService manifestService;
	
	private LauncherParameters currentLauncherParameters = null;
	
	private ScheduledThreadPoolExecutor checkServicesExecutor;
	private CheckServicesTask checkServicesTask;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
			
		checkServicesTask = new CheckServicesTask();
		
		Intent manifestServiceIntent = new Intent(this, ManifestService.class);
		bindService(manifestServiceIntent, manifestServiceConnection, Context.BIND_AUTO_CREATE);
		
		return START_STICKY;
	}
	
	private ServiceConnection manifestServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			ManifestBinder binder = (ManifestBinder) service;
			manifestService = binder.getService();
			manifestService.receiveManifestUpdates(LauncherService.this);
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			LauncherService.this.stopSelf();
		}
	};
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void remoteUpdate(String manifestString) {
		Log.v(TAG, manifestString);
		Serializer parameterDeserializer = new Persister();
		LauncherParameters newLauncherParameters;
		try {
			newLauncherParameters = parameterDeserializer.read(LauncherParameters.class, manifestString);
		} catch (Exception e) {
			Log.d(TAG, "Could not deserialize manifest string: " + e.toString());
			return;
		}
		if (currentLauncherParameters == null ||
			(!(currentLauncherParameters.equals(newLauncherParameters)))) {
			Log.i(TAG, "Launcher parameters have changed.");
			updateParameters(newLauncherParameters);
		} else {
			Log.v(TAG, "Launcher parameters are unchanged.");
		}
	}
	
	private void updateParameters(LauncherParameters newLauncherParameters) {
		
		if (currentLauncherParameters == null ||
				currentLauncherParameters.checkInterval == null ||
				(!(currentLauncherParameters.checkInterval.equals(newLauncherParameters.checkInterval)))) {
			
			int nextInterval;
			if (checkServicesExecutor != null) {
				nextInterval = newLauncherParameters.checkInterval;
				checkServicesExecutor.shutdown();
			} else {
				nextInterval = 0;
			}
			
			checkServicesExecutor = new ScheduledThreadPoolExecutor(1);
			checkServicesExecutor.scheduleAtFixedRate(checkServicesTask, nextInterval, newLauncherParameters.checkInterval, TimeUnit.SECONDS);
			
			Log.v(TAG, "Updated checkInterval to " + newLauncherParameters.checkInterval + ".");
		}
		
		currentLauncherParameters = newLauncherParameters;
		TAG = currentLauncherParameters.logTag;
	}
	
	private class CheckServicesTask implements Runnable {
		@Override
		public void run() {
			checkServices();
			Log.v(TAG, "CheckServicesTask ran.");
		}
	}
	
	private synchronized void checkServices() {
		HashSet<String> runningServices = new HashSet<String>();
		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
			
		for (RunningServiceInfo runningServiceInfo : activityManager.getRunningServices(Integer.MAX_VALUE)) {
			runningServices.add(runningServiceInfo.service.getClassName());
		}
		
		Iterator<String> startingServicesIterator = currentLauncherParameters.phoneLabRunningServices.iterator();
		while (startingServicesIterator.hasNext()) {
			String startingService = startingServicesIterator.next();
			if (runningServices.contains(startingService)) {
				Log.v(TAG, "PhoneLab service " + startingService + " is running.");
			} else {
				Log.i(TAG, "Starting PhoneLab service " + startingService);
				Intent startingIntent = new Intent(startingService);
				this.startService(startingIntent);
			}
		}
		
		Iterator<String> stoppingServicesIterator = currentLauncherParameters.phoneLabStoppedServices.iterator();
		while (stoppingServicesIterator.hasNext()) {
			String stoppingService = stoppingServicesIterator.next();
			if (!(runningServices.contains(stoppingService))) {
				Log.v(TAG, "PhoneLab service " + stoppingService + " is not running.");
			} else {
				Log.i(TAG, "Stopping PhoneLab service " + stoppingService);
				Intent stoppingIntent = new Intent(stoppingService);
				this.stopService(stoppingIntent);
			}
		}
	}
	
	@Override
	public String localUpdate() {
		// TODO Auto-generated method stub
		return null;
	}
}
