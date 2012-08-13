package edu.buffalo.cse.phonelab.manifest;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root
public class ManifestParameters {
	@Element
	public Integer downloadRate;
}
