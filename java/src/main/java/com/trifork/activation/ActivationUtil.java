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

	public static <T> T decode(DataSource ds, Class<T> representationClass)
			throws IOException, UnsupportedFlavorException {
		return (T) decode(ds, representationClass, null);
	}

	/** Try decoding the DataSource first as representationClass, then
	 * (if that fails) as fallbackClass.
	 */
	public static Object decode(DataSource ds,
								Class representationClass, Class fallbackClass)
		throws IOException, UnsupportedFlavorException {

		if (representationClass == DataSource.class)
			return ds;

		if (representationClass == InputStream.class)
			return ds.getInputStream();

		if (representationClass == byte[].class) {
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			IO.copystream(ds.getInputStream(), bao);
			return bao.toByteArray();
		}

		String contentType = ds.getContentType();
		// CommandMap appears to expect a parameter-less mimetype:
		int semi_pos = contentType.indexOf(';');
		if (semi_pos >= 0) contentType = contentType.substring(0,semi_pos);

		CommandMap defaultCommandMap = CommandMap.getDefaultCommandMap();
		CommandInfo cc = defaultCommandMap.getCommand(contentType, "content-handler", ds);
		
		DataContentHandler co;
		try {
			co = (DataContentHandler) cc.getCommandObject(null, null);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}

		for (DataFlavor flavor : co.getTransferDataFlavors()) {
			if (representationClass.isAssignableFrom(flavor.getRepresentationClass())) {
				return co.getTransferData(flavor, ds);
			}
		}

		if (fallbackClass != null)
			return decode(ds, fallbackClass, null);
		else
			throw new UnsupportedEncodingException();
	}

}
