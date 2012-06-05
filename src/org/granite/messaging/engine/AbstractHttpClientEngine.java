/*
  GRANITE DATA SERVICES
  Copyright (C) 2011 GRANITE DATA SERVICES S.A.S.

  This file is part of Granite Data Services.

  Granite Data Services is free software; you can redistribute it and/or modify
  it under the terms of the GNU Library General Public License as published by
  the Free Software Foundation; either version 2 of the License, or (at your
  option) any later version.

  Granite Data Services is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE. See the GNU Library General Public License
  for more details.

  You should have received a copy of the GNU Library General Public License
  along with this library; if not, see <http://www.gnu.org/licenses/>.
*/

package org.granite.messaging.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import org.granite.context.GraniteContext;
import org.granite.context.SimpleGraniteContext;
import org.granite.messaging.amf.AMF0Message;
import org.granite.messaging.amf.io.AMF0Deserializer;
import org.granite.messaging.amf.io.AMF0Serializer;

/**
 * @author Franck WOLFF
 */
public abstract class AbstractHttpClientEngine extends AbstractEngine implements HttpClientEngine {
	
	protected void serialize(AMF0Message message, OutputStream os) throws IOException {
		AMF0Serializer serializer = new AMF0Serializer(os);
		SimpleGraniteContext.createThreadInstance(graniteConfig, servicesConfig, new HashMap<String, Object>(0));
		try {
			serializer.serializeMessage(message);
		}
		finally {
			GraniteContext.release();
		}
	}
	
	protected AMF0Message deserialize(InputStream is) throws IOException {
		SimpleGraniteContext.createThreadInstance(graniteConfig, servicesConfig, new HashMap<String, Object>(0));
		try {
			AMF0Deserializer deserializer = new AMF0Deserializer(is);
			return deserializer.getAMFMessage();
		}
		finally {
			GraniteContext.release();
		}
	}
}
