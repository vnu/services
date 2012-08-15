package edu.buffalo.cse.phonelab.platform;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import edu.buffalo.cse.phonelab.launcher.LauncherParameters;
import edu.buffalo.cse.phonelab.manifest.ManifestInterface;
import edu.buffalo.cse.phonelab.manifest.ManifestService;
import edu.buffalo.cse.phonelab.manifest.ManifestService.ManifestBinder;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class PlatformService extends Service implements ManifestInterface {
	
	private String TAG = "PlatformService";
	
	ManifestService manifestService;
	
	private PlatformParameters currentPlatformParameters = null;
	
	private ServiceConnection manifestServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			ManifestBinder binder = (ManifestBinder) service;
			manifestService = binder.getService();
			manifestService.receiveManifestUpdates(PlatformService.this);
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			PlatformService.this.stopSelf();
		}
	};
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
				
		Intent manifestServiceIntent = new Intent(this, ManifestService.class);
		bindService(manifestServiceIntent, manifestServiceConnection, Context.BIND_AUTO_CREATE);
		
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void remoteUpdate(String manifestString) {
		Log.v(TAG, manifestString);
		Serializer parameterDeserializer = new Persister();
		PlatformParameters newPlatformParameters;
		try {
			newPlatformParameters = parameterDeserializer.read(PlatformParameters.class, manifestString);
		} catch (Exception e) {
			Log.d(TAG, "Could not deserialize manifest string: " + e.toString());
			return;
		}
		if (currentPlatformParameters == null ||
			(!(currentPlatformParameters.equals(newPlatformParameters)))) {
			Log.i(TAG, "Platform parameters have changed.");
			updateParameters(newPlatformParameters);
		} else {
			Log.v(TAG, "Platform parameters are unchanged.");
		}
	}
	
	private void updateParameters(PlatformParameters newParameters) {
		Log.v(TAG, Build.FINGERPRINT);
	}

	@Override
	public String localUpdate() {
		return null;
	}

}
