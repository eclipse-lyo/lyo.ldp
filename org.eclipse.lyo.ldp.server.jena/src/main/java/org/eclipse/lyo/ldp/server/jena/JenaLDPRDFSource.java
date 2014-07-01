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
 *     Samuel Padgett - use TDB transactions
 *     Samuel Padgett - add Allow header to GET responses
 *     Samuel Padgett - add request headers to put() parameters
 *     Samuel Padgett - add support for LDP Non-RDF Source
 *     Samuel Padgett - support read-only properties and rel="describedby"
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;
import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPRDFSource;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;
import org.eclipse.lyo.ldp.server.jena.vocabulary.Lyo;
import org.eclipse.lyo.ldp.server.service.LDPService;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.DCTerms;


public class JenaLDPRDFSource extends LDPRDFSource {

	public static String UNSPECIFIED_USER = "http://unspecified.user"; // TODO: How to handle this properly?
	public static final String CONSTRAINTS_URI =
			UriBuilder.fromPath(LDPService.ROOT_APP_URL).path("constraints.ttl").build().toString();

    /**
     * A companion resource "next to" the "real" resource, used to hold implementation
     * specific data.
     */
    protected String fConfigGraphURI;
	protected final TDBGraphStore fGraphStore; // GraphStore in which to store the container and member resources   
    
	protected JenaLDPRDFSource(String resourceURI, TDBGraphStore graphStore)
	{
		super(resourceURI, graphStore);
		fRDFType = LDPConstants.CLASS_RDFSOURCE;
		fGraphStore = graphStore;
		fConfigGraphURI = JenaLDPResourceManager.mintConfigURI(fURI);
	}
	
	protected Model getConfigModel() {
		return fGraphStore.getGraph(fConfigGraphURI);
	}

	@Override
	public void putUpdate(InputStream stream, String contentType, String user, HttpHeaders requestHeaders) {
		fGraphStore.writeLock();
		try {
			updateResource(stream, contentType, requestHeaders);
			fGraphStore.commit();
		} finally {
			fGraphStore.end();
		}
	}

	protected void updateResource(InputStream stream, String contentType, HttpHeaders requestHeaders) {
	    Model model = readModel(getURI(), stream, contentType);

	    Model before = fGraphStore.getGraph(getURI());
	    // We shouldn't have gotten this far but to be safe
	    if (before == null)
	    	throw new WebApplicationException(HttpStatus.SC_NOT_FOUND);

	    // Check the If-Match request header.
	    checkIfMatch(requestHeaders, before);

	    for (String property : getReadOnlyProperties()) {
	    	failIfReadOnlyPropertyChanged(before, model, property);
	    }

	    // Update dcterms:modified
	    Calendar time = Calendar.getInstance();
	    Resource newResource = model.getResource(getURI());
	    model.removeAll(newResource, DCTerms.modified, null);
	    model.add(newResource, DCTerms.modified, model.createTypedLiteral(time));

	    fGraphStore.putGraph(getURI(), model);
    }

	protected void checkIfMatch(HttpHeaders requestHeaders, Model before) {
	    if (requestHeaders != null) {
	    	String ifMatch = requestHeaders.getHeaderString(HttpHeaders.IF_MATCH);
	    	if (ifMatch == null) {
	    		// condition required
	    		throw new WebApplicationException(build(Response.status(428)));
	    	}
	    	final String originalETag = getETag(before);
	    	// FIXME: Does not handle wildcards or comma-separated values...
	    	if (!originalETag.equals(ifMatch)) {
	    	    fail(Status.PRECONDITION_FAILED);
	    	}
	    }
    }
	
	@Override
	public void patch(String resourceURI, InputStream stream,
			String contentType, String user) {
		// TODO Auto-generated method stub
	}

	@Override
	public void delete() {
		// If this is a companion to another resources (e.g., a config graph), don't delete it.
		if (JenaLDPResourceManager.isCompanion(getURI())) {
		    fail(Status.FORBIDDEN);
		}

		fGraphStore.writeLock();
		try {
		    // FIXME: Logic to remove containment and membership triples should really be in JenaLDPContainer and subclasses.
			final String containerURI = getContainerURIForResource(getURI());
			final Model containerModel = fGraphStore.getGraph(containerURI);
			final Resource containerResource = containerModel.getResource(containerURI);
			final Property memberRelation = JenaLDPDirectContainer.getMemberRelation(containerModel, containerResource);
			final Calendar time = Calendar.getInstance();

			// Remove the membership triples.
			if (memberRelation != null) {
			    final String membershipResourceURI = JenaLDPDirectContainer.getMembershipResourceURI(containerModel, containerResource);
			    final Model membershipResourceModel = (membershipResourceURI.equals(containerURI)) ? containerModel : fGraphStore.getGraph(membershipResourceURI);
			    final Resource membershipResource = membershipResourceModel.getResource(membershipResourceURI);
			    membershipResource.removeAll(DCTerms.modified);
			    membershipResource.addLiteral(DCTerms.modified, membershipResourceModel.createTypedLiteral(time));
			    membershipResourceModel.remove(membershipResource, memberRelation, membershipResourceModel.getResource(getURI()));
			}

			// Remove containment triples.
			containerModel.remove(containerResource, LDP.contains, containerModel.getResource(getURI()));
			containerResource.removeAll(DCTerms.modified);
			containerResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));

			// Delete the resource itself
			fGraphStore.deleteGraph(getURI());
			
			// Keep track of the deletion by logging the delete time
			final String configURI = JenaLDPResourceManager.mintConfigURI(getURI());
			Model configModel = fGraphStore.getGraph(configURI);
			if (configModel == null) {
				configModel = fGraphStore.createCompanionGraph(getURI(), configURI);
			}
			configModel.getResource(getURI()).addLiteral(Lyo.deleted, configModel.createTypedLiteral(time));
			
			fGraphStore.commit();
		} finally {
			fGraphStore.end();
		}
	}

	@Override
	public Response get(String contentType, MultivaluedMap<String, String> preferences) {
		fGraphStore.readLock();
		try {
			Model graph = fGraphStore.getGraph(fURI);
			if (graph == null)
				throw new WebApplicationException(Status.NOT_FOUND);

			final String eTag = getETag(graph);
			graph = amendResponseGraph(graph, preferences);
			StreamingOutput out;
			if (LDPConstants.CT_APPLICATION_JSON.equals(contentType)) {
				out = getJSONLD(graph);
			} else {
				final String lang = WebContent.contentTypeToLang(contentType).getName();
				final Model responseModel = graph;
				out = new StreamingOutput() {
					@Override
					public void write(OutputStream output) throws IOException, WebApplicationException {
						responseModel.write(output, lang);
					}
				};
			}

			ResponseBuilder response = Response.ok(out).header(LDPConstants.HDR_ETAG, eTag);
			amendResponse(response, preferences);

			return build(response);
		} finally {
			fGraphStore.end();
		}
	}

	protected void amendResponse(ResponseBuilder response, MultivaluedMap<String, String> preferences) {
    }

    /**
	 * Create a weak ETag value from a Jena model.
	 * 
	 * @param m the model that represents the HTTP response body
	 * @return an ETag value
	 * 
	 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.19">HTTP 1.1: Section 14.19 - ETag</a>
	 */
	protected String getETag(Model m) {
		// Get the MD5 hash of the model as N-Triples.
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		m.write(out,  "N-TRIPLE");
		String md5 = DigestUtils.md5Hex(out.toByteArray());

		// Create a weak entity tag from the MD5 hash.
	    return "W/\"" + md5 + "\"";
    }
	
	/**
	 * For sub-classes to implement, given the graph for resource R, amend some triples before
	 * response set to client
	 * @param graph
	 * @param preferences 
	 * @return the amended model
	 */
	protected Model amendResponseGraph(Model graph, MultivaluedMap<String, String> preferences) {
	    // Nothing to do unless we're a container, which is handled by subclasses.
	    return graph;
	}
	
	private StreamingOutput getJSONLD(Model graph) {
	    if (!isJSONLDPresent()) {
	        fail(Status.UNSUPPORTED_MEDIA_TYPE);
	        return null;
	    }

	    try {
	    	// Use Java reflection for the optional jsonld-java depedency.
	    	Class<?> jsonldClass = Class.forName("com.github.jsonldjava.core.JSONLD");
	    	Class<?> rdfParserClass = Class.forName("com.github.jsonldjava.core.RDFParser");
	    	Class<?> jsonldUtilsClass = Class.forName("com.github.jsonldjava.utils.JSONUtils");
	    	Class<?> jenaRDFParserClass = Class.forName("com.github.jsonldjava.impl.JenaRDFParser");
	    	Class<?> optionsClass = Class.forName("com.github.jsonldjava.core.Options");

	    	//Object json = JSONLD.fromRDF(graph, new JenaRDFParser());
	    	Method fromRDFMethod = jsonldClass.getMethod("fromRDF", Object.class, rdfParserClass);
	    	Object json = fromRDFMethod.invoke(null, graph, jenaRDFParserClass.newInstance());

	    	InputStream is = getClass().getClassLoader().getResourceAsStream("context.jsonld");

	    	//Object context = JSONUtils.fromInputStream(is);
	    	Method fromInputStreamMethod = jsonldUtilsClass.getMethod("fromInputStream", InputStream.class);
	    	Object context = fromInputStreamMethod.invoke(null, is);
	    	
	    	//json = JSONLD.compact(json, context, new Options("", true));
	    	Method compactMethod = jsonldClass.getMethod("compact", Object.class, Object.class, optionsClass);
	    	Constructor<?> optionsContructor = optionsClass.getDeclaredConstructor(String.class, Boolean.class);
	    	Object options = optionsContructor.newInstance("", true);
	    	final Object compactedJson = compactMethod.invoke(null, json, context, options);

	    	//JSONUtils.writePrettyPrint(new PrintWriter(outStream), json);
	    	final Method writePrettyPrintMethod = jsonldUtilsClass.getMethod("writePrettyPrint", Writer.class, Object.class);
	    	StreamingOutput out = new StreamingOutput() {
	    		@Override
	            public void write(OutputStream output) throws IOException, WebApplicationException {
	    			try {
	                    writePrettyPrintMethod.invoke(null, new PrintWriter(output), compactedJson);
	                } catch (Exception e) {
	                    fail(Status.INTERNAL_SERVER_ERROR);
	                }
	            }
	        };
	        
	        return out;
	    } catch (Exception e) {
	        fail(Status.INTERNAL_SERVER_ERROR);
	        return null;
	    }
    }
	
	public TDBGraphStore getGraphStore() {
		return fGraphStore;
	}

	/*
	 * Check if the jsonld-java library is present.
	 */
	protected boolean isJSONLDPresent() {
		try {
			Class.forName("com.github.jsonldjava.core.JSONLD");
			Class.forName("com.github.jsonldjava.impl.JenaRDFParser");
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
	/**
	 * Given as input resourceURI, find the containerURI that ldp:contains it
	 * @param resourceURI
	 * @return containerURI
	 */
	protected String getContainerURIForResource(String resourceURI) {
		// Determine which container this resource belongs (so we can remove the right membership and containment triples)
		Model globalModel = fGraphStore.getGraph("urn:x-arq:UnionGraph");     	
		StmtIterator stmts = globalModel.listStatements(null, LDP.contains, globalModel.getResource(resourceURI));
		String containerURI = null;
		if (stmts.hasNext()) {
			containerURI = stmts.next().getSubject().asResource().getURI();
		} else {
			containerURI = fURI;
		}
		return containerURI;
	}
	
	protected void fail(Status status) {
	    throw new WebApplicationException(build(Response.status(status)));
	}
	
	/**
     * Helper to add standard content (Allow header, Link header) to this response.
     * 
     * @param response
     *            the response builder to add to
     * @return the response with additional content common to all responses
     */
	protected Response build(ResponseBuilder response) {
	   return response
    			.allow(getAllowedMethods())
    			.header(LDPConstants.HDR_LINK, "<" + getTypeURI() + ">; " + LDPConstants.HDR_LINK_TYPE)
    			.build();
	}

	@Override
	public Response options() {
    	return build(Response.ok());
	}
	
	@Override
    public Set<String> getAllowedMethods() {
		HashSet<String> allowedMethods = new HashSet<String>();
		allowedMethods.add(HttpMethod.GET);
		allowedMethods.add(HttpMethod.PUT);
		allowedMethods.add(HttpMethod.DELETE);
		allowedMethods.add(HttpMethod.HEAD);
		allowedMethods.add(HttpMethod.OPTIONS);
		allowedMethods.add("PATCH");

	    return allowedMethods;
    }
	
	protected Set<String> getReadOnlyProperties() {
		HashSet<String> readOnly = new HashSet<String>();
		readOnly.add(DCTerms.created.getURI());
		readOnly.add(DCTerms.modified.getURI());
		
		return readOnly;
	}
	
	protected Model readModel(String baseURI, InputStream stream, String contentType) {
		final Model model;
		if (LDPConstants.CT_APPLICATION_JSON.equals(contentType) || LDPConstants.CT_APPLICATION_LD_JSON.equals(contentType)) {
			if (!isJSONLDPresent()) {
			    fail(Status.UNSUPPORTED_MEDIA_TYPE);
			    return null;
			}

	        try {
	        	// Use reflection to invoke the optional jsonld-java dependency.
	        	Class<?> jsonldTripleCallback = Class.forName("com.github.jsonldjava.core.JSONLDTripleCallback");
	        	Class<?> jenaTripleCallback = Class.forName("com.github.jsonldjava.impl.JenaTripleCallback");
				Class<?> jsonldUtilsClass = Class.forName("com.github.jsonldjava.utils.JSONUtils");
				Class<?> jsonldClass = Class.forName("com.github.jsonldjava.core.JSONLD");

	        	//final JSONLDTripleCallback callback = new JenaTripleCallback();
				//model = (Model) JSONLD.toRDF(JSONUtils.fromInputStream(stream), callback);
				Method fromInputStreamMethod = jsonldUtilsClass.getMethod("fromInputStream", InputStream.class);
				Object input = fromInputStreamMethod.invoke(null, stream);
				Method toRDFMethod = jsonldClass.getMethod("toRDF", Object.class, jsonldTripleCallback);
				model = (Model) toRDFMethod.invoke(null, input, jenaTripleCallback.newInstance());
			} catch (Exception e) {
			    fail(Status.INTERNAL_SERVER_ERROR);
			    return null;
			}
		} else {
			model = ModelFactory.createDefaultModel();
			String lang = WebContent.contentTypeToLang(contentType).getName();
			model.read(stream, baseURI, lang);
		}

		return model;
	}
	
	protected void failIfReadOnlyPropertyChanged(Model before, Model after, String property) {
		Resource newResource = after.getResource(getURI());
		List<Statement> originalTriples = before.listStatements(before.getResource(getURI()), before.getProperty(property), (RDFNode) null).toList();
		List<Statement> newTriples = newResource.listProperties(after.getProperty(property)).toList();
		if (newTriples.size() != originalTriples.size()) {
			failReadOnlyProperty(property);
		}

		for (Statement s : originalTriples) {
		    if (!newResource.hasProperty(s.getPredicate(), s.getObject())) {
		    	failReadOnlyProperty(property);
		    }
		}
	}

	protected Response buildErrorResponse(Model body) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		body.write(out, "TURTLE");

		return build(Response.status(Status.CONFLICT)
		        .link(CONSTRAINTS_URI, "\"describedby\"")
		        .entity(out.toByteArray()));
	}
	
	protected void failReadOnlyProperty(String uri) {
		Model responseBody = ModelFactory.createDefaultModel();
		Resource error = responseBody.createResource(Lyo.Error);
		error.addProperty(DCTerms.title, "Cannot change property");
		error.addProperty(
		        DCTerms.description,
		        "The property <" + uri + "> is read-only and cannot be assigned by clients.");

		throw new WebApplicationException(buildErrorResponse(responseBody));
	}
}
