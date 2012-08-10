package edu.buffalo.cse.phonelab.manifest;

import org.w3c.dom.Node;

public interface ManifestInterface {
	public void remoteUpdate(Node manifestFragment);
	public Node localUpdate();
}