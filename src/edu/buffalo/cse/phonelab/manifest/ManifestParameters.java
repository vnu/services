package edu.buffalo.cse.phonelab.manifest;

import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root
public class ManifestParameters {
	
	@Element
	public String logTag;
	
	@Element
	public Integer updateRate;
	
	@Element
	public Boolean compareFiles;
	
	@Element
	public Boolean compareNodes;
	
	@Element
	public String manifestURL;
	
	@ElementList(type=String.class)
	public HashSet<String> phoneLabServices;
	
	public ManifestParameters() {
		logTag = "ManifestParameters";
		updateRate = 30;
		manifestURL = "http://blue.cse.buffalo.edu/manifest/";
		compareFiles = false;
		compareNodes = false;
		phoneLabServices = new HashSet<String>();
	}
	
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
		
		return updateRate != null && updateRate.equals(lhs.updateRate) &&
				logTag != null && logTag.equals(lhs.logTag) &&
				compareFiles != null && compareFiles.equals(lhs.compareFiles) &&
				compareNodes != null && compareNodes.equals(lhs.compareNodes) &&
				manifestURL != null && manifestURL.equals(lhs.manifestURL) &&
				phoneLabServices != null && phoneLabServices.equals(lhs.phoneLabServices);
				
	}
}