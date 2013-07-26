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
package org.eclipse.lyo.ldp.server.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

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

@Path("/")
public abstract class LDPService {
	
	@Context HttpHeaders fRequestHeaders;
	@Context UriInfo fRequestUrl;
	
	// TODO: Need to properly setup public URL
	public static final String ROOT_APP_URL = "http://localhost:8080/ldp";
	public static final String ROOT_CONTAINER_URL = ROOT_APP_URL + "/resources/";
	
//	static {
//		// Check to see if we should bootstrap some examples.
//		if (rootContainer == null) {
//			resetContainer();
//			// Create an empty container.
//			String stuff="<"+ROOT_CONTAINER_URL+"> a <" + LDP.Container.getURI() + ">.";
//			rootContainer.put(new ByteArrayInputStream( stuff.getBytes() ), LDPConstants.CT_TEXT_TURTLE);
//		}
//	}

//	static private void resetContainer() {
//		rootContainer = LDPContainer.create(ROOT_CONTAINER_URL, new TDBGraphStore(), new TDBGraphStore());
//	}
	
	protected abstract void resetContainer();
	protected abstract LDPContainer getRootContainer();
	
    public LDPService() {
    }

    @GET
    @Path("{id}")
    @Produces(LDPConstants.CT_APPLICATION_RDFXML)
    public StreamingOutput getResourceApplicationRDFXML() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_RDFXML);
    }
    
    @GET
    @Produces(LDPConstants.CT_APPLICATION_RDFXML)
    public StreamingOutput getContainerApplicationRDFXML() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_RDFXML);
    }
    
    @GET
    @Path("{id}")
    @Produces(LDPConstants.CT_TEXT_TURTLE)
    public StreamingOutput getResourceTextTurtle() {	
        return getResourceRDF(LDPConstants.CT_TEXT_TURTLE);
    }

    @GET
    @Produces(LDPConstants.CT_TEXT_TURTLE)
    public StreamingOutput getContainerTextTurtle() {	
        return getResourceRDF(LDPConstants.CT_TEXT_TURTLE);
    }

    @GET
    @Path("{id}")
    @Produces(LDPConstants.CT_APPLICATION_XTURTLE)
    public StreamingOutput getResourceApplicationXTurtle() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_XTURTLE);
    }
    
    @GET
    @Produces(LDPConstants.CT_APPLICATION_XTURTLE)
    public StreamingOutput getContainerApplicationXTurtle() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_XTURTLE);
    }

    @GET
    @Path("{id}")
    @Produces({ LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public StreamingOutput getResourceJSON() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_JSON);
    }

    @GET
    @Produces({ LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public StreamingOutput getContainerJSON() {	
        return getResourceRDF(LDPConstants.CT_APPLICATION_JSON);
    }
    
    private StreamingOutput getResourceRDF(final String type) {	
        return new StreamingOutput() {
            public void write(OutputStream output) throws IOException, WebApplicationException {
        		try {
        			getRootContainer().get(getConanicalURL(fRequestUrl.getRequestUri()), output, type);
        		} catch (IllegalArgumentException e) { 
        			throw new WebApplicationException(Response.status(Status.NOT_FOUND).build()); 
        		}
            }
        };
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
    	// TODO only allow if privileged user
    	resetContainer();
    	getRootContainer().put(content, stripCharset(fRequestHeaders.getMediaType().toString()));
        return Response.status(Status.NO_CONTENT).build();
    }

    @PUT
    @Path("{id}")
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE, LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public Response updateConfig(InputStream content, @PathParam("id") String id) {
    	if ("config".equals(id)) {
    		// This should be called once, immediately after setting the initial container representation (i.e., before POSTing entries)
    		// TODO only allow if privileged user
    		getRootContainer().setConfigParameters(content, stripCharset(fRequestHeaders.getMediaType().toString()));
    	}
    	else {
    		getRootContainer().put(getConanicalURL(fRequestUrl.getRequestUri()), content, stripCharset(fRequestHeaders.getMediaType().toString()));
    	}
        return Response.status(Status.NO_CONTENT).build();
    }

    @POST
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE, LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public Response addResource(InputStream content) {
    	String loc = getRootContainer().post(content, stripCharset(fRequestHeaders.getMediaType().toString()));
    	if (loc != null)
    		return Response.status(Status.CREATED).header(HttpHeaders.LOCATION, loc).build();
    	else
    		return Response.status(Status.CONFLICT).build();
    }
    
    @POST
    @Path("{id}")
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE, LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public Response addResource(InputStream content, @PathParam("id") String id) {
    	String loc = getRootContainer().post(content, stripCharset(fRequestHeaders.getMediaType().toString()));
    	if (loc != null)
    		return Response.status(Status.CREATED).header(HttpHeaders.LOCATION, loc).build();
    	else
    		return Response.status(Status.CONFLICT).build();
    }

    @POST
    @Path("{id}")
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
    @Path("{id}")
    public Response deleteResource() {
    	String uri = getConanicalURL(fRequestUrl.getRequestUri());
    	getRootContainer().delete(uri);
        return Response.status(Status.NO_CONTENT).build();
    }
    
    @PATCH
    @Path("id")
    @Consumes(LDPConstants.CT_TEXT_TRIG)    
    public Response patchResource(final InputStream content, @PathParam("id") String id) {
    	getRootContainer().patch(getConanicalURL(fRequestUrl.getRequestUri()), content, stripCharset(fRequestHeaders.getMediaType().toString()));

      	return Response.status(Status.OK).build();
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
    
    

}

