/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation.
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
 *     Steve Speicher - updates for recent LDP spec changes
 *     Steve Speicher - make root URI configurable 
 *     Samuel Padgett - add LDP-RS Link header to responses
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.LDPResource;
import org.eclipse.lyo.ldp.server.LDPResourceManager;

@Path("/{path:.*}")
public abstract class LDPService {
	
	@Context HttpHeaders fRequestHeaders;
	@Context UriInfo fRequestUrl;
	@Context HttpServletResponse fResponse;
	@PathParam("path") String fPath;
	private String fPublicURI = ROOT_APP_URL;
	
	public static final String ROOT_APP_URL = (System.getProperty("ldp.rooturi") != null) ? System.getProperty("ldp.rooturi") : "http://localhost:8080/ldp";
	public static final String ROOT_CONTAINER_URL = ROOT_APP_URL + "/resources/";
	
	public static final String[] ACCEPT_POST_CONTENT_TYPES = {
			LDPConstants.CT_APPLICATION_RDFXML, 
			LDPConstants.CT_TEXT_TURTLE,
			LDPConstants.CT_APPLICATION_XTURTLE,
			LDPConstants.CT_APPLICATION_JSON,
			LDPConstants.CT_APPLICATION_LD_JSON };
	
	public static final String ACCEPT_POST_CONTENT_TYPES_STR = encodeAccept(ACCEPT_POST_CONTENT_TYPES);
	
	protected abstract void resetContainer();
	protected abstract LDPContainer getRootContainer();
	protected abstract LDPResourceManager getResourceManger();
	
    public LDPService() { }
    
    /**
     * @return Does NOT include segment for container, use getRootContainer().getURI() for that.
     */
	protected String getPublicURI() { return fPublicURI ; }

    @GET
    @Produces(LDPConstants.CT_APPLICATION_RDFXML)
    public StreamingOutput getContainerApplicationRDFXML() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_RDFXML);
    }
  
    @GET
    @Produces(LDPConstants.CT_TEXT_TURTLE)
    public StreamingOutput getContainerTextTurtle() {	
        return getResourceRDF(LDPConstants.CT_TEXT_TURTLE);
    }
    
    @GET
    @Produces(LDPConstants.CT_APPLICATION_XTURTLE)
    public StreamingOutput getContainerApplicationXTurtle() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_XTURTLE);
    }

    @GET
    @Produces({ LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public StreamingOutput getContainerJSON() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_JSON);
    }

    @GET
    @Produces(LDPConstants.CT_TEXT_HTML)
    public StreamingOutput getResourceHTML() {
    	return null; // TODO fix me
    }
    
    @PUT
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE })
    public Response updateResource(InputStream content) {
    	// Set the initial container representation. Should only be called once.
    	// May be invoked when query params are used, like ?_admin or ?_meta.
    	getRootContainer().put(fRequestUrl.getRequestUri().toString(),  content, stripCharset(fRequestHeaders.getMediaType().toString()));
    	if (getRootContainer().getURI().equals(fRequestUrl.getRequestUri())) {
    		return Response.status(Status.NO_CONTENT).header("Warning", "Overwriting ROOT container contents.").build();
    	}
        return Response.status(Status.NO_CONTENT).build();
    }
    
    @POST
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE, LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public Response createResource(InputStream content) {
    	
    	// Look up content at Request-URI
    	//   if null return 404
    	//   if not container return 400
    	//   else follow model for container
    	LDPResource ldpR = getResourceManger().get(getConanicalURL(fRequestUrl.getRequestUri()));
    	if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
    	else if (!(ldpR instanceof LDPContainer))  return Response.status(Status.BAD_REQUEST).build();  // TODO: Provide some details in response
    	
    	//   else follow model for POST against container
    	
    	LDPContainer ldpC = (LDPContainer)ldpR;
    	
    	String slug = fRequestHeaders.getHeaderString(LDPConstants.HDR_SLUG);
    	
    	//  Look at headers (rel='type') and content to determine kind + interaction model
    	/* List<String> typeHeaders = getLDPTypesFromTypeHeader(fRequestHeaders); */
    	
    	//   if null/no-type and RDF content then create LDP-RR
    	/* @SuppressWarnings("rawtypes")
		Class interactionModel = LDPRDFResource.class; */
    	
    	//   TODO: if null/no-type and NOT RDF content then create LDP-BR and meta-LDP-RR (note: this could be separate @Consumes(XML, GIF, ...)
    	
    	//   TODO: if LDPC/w-LDPR interaction model, create container and mark as LDPR model
    	
    	//   else create LDPC with default interaction model (based on rdf:type)
    	String loc = ldpC.post(content, stripCharset(fRequestHeaders.getMediaType().toString()), null, slug);
    	if (loc != null)
    		return Response.status(Status.CREATED).header(HttpHeaders.LOCATION, loc).
    				header(LDPConstants.HDR_LINK, "<"+LDPConstants.CLASS_RESOURCE+">; " + LDPConstants.HDR_LINK_TYPE).build();
    	else
    		return Response.status(Status.CONFLICT).build();
    }
    
    @POST
    @Path("{id:.*}")
    @Consumes(LDPConstants.CT_APPLICATION_SPARQLQUERY)
    @Produces(LDPConstants.CT_APPLICATION_SPARQLRESULTSJSON)
    public StreamingOutput postQuery(final InputStream content, @PathParam("id") String id) {
    	//TODO Implement complete SPARQL protocol. This impl only supports SELECT queries via POST directly.
    	if ("sparql".equals(id))
            return new StreamingOutput() {
            	public void write(OutputStream output) throws IOException, WebApplicationException {
            		try {
            			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            			IOUtils.copy(content, byteStream);
            			getRootContainer().query(output, byteStream.toString(), LDPConstants.CT_APPLICATION_SPARQLRESULTSJSON);
            		} catch (Exception e) { 
            			throw new WebApplicationException(Response.status(Status.BAD_REQUEST).build()); 
            		}
            	}
        	};
    	else 
    		return null;
    }

    @DELETE
    public Response deleteResource() {
    	String uri = getConanicalURL(fRequestUrl.getRequestUri());
    	getRootContainer().delete(uri);
        return Response.status(Status.NO_CONTENT).build();
    }
    
    @PATCH
    @Path("id")
    @Consumes(LDPConstants.CT_TEXT_TURTLE)    
    public Response patchResource(final InputStream content, @PathParam("id") String id) {
    	getRootContainer().patch(getConanicalURL(fRequestUrl.getRequestUri()), content, stripCharset(fRequestHeaders.getMediaType().toString()));

      	return Response.status(Status.OK).build();
    }
    
    private StreamingOutput getResourceRDF(final String type) {	
    	fResponse.addHeader(LDPConstants.HDR_LINK, "<"+LDPConstants.CLASS_RESOURCE+">; " + LDPConstants.HDR_LINK_TYPE);
        return new StreamingOutput() {
            public void write(OutputStream output) throws IOException, WebApplicationException {
        		try {
        			getRootContainer().get(getConanicalURL(fRequestUrl.getRequestUri()), output, type);
        		} catch (IllegalArgumentException e) { 
        			fResponse.reset();
        			throw new WebApplicationException(Response.status(Status.NOT_FOUND).build()); 
        		}
            }
        };
    }
    
    String stripCharset(String contentType) {
    	int i = contentType.indexOf(";");
    	if (i == -1)
    		return contentType;
    	return contentType.substring(0, i);
    }
    
    String getConanicalURL(URI url) {
    	// TODO: Map request URL to the URL prefix that is stored in repo
    	return url.toString();
    }
    
    public static String encodeAccept(String[] contentTypes) {
    	String result = "";
    	for (int i = 0; i < contentTypes.length; i++) {
			result += contentTypes[i];
			if (i+1 < contentTypes.length) result += ", ";
		}
    	return result;
    }
    
    public static String parseSlug(String header) {
    	return header;
    }
    
}

