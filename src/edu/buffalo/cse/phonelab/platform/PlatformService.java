package edu.buffalo.cse.phonelab.platform;

import java.io.File;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.buffalo.cse.phonelab.manifest.ManifestInterface;
import edu.buffalo.cse.phonelab.manifest.ManifestService;
import edu.buffalo.cse.phonelab.manifest.ManifestService.ManifestBinder;

public class PlatformService extends Service implements ManifestInterface {
	
	private String TAG = "PlatformService";
	
	ManifestService manifestService;
	
	private PlatformParameters currentPlatformParameters = null;
	
	private File platformDirectory;
	
	private DownloadManager platformDownloadManager;
	private SharedPreferences platformSharedPreferences;
	private DownloadPlatformsTask downloadPlatformsTask;
	private ScheduledThreadPoolExecutor downloadPlatformsExecutor;
	
	private long currentPlatformDownloadID;
	private String currentPlatformDownloadKey = "PlatformService_currentPlatformDownloadID";
	private Object currentPlatformDownloadLock = new Object();
	
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
	
	private BroadcastReceiver platformDownloadReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			synchronized(currentPlatformDownloadLock) {
				if (currentPlatformDownloadID == 0) {
					return;
				}
				DownloadManager.Query platformDownloadQuery = new DownloadManager.Query();
				platformDownloadQuery.setFilterById(currentPlatformDownloadID);
				Cursor platformDownloadCursor = platformDownloadManager.query(platformDownloadQuery);
				if (!(platformDownloadCursor.moveToFirst())) {
					return;
				}
				
				int platformDownloadStatus = platformDownloadCursor.getInt(platformDownloadCursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
				String platformDownloadURI = platformDownloadCursor.getString(platformDownloadCursor.getColumnIndex(DownloadManager.COLUMN_URI));
				
				if (platformDownloadStatus == DownloadManager.STATUS_SUCCESSFUL) {
					currentPlatformDownloadID = 0;
					downloadPlatformsExecutor.execute(downloadPlatformsTask);
					Log.i(TAG, "Download of " + platformDownloadURI + " succeeded.");
				} else if (platformDownloadStatus == DownloadManager.STATUS_FAILED) {
					currentPlatformDownloadID = 0;
					downloadPlatformsExecutor.schedule(downloadPlatformsTask, currentPlatformParameters.failedRetryDelay, TimeUnit.SECONDS);
					Log.d(TAG, "Download of " + platformDownloadURI + " failed. Retrying in " + currentPlatformParameters.failedRetryDelay + " seconds.");
				}
				platformDownloadCursor.close();
			}
		}
		
	};
	
	private class DownloadPlatformsTask implements Runnable {
		@Override
		public void run() {
			downloadPlatforms();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		File externalStorageRoot = this.getExternalFilesDir(null);
		Log.v(TAG, externalStorageRoot.toString());
		platformDirectory = new File(externalStorageRoot, "/platform/");
		checkStorage();
		
		platformDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
		platformSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		currentPlatformDownloadID = platformSharedPreferences.getLong(currentPlatformDownloadKey, 0);
		
		downloadPlatformsExecutor = new ScheduledThreadPoolExecutor(1);
		downloadPlatformsTask = new DownloadPlatformsTask();
		
		IntentFilter platformDownloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
		registerReceiver(platformDownloadReceiver, platformDownloadFilter);
		
		Intent manifestServiceIntent = new Intent(this, ManifestService.class);
		bindService(manifestServiceIntent, manifestServiceConnection, Context.BIND_AUTO_CREATE);
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		Editor editor = platformSharedPreferences.edit();
		editor.putLong(currentPlatformDownloadKey, currentPlatformDownloadID);
		editor.commit();
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
	
	private void updateParameters(PlatformParameters newPlatformParameters) {
		currentPlatformParameters = newPlatformParameters;
		TAG = currentPlatformParameters.logTag;
		downloadPlatformsExecutor.execute(downloadPlatformsTask);
	}

	@Override
	public String localUpdate() {
		return null;
	}

	private void downloadPlatforms() {
		synchronized(currentPlatformDownloadLock) {
			assert currentPlatformParameters != null : currentPlatformParameters;
			
			if (!(checkStorage())) {
				Log.w(TAG, "Storage is not ready. Not downloading.");
				return;
			}
			
			if (currentPlatformDownloadID != 0) {
				Log.w(TAG, "Platform download in progress. Not downloading.");
			}
			
			PlatformDescription goldenPlatform = currentPlatformParameters.getGoldenPlatform();
			if (goldenPlatform != null) {
				if (retrievePlatform(goldenPlatform) == null) {
					Log.i(TAG, "Golden platform missing. Initiating download.");
					downloadPlatform(goldenPlatform);
					return;
				} else {
					Log.i(TAG, "Golden platform present.");
				}
			} else {
				Log.w(TAG, "Manifest missing golden platform.");
			}
		}
	}
	
	private File retrievePlatform(PlatformDescription platform) {
		
		File platformFile = new File(platformDirectory, platform.filename);
		if (!(platformFile.exists())) {
			return null;
		}		
		if (platformFile.length() != platform.size) {
			return null;
		}
		
		/*
		 * 15 Aug 2012 : GWA : TODO : Add md5 hash checking.
		 */
		
		return platformFile;
	}
	
		
	@SuppressWarnings("deprecation")
	private boolean downloadPlatform(PlatformDescription platform) {
		
		assert retrievePlatform(platform) == null : platform;
		assert currentPlatformDownloadID == 0 : currentPlatformDownloadID;
		
		File platformFile = new File(platformDirectory, platform.filename);
		
		DownloadManager.Request platformDownloadRequest = new DownloadManager.Request(Uri.parse(platform.url));
		
		platformDownloadRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
		platformDownloadRequest.setDestinationUri(Uri.fromFile(platformFile));
		platformDownloadRequest.setShowRunningNotification(false);
		platformDownloadRequest.setVisibleInDownloadsUi(false);
		
		currentPlatformDownloadID = platformDownloadManager.enqueue(platformDownloadRequest);
		
		Log.i(TAG, "Initiating download from " + platform.url + " to " + platformFile + ".");
		
		return true;
	}
	
	private boolean checkStorage() {
		
		String externalStorageState = Environment.getExternalStorageState();
		if (!(Environment.MEDIA_MOUNTED.equals(externalStorageState))) {
			Log.i(TAG, "External storage not available.");
			return false;
		}
		if (!(platformDirectory.exists())) {
			if (platformDirectory.mkdirs() != true) {
				Log.d(TAG, "Unable to create platform directory " + platformDirectory);
				return false;
			} else {
				Log.v(TAG, "Created platform directory " + platformDirectory);
			}
		}
		return true;
	}
}
