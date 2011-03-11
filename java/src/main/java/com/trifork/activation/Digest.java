package com.trifork.activation;

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


	/** Perform a MessageDigest.update() on the contents of ds.
	 *  Try to avoid copying when possible.
	 */
	public static void updateDigest(MessageDigest md, DataSource ds) throws IOException {
		if (ds instanceof BSDataSource) {
			md.update(((BSDataSource)ds).getByteBuffer());
		} else { // Too bad we can't handle ByteArrayDataSource better as well...
			InputStream is = ds.getInputStream();
			byte[] buf = new byte[100];
			int nread;
			while ( (nread=is.read(buf)) > 0) {
				md.update(buf, 0, nread);
			}
			is.close();
		}
	}
}
