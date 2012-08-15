package edu.buffalo.cse.phonelab.launcher;

import java.util.HashSet;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

public class LauncherParameters {
	@Element
	public String logTag;
	
	@Element
	public Integer checkInterval;
	
	@ElementList(type=String.class)
	public HashSet<String> phoneLabRunningServices;
	
	@ElementList(type=String.class)
	public HashSet<String> phoneLabStoppedServices;
	
	public LauncherParameters() {
		logTag = "LauncherService";
		phoneLabRunningServices = new HashSet<String>();
		phoneLabStoppedServices = new HashSet<String>();
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
				phoneLabRunningServices != null && phoneLabRunningServices.equals(lhs.phoneLabRunningServices) &&
				phoneLabStoppedServices != null && phoneLabStoppedServices.equals(lhs.phoneLabStoppedServices);
	}
}
