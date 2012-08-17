package edu.buffalo.cse.phonelab.platform;

import java.util.ArrayList;
import java.util.Date;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

public class PlatformParameters {
	
	@Element(required=false)
	public Date modified;
	
	@Element
	public String logTag;
	
	@Element
	public Long failedRetryDelay;
	
	@ElementList(type=PlatformImage.class)
	public ArrayList<PlatformImage> platforms;
	
	public PlatformParameters() {
		logTag = "PlatformService";
		platforms = new ArrayList<PlatformImage>();
		failedRetryDelay = 1200L;
	}
	
	@Override
	public String toString() {
		return "" + logTag + " " + failedRetryDelay + " " + platforms.size();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlatformParameters)) {
			return false;
		}
		PlatformParameters lhs = (PlatformParameters) o;
		
		for (PlatformImage platform : platforms) {
			if (!(lhs.platforms.contains(platform))) {
				return false;
			}
		}
	
		return logTag.equals(lhs.logTag) &&
				failedRetryDelay.equals(lhs.failedRetryDelay) &&
				platforms.size() == lhs.platforms.size();
	}
}
