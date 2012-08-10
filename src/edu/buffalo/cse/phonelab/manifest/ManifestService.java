package edu.buffalo.cse.phonelab.manifest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
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

public class ManifestService extends Service implements ManifestInterface {
	
	private final IBinder manifestBinder = new ManifestBinder();
	
	private HashMap<String, ManifestInterface> receiverHash = new HashMap<String, ManifestInterface>();
	
	private AssetManager assetManager;
	private byte[] localManifestDigest;
	private DocumentBuilderFactory manifestBuilderFactory;
	private DocumentBuilder manifestBuilder;
	private Document manifestDocument;
	
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
		Log.i("ManifestService", "Started.");
		return START_STICKY;
	}
	
	private void reloadManifest() throws SAXException, IOException, NoSuchAlgorithmException {
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
		
		for (HashMap.Entry<String, ManifestInterface> entry : receiverHash.entrySet()) {
			Log.i("ManifestService", "Looking for " + entry.getKey() + " in receive hash.");
			String classNamePattern = "/manifest/" + entry.getKey();
			try {
				Node classNameNode = (Node) manifestXPath.evaluate(classNamePattern, manifestDocument, XPathConstants.NODE);
				entry.getValue().remoteUpdate(classNameNode);
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
	public void remoteUpdate(Node manifestNode) {
		Log.i("ManifestService", "remoteUpdate called.");
		DOMSource domSource = new DOMSource(manifestNode);
		StringWriter writer = new StringWriter();
		StreamResult result = new StreamResult(writer);
		Transformer transformer;
		try {
			transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.transform(domSource, result);
			Log.i("ManifestService", writer.toString());
		} catch (Exception e) {
		}
	}

	@Override
	public Node localUpdate() {
		// TODO Auto-generated method stub
		return null;
	}
}