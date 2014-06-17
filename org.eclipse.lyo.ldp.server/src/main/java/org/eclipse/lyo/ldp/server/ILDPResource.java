/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  
 *  Contributors:
 *  
 *     Steve Speicher - support for various container types
 *     Samuel Padgett - return accurate Allow header values for HTTP OPTIONS
 *     Samuel Padgett - pass request headers on HTTP PUT
 *******************************************************************************/
package org.eclipse.lyo.ldp.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

public interface ILDPResource {

	public abstract String getURI();

	public abstract void setURI(String resourceURI);

	public abstract Object getModel();

	public abstract void setModel(Object model);

	public abstract String getTypeURI();

	/**
	 * Updates state of the resource with the contents of the specified stream.
	 * <p>The Content-Type of the input stream is specified by the
	 * <code>contentType</code> argument.</p>
	 * @param stream the input stream containing the resource representation.
	 * @param contentType the Content-Type of the input stream.
	 * @param requestHeaders the HTTP request headers
	 */
	public abstract void putUpdate(InputStream stream,
			String contentType, String user, HttpHeaders requestHeaders);

	public abstract void patch(String resourceURI, InputStream stream,
			String contentType, String user);

	/**
	 * Delete the specified member resource and remove it from the container.
	 */
	public abstract void delete();

	/**
	 * Get the current state of resource with the specified content type.
	 * <p>The Content-Type of which to write the model is specified by the
	 * <code>contentType</code> argument.</p>
	 * @param contentType the Content-Type of which to write the model.
	 * @return the HTTP response
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonGenerationException 
	 */
	public abstract Response get(String contentType);

	public abstract Response options();

	/**
	 * Returns the allowed HTTP methods for this resource as defined in RFC 2616.
	 * 
	 * @return the allowed HTTP methods, a set of strings
	 * 
	 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html">RFC 2616</a>
	 */
	public abstract Set<String> getAllowedMethods();
}