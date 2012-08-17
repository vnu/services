package edu.buffalo.cse.phonelab.platform;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import android.util.Log;

@Root
public class PlatformImage {
	
	@Attribute(required=false)
	public boolean golden;
	
	@Attribute(required=false)
	public boolean running;
	
	@Attribute(required=false)
	public boolean available;
	
	@Attribute(required=false)
	public boolean valid;
	
	@Element
	public String uri;
	
	@Element
	public String fingerprint;
	
	@Element
	public String hash;
	
	public File file;
	public Long downloadID;
	
	public PlatformImage(File platformImageFile) throws IOException, NullPointerException, NoSuchAlgorithmException {
		file = platformImageFile;
		fingerprint = getFileFingerprint();
		hash = getFileHash();
	}
	
	public PlatformImage() {
	}
	
	public String getFileFingerprint() throws IOException, NullPointerException {
		ZipFile zipImageFile = new ZipFile(file);
		Scanner fingerprintScanner = new Scanner(new BufferedInputStream(zipImageFile.getInputStream(zipImageFile.getEntry("META-INF/com/android/metadata"))));
		fingerprintScanner.findInLine(Pattern.compile("^post-build=(.*)$"));
		MatchResult fingerprintMatch = fingerprintScanner.match();
		zipImageFile.close();
		return fingerprintMatch.group(1);
	}
	
	public String getFileHash() throws NoSuchAlgorithmException, IOException {
		BufferedInputStream fStream = new BufferedInputStream(new FileInputStream(file));
		MessageDigest digester = MessageDigest.getInstance("MD5");
		byte[] buffer = new byte[8192];
		int count;
		while ((count = fStream.read(buffer)) > 0) {
			digester.update(buffer, 0, count);
		}
		fStream.close();
		byte[] digest = digester.digest();
		return new BigInteger(1, digest).toString(16);
	}
	
	public void removeFile() {
		if (file != null) {
			file.delete();
			file = null;
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlatformImage)) {
			return false;
		}
		PlatformImage lhs = (PlatformImage) o;
		
		return fingerprint.equals(lhs.fingerprint) &&
				hash.equals(lhs.hash);
	}
	
	@Override
	public int hashCode() {
		return (fingerprint + hash).hashCode();
	}
}