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
 *     Samuel Padgett - allow implementations to set response headers using Response
 *     Samuel Padgett - add Accept-Patch header constants
 *     Samuel Padgett - pass request headers on HTTP PUT
 *     Samuel Padgett - return 201 status when PUT is used to create a resource
 *     Samuel Padgett - add support for LDP Non-RDF Source
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
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
import org.eclipse.lyo.ldp.server.ILDPContainer;
import org.eclipse.lyo.ldp.server.ILDPResource;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPNonRDFSource;
import org.eclipse.lyo.ldp.server.LDPResourceManager;

@Path("/{path:.*}")
public abstract class LDPService {
	public static final String LDP_CONTENT_SEGMENT = "ldp.contseg";
	public static final String LDP_ROOTURI = "ldp.rooturi";

	
	@Context HttpHeaders fRequestHeaders;
	@Context UriInfo fRequestUrl;
	@Context ServletContext context;
	@PathParam("path") String fPath;
	
	public static final String ROOT_APP_URL = System.getProperty(LDP_ROOTURI, "http://localhost:8080/ldp");
	public static final String ROOT_PATH_SEG = System.getProperty(LDP_CONTENT_SEGMENT, "/resources/");
	public static final String ROOT_CONTAINER_URL = ROOT_APP_URL + ROOT_PATH_SEG;
	//public static final String FILE_DIR = System.getProperty(LDP_FILE_DIR, getCon);
	private static String fPublicURI = ROOT_APP_URL;
	
	public static final String[] ACCEPT_POST_CONTENT_TYPES = {
			LDPConstants.CT_APPLICATION_RDFXML, 
			LDPConstants.CT_TEXT_TURTLE,
			LDPConstants.CT_APPLICATION_XTURTLE,
			LDPConstants.CT_APPLICATION_JSON,
			LDPConstants.CT_APPLICATION_LD_JSON };
	public static final String[] ACCEPT_PATCH_CONTENT_TYPES = ACCEPT_POST_CONTENT_TYPES;
	
	public static final String ACCEPT_POST_CONTENT_TYPES_STR = encodeAccept(ACCEPT_POST_CONTENT_TYPES);
	public static final String ACCEPT_PATCH_CONTENT_TYPES_STR = encodeAccept(ACCEPT_PATCH_CONTENT_TYPES);
	
	protected abstract void resetContainer();
	protected abstract ILDPContainer getRootContainer();
	protected abstract LDPResourceManager getResourceManger();
	
    public LDPService() { }
    
    /**
     * @return Does NOT include segment for container, use getRootContainer().getURI() for that.
     */
	protected String getPublicURI() { return fPublicURI ; }

    @GET
    @Produces(LDPConstants.CT_APPLICATION_RDFXML)
    public Response getContainerApplicationRDFXML() {	
        return getResource(LDPConstants.CT_APPLICATION_RDFXML);
    }
  
    @GET
    @Produces(LDPConstants.CT_TEXT_TURTLE)
    public Response getContainerTextTurtle() {	
        return getResource(LDPConstants.CT_TEXT_TURTLE);
    }
    
    @GET
    @Produces(LDPConstants.CT_APPLICATION_XTURTLE)
    public Response getContainerApplicationXTurtle() {	
        return getResource(LDPConstants.CT_APPLICATION_XTURTLE);
    }

    @GET
    @Produces({ LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public Response getContainerJSON() {	
        return getResource(LDPConstants.CT_APPLICATION_JSON);
    }

    @GET
    @Produces("*/*")
    public Response getNonRdfSource() {
    	return getResource(null);
    }

    @OPTIONS
    public Response options() {
    	String resourceURI = getConanicalURL(fRequestUrl.getRequestUri());
    	ILDPResource ldpR = getResourceManger().get(resourceURI);
    	if (ldpR == null) {
    		return Response.status(Status.NOT_FOUND).build();
    	}
    	
    	return Response.ok().allow(ldpR.getAllowedMethods()).build();
    }
    
    @PUT
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE })
    public Response updateResource(InputStream content) {
    	// Set the initial container representation. Should only be called once.
    	// May be invoked when query params are used, like ?_admin or ?_meta.
    	boolean created = getRootContainer().put(fRequestUrl.getRequestUri().toString(),  content, stripCharset(fRequestHeaders.getMediaType().toString()), null, fRequestHeaders);
        return Response.status((created) ? Status.CREATED : Status.NO_CONTENT).build();
    }
    
    @PUT
    @Consumes("*/*")
    public Response putLDPNR(InputStream content) {
    	String resourceURI = getConanicalURL(fRequestUrl.getRequestUri());
    	ILDPResource ldpR = getResourceManger().get(resourceURI);
    	if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
    	// We don't allow changing an LDP-RS to an LDP-NR.
    	if (!(ldpR instanceof LDPNonRDFSource)) return Response.status(Status.CONFLICT).build();
    	
    	ldpR.put(fRequestUrl.getRequestUri().toString(),  content, stripCharset(fRequestHeaders.getMediaType().toString()), null, fRequestHeaders);

    	return Response.status(Status.NO_CONTENT).build();
    }
    
    @POST
    @Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE, LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
    public Response createResource(@HeaderParam(LDPConstants.HDR_SLUG) String slug, InputStream content) {
    	
    	ILDPContainer ldpC = getRequestContainer();
    	
    	//  Look at headers (rel='type') and content to determine kind + interaction model
    	/* List<String> typeHeaders = getLDPTypesFromTypeHeader(fRequestHeaders); */
    	
    	//   if null/no-type and RDF content then create LDP-RR
    	/* @SuppressWarnings("rawtypes")
		Class interactionModel = LDPRDFResource.class; */
    	
    	//   TODO: if LDPC/w-LDPR interaction model, create container and mark as LDPR model
    	
    	//   else create LDPC with default interaction model (based on rdf:type)
    	String loc = ldpC.post(content, stripCharset(fRequestHeaders.getMediaType().toString()), null, slug);
    	if (loc != null)
    		return Response.status(Status.CREATED).header(HttpHeaders.LOCATION, loc)
					.header(LDPConstants.HDR_LINK, "<" + ldpC.getTypeURI() + ">; " + LDPConstants.HDR_LINK_TYPE).build();
    	else
    		return Response.status(Status.CONFLICT).build();
    }

	protected ILDPContainer getRequestContainer() {
	    // Look up content at Request-URI
    	//   if null return 404
    	//   if not container return 400
    	//   else follow model for container
    	ILDPResource ldpR = getResourceManger().get(getConanicalURL(fRequestUrl.getRequestUri()));
    	if (ldpR == null) throw new WebApplicationException(Status.NOT_FOUND);
    	else if (!(ldpR instanceof ILDPContainer)) throw new WebApplicationException(Status.BAD_REQUEST);  // TODO: Provide some details in response
    	
    	//   else follow model for POST against container
    	
    	ILDPContainer ldpC = (ILDPContainer)ldpR;
	    return ldpC;
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

    /*
     * POST non-RDF source
     */
    @POST
    @Consumes("*/*")
    public Response createLDPNR(@HeaderParam(LDPConstants.HDR_SLUG) String slug, InputStream content) {
    	return getRequestContainer().postLDPNR(content, stripCharset(fRequestHeaders.getMediaType().toString()), slug);
    }
 
    @DELETE
    public Response deleteResource() {
    	String uri = getConanicalURL(fRequestUrl.getRequestUri());
    	ILDPResource ldpR = getResourceManger().get(uri);
    	if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
    	// FIXME: Check if this is the root container before allowing delete.
    	ldpR.delete(uri);
        return Response.status(Status.NO_CONTENT).build();
    }
    
    @PATCH
    @Path("id")
    @Consumes(LDPConstants.CT_TEXT_TURTLE)    
    public Response patchResource(final InputStream content, @PathParam("id") String id) {
    	getRootContainer().patch(getConanicalURL(fRequestUrl.getRequestUri()), content, stripCharset(fRequestHeaders.getMediaType().toString()), null);

      	return Response.status(Status.OK).build();
    }
    
    private Response getResource(final String type) {	
    	String resourceURI = getConanicalURL(fRequestUrl.getRequestUri());
    	ILDPResource ldpR = getResourceManger().get(resourceURI);
    	if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
    	
    	return ldpR.get(resourceURI, type);
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
    
    /**
     * Make sure path segment, begins and ends with /
    */		
    static public String wrapPathSeg(String pathSeg) {
    	String str;
    	if (pathSeg.startsWith("//")) {
    		int i=0;
    		for(;i<pathSeg.length(); i++) {
    			if (pathSeg.charAt(i) != '/') break;
    		}
    		str = pathSeg.substring(i-1);
    	} else if (pathSeg.charAt(0)=='/')
    		str = pathSeg;
    	else 
   			str = "/"+pathSeg;

    	if (str.endsWith("//"))  {
    		int i=str.length()-1;
    		for (;i>0;i--){
    			if (str.charAt(i) != '/') break;
    		}
    		str = str.substring(0, i+2);
    	} else if (!str.endsWith("/")) {
    		str = str +"/";
    	} else if (str.length() == 1)
    		str = "";
    	
    	return str;
    }
    
}

