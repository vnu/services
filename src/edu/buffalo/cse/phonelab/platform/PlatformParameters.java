package edu.buffalo.cse.phonelab.platform;

import java.util.List;

import org.simpleframework.xml.ElementList;

public class PlatformParameters {	
	@ElementList(type=PlatformDescription.class)
	public List<PlatformDescription> platforms;
}
