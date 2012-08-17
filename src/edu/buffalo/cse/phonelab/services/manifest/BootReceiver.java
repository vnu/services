package edu.buffalo.cse.phonelab.services.manifest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import edu.buffalo.cse.phonelab.services.manifest.ManifestService;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context arg0, Intent arg1) {
		Intent manifestService = new Intent(arg0, ManifestService.class);
		arg0.startService(manifestService);
	}
}
