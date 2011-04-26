package com.trifork.activation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
}
