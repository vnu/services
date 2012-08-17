package edu.buffalo.cse.phonelab.services.manifest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import edu.buffalo.cse.phonelab.services.launcher.LauncherService;

public class ManifestService extends Service implements ManifestInterface {
	
	private String TAG = "PhoneLab.ManifestService";
	
	private final IBinder manifestBinder = new ManifestBinder();
	
	private class ManifestReceiver {
		public ManifestReceiver(ManifestInterface receiver, Node node) {
			this.receiver = receiver;
			this.node = node;
		}
		public ManifestInterface receiver;
		public Node node;
	}
	private HashMap<String, ManifestReceiver> receiverHash = new HashMap<String, ManifestReceiver>();
	
	private AssetManager assetManager;
	private DocumentBuilderFactory manifestBuilderFactory;
	private DocumentBuilder manifestBuilder;
	private Document manifestDocument;
	
	private ScheduledThreadPoolExecutor updateManifestExecutor;
	private UpdateManifestRunnable updateManifestRunnable;
	private ScheduledFuture<Void> updateManifestFuture;
	
	private ManifestParameters currentManifestParameters = null;
	
	private File serverManifestFile;
	private byte[] serverManifestDigest;
	
	private File clientManifestFile;
	private File newManifestFile;
	
	public class ManifestBinder extends Binder {
		public ManifestService getService() {
			return ManifestService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return manifestBinder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
				
		File manifestDir = getApplicationContext().getDir("manifest", Context.MODE_PRIVATE);
		serverManifestFile = new File(manifestDir, "server.xml");
		clientManifestFile = new File(manifestDir, "client.xml");
		newManifestFile = new File(manifestDir, "new.xml");
		
		Log.v(TAG, "-------------- STARTING MANIFEST SERVICE ---------------");
		
		assetManager = getApplicationContext().getAssets();
		
		if (!(serverManifestFile.exists())) {
			BufferedInputStream assetManifestStream;
			BufferedOutputStream serverManifestStream;
			
			Log.i(TAG, "Copying manifest from assets.");
			
			try {
				assetManifestStream = new BufferedInputStream(assetManager.open("manifest.xml", AssetManager.ACCESS_BUFFER));
				serverManifestStream = new BufferedOutputStream(new FileOutputStream(serverManifestFile));
				copyFile(assetManifestStream, serverManifestStream);
			} catch (Exception e) {
				this.stopSelfResult(startId);
				Log.d(TAG, "Failed to start." + e);
				return START_NOT_STICKY;
			}
		}
		
		try {
			serverManifestDigest = hashFile(serverManifestFile);
		} catch (Exception e) {
			this.stopSelfResult(startId);
			Log.d(TAG, "Failed to start." + e);
			return START_NOT_STICKY;
		}
		
		updateManifestRunnable = new UpdateManifestRunnable();
		updateManifestExecutor = new ScheduledThreadPoolExecutor(1);
		
		ManifestParameters newManifestParameters = new ManifestParameters();
		updateParameters(newManifestParameters);
		manifestBuilderFactory = DocumentBuilderFactory.newInstance();
		
		try {
			manifestBuilder = manifestBuilderFactory.newDocumentBuilder();
			receiverHash.clear();
			this.receiveManifestUpdates(this);
			
		} catch (Exception e) {
			this.stopSelfResult(startId);
			Log.d(TAG, "Failed to start." + e);
			return START_NOT_STICKY;
		}
		
		Intent launcherServiceIntent = new Intent(getApplicationContext(), LauncherService.class);
		this.startService(launcherServiceIntent);
		
		Log.i(TAG, "Started launcher service. Manifest service started.");
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		Log.v(TAG, "-------------- STOPPING MANIFEST SERVICE ---------------");
		
		updateManifestExecutor.shutdown();
		Intent launcherServiceIntent = new Intent(getApplicationContext(), LauncherService.class);
		this.stopService(launcherServiceIntent);
	}
	
	private class UpdateManifestRunnable implements Runnable {
		@Override
		public void run() {
			try {
				collectManifest();
				boolean receivedNewManifest = downloadManifest();
				if (receivedNewManifest == true ||
					currentManifestParameters.compareFiles == false) {
					distributeManifest();
				}
			} catch (Exception e) {
				Log.d(TAG, "reloadManifest() failed " + e.toString());
			}
		}
	}
	
	private synchronized boolean collectManifest() {
		try {
			BufferedWriter clientManifestWriter = new BufferedWriter(new FileWriter(clientManifestFile));
			clientManifestWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			
			for (HashMap.Entry<String, ManifestReceiver> entry : receiverHash.entrySet()) {
				
				String manifestReceiverName = entry.getKey();
				ManifestReceiver manifestReceiver = entry.getValue();
				
				try {
					Log.v(TAG, "Collecting manifest from " + manifestReceiverName);
					String localReceiverUpdate = manifestReceiver.receiver.localUpdate();
					if (localReceiverUpdate != null) {
						clientManifestWriter.write(localReceiverUpdate);
					}
				} catch (Exception e) {
					continue;
				}
			}
			clientManifestWriter.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	private synchronized boolean downloadManifest() throws NoSuchAlgorithmException {

		MessageDigest downloadManifestDigester = MessageDigest.getInstance("MD5");
		byte[] downloadManifestDigest = null;
		String downloadManifestString = currentManifestParameters.manifestURL + ((TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId() + "/manifest.xml";
		
		try {	
			URL downloadManifestURL = new URL(downloadManifestString);
			HttpURLConnection downloadManifestConnection = (HttpURLConnection) downloadManifestURL.openConnection();
			BufferedOutputStream newManifestStream = new BufferedOutputStream(new FileOutputStream(newManifestFile));
			
			try {
				DigestInputStream downloadManifestStream = new DigestInputStream(new BufferedInputStream(downloadManifestConnection.getInputStream()), downloadManifestDigester);
				copyFile(downloadManifestStream, newManifestStream);
			} finally {
				downloadManifestConnection.disconnect();		
			}
			if (newManifestFile.exists() && (newManifestFile.length() != 0)) {
				copyFile(newManifestFile, serverManifestFile);
				downloadManifestDigest = downloadManifestDigester.digest();
				Log.i(TAG, "Retrieved manifest with length " + newManifestFile.length() + " from " + downloadManifestString);
			} else {
				Log.d(TAG, "Manifest cannot be saved or has zero length.");
				return false;
			}
		} catch (IOException e) {
			Log.i(TAG, "Unable to download manifest from " + downloadManifestString + ": " + e);
			return false;
		}
		
		return (!(MessageDigest.isEqual(serverManifestDigest, downloadManifestDigest)));
	}
	
	public synchronized boolean distributeManifest() throws TransformerConfigurationException, TransformerFactoryConfigurationError {
		
		BufferedInputStream manifestInputStream;
		try {
			manifestInputStream = new BufferedInputStream(new FileInputStream(serverManifestFile));
			manifestDocument = manifestBuilder.parse(manifestInputStream);
		} catch (Exception e) {
			Log.d(TAG, "Unable to parse and distribute manifest: " + e);
			return false;
		}
	
		XPath manifestXPath = XPathFactory.newInstance().newXPath();
		
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		for (HashMap.Entry<String, ManifestReceiver> entry : receiverHash.entrySet()) {
			
			String receiverName = entry.getKey();
			ManifestReceiver manifestReceiver = entry.getValue();
			
			Log.i(TAG, "Looking for " + receiverName + " in receive hash.");
			String classNamePattern = "/manifest/" + receiverName;
			try {
				Node newReceiverNode = (Node) manifestXPath.evaluate(classNamePattern, manifestDocument, XPathConstants.NODE);
				newReceiverNode.normalize();
				
				boolean nodeChanged = false;
				
				if (manifestReceiver.node == null) {
					Log.i(TAG, "No record for this receiver.");
					nodeChanged = true;
				} else if (!(currentManifestParameters.compareNodes)) {
					Log.i(TAG, "Updates forced regardless of node similarity.");
					nodeChanged = true;
				} else if (!(newReceiverNode.isEqualNode(manifestReceiver.node))) {
					Log.i(TAG, "Updates due to changes in manifest for this receiver.");
					nodeChanged = true;
				}
				
				if (nodeChanged) {
					Log.i(TAG, "Manifest for " + receiverName + " has changed. Updating.");
					manifestReceiver.node = newReceiverNode;
					entry.setValue(manifestReceiver);
					
					DOMSource domSource = new DOMSource(manifestReceiver.node);
					StringWriter writer = new StringWriter();
					StreamResult result = new StreamResult(writer);
					transformer.transform(domSource, result);
					manifestReceiver.receiver.remoteUpdate(writer.toString());
				} else {
					Log.i(TAG, "Manifest for " + receiverName + " is unchanged.");
				}
			} catch (Exception e) {
				Log.d(TAG, e.toString());
				continue;
			}
		}
		return true;
	}
		
	public void receiveManifestUpdates(ManifestInterface receiver) {
		assert receiverHash.containsKey(receiver.getClass().getName()) == false : receiver;
		ManifestReceiver manifestReceiver = new ManifestReceiver(receiver, null);
		receiverHash.put(receiver.getClass().getName(), manifestReceiver);
		try {
			distributeManifest();
		} catch (Exception e) {
			Log.d(TAG, "Unable to distribute manifest.");
		}
		Log.i(TAG, "Registered " + receiver.getClass().getName() + " for manifest updates.");
	}
	
	public void discardManifestUpdates(ManifestInterface receiver) {
		assert receiverHash.containsKey(receiver.getClass().getName()) : receiver;
		receiverHash.remove(receiver.getClass().getName());
		Log.i(TAG, "Removed " + receiver.getClass().getName() + " for manifest updates.");
	}
	
	@Override
	public void remoteUpdate(String manifestString) {
		Log.v(TAG, manifestString);
		Serializer parameterDeserializer = new Persister();
		ManifestParameters newManifestParameters;
		try {
			newManifestParameters = parameterDeserializer.read(ManifestParameters.class, manifestString);
		} catch (Exception e) {
			Log.d(TAG, "Could not deserialize manifest string: " + e.toString());
			return;
		}
		if (!(currentManifestParameters.equals(newManifestParameters))) {
			Log.i(TAG, "Manifest parameters have changed. Updating.");
			updateParameters(newManifestParameters);
		} else {
			Log.i(TAG, "Manifest parameters for ManifestService are unchanged.");
		}
		Log.i(TAG, "Received new manifest.");
	}

	@SuppressWarnings("unchecked")
	private synchronized void updateParameters(ManifestParameters newManifestParameters) {
		
		if (currentManifestParameters == null ||
			currentManifestParameters.updateRate == null ||
			currentManifestParameters.updateRate != newManifestParameters.updateRate) {
			
			Log.v(TAG, "Updating update rate.");
			
			if (updateManifestFuture != null) {
				updateManifestFuture.cancel(false);
			}
			updateManifestFuture = (ScheduledFuture<Void>) updateManifestExecutor.scheduleAtFixedRate(updateManifestRunnable,
					newManifestParameters.updateRate, newManifestParameters.updateRate, TimeUnit.SECONDS);
			
			Log.v(TAG, "Updated updateRate to " + newManifestParameters.updateRate + ".");
		}
		
		Log.i(TAG, "Completed loading new parameters.");
		
		currentManifestParameters = newManifestParameters;
		TAG = currentManifestParameters.logTag;
	}
	
	@Override
	public String localUpdate() {
		return null;
	}
	
	private void copyFile(File in, File out) throws IOException {
		BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(in));
		BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(out));
		copyFile(inStream, outStream);
	}
	
	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[4096];
		int count;
		while ((count = in.read(buffer)) > 0) {
			out.write(buffer, 0, count);
		}
		in.close();
		out.flush();
		out.close();
	}
	
	private byte[] hashFile(File f) throws IOException, NoSuchAlgorithmException {
		BufferedInputStream fStream = new BufferedInputStream(new FileInputStream(f));
		MessageDigest digester = MessageDigest.getInstance("MD5");
		byte[] buffer = new byte[8192];
		int count;
		while ((count = fStream.read(buffer)) > 0) {
			digester.update(buffer, 0, count);
		}
		fStream.close();
		return digester.digest();
	}
}