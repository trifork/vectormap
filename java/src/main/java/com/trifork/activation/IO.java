package com.trifork.activation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;
import java.security.MessageDigest;

public class IO {

	public static long copystream(InputStream in, OutputStream out)
			throws IOException {
		long length = 0;
		byte[] data = new byte[8 * 1024];
		while (true) {
			int len = in.read(data);
			if (len == -1)
				return length;
			length += len;
			out.write(data, 0, len);
		}
	}

	/** Perform a MessageDigest.update().
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
