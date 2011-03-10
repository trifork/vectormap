package com.trifork.activation;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import javax.activation.DataSource;
import javax.activation.CommandInfo;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;

import com.trifork.activation.IO;

public abstract class ActivationUtil {

	public static abstract class Decoder<T> {
		public abstract Class<T> getRepresentationClass();
		public abstract T decode(DataSource ds) throws IOException;
	}

	public static class DecoderToDataSource extends Decoder<DataSource> {
		public Class<DataSource> getRepresentationClass() {return DataSource.class;}
		public DataSource decode(DataSource ds) throws IOException {return ds;}
	}

	public static class DecoderToInputStream extends Decoder<InputStream> {
		public Class<InputStream> getRepresentationClass() {return InputStream.class;}
		public InputStream decode(DataSource ds) throws IOException {
			return ds.getInputStream();
		}
	}

	public static class DecoderToByteArray extends Decoder<byte[]> {
		public Class<byte[]> getRepresentationClass() {return byte[].class;}
		public byte[] decode(DataSource ds) throws IOException {
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			IO.copystream(ds.getInputStream(), bao);
			return bao.toByteArray();
		}
	}

	public static class DataContentHandlerDecoder<T> extends Decoder<T> {
		final DataContentHandler dch;
		final DataFlavor flavor;
		public DataContentHandlerDecoder(DataContentHandler dch,
										 DataFlavor flavor) {
			this.dch = dch;
			this.flavor = flavor;
		}
		public Class<T> getRepresentationClass() {
			return (Class<T>)flavor.getRepresentationClass();
		}
		public T decode(DataSource ds) throws IOException {
			try {
				return (T) dch.getTransferData(flavor, ds);
			} catch (UnsupportedFlavorException ufe) {
				throw new IOException(ufe);
			}
		}
	}

	static final Decoder<DataSource>  DECODER_TO_DATASOURCE  = new DecoderToDataSource();
	static final Decoder<InputStream> DECODER_TO_INPUTSTREAM = new DecoderToInputStream();
	static final Decoder<byte[]>      DECODER_TO_BYTEARRAY   = new DecoderToByteArray();


	public static <T> T decode(DataSource ds, Class<T> representationClass)
			throws IOException, UnsupportedFlavorException {
		return (T) getDecoder(ds, representationClass).decode(ds);
	}

	public static <T> Decoder<T> getDecoder(DataSource ds, Class<T> representationClass) throws IOException, UnsupportedFlavorException {
		return getDecoder(ds.getContentType(), representationClass);
	}

	@SuppressWarnings("unchecked")
	public static <T> Decoder<T> getDecoder(String contentType, Class<T> representationClass)
		throws IOException, UnsupportedFlavorException {

		if (representationClass == DataSource.class)
			return (Decoder<T>) DECODER_TO_DATASOURCE;

		if (representationClass == InputStream.class)
			return (Decoder<T>) DECODER_TO_INPUTSTREAM;

		if (representationClass == byte[].class)
			return (Decoder<T>) DECODER_TO_BYTEARRAY;

		// CommandMap appears to expect a parameter-less mimetype:
		int semi_pos = contentType.indexOf(';');
		if (semi_pos >= 0) contentType = contentType.substring(0,semi_pos);

		CommandMap defaultCommandMap = CommandMap.getDefaultCommandMap();
		CommandInfo cc = defaultCommandMap.getCommand(contentType, "content-handler");

		if (cc==null) throw new UnsupportedEncodingException("No content handler found for "+contentType);
		
		DataContentHandler co;
		try {
			co = (DataContentHandler) cc.getCommandObject(null, null);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}

		for (DataFlavor flavor : co.getTransferDataFlavors()) {
			if (representationClass.isAssignableFrom(flavor.getRepresentationClass())) {
				return new DataContentHandlerDecoder<T>(co,flavor);
			}
		}
		
		throw new UnsupportedEncodingException();
	}
}
