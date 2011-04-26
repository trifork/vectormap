package com.trifork.multiversion_common;

import java.io.IOException;
import java.io.InputStream;

import javax.activation.DataSource;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class Digest {

	public static MessageDigest createSHA1() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException anse) {
			throw new RuntimeException(anse); // We expect SHA-1 to be available.
		}
	}

	public static byte[] digestOf(Digestable d) {
		MessageDigest md = createSHA1();
		d.updateDigest(md);
		return md.digest();
	}

	public static byte[] digestOf(byte[] data) {
		MessageDigest md = createSHA1();
		md.update(data);
		return md.digest();
	}


	public static int compareHashes(byte[] hash1, byte[] hash2) {
		// Compare bytes unsigned:
		for (int i=0; i<20; i++) {
			int diff = (hash1[i] & 0xff) - (hash2[i] & 0xff);
			if (diff != 0) return diff;
		}
		return 0;
	}

}
