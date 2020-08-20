/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation.
 *
 *	All rights reserved. This program and the accompanying materials
 *	are made available under the terms of the Eclipse Public License v1.0
 *	and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *	
 *	The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *	and the Eclipse Distribution License is available at
 *	http://www.eclipse.org/org/documents/edl-v10.php.
 *	
 *	Contributors:
 *	
 *	   Frank Budinsky - initial API and implementation
 *	   Steve Speicher - initial API and implementation
 *	   Samuel Padgett - initial API and implementation
 *	   Steve Speicher - updates for recent LDP spec changes
 *	   Steve Speicher - make root URI configurable 
 *	   Samuel Padgett - add LDP-RS Link header to responses
 *	   Samuel Padgett - allow implementations to set response headers using Response
 *	   Samuel Padgett - add Accept-Patch header constants
 *	   Samuel Padgett - pass request headers on HTTP PUT
 *	   Samuel Padgett - return 201 status when PUT is used to create a resource
 *	   Samuel Padgett - add support for LDP Non-RDF Source
 *	   Samuel Padgett - respond with text/turtle when Accept header missing
 *	   Samuel Padgett - support Prefer header
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.Principal;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
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
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/{path:.*}")
public abstract class LDPService {
	private final static Logger log = LoggerFactory.getLogger(LDPService.class);
	public static final  String LDP_CONTENT_SEGMENT = "ldp.contseg";
	public static final  String LDP_ROOTURI = "ldp.rooturi";

	/**
	 * Regular expression that matches Link headers with URI
	 * {@link LDPConstants#CLASS_RESOURCE} and linknrelation {@code "type"}. These all match:
	 * <ul>
	 * <li>{@code <http://www.w3.org/ns/ldp#Resource>; rel="type"}</li>
	 * <li>{@code <http://www.w3.org/ns/ldp#Resource>; rel="type"; title="LDP Resource"}</li>
	 * <li>{@code <http://www.w3.org/ns/ldp#Resource>;rel=type}</li>
	 * <li>{@code <http://www.w3.org/ns/ldp#Resource>; rel="type http://example.net/relation/other"}</li>
	 * </ul>
	 * 
	 * @see #hasResourceTypeHeader(HttpHeaders)
	 */
	public static final String LINK_TYPE_RESOURCE_REGEX
                = "<http://www\\.w3\\.org/ns/ldp#Resource\\>\\s*;\\s*rel\\s*=\\s*((\"\\s*([^\"]+\\s+)*type(\\s+[^\"]+)*\\s*\")|\\s*type)([\\s;,]+.*|\\z)";
	
	@Context HttpServletRequest fRequest;
	@Context HttpHeaders fRequestHeaders;
	@Context UriInfo fRequestUrl;
	@Context ServletContext context;
	@PathParam("path") String fPath;
	
	public static final String ROOT_APP_URL = System.getProperty(LDP_ROOTURI, "http://localhost:8080/ldp");
	public static final String ROOT_PATH_SEG = System.getProperty(LDP_CONTENT_SEGMENT, "/resources/");
	public static final String ROOT_CONTAINER_URL = ROOT_APP_URL + ROOT_PATH_SEG;
	//public static final String FILE_DIR = System.getProperty(LDP_FILE_DIR, getCon);
	private static String fPublicURI = ROOT_APP_URL;
	
	public static final String[] ACCEPT_PATCH_CONTENT_TYPES = {
			LDPConstants.CT_APPLICATION_RDFXML, 
			LDPConstants.CT_TEXT_TURTLE,
			LDPConstants.CT_APPLICATION_XTURTLE,
			LDPConstants.CT_APPLICATION_JSON,
			LDPConstants.CT_APPLICATION_LD_JSON };
	
	public static final String ACCEPT_POST_CONTENT_TYPES_STR = "*/*";
	public static final String ACCEPT_PATCH_CONTENT_TYPES_STR = encodeAccept(ACCEPT_PATCH_CONTENT_TYPES);
	
	protected abstract void resetContainer();
	protected abstract ILDPContainer getRootContainer();
	protected abstract LDPResourceManager getResourceManger();

	public LDPService() {
		log.info(
				"Initialising LDPService with rooturi={}; contseg={}",
				ROOT_APP_URL,
				ROOT_CONTAINER_URL);
	}
	
	/**
	 * @return Does NOT include segment for container, use getRootContainer().getURI() for that.
	 */
	protected String getPublicURI() { return fPublicURI ; }
	
	@GET
	@Produces(LDPConstants.CT_TEXT_TURTLE)
	public Response getTextTurtle() {	
		return getResource(LDPConstants.CT_TEXT_TURTLE);
	}
	
	@GET
	@Produces(LDPConstants.CT_APPLICATION_XTURTLE)
	public Response getApplicationXTurtle() {	
		return getResource(LDPConstants.CT_APPLICATION_XTURTLE);
	}

	@GET
	@Produces({ LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
	public Response getJSON() { 
		return getResource(LDPConstants.CT_APPLICATION_JSON);
	}

	@GET
	@Produces(LDPConstants.CT_APPLICATION_RDFXML)
	public Response getApplicationRDFXML() {	
		return getResource(LDPConstants.CT_APPLICATION_RDFXML);
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
		
		return ldpR.options();
	}
 
	@PUT
	@Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE })
	public Response putRDFSource(InputStream content) {
		// Set the initial container representation. Should only be called once.
		// May be invoked when query params are used, like ?_admin or ?_meta.
		String resourceURI = getConanicalURL(fRequestUrl.getRequestUri());
		ILDPResource ldpR = getResourceManger().get(resourceURI);
		boolean created = false;
		if (ldpR != null) {
			ldpR.putUpdate(content, stripCharset(fRequestHeaders.getMediaType().toString()), null, fRequestHeaders);
		} else {
			created = getRootContainer().putCreate(fRequestUrl.getRequestUri().toString(),	content, stripCharset(fRequestHeaders.getMediaType().toString()), null, fRequestHeaders);
		}
		return Response.status((created) ? Status.CREATED : Status.NO_CONTENT).build();
	}
	
	@PUT
	@Consumes("*/*")
	public Response putNonRDFSource(InputStream content) {
		String resourceURI = getConanicalURL(fRequestUrl.getRequestUri());
		ILDPResource ldpR = getResourceManger().get(resourceURI);
		if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
		// We don't allow changing an LDP-RS to an LDP-NR.
		if (!(ldpR instanceof LDPNonRDFSource)) return Response.status(Status.CONFLICT).build();

		ldpR.putUpdate(content, stripCharset(fRequestHeaders.getMediaType().toString()), getCurrentUser(), fRequestHeaders);

		return Response.status(Status.NO_CONTENT).build();
	}
	
	@POST
	@Consumes({ LDPConstants.CT_APPLICATION_RDFXML, LDPConstants.CT_TEXT_TURTLE, LDPConstants.CT_APPLICATION_XTURTLE, LDPConstants.CT_APPLICATION_JSON, LDPConstants.CT_APPLICATION_LD_JSON })
	public Response post(@HeaderParam(LDPConstants.HDR_SLUG) final String slug, final InputStream content) {
		final ILDPContainer ldpC = getRequestContainer();
		final String contentType = stripCharset(fRequestHeaders.getMediaType().toString());
		final String user = getCurrentUser();
		final boolean isResourceInteractionModel = hasResourceTypeHeader(fRequestHeaders);
		String loc = ldpC.post(content, contentType, user, slug, isResourceInteractionModel);
		if (loc != null)
			return Response.status(Status.CREATED)
					.header(HttpHeaders.LOCATION, loc)
					.link(ldpC.getTypeURI(), LDPConstants.LINK_REL_TYPE).build();
		else
			return Response.status(Status.CONFLICT).build();
	}

	private String getCurrentUser() {
		final Principal principal = fRequest.getUserPrincipal();
		return (principal == null) ? null : principal.getName();
	}

	/**
	 * Given a set of request headers, return true if any of them are (roughly):
	 *	 Link: <http://www.w3.org/ns/ldp#Resource>; rel="type"
	 */
	public static boolean hasResourceTypeHeader(HttpHeaders headers) {
		List<String> linkHeaders = headers.getRequestHeader(LDPConstants.HDR_LINK);
		if (linkHeaders == null) {
			System.err.println("Null link header");
			return false;
		}
		for (String header : linkHeaders) {
			if (header.matches(LINK_TYPE_RESOURCE_REGEX)) {
				return true;
			}
		}
		return false;
	}

	protected static boolean isRelType(String p0) {
		String[] type = p0.split("=");
		if (type.length == 2)
			if (type[0].toLowerCase().contains("type")) {
				if (type[1].toLowerCase().contains("rel"))
					return true;
			} else if (type[1].toLowerCase().contains("type")) {
				if (type[0].toLowerCase().contains("rel"))
					return true;
			}
		return false;
	}

	protected ILDPContainer getRequestContainer() {
		// Look up content at Request-URI
		//	 if null return 404
		//	 if not container return 400
		//	 else follow model for container
		ILDPResource ldpR = getResourceManger().get(getConanicalURL(fRequestUrl.getRequestUri()));
		if (ldpR == null) throw new WebApplicationException(Status.NOT_FOUND);
		else if (!(ldpR instanceof ILDPContainer)) throw new WebApplicationException(Status.BAD_REQUEST);  // TODO: Provide some details in response
		
		//	 else follow model for POST against container
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
	public Response postNonRDFSource(@HeaderParam(LDPConstants.HDR_SLUG) String slug, InputStream content) {
		return getRequestContainer().postNonRDFSource(content, stripCharset(fRequestHeaders.getMediaType().toString()), getCurrentUser(), slug);
	}
 
	@DELETE
	public Response delete() {
		String uri = getConanicalURL(fRequestUrl.getRequestUri());
		ILDPResource ldpR = getResourceManger().get(uri);
		if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
		// FIXME: Check if this is the root container before allowing delete.
		ldpR.delete();
		return Response.status(Status.NO_CONTENT).build();
	}
	
	@PATCH
	@Path("id")
	@Consumes(LDPConstants.CT_TEXT_TURTLE)	  
	public Response patch(final InputStream content, @PathParam("id") String id) {
		getRootContainer().patch(getConanicalURL(fRequestUrl.getRequestUri()), content, stripCharset(fRequestHeaders.getMediaType().toString()), null);

		return Response.status(Status.OK).build();
	}
	
	private Response getResource(final String type) {	
		String resourceURI = getConanicalURL(fRequestUrl.getRequestUri());
		ILDPResource ldpR = getResourceManger().get(resourceURI);
		log.info("Resource {}={}", resourceURI, ldpR);
		if (ldpR == null) return Response.status(Status.NOT_FOUND).build();
		return ldpR.get(type, getPreferencesFromRequest());
	}

	/**
	 * Gets the <code>include</code> and <code>omit</code> values in the
	 * HTTP <code>Prefer</code> header for this request.
	 * 
	 * @param include a list of include values to populate
	 * @param omit a list of omit values to populate
	 * 
	 * @see <a href="http://tools.ietf.org/html/draft-snell-http-prefer-12">Prefer Header for HTTP</a>
	 */
	protected MultivaluedMap<String, String> getPreferencesFromRequest() {
		MultivaluedHashMap<String, String> preferencesMap = new MultivaluedHashMap<String, String>();
		List<String> preferences = fRequestHeaders.getRequestHeaders().get(LDPConstants.HDR_PREFER);
		if (preferences != null) {
			for (String preference : preferences) {
				// for example...
				// Prefer: return=representation; omit="http://www.w3.org/ns/ldp#PreferMembership http://www.w3.org/ns/ldp#PreferContainment"
				// Split tokens separated by ";"
				String[] tokens = preference.split(";");
				for (String token : tokens) {
					// for example, token might be...
					//	omit="http://www.w3.org/ns/ldp#PreferMembership http://www.w3.org/ns/ldp#PreferContainment"
					// Trim leading and trailing whitespace and split on the first "="
					String[] nameAndValues = token.trim().split("=", 2);
					if (nameAndValues.length == 2) {
						String name = nameAndValues[0].trim();

						// for example, nameAndValues[1] might be....
						// "http://www.w3.org/ns/ldp#PreferMembership http://www.w3.org/ns/ldp#PreferContainment"
						// Remove leading and trailing quotation marks and split on spaces
						String[] values = nameAndValues[1].replaceAll("^\"|\"$", "").split(" ");

						preferencesMap.addAll(name, values);
					}
				}
			}
		}
 
		return preferencesMap;
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

		if (str.endsWith("//"))	 {
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

