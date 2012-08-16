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
	public String filename;
	
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
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlatformDescription)) {
			return false;
		}
		PlatformDescription lhs = (PlatformDescription) o;
		
		return fingerprint != null && fingerprint.equals(lhs.fingerprint) &&
				url != null && url.equals(lhs.url) &&
				filename != null && filename.equals(lhs.filename) &&
				size != null && size.equals(lhs.size) &&
				currentPlatform != null && currentPlatform.equals(lhs.currentPlatform) &&
				goldenPlatform != null && goldenPlatform.equals(lhs.goldenPlatform);
	}
}