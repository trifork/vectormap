package com.trifork.activation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.activation.DataSource;

import com.google.protobuf.ByteString;

public class BSDataSource implements DataSource {

	private final String contentType;
	private final ByteString data;

	public BSDataSource(String contentType, ByteString data) {
		this.contentType = contentType;
		this.data = data;
	}
	
	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return data.newInput();
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return null;
	}

	public ByteBuffer getByteBuffer() {
		return data.asReadOnlyByteBuffer();
	}

}
