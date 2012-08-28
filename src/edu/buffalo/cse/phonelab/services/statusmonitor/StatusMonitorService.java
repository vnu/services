package edu.buffalo.cse.phonelab.services.statusmonitor;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import edu.buffalo.cse.phonelab.services.manifest.ManifestInterface;

public class StatusMonitorService extends Service implements ManifestInterface {

	@Override
	public void remoteUpdate(String manifestString) {
		// TODO Auto-generated method stub

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
