package edu.buffalo.cse.phonelab.services.statusmonitor;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import edu.buffalo.cse.phonelab.services.launcher.LauncherParameters;
import edu.buffalo.cse.phonelab.services.manifest.ManifestInterface;
import edu.buffalo.cse.phonelab.services.manifest.ManifestService;
import edu.buffalo.cse.phonelab.services.manifest.ManifestService.ManifestBinder;

public class StatusMonitorService extends Service implements ManifestInterface {
	
	private String TAG="StatusMonitorService";
	ManifestService manifestService;
	private StatusMonitorParameters currentStatusMonitorParameters = null;
	
	private ScheduledThreadPoolExecutor checkServicesExecutor;
	private ScheduledFuture<Void> checkServicesFuture;
	private CheckServicesRunnable checkServicesRunnable;
	
   private ServiceConnection manifestServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			ManifestBinder binder = (ManifestBinder) service;
			manifestService = binder.getService();
			manifestService.receiveManifestUpdates(StatusMonitorService.this);
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			StatusMonitorService.this.stopSelf();
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
	
	private class CheckServicesRunnable implements Runnable {
		@Override
		public void run() {
			//checkServices();
			Log.v(TAG, "CheckServicesRunnable ran.");
		}
	}
	
	

	@Override
	public void remoteUpdate(String manifestString) {
		Log.v(TAG, manifestString);
		Serializer parameterDeserializer = new Persister();
		StatusMonitorParameters newStatusMonitorParameters;
		
		try {
			newStatusMonitorParameters = parameterDeserializer.read(StatusMonitorParameters.class, manifestString);
		} catch (Exception e) {
			Log.d(TAG, "Could not deserialize manifest string: " + e.toString());
			return;
		}
	}

	@Override
	public String localUpdate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
