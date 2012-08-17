package edu.buffalo.cse.phonelab.services.launcher;

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ScheduledFuture;
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
import edu.buffalo.cse.phonelab.services.manifest.ManifestInterface;
import edu.buffalo.cse.phonelab.services.manifest.ManifestService;
import edu.buffalo.cse.phonelab.services.manifest.ManifestService.ManifestBinder;

/*
 * 15 Aug 2012 : GWA : Example of a simple PhoneLab service.
 * 
 * 1) It binds to the ManifestService and registers for manifest updates.
 * 2) It processes manifest updates, deserializing them into LauncherParameter objects using the simple library.
 * 3) It uses a periodic task to check that services are running or stopped as specified by the manifest.
 * 4) The task interval and logging tag are also controlled by the manifest.
 * 
 */

public class LauncherService extends Service implements ManifestInterface {
	
	/*
	 * 15 Aug 2012 : GWA : PhoneLab services should allow their logging tags to be controlled by a logTag manifest parameter like LauncherParameters.logTag. However,
	 * to enable logging before you receive a manifest update you can use an initialized TAG variable like this.
	 * 
	 * See updateParameters() below for how to overwrite this on a manifest update.
	 */
	
	private String TAG = "LauncherService";
	
	ManifestService manifestService;
	
	private LauncherParameters currentLauncherParameters = null;
	
	private ScheduledThreadPoolExecutor checkServicesExecutor;
	private ScheduledFuture<Void> checkServicesFuture;
	private CheckServicesRunnable checkServicesRunnable;
	
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
	public int onStartCommand(Intent intent, int flags, int startId) {
			
		checkServicesRunnable = new CheckServicesRunnable();
		checkServicesExecutor = new ScheduledThreadPoolExecutor(1);
		
		Intent manifestIntent = new Intent(this, ManifestService.class);
		bindService(manifestIntent, manifestServiceConnection, Context.BIND_AUTO_CREATE);
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		if (checkServicesExecutor != null) {
			checkServicesExecutor.shutdown();
		}
		
		LauncherParameters parameters = new LauncherParameters();
		parameters.stoppedServices.addAll(currentLauncherParameters.runningServices);
		parameters.stoppedServices.addAll(currentLauncherParameters.stoppedServices);
		checkServices();
	}
	
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
	
	@SuppressWarnings("unchecked")
	private void updateParameters(LauncherParameters newLauncherParameters) {
		
		if (currentLauncherParameters == null ||
				currentLauncherParameters.checkInterval == null ||
				(!(currentLauncherParameters.checkInterval.equals(newLauncherParameters.checkInterval)))) {
			
			/*
			 * 15 Aug 2012 : GWA : When started for the first time check immediately.
			 */
			
			int nextInterval;
			if (checkServicesFuture != null) {
				nextInterval = newLauncherParameters.checkInterval;
				checkServicesFuture.cancel(false);
			} else {
				nextInterval = 0;
			}
			
			checkServicesFuture = (ScheduledFuture<Void>) checkServicesExecutor.scheduleAtFixedRate(checkServicesRunnable, nextInterval, newLauncherParameters.checkInterval, TimeUnit.SECONDS);
			
			Log.v(TAG, "Updated checkInterval to " + newLauncherParameters.checkInterval + ".");
		}
		
		currentLauncherParameters = newLauncherParameters;
		TAG = currentLauncherParameters.logTag;
	}
	
	private class CheckServicesRunnable implements Runnable {
		@Override
		public void run() {
			checkServices();
			Log.v(TAG, "CheckServicesRunnable ran.");
		}
	}
	
	private synchronized void checkServices() {
		HashSet<String> runningServices = new HashSet<String>();
		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
			
		for (RunningServiceInfo runningServiceInfo : activityManager.getRunningServices(Integer.MAX_VALUE)) {
			runningServices.add(runningServiceInfo.service.getClassName());
		}
		
		Iterator<String> startingServicesIterator = currentLauncherParameters.runningServices.iterator();
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
		
		Iterator<String> stoppingServicesIterator = currentLauncherParameters.stoppedServices.iterator();
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
	
	/*
	 * 15 Aug 2012 : GWA : Not supported yet by the ManifestService.
	 */
	
	@Override
	public String localUpdate() {
		return null;
	}
}
