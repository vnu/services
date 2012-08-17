package edu.buffalo.cse.phonelab.services.launcher;

import java.util.HashSet;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(name="LauncherParameters")
public class LauncherParameters {
	@Element
	public String logTag;
	
	@Element
	public Integer checkInterval;
	
	@ElementList(type=String.class)
	public HashSet<String> runningServices;
	
	@ElementList(type=String.class)
	public HashSet<String> stoppedServices;
	
	public LauncherParameters() {
		logTag = "LauncherService";
		checkInterval = 600;
		runningServices = new HashSet<String>();
		stoppedServices = new HashSet<String>();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof LauncherParameters)) {
			return false;
		}
		LauncherParameters lhs = (LauncherParameters) o;
		
		return logTag != null && logTag.equals(lhs.logTag) &&
				checkInterval != null && checkInterval.equals(lhs.checkInterval) &&
				runningServices != null && runningServices.equals(lhs.runningServices) &&
				stoppedServices != null && stoppedServices.equals(lhs.stoppedServices);
	}
}
