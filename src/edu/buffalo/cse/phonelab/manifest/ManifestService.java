package edu.buffalo.cse.phonelab.manifest;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.util.Log;

public class ManifestService extends Service {
	
	private AssetManager assetManager;
	private DocumentBuilderFactory manifestBuilderFactory;
	private DocumentBuilder manifestBuilder;
	private Document manifestDocument;
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		manifestBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			manifestBuilder = manifestBuilderFactory.newDocumentBuilder();
			assetManager = this.getAssets();
			loadManifest();
		} catch (Exception e) {
			this.stopSelfResult(startId);
			Log.d("ManifestService", "Failed to start.");
			return START_NOT_STICKY;
		}
		Log.i("ManifestService", "Started.");
		return START_STICKY;
	}
	
	private void loadManifest() throws SAXException, IOException {
		InputStream manifestInputStream = assetManager.open("server-manifest.xml", AssetManager.ACCESS_BUFFER);
		manifestDocument = manifestBuilder.parse(manifestInputStream);
	}
}