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
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Calendar;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.Response.Status;

import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPRDFSource;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.DCTerms;


public class JenaLDPRDFSource extends LDPRDFSource {
		
    public static final String CONFIG_PARAM = "?_config";
	
    /**
     * A companion resource "next to" the "real" resource, used to hold implementation
     * specific data.
     */
    protected String fConfigGraphURI;
	protected final GraphStore fGraphStore; // GraphStore in which to store the container and member resources   
    
	protected JenaLDPRDFSource(String resourceURI, GraphStore graphStore, GraphStore pageStore, InputStream config)
	{
		super(resourceURI, graphStore);
		fRDFType = LDPConstants.CLASS_RDFSOURCE;
		fGraphStore = graphStore;
		fConfigGraphURI = fURI + CONFIG_PARAM;
		setConfigParameters(config, LDPConstants.CT_TEXT_TURTLE);
	}
	
	protected Model getConfigModel(InputStream config, String contentType) {
		Model configGraph = null;
		if (config != null) {
	        configGraph = ModelFactory.createDefaultModel();
			String lang = WebContent.contentTypeToLang(contentType).getName();
			configGraph.read(config, fURI, lang);
			fGraphStore.putGraph(fConfigGraphURI, configGraph); // store config info with special side graph.
		} else {
			configGraph = fGraphStore.getGraph(fConfigGraphURI);
			if (configGraph == null) {
				configGraph = ModelFactory.createDefaultModel();  
				fGraphStore.putGraph(fConfigGraphURI, configGraph);
			}
		}
		return configGraph;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#setConfigParameters(java.io.InputStream, java.lang.String)
	 */
	public void setConfigParameters(InputStream config, String contentType)
	{
		Model graphModel = getConfigModel(config, contentType);
        graphModel.close();
	}

	@Override
	public void put(String resourceURI, InputStream stream, String contentType,
			String user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void patch(String resourceURI, InputStream stream,
			String contentType, String user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void delete(String resourceURI) {
		String containerURI = getContainerURIForResource(resourceURI);
		
		// Remove the resource from the container
		Model containerModel = fGraphStore.getGraph(containerURI);
		Resource containerResource = containerModel.getResource(containerURI);
		Model membershipResourceModel = containerModel;
		Property memberRelation = JenaLDPContainer.getMemberRelation(containerModel, containerResource);
		Resource membershipResource = JenaLDPContainer.getMembershipResource(containerModel, containerResource);
        Calendar time = Calendar.getInstance();

		if (!membershipResource.asResource().getURI().equals(containerURI)) {
			membershipResourceModel = fGraphStore.getGraph(membershipResource.asResource().getURI());
			if (membershipResourceModel == null) {
				membershipResourceModel = containerModel;
			} else {
				membershipResource = membershipResourceModel.getResource(membershipResource.asResource().getURI());
				// If isMemberOfRelation then membership triple will be nuked with the LDPR graph
				if (memberRelation != null)
					memberRelation = membershipResourceModel.getProperty(memberRelation.asResource().getURI());        			
				// Update dcterms:modified
			}
			membershipResource.removeAll(DCTerms.modified);
			membershipResource.addLiteral(DCTerms.modified, membershipResourceModel.createTypedLiteral(time));
		}
		membershipResourceModel.remove(membershipResource, memberRelation, membershipResourceModel.getResource(resourceURI));

		containerModel.remove(containerResource, LDP.contains, containerModel.getResource(resourceURI));
		containerResource.removeAll(DCTerms.modified);
		containerResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));

		// Delete the resource itself
		fGraphStore.deleteGraph(resourceURI);
	}

	@Override
	public Response get(String resourceURI, String contentType) {
		Model graph = fGraphStore.getGraph(fURI);
		if (graph == null)
			throw new WebApplicationException(Status.NOT_FOUND);
		
		Resource r = graph.getResource(fURI);
		amendResponseGraph(graph);

		Statement s = r.getProperty(DCTerms.modified);
		final String eTag;
		if (s == null) {
			// uh oh. this should never be null.
			System.err.println("WARNING: Last modified is null for resource! <" + fURI + ">");
			Literal modified = graph.createTypedLiteral(Calendar.getInstance());
			r.addLiteral(DCTerms.modified, modified);
			eTag = modified.getLexicalForm();
		} else {
			eTag = s.getLiteral().getLexicalForm();
		}
	
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
		
		// Determine the right type for the Link response header.
		String type = getTypeURI();
	
		return Response.ok(out).header(LDPConstants.HDR_ETAG, eTag).header(LDPConstants.HDR_LINK, "<" + type + ">; " + LDPConstants.HDR_LINK_TYPE).build();

	}
	
	/**
	 * For sub-classes to implement, given the graph for resource R, amend some triples before
	 * response set to client
	 * @param graph
	 */
	protected void amendResponseGraph(Model graph) { }
	
	private StreamingOutput getJSONLD(Model graph) {
	    if (!isJSONLDPresent()) {
	    	throw new WebApplicationException(Status.UNSUPPORTED_MEDIA_TYPE);
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
	        			throw new WebApplicationException(e, Response.status(Status.INTERNAL_SERVER_ERROR).build()); 
	                }
	            }
	        };
	        
	        return out;
	    } catch (Exception e) {
	    	throw new WebApplicationException(e, Response.status(Status.INTERNAL_SERVER_ERROR).build()); 
	    }
    }
	
	public GraphStore getGraphStore() {
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
		Model globalModel = JenaLDPService.getJenaRootContainer().getGraphStore().getGraph("urn:x-arq:UnionGraph");     	
		StmtIterator stmts = globalModel.listStatements(null, LDP.contains, globalModel.getResource(resourceURI));
		String containerURI = null;
		if (stmts.hasNext()) {
			containerURI = stmts.next().getSubject().asResource().getURI();
		} else {
			containerURI = fURI;
		}
		return containerURI;
	}

}
