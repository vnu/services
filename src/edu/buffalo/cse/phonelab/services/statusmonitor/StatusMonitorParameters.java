package edu.buffalo.cse.phonelab.services.statusmonitor;

import org.simpleframework.xml.Element;

public class StatusMonitorParameters {
	
	@Element
	public String logTag;
	
	@Element
	public Integer checkInterval;
	
	@Element(type=ServiceIntervals.class)
	public ServiceIntervals serviceInterval;

}
