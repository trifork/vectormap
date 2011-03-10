package com.trifork.activation;

import javax.activation.DataSource;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Decoration of a DataSource with a cached hash code computed from
 * the content-type and bytes of original.
 */
public class RichDataSource implements DataSource {
	private final DataSource data_source;
	private byte[] hash;
		

	public RichDataSource(DataSource ds) {
		this.data_source = ds;
	}

	public static RichDataSource make(DataSource ds) {
		if (ds==null) return null;
		else if (ds instanceof RichDataSource) return (RichDataSource)ds;
		else return new RichDataSource(ds);
	}

	public DataSource getDataSource() {return data_source;}

	//========== Accessors: ========================================
	public String getName() {return data_source.getName();}
	public String getContentType() {return data_source.getContentType();}
	public InputStream getInputStream() throws IOException {
		return data_source.getInputStream();
	}
	public OutputStream getOutputStream() throws IOException {
		throw new IOException("getOutputStream() not supported");
	}

	//========== Hash stuff: ========================================
	private byte[] hash() {
		if (hash==null) hash = computeHash();
		return hash;
	}
		
	private static final Charset US_ASCII = Charset.forName("US-ASCII"); 
	private byte[] computeHash() {
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException anse) {
			throw new RuntimeException(anse); // We expect SHA-1 to be available.
		}

		// Process mimetype...
		String mimetype = data_source.getContentType();
		md.update(mimetype.getBytes(US_ASCII));

		// Process separator...
		md.update((byte)'\n');

		// Process contents...
		try {
			IO.updateDigest(md, data_source);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe); // What to do?
		}

		// ...and done.
		return md.digest();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof RichDataSource) &&
			equals((RichDataSource)other);
	}

	public boolean equals(RichDataSource other) {
		return Arrays.equals(hash(), other.hash());
	}

	public int hashCode() {
		byte[] hash = hash();
		return (hash[0] |
				(hash[1] << 8) |
				(hash[2] << 16) |
				(hash[3] << 24));
					
	}
}
