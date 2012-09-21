package edu.buffalo.cse.phonelab.services.statusmonitor;

import org.simpleframework.xml.Element;

public class StatusMonitorParameters {
	
	@Element
	public String logTag;
	
	@Element
	public Integer checkInterval;
	
	@Element(type=ServiceIntervals.class)
	public ServiceIntervals serviceInterval;
	
	public StatusMonitorParameters(){
		logTag="StatusMonitorService";
		checkInterval = 60;
		
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StatusMonitorParameters)) {
			return false;
		}
		StatusMonitorParameters lhs = (StatusMonitorParameters) o;
		
		return logTag != null && logTag.equals(lhs.logTag) &&
				checkInterval != null && checkInterval.equals(lhs.checkInterval);
	}

}
