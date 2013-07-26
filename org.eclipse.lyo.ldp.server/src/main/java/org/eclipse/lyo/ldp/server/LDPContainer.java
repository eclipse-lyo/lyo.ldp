/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
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
 *     Frank Budinsky - initial API and implementation
 *     Steve Speicher - initial API and implementation
 *     Samuel Padgett - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.ldp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface LDPContainer {

	/**
	 * Set the configuration parameters.
	 * @param config an RDF input stream containing configuration parameters.
	 * @param contentType the Content-Type of the input stream.
	 */
	public abstract void setConfigParameters(InputStream config,
			String contentType);

	/**
	 * Get the value of the container.
	 * @see #get(String, OutputStream, String)
	 */
	public abstract void get(OutputStream outStream, String contentType);

	/**
	 * Set the value of the container.
	 * @see #put(String, InputStream, String)
	 */
	public abstract void put(InputStream stream, String contentType);

	public abstract void put(InputStream stream, String contentType, String user);

	/**
	 * Execute the specified SPARQL query over the container and its entries,
	 * specifying the output format. Supported formats are "application/sparql-results+xml"
	 * and "application/sparql-results+json".
	 * @param outStream the output stream in which to write the query result.
	 * @param queryString the SPARQL query to execute.
	 * @param resultsFormat the desired result format content type ; if <code>null</code>
	 * or not supported, the format will be XML.
	 */
	public abstract void query(OutputStream outStream, String queryString,
			String resultsFormat);

	/**
	 * Post a new member to the container.
	 * <p>The Content-Type of the input stream is specified by the
	 * <code>contentType</code> argument. Supported values are "application/rdf+xml",
	 * "text/turtle", and "application/x-turtle".</p>
	 * @param stream the input stream containing the posted resource representation.
	 * @param contentType the Content-Type of the input stream.
	 */
	public abstract String post(InputStream stream, String contentType);

	public abstract String post(InputStream stream, String contentType,
			String user);

	/**
	 * Set the value of the container or member resource to the content of the specified stream.
	 * <p>The Content-Type of the input stream is specified by the
	 * <code>contentType</code> argument. Supported values are "application/rdf+xml",
	 * "text/turtle", and "application/x-turtle".</p>
	 * @param resourceURI the URI of the BPC or a member resource.
	 * @param stream the input stream containing the resource representation.
	 * @param contentType the Content-Type of the input stream.
	 */
	public abstract void put(String resourceURI, InputStream stream,
			String contentType);

	public abstract void put(String resourceURI, InputStream stream,
			String contentType, String user);

	public abstract void patch(String resourceURI, InputStream stream,
			String contentType);

	public abstract void patch(String resourceURI, InputStream stream,
			String contentType, String user);

	/**
	 * Delete the specified member resource and remove it from the container.
	 * @param resourceURI the URI of the resource to delete.
	 */
	public abstract void delete(String resourceURI);

	/**
	 * Get the container or member resource with the specified URI.
	 * <p>The Content-Type of which to write the model is specified by the
	 * <code>contentType</code> argument. Supported values are "application/rdf+xml",
	 * "text/turtle", and "application/x-turtle".</p>
	 * @param resourceURI the URI of the BPC (or one of its pages) or a member resource.
	 * @param outStream the output stream in which to write the resource representation.
	 * @param contentType the Content-Type of which to write the model.
	 * @return the resource URI of a following page or null if the representation has no following page.
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonGenerationException 
	 */
	public abstract String get(String resourceURI, OutputStream outStream,
			String contentType);

}