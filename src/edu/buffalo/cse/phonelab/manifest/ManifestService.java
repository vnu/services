package edu.buffalo.cse.phonelab.manifest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

public class ManifestService extends Service implements ManifestInterface {
	
	private final IBinder manifestBinder = new ManifestBinder();
	
	private HashMap<String, ManifestInterface> receiverHash = new HashMap<String, ManifestInterface>();
	
	private AssetManager assetManager;
	private byte[] localManifestDigest;
	private DocumentBuilderFactory manifestBuilderFactory;
	private DocumentBuilder manifestBuilder;
	private Document manifestDocument;
	
	private ScheduledThreadPoolExecutor downloadManifestExecutor;
	
	private ManifestParameters manifestParameters;
	
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
		
		manifestBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			manifestBuilder = manifestBuilderFactory.newDocumentBuilder();
			assetManager = getApplicationContext().getAssets();
			receiverHash.clear();
			this.receiveManifestUpdates(this);
			reloadManifest();
		} catch (Exception e) {
			this.stopSelfResult(startId);
			Log.d("ManifestService", "Failed to start." + e);
			return START_NOT_STICKY;
		}
		downloadManifestExecutor = new ScheduledThreadPoolExecutor(1);
		DownloadManifestTask task = new DownloadManifestTask();
		downloadManifestExecutor.scheduleAtFixedRate(task, 0L, 10L, TimeUnit.SECONDS);
		
		Log.i("ManifestService", "Started.");
		return START_STICKY;
	}
	
	private class DownloadManifestTask implements Runnable {
		@Override
		public void run() {
			return;
		}
		
	}
	private void reloadManifest() throws SAXException, IOException, NoSuchAlgorithmException, TransformerConfigurationException, TransformerFactoryConfigurationError {
		BufferedInputStream manifestInputStream = new BufferedInputStream(assetManager.open("server-manifest.xml", AssetManager.ACCESS_BUFFER));
		
		MessageDigest manifestDigest = MessageDigest.getInstance("MD5");
		byte[] bytes = new byte[8192];
		int byteCount;
				
		while ((byteCount = manifestInputStream.read(bytes)) > 0) {
			manifestDigest.update(bytes, 0, byteCount);
		}
		
		if (localManifestDigest != null && MessageDigest.isEqual(localManifestDigest, manifestDigest.digest())) {
			Log.i("ManifestService", "Manifest is unchanged.");
		} else {
			manifestInputStream = new BufferedInputStream(assetManager.open("server-manifest.xml", AssetManager.ACCESS_BUFFER));
			manifestDocument = manifestBuilder.parse(manifestInputStream);
		}
	
		XPath manifestXPath = XPathFactory.newInstance().newXPath();
		
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		for (HashMap.Entry<String, ManifestInterface> entry : receiverHash.entrySet()) {
			Log.i("ManifestService", "Looking for " + entry.getKey() + " in receive hash.");
			String classNamePattern = "/manifest/" + entry.getKey();
			try {
				Node classNameNode = (Node) manifestXPath.evaluate(classNamePattern, manifestDocument, XPathConstants.NODE);
				DOMSource domSource = new DOMSource(classNameNode);
				StringWriter writer = new StringWriter();
				StreamResult result = new StreamResult(writer);
				transformer.transform(domSource, result);
				entry.getValue().remoteUpdate(writer.toString());
			} catch (Exception e) {
				Log.d("ManifestReceiver", e.toString());
				continue;
			}
		}
		return;
	}
		
	public void receiveManifestUpdates(ManifestInterface receiver) {
		Log.i("ManifestService", "Registered " + receiver.getClass().getName() + " for manifest updates.");
		receiverHash.put(receiver.getClass().getName(), receiver);
	}
	public void discardManifestUpdates(ManifestInterface receiver) {
		receiverHash.remove(receiver.getClass().getName());
	}
	
	@Override
	public void remoteUpdate(String manifestString) {
		Log.i("ManifestService", "manifestString" + manifestString);
		Serializer parameterDeserializer = new Persister();
		ManifestParameters newParameters;
		try {
			newParameters = parameterDeserializer.read(ManifestParameters.class, manifestString);
		} catch (Exception e) {
			Log.d("ManifestService", e.toString());
			return;
		}
		Log.i("ManifestService", newParameters.downloadRate.toString());
	}

	@Override
	public String localUpdate() {
		// TODO Auto-generated method stub
		return null;
	}
}