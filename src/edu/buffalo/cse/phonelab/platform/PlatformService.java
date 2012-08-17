package edu.buffalo.cse.phonelab.platform;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
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
import android.telephony.SignalStrength;
import android.util.Log;
import edu.buffalo.cse.phonelab.manifest.ManifestInterface;
import edu.buffalo.cse.phonelab.manifest.ManifestService;
import edu.buffalo.cse.phonelab.manifest.ManifestService.ManifestBinder;

public class PlatformService extends Service implements ManifestInterface {
	
	private String TAG = "PlatformService";
	
	ManifestService manifestService;
	
	private PlatformParameters currentPlatformParameters;
	
	public File platformImageDirectory;
	public DownloadManager platformImagesDownloadManager;
	
	private SharedPreferences platformSharedPreferences;	
	private DownloadPlatformImagesCallable downloadPlatformImagesCallable;
	private ScheduledFuture<Void> downloadPlatformImagesFuture;
	
	private ScheduledThreadPoolExecutor downloadPlatformImagesExecutor;
	
	private long currentPlatformImageDownloadID;
	private String currentPlatformImageDownloadKey = "PlatformService_currentPlatformDownloadID";
	private Object currentPlatformImageDownloadLock = new Object();
	
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
			synchronized(currentPlatformImageDownloadLock) {
				
				DownloadManager.Query query = new DownloadManager.Query();
				long[] downloadIDs = getDownloadIDs();
				if (downloadIDs.length == 0) {
					return;
				}
				query.setFilterById(downloadIDs);
				Cursor cursor = platformImagesDownloadManager.query(query);
				
				if (!(cursor.moveToFirst())) {
					return;
				}
				do {
					long downloadID = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
					PlatformImage platform = getPlatformByDownloadID(downloadID);
					assert platform != null;
					
					int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
						
					if (status == DownloadManager.STATUS_SUCCESSFUL) {
						
						platform.downloadID = null;
						platform.available = true;
						Log.i(TAG, "Download of " + platform.uri + " succeeded.");
						
						try {
							platform.valid = true;
							String fileFingerprint = platform.getFileFingerprint();
							if (!(platform.fingerprint.equals(fileFingerprint))) {
								Log.i(TAG, "Update fails fingerprint test: " + platform.fingerprint + " != " + fileFingerprint);
								platform.valid = false;
							} else {
								Log.i(TAG, "Update passes fingerprint test.");
							}
							
							String fileHash = platform.getFileHash();
							if (!(platform.hash.equals(fileHash))) {
								Log.i(TAG, "Update fails hash test: " + platform.hash + " != " + fileHash);
								platform.valid = false;
							} else {
								Log.i(TAG, "Update passes hash test");
							}
							
							if (platform.valid) {
								Log.i(TAG, "Update passes fingerprint and hash tests.");
							}
						} catch (Exception e) {
							Log.i(TAG, "Fingerprint or hash caused exception.");
							platform.valid = false;
						}
							
						downloadPlatformImagesFuture = downloadPlatformImagesExecutor.schedule(downloadPlatformImagesCallable, 0, TimeUnit.SECONDS);
						
					} else if (status == DownloadManager.STATUS_FAILED) {
						platform.downloadID = null;
						platform.available = false;
						downloadPlatformImagesFuture = downloadPlatformImagesExecutor.schedule(downloadPlatformImagesCallable, currentPlatformParameters.failedRetryDelay, TimeUnit.SECONDS);
						Log.d(TAG, "Download of " + platform.uri + " failed. Retrying in " + currentPlatformParameters.failedRetryDelay + " seconds.");
					} else if (status == DownloadManager.STATUS_RUNNING) {
						platform.available = true;
					}
				} while (cursor.moveToNext());
				
				cursor.close();
			}
		}
		
	};
	
	private class DownloadPlatformImagesCallable implements Callable<Void> {
		public Void call() {
			downloadPlatforms();
			downloadPlatformImagesFuture = null;
			return null;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		File externalStorageRoot = this.getExternalFilesDir(null);
		Log.v(TAG, externalStorageRoot.toString());
		platformImageDirectory = new File(externalStorageRoot, "/platform/");
		checkStorage();
		
		platformImagesDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
		platformSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		currentPlatformImageDownloadID = platformSharedPreferences.getLong(currentPlatformImageDownloadKey, 0);
		currentPlatformParameters = new PlatformParameters();
		
		downloadPlatformImagesExecutor = new ScheduledThreadPoolExecutor(1);
		downloadPlatformImagesCallable = new DownloadPlatformImagesCallable();
		
		IntentFilter platformDownloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
		registerReceiver(platformDownloadReceiver, platformDownloadFilter);
		
		Intent manifestServiceIntent = new Intent(this, ManifestService.class);
		bindService(manifestServiceIntent, manifestServiceConnection, Context.BIND_AUTO_CREATE);
		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		Editor editor = platformSharedPreferences.edit();
		editor.putLong(currentPlatformImageDownloadKey, currentPlatformImageDownloadID);
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
			Log.v(TAG, currentPlatformParameters.toString());
			Log.v(TAG, newPlatformParameters.toString());
			updateParameters(newPlatformParameters);
		} else {
			Log.v(TAG, "Platform parameters are unchanged.");
		}
	}
	
	private void updateParameters(PlatformParameters newPlatformParameters) {
		TAG = newPlatformParameters.logTag;
		
		/*
		 * 16 Aug 2012 : GWA : We save state that the manifest does not, so avoid losing it here.
		 */
		
		for (PlatformImage platform : newPlatformParameters.platforms) {
			if (!(currentPlatformParameters.platforms.contains(platform))) {
				currentPlatformParameters.platforms.add(platform);
			}
		}
		
		/*
		 * 16 Aug 2012 : GWA : Should we really remove images this aggressively?
		 */
		
		for (PlatformImage platform : currentPlatformParameters.platforms) {
			if (!(newPlatformParameters.platforms.contains(platform))) {
				platform.removeFile();
			}
		}
		
		Log.v(TAG, newPlatformParameters.platforms.size() + " " + currentPlatformParameters.platforms.size());
		newPlatformParameters.platforms = new ArrayList<PlatformImage>(currentPlatformParameters.platforms);
		Log.v(TAG, newPlatformParameters.platforms.size() + " " + currentPlatformParameters.platforms.size());
		currentPlatformParameters = newPlatformParameters;
		Log.v(TAG, newPlatformParameters.platforms.size() + " " + currentPlatformParameters.platforms.size());
		
		if (downloadPlatformImagesFuture != null) {
			downloadPlatformImagesFuture.cancel(false);
		}
		downloadPlatformImagesFuture = downloadPlatformImagesExecutor.schedule(downloadPlatformImagesCallable, 0, TimeUnit.SECONDS);
	}

	@Override
	public String localUpdate() {
		return null;
		/*
		Serializer serializer = new Persister();
		StringWriter writer = new StringWriter();
		try {
			serializer.write(currentPlatformParameters, writer);
		} catch (Exception e) {
			Log.d(TAG, "Unable to generate local manifest: " + e);
		}
		String localManifest = writer.toString();
		Log.v(TAG, localManifest);
		return localManifest;*/
	}

	private void downloadPlatforms() {
		synchronized(currentPlatformImageDownloadLock) {
			assert currentPlatformParameters != null : currentPlatformParameters;
			
			if (!(checkStorage())) {
				Log.w(TAG, "Storage is not ready. Not downloading.");
				return;
			}
			
			if (currentPlatformImageDownloadID != 0) {
				Log.w(TAG, "Platform download in progress. Not downloading.");
			}
			
			PlatformImage goldenPlatform = getGoldenPlatform();
			
			if (goldenPlatform != null) {
				if (!(goldenPlatform.valid)) {
					Log.i(TAG, "Golden platform missing or invalid. Initiating download.");
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
		
	private boolean downloadPlatform(PlatformImage platform) {
		
		assert platform.downloadID == null : platform;
		
		File tempFileName;
		
		try {
			platform.file = File.createTempFile("image", ".zip", platformImageDirectory);
			tempFileName = new File(platform.file.getPath());
			platform.file.delete();
			platform.file = tempFileName;
		} catch (IOException e) {
			return false;
		}
		
		DownloadManager.Request request = new DownloadManager.Request(Uri.parse(platform.uri));
		
		request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
		request.setDestinationUri(Uri.fromFile(platform.file));
		request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
		request.setVisibleInDownloadsUi(false);
		
		platform.downloadID = platformImagesDownloadManager.enqueue(request);

		Log.i(TAG, "Initiating download from " + platform.uri + " to " + platform.file + ".");
		
		return true;
	}
	
	private boolean checkStorage() {
		
		String externalStorageState = Environment.getExternalStorageState();
		if (!(Environment.MEDIA_MOUNTED.equals(externalStorageState))) {
			Log.i(TAG, "External storage not available.");
			return false;
		}
		
		if (!(platformImageDirectory.exists())) {
			if (platformImageDirectory.mkdirs() != true) {
				Log.d(TAG, "Unable to create platform directory " + platformImageDirectory);
				return false;
			} else {
				Log.v(TAG, "Created platform directory " + platformImageDirectory);
			}
		}
		
		return true;
	}
	
	public PlatformImage getGoldenPlatform() {
		for (PlatformImage platform : currentPlatformParameters.platforms) {
			if (platform.golden) {
				return platform;
			}
		}
		return null;
	}
	
	public long[] getDownloadIDs() {
		
		ArrayList<Long> downloadIDsArray = new ArrayList<Long>();
		for (PlatformImage platform : currentPlatformParameters.platforms) {
			if (platform.downloadID != null) {
				downloadIDsArray.add(platform.downloadID);
			}
		}
		
		long[] downloadIDs = new long[downloadIDsArray.size()];
		for (int i = 0; i < downloadIDsArray.size(); i++) {
			downloadIDs[i] = downloadIDsArray.get(i);
		}
		
		return downloadIDs;
	}
	
	public PlatformImage getPlatformByDownloadID(long downloadID) {
		for (PlatformImage platform : currentPlatformParameters.platforms) {
			if (platform.downloadID != null &&
					platform.downloadID == downloadID) {
				return platform;
			}
		}
		return null;
	}
	
	
}
