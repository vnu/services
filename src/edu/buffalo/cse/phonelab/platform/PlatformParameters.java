package edu.buffalo.cse.phonelab.platform;

import java.util.ArrayList;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

public class PlatformParameters {
	
	@Element
	public String logTag;
	
	@ElementList(type=PlatformDescription.class)
	public ArrayList<PlatformDescription> platforms;
	
	public PlatformParameters() {
		logTag = "PlatformService";
		platforms = new ArrayList<PlatformDescription>();
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
		
		return logTag != null && logTag.equals(lhs.logTag) &&
				platforms != null && platforms.equals(lhs.platforms);
	}
}
