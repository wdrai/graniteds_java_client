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

import java.net.URI;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.nio.client.DefaultHttpAsyncClient;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.granite.logging.Logger;
import org.granite.messaging.amf.AMF0Message;

/**
 * @author Franck WOLFF
 */
public class ApacheAsyncEngine extends AbstractHttpClientEngine {
	
	private static final Logger log = Logger.getLogger(ApacheAsyncEngine.class);

	protected DefaultHttpAsyncClient httpClient = null;
	protected CookieStore cookieStore = new BasicCookieStore();

	@Override
	public synchronized void start() {
		super.start();
		
		try {
			httpClient = new DefaultHttpAsyncClient();
			httpClient.setCookieStore(cookieStore);
			httpClient.start();
			
			final long timeout = System.currentTimeMillis() + 10000L; // 10sec.
			while (httpClient.getStatus() != IOReactorStatus.ACTIVE) {
				if (System.currentTimeMillis() > timeout)
					throw new TimeoutException("HttpAsyncClient start process too long");
				Thread.sleep(100);
			}
		}
		catch (Exception e) {
			super.stop();
			
			httpClient = null;
			
			statusHandler.handleException(new EngineException("Could not start Apache HttpAsyncClient", e));
		}
	}

	@Override
	public synchronized boolean isStarted() {
		return (super.isStarted() && httpClient != null && httpClient.getStatus() == IOReactorStatus.ACTIVE);
	}

	@Override
	public synchronized void send(final URI uri, final AMF0Message message, final EngineResponseHandler handler) {
	    
	    if (!isStarted()) {
			statusHandler.handleException(new EngineException("Apache HttpAsyncClient not started"));
			return;
		}
		
		PublicByteArrayOutputStream os = new PublicByteArrayOutputStream();
		
		try {
			serialize(message, os);
		}
		catch (Exception e) {
			log.error(e, "Could not serialize AMF0 message");
			statusHandler.handleException(new EngineException("Could not serialize AMF0 message", e));
			return;
		}
		
		final HttpPost request = new HttpPost(uri);
		request.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		request.setHeader("Content-Type", CONTENT_TYPE);
		request.setEntity(new ByteArrayEntity(os.getBytes()));
		
		statusHandler.handleIO(true);
		
		httpClient.execute(request, new FutureCallback<HttpResponse>() {

            public void completed(final HttpResponse response) {
            	AMF0Message responseMessage = null;
            	try {
                	HttpEntity entity = response.getEntity();
            		responseMessage = deserialize(entity.getContent());
				}
            	catch (Exception e) {
            		handler.failed(e);
            		statusHandler.handleIO(false);
            		statusHandler.handleException(new EngineException("Could not deserialize AMF0 message", e));
            		return;
				}
            	handler.completed(responseMessage);
        		statusHandler.handleIO(false);
            }

            public void failed(final Exception e) {
            	handler.failed(e);
        		statusHandler.handleIO(false);
            	statusHandler.handleException(new EngineException("Request failed", e));
            }

            public void cancelled() {
            	handler.cancelled();
        		statusHandler.handleIO(false);
            }
        });
		
	}

	@Override
	public synchronized void stop() {
		super.stop();
		
		try {
			httpClient.shutdown();
		}
		catch (InterruptedException e) {
			statusHandler.handleException(new EngineException("Could not stop Apache HttpAsyncClient", e));
		}
		finally {
			httpClient = null;
		}
	}
}
