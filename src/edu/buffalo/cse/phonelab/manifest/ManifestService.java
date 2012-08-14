package edu.buffalo.cse.phonelab.manifest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import org.xml.sax.SAXException;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

public class ManifestService extends Service implements ManifestInterface {
	
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
	private UpdateManifestTask updateManifestTask;
	
	private ManifestParameters manifestParameters;
	
	private File serverManifestFile;
	private byte[] serverManifestDigest;
	
	private File clientManifestFile;
	private File newManifestFile;
	
	public class ManifestBinder extends Binder {
		ManifestService getService() {
			return ManifestService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return manifestBinder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		manifestParameters = new ManifestParameters();
		manifestParameters.logTag = "ManifestService";
		manifestParameters.compareFiles = true;
		manifestParameters.compareNodes = true;
		
		File manifestDir = getApplicationContext().getDir("manifest", Context.MODE_PRIVATE);
		serverManifestFile = new File(manifestDir, "server.xml");
		clientManifestFile = new File(manifestDir, "client.xml");
		newManifestFile = new File(manifestDir, "new.xml");
		
		assetManager = getApplicationContext().getAssets();
		
		if (!(serverManifestFile.exists())) {
			BufferedInputStream assetManifestStream;
			BufferedOutputStream serverManifestStream;
			
			Log.i(manifestParameters.logTag, "Copying manifest from assets.");
			
			try {
				assetManifestStream = new BufferedInputStream(assetManager.open("server-manifest.xml", AssetManager.ACCESS_BUFFER));
				serverManifestStream = new BufferedOutputStream(new FileOutputStream(serverManifestFile));
				copyFile(assetManifestStream, serverManifestStream);
			} catch (Exception e) {
				this.stopSelfResult(startId);
				Log.d(manifestParameters.logTag, "Failed to start." + e);
				return START_NOT_STICKY;
			}
		}
		
		try {
			serverManifestDigest = hashFile(serverManifestFile);
		} catch (Exception e) {
			this.stopSelfResult(startId);
			Log.d(manifestParameters.logTag, "Failed to start." + e);
			return START_NOT_STICKY;
		}
		
		updateManifestTask = new UpdateManifestTask();
		manifestBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			manifestBuilder = manifestBuilderFactory.newDocumentBuilder();
			receiverHash.clear();
			this.receiveManifestUpdates(this);
			
		} catch (Exception e) {
			this.stopSelfResult(startId);
			Log.d(manifestParameters.logTag, "Failed to start." + e);
			return START_NOT_STICKY;
		}
		
		Log.i(manifestParameters.logTag, "Started.");
		return START_STICKY;
	}
	
	private class UpdateManifestTask implements Runnable {
		@Override
		public void run() {
			try {
				reloadManifest();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void reloadManifest() throws NoSuchAlgorithmException, TransformerConfigurationException, SAXException, IOException, TransformerFactoryConfigurationError {
		reloadManifest(false);
	}
	
	private synchronized void reloadManifest(boolean ignoreFileSimilarity) throws SAXException, NoSuchAlgorithmException, TransformerConfigurationException, TransformerFactoryConfigurationError, IOException {

		
		MessageDigest downloadManifestDigester = MessageDigest.getInstance("MD5");
		byte[] downloadManifestDigest = null;
		
		if (manifestParameters.manifestURL != null) {
			String downloadManifestString = manifestParameters.manifestURL + ((TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId() + "/manifest.xml";
			URL downloadManifestURL = new URL(downloadManifestString);
			try {
				
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
					Log.i(manifestParameters.logTag, "Retrieved manifest with length " + newManifestFile.length() + " from " + downloadManifestString);
				}
			} catch (IOException e) {
				e.printStackTrace();
				Log.i(manifestParameters.logTag, "Unable to download manifest from " + downloadManifestString);
			}
		}
		
		boolean manifestChanged = false;
		
		if (ignoreFileSimilarity) {
			manifestChanged = true;
			Log.i(manifestParameters.logTag, "ignoreFileSimilarity forced manifest update.");
		} else if (downloadManifestDigest != null) {
			if (!(MessageDigest.isEqual(serverManifestDigest, downloadManifestDigest))) {
				manifestChanged = true;
				serverManifestDigest = downloadManifestDigest;
				Log.i(manifestParameters.logTag, "Digest difference forced manifest update.");
			}
		}
		
		if (manifestChanged) {
			BufferedInputStream manifestInputStream = new BufferedInputStream(new FileInputStream(serverManifestFile));
			manifestDocument = manifestBuilder.parse(manifestInputStream);
			Log.i(manifestParameters.logTag, "Manifest has changed or updating was forced.");
		} else {
			Log.i(manifestParameters.logTag, "Manifest is unchanged.");
			return;
		}
	
		XPath manifestXPath = XPathFactory.newInstance().newXPath();
		
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		for (HashMap.Entry<String, ManifestReceiver> entry : receiverHash.entrySet()) {
			
			String receiverName = entry.getKey();
			ManifestReceiver manifestReceiver = entry.getValue();
			
			Log.i(manifestParameters.logTag, "Looking for " + receiverName + " in receive hash.");
			String classNamePattern = "/manifest/" + receiverName;
			try {
				Node newReceiverNode = (Node) manifestXPath.evaluate(classNamePattern, manifestDocument, XPathConstants.NODE);
				newReceiverNode.normalize();
				
				boolean nodeChanged = false;
				
				if (manifestReceiver.node == null) {
					Log.i(manifestParameters.logTag, "No record for this receiver.");
					nodeChanged = true;
				} else if (!(manifestParameters.compareNodes)) {
					Log.i(manifestParameters.logTag, "Updates forced regardless of node similarity.");
					nodeChanged = true;
				} else if (!(newReceiverNode.isEqualNode(manifestReceiver.node))) {
					Log.i(manifestParameters.logTag, "Updates due to changes in manifest for this receiver.");
					nodeChanged = true;
				}
				
				if (nodeChanged) {
					Log.i(manifestParameters.logTag, "Manifest for " + receiverName + " has changed. Updating.");
					manifestReceiver.node = newReceiverNode;
					entry.setValue(manifestReceiver);
					
					DOMSource domSource = new DOMSource(manifestReceiver.node);
					StringWriter writer = new StringWriter();
					StreamResult result = new StreamResult(writer);
					transformer.transform(domSource, result);
					manifestReceiver.receiver.remoteUpdate(writer.toString());
				} else {
					Log.i(manifestParameters.logTag, "Manifest for " + receiverName + " is unchanged.");
				}
			} catch (Exception e) {
				Log.d(manifestParameters.logTag, e.toString());
				continue;
			}
		}
		return;
	}
		
	public void receiveManifestUpdates(ManifestInterface receiver) {
		assert receiverHash.containsKey(receiver.getClass().getName()) == false : receiver;
		ManifestReceiver manifestReceiver = new ManifestReceiver(receiver, null);
		receiverHash.put(receiver.getClass().getName(), manifestReceiver);
		Log.i(manifestParameters.logTag, "Registered " + receiver.getClass().getName() + " for manifest updates.");
		try {
			reloadManifest(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void discardManifestUpdates(ManifestInterface receiver) {
		assert receiverHash.containsKey(receiver.getClass().getName()) : receiver;
		receiverHash.remove(receiver.getClass().getName());
		Log.i(manifestParameters.logTag, "Removed " + receiver.getClass().getName() + " for manifest updates.");
	}
	
	@Override
	public void remoteUpdate(String manifestString) {
		Log.i(manifestParameters.logTag, manifestString);
		Serializer parameterDeserializer = new Persister();
		ManifestParameters newParameters;
		try {
			newParameters = parameterDeserializer.read(ManifestParameters.class, manifestString);
		} catch (Exception e) {
			Log.d("ManifestService", e.toString());
			return;
		}
		if (!(manifestParameters.equals(newParameters))) {
			updateParameters(newParameters);
		} else {
			Log.i(manifestParameters.logTag, "Manifest parameters for ManifestService are unchanged.");
		}
		Log.i(manifestParameters.logTag, "Received new manifest.");
	}

	private void updateParameters(ManifestParameters newParameters) {
		
		Log.v(manifestParameters.logTag, newParameters.toString());
		
		if (manifestParameters.updateRate != newParameters.updateRate) {
			
			if (updateManifestExecutor != null) {
				updateManifestExecutor.shutdown();
			}
			
			updateManifestExecutor = new ScheduledThreadPoolExecutor(1);
			updateManifestExecutor.scheduleAtFixedRate(updateManifestTask,
					newParameters.updateRate, newParameters.updateRate, TimeUnit.SECONDS);
			
			Log.i("ManifestService", "Updated updateRate.");
		}
		
		manifestParameters = newParameters;
	}
	
	@Override
	public String localUpdate() {
		// TODO Auto-generated method stub
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