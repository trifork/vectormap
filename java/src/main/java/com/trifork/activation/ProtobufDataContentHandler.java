package com.trifork.activation;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.ActivationDataFlavor;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;

import com.google.protobuf.AbstractMessage;
import com.trifork.vmap.PBDecoder;
import com.trifork.vmap.PBEncoder;
import com.trifork.vmap.VectorMap;

public class ProtobufDataContentHandler implements DataContentHandler {

	private final DataFlavor[] flavors;

	public ProtobufDataContentHandler() {
		flavors = new DataFlavor[] {
				new ActivationDataFlavor(VectorMap.class,
						"application/x-protobuf", "VectorMap"),
				new ActivationDataFlavor(VectorMap.class, "application/json",
						"VectorMap"),
				new ActivationDataFlavor(VectorMap.class, "multipart/mixed",
						"VectorMap") };
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}

	@Override
	public Object getContent(DataSource ds) throws IOException {

		if (ds.getContentType().startsWith("application/x-protobuf")) {
			return new PBDecoder().decode(ds.getInputStream());
		} else {
			return ds.getInputStream();
		}

	}

	@Override
	public Object getTransferData(DataFlavor df, DataSource ds)
			throws UnsupportedFlavorException, IOException {
		for (DataFlavor aFlavor : flavors) {
			if (aFlavor.equals(df)) {
				return getContent(ds);
			}
		}
		return null;
	}

	@Override
	public void writeTo(Object value, String mimeType, OutputStream out)
			throws IOException {

		if (mimeType.startsWith("application/x-protobuf")) {
			if (value instanceof InputStream) {
				IO.copystream((InputStream) value, out);
				return;
			}

			if (value instanceof VectorMap) {
				value = PBEncoder.toMessage((VectorMap) value);
			}

			if (value instanceof AbstractMessage) {
				AbstractMessage am = (AbstractMessage) value;
				am.writeTo(out);
				return;
			}
		}

		throw new IOException("Cannot encode");
	}

	public static void install() {
		CommandMap m = CommandMap.getDefaultCommandMap();
		MailcapCommandMap mcm = (MailcapCommandMap) m;
		mcm.addMailcap("application/x-protobuf; ; x-java-content-handler="+ProtobufDataContentHandler.class.getName());
	}
}
