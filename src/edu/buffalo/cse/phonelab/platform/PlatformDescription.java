package edu.buffalo.cse.phonelab.platform;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root
public class PlatformDescription {
	@Attribute
	public String fingerprint;
	
	@Attribute
	public String url;
	
	@Attribute
	public String relativePath;
	
	@Attribute
	public Integer size;
	
	@Attribute(required=false)
	public Boolean currentPlatform;
	
	@Attribute(required=false)
	public Boolean goldenPlatform;
	
	public PlatformDescription() {
		currentPlatform = false;
		goldenPlatform = false;
	}
}