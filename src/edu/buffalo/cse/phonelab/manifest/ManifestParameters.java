package edu.buffalo.cse.phonelab.manifest;

import java.util.Formatter;
import java.util.Locale;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root
public class ManifestParameters {
	
	@Element
	public String logTag;
	
	@Element
	public Integer updateRate;
	
	@Element
	public boolean compareFiles;
	
	@Element
	public boolean compareNodes;
	
	@Element
	public String manifestURL;
	
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		Formatter formatter = new Formatter(stringBuilder, Locale.US);
		formatter.format("updateRate: %s, compareFiles: %s, compareNodes: %s, manifestURL: %s",
				updateRate, compareFiles, compareNodes, manifestURL);
		formatter.close();
		return stringBuilder.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ManifestParameters)) {
			return false;
		}
		ManifestParameters lhs = (ManifestParameters) o;
		
		return updateRate == lhs.updateRate &&
				logTag == lhs.logTag &&
				compareFiles == lhs.compareFiles &&
				compareNodes == lhs.compareNodes &&
				manifestURL == lhs.manifestURL;
				
	}
}
