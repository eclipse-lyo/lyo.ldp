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
 *     Samuel Padgett - make jsonld-java dependency optional
 *     Steve Speicher - updates for recent LDP spec changes
 *     Steve Speicher - make root URI configurable 
 *     Samuel Padgett - remove membership and containment triples on delete and update dcterms:modified
 *     Samuel Padgett - add ETag and Link headers with correct types on GET requests
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;
import org.eclipse.lyo.ldp.server.service.LDPService;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * This class implements a Linked Data Profile Container (LDP-C) using an RDF Graph Store.
 * The LDP specification can be found at www.w3.org/2012/ldp/hg/ldp.html.
 */
public class JenaLDPContainer extends LDPContainer
{
	protected String fResourceURIPrefix; // New resource name template, default is "res" + N
	protected boolean fMemberInfo; // include member info in container representation
	protected Set<Property> fMemberFilter; // filtered list of members to include
	protected String fConfigGraphURI;
	protected String fContainerMetaURI;
	
    public static final String NON_MEMBER_PROPERTIES = "?_meta";
    public static final String ADMIN = "?_admin";
    public static final String DEFAULT_RESOURCE_PREFIX = "res";


	protected final GraphStore fGraphStore; // GraphStore in which to store the container and member resources
	/**
	 * Create a LDPContainer instance for the specified URI.
	 * @param containerURI the URI of the BPC.
	 * @param graphStore the GraphStore for the container and member resources.
	 * @param pageStore the GraphStore for page graphs.
	 * @param config an RDF input stream containing configuration parameters (in Turtle format).
	 * @return the created LDPContainer.
	 */
	public static JenaLDPContainer create(String containerURI, GraphStore graphStore, GraphStore pageStore, InputStream config)
	{
		return new JenaLDPContainer(containerURI, graphStore, pageStore, config);
	}

	/**
	 * Create a LDPContainer instance for the specified URI and with the default configuration parameters}.
	 * @see #LDPContainer(String, String, GraphStore, GraphStore, InputStream)
	 */
	public static JenaLDPContainer create(String containerURI, GraphStore graphStore, GraphStore pageStore)
	{
		// Order is important here, need to see if the graph store does NOT an instance of the container
		// then create a bootstrap container.
		Model graphModel = graphStore.getGraph(containerURI);
		JenaLDPContainer rootContainer = create(containerURI, graphStore, pageStore, null);
		if (graphModel == null) {
			Model containerModel = ModelFactory.createDefaultModel();
			Resource containerResource = containerModel.createResource(LDPService.ROOT_CONTAINER_URL);
			containerResource.addProperty(RDF.type, LDP.DirectContainer);
			containerResource.addProperty(LDP.membershipResource, containerResource);
			containerResource.addProperty(LDP.hasMemberRelation, LDP.member);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			containerModel.write(out, "TURTLE");
			rootContainer.put(new ByteArrayInputStream(out.toByteArray()), LDPConstants.CT_TEXT_TURTLE);
		}
		return rootContainer;
	}

	protected JenaLDPContainer(String containerURI, GraphStore graphStore, GraphStore pageStore, InputStream config)
	{
		super(containerURI, graphStore);
		fGraphStore = graphStore;
		fConfigGraphURI = fURI + ADMIN;
		fContainerMetaURI = fURI + NON_MEMBER_PROPERTIES;
		setConfigParameters(config, LDPConstants.CT_TEXT_TURTLE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#setConfigParameters(java.io.InputStream, java.lang.String)
	 */
	public void setConfigParameters(InputStream config, String contentType)
	{
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

        Resource containerResource = configGraph.getResource(fConfigGraphURI);

        // Get member info boolean value
        Statement stmt = containerResource.getProperty(JenaLDPImpl.memberInfo);
        if (stmt != null) {
        	fMemberInfo = stmt.getObject().asLiteral().getBoolean();
        } else {
        	fMemberInfo = false;
			containerResource.addLiteral(JenaLDPImpl.memberInfo, fMemberInfo);
        }
        
        // Get member filter Property values
        NodeIterator iter = configGraph.listObjectsOfProperty(containerResource, JenaLDPImpl.memberFilter);
        if (iter.hasNext()) {
        	fMemberFilter = new HashSet<Property>();
        	do {
        		String uri = iter.next().asResource().getURI();
        		fMemberFilter.add(ResourceFactory.createProperty(uri));
        	} while (iter.hasNext());
        }
        else
        	fMemberFilter = null;

        // Get resource URI prefix string value
		stmt = containerResource.getProperty(JenaLDPImpl.resourceURIPrefix);
		if (stmt != null) {
			fResourceURIPrefix = stmt.getObject().asLiteral().getString();
		} else {
			fResourceURIPrefix = DEFAULT_RESOURCE_PREFIX;
			containerResource.addLiteral(JenaLDPImpl.resourceURIPrefix, fResourceURIPrefix);
		}
		fResourceURIPrefix = appendURISegment(fURI, fResourceURIPrefix);
		configGraph.close();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#put(java.io.InputStream, java.lang.String)
	 */
	public void put(InputStream stream, String contentType)
	{
		put(stream, contentType, null);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#put(java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public void put(InputStream stream, String contentType, String user)
	{
		put(fURI, stream, contentType);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#query(java.io.OutputStream, java.lang.String, java.lang.String)
	 */
	public void query(OutputStream outStream, String queryString, String resultsFormat)
	{
		fGraphStore.query(outStream, queryString, resultsFormat);
	}
	
	/*
	public void construct(OutputStream outStream, String constructQuery) // FB TEMP, for testing.
	{
		Model model = fGraphStore.construct(constructQuery);
		model.write(outStream, "Turtle");
	}
	*/

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#post(java.io.InputStream, java.lang.String)
	 */
	public String post(InputStream stream, String contentType)
	{
		return post(stream, contentType, null, null);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#post(java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public String post(InputStream stream, String contentType, String user, String nameHint)
	{
		String resourceURI = fGraphStore.createGraph(fURI, fResourceURIPrefix, nameHint);
		return addResource(resourceURI, true, stream, contentType, user);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#put(java.lang.String, java.io.InputStream, java.lang.String)
	 */
	public void put(String resourceURI, InputStream stream, String contentType)
	{
		put(resourceURI, stream, contentType, null);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#put(java.lang.String, java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public void put(String resourceURI, InputStream stream, String contentType, String user)
	{
		/* TODO: Handle ?_meta updates
		String baseURI = resourceURI.equals(fContainerMetaURI) ? fURI : resourceURI; */
		if (fGraphStore.getGraph(resourceURI) == null)
			addResource(resourceURI, false, stream, contentType, user);
		else
			updateResource(resourceURI, resourceURI, stream, contentType, user);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#patch(java.lang.String, java.io.InputStream, java.lang.String)
	 */
	public void patch(String resourceURI, InputStream stream, String contentType)
	{
		patch(resourceURI, stream, contentType, null);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#patch(java.lang.String, java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public void patch(String resourceURI, InputStream stream, String contentType, String user)
	{
		String baseURI = resourceURI.equals(fContainerMetaURI) ? fURI : resourceURI;
		if (fGraphStore.getGraph(resourceURI) == null)
			addResource(resourceURI, true, stream, contentType, user);
		else
			patchResource(resourceURI, baseURI, stream, contentType, user);
	}

	protected static String UNSPECIFIED_USER = "http://unspecified.user"; // TODO: How to handle this properly?
	
	/**
	 * Create resource and add membership triples
	 * @param resourceURI The NEW resource being added (including any query params, etc)
	 * @param addMembership If true, will add membership and containment triples
	 * @param stream Bytes from the request
	 * @param contentType Suggested type of the stream (turtle, rdf/xml, json, ...)
	 * @param user dcterms:creator name
	 * @return
	 */
	protected String addResource(String resourceURI, boolean addMembership, InputStream stream, String contentType, String user)
	{
        Model model = readModel(resourceURI, stream, contentType);
        Resource subject = model.getResource(resourceURI);
        Calendar time = Calendar.getInstance(); // to update dcterms:modified
		String containerURI = getContainerURIForResource(resourceURI);


       // Add membership triple
       if (addMembership) {
        	Model containerModel = fGraphStore.getGraph(containerURI);
        	Resource containerResource = containerModel.getResource(containerURI);
        	Property memberRelation = getMemberRelation(containerModel, containerResource);
        	Property memberOfRelation = getIsMemberOfRelation(containerModel, containerResource);
        	Resource membershipResource = getMembershipResource(containerModel, containerResource);
        	
        	// If membership resource is NOT the same as the container
        	if (!membershipResource.asResource().getURI().equals(containerURI)) {
        		Model membershipResourceModel = fGraphStore.getGraph(membershipResource.asResource().getURI());
        		if (membershipResourceModel == null) {
        			membershipResourceModel = containerModel;
        		} else {
        			membershipResource = membershipResourceModel.getResource(membershipResource.asResource().getURI());
        			if (memberRelation != null)
        				memberRelation = membershipResourceModel.getProperty(memberRelation.asResource().getURI());        			
        			// Update dcterms:modified
        			membershipResource.removeAll(DCTerms.modified);
        			membershipResource.addLiteral(DCTerms.modified, membershipResourceModel.createTypedLiteral(time));
        		}
        	}
        	if (memberOfRelation != null) {
        		model.add(subject, memberOfRelation, model.getResource(containerResource.getURI()));
        	} else {
        		membershipResource.addProperty(memberRelation, subject);
        	}
        	
        	// Put containment triples in container
        	containerResource.addProperty(LDP.contains, subject);
        	containerResource.removeAll(DCTerms.modified);
        	containerResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));
       }

       // Add dcterms:creator, dcterms:created, dcterms:contributor, and dcterms:modified
       if (user == null) user = UNSPECIFIED_USER;
       if (!model.contains(subject, DCTerms.creator))
    	   model.add(subject, DCTerms.creator, model.createResource(user));
       if (!model.contains(subject, DCTerms.contributor))
    	   model.add(subject, DCTerms.contributor, model.createResource(user));
       if (!model.contains(subject, DCTerms.created))
    	   model.add(subject, DCTerms.created, model.createTypedLiteral(time));
       if (!model.contains(subject, DCTerms.modified))
    	   model.add(subject, DCTerms.modified, model.createTypedLiteral(time));

       fGraphStore.putGraph(resourceURI, model);
       return resourceURI;	
	}

	private Model readModel(String baseURI, InputStream stream, String contentType) {
		final Model model;
		if (LDPConstants.CT_APPLICATION_JSON.equals(contentType) || LDPConstants.CT_APPLICATION_LD_JSON.equals(contentType)) {
			if (!isJSONLDPresent()) {
				throw new WebApplicationException(Status.UNSUPPORTED_MEDIA_TYPE);
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
				throw new RuntimeException(e);
			}
		} else {
			model = ModelFactory.createDefaultModel();
			String lang = WebContent.contentTypeToLang(contentType).getName();
			model.read(stream, baseURI, lang);
		}

		return model;
	}
	
	protected void updateResource(String resourceURI, String baseURI, InputStream stream, String contentType, String user)
	{
		Model model = readModel(baseURI, stream, contentType);
        Resource subject = model.getResource(resourceURI);
		
        // FIXME: Never used?
        if (user == null) user = UNSPECIFIED_USER;

        // Update dcterms:modified
        Calendar time = Calendar.getInstance();
		model.removeAll(subject, DCTerms.modified, null);
        model.add(subject, DCTerms.modified, model.createTypedLiteral(time));

		fGraphStore.putGraph(resourceURI, model);
		model.close();
	}
	
	protected void patchResource(String resourceURI, String baseURI, InputStream stream, String contentType, String user)
	{
		Model model = readModel(baseURI, stream, contentType);
        Resource subject = model.getResource(resourceURI);
		
        // Update dcterms:modified

        Calendar time = Calendar.getInstance();
        subject.removeAll(DCTerms.modified);
        subject.addLiteral(DCTerms.modified, model.createTypedLiteral(time));

		// TODO: Process patch contents
       
        /*fGraphStore.putGraph(resourceURI, model);
		model.close(); */
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#delete(java.lang.String)
	 */
	public void delete(String resourceURI)
	{
		String containerURI = getContainerURIForResource(resourceURI);
		
		// Remove the resource from the container
		Model containerModel = fGraphStore.getGraph(containerURI);
		Resource containerResource = containerModel.getResource(containerURI);
		Model membershipResourceModel = containerModel;
		Property memberRelation = getMemberRelation(containerModel, containerResource);
		Resource membershipResource = getMembershipResource(containerModel, containerResource);
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

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#get(java.lang.String, java.io.OutputStream, java.lang.String)
	 */
	public Response get(String resourceURI, String contentType)
	{
		Model graph = fGraphStore.getGraph(resourceURI);
		if (graph == null)
			throw new WebApplicationException(Status.NOT_FOUND);
		
		Resource r = graph.getResource(resourceURI);
		if (r != null && isContainerType(r)) {
			// TODO: Determine if getMembersQuery() is needed
			// graph.add(fGraphStore.construct(getMembersQuery(resourceURI)));
			if (fMemberInfo)
				graph = addMemberInformation(graph, r);
		}

		Statement s = r.getProperty(DCTerms.modified);
		final String eTag;
		if (s == null) {
			// uh oh. this should never be null.
			System.err.println("WARNING: Last modified is null for resource! <" + resourceURI + ">");
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
		String type;
		if (r.hasProperty(RDF.type, LDP.DirectContainer)) {
			type = LDPConstants.CLASS_DIRECT_CONTAINER;
		} else {
			type = LDPConstants.CLASS_RESOURCE;
		}
	
		return Response.ok(out).header(LDPConstants.HDR_ETAG, eTag).header(LDPConstants.HDR_LINK, "<" + type + ">; " + LDPConstants.HDR_LINK_TYPE).build();
	}

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

	protected Model addMemberInformation(Model container, Resource containerResource)
	{
		Model result = ModelFactory.createDefaultModel();
		result.add(container);

    	Property isMemberOfRelation = getIsMemberOfRelation(container, containerResource);
        Resource membershipResource = getMembershipResource(container, containerResource);

        if (isMemberOfRelation != null) {
        	// Handling ldp:isMemberOfRelation, where all membership triples are stored in member resource graphs
        	Model globalModel = JenaLDPService.getJenaRootContainer().getGraphStore().getGraph("urn:x-arq:UnionGraph"); 
        	result.add(globalModel.listStatements(null, isMemberOfRelation, membershipResource));
        } else {
    		Property memberRelation = getMemberRelation(container, containerResource);
			for (NodeIterator iter = container.listObjectsOfProperty(membershipResource, memberRelation); iter.hasNext(); ) {
				Resource member = iter.next().asResource();
				if (fMemberFilter == null) {
					// Add all the triples from the member
					result.add(fGraphStore.getGraph(member.getURI()));
				}
				else {
					// Filter the triples to be added
					Model memberGraph = fGraphStore.getGraph(member.getURI());
					//for (StmtIterator siter = memberGraph.listStatements(member, null, (RDFNode)null); siter.hasNext(); ) {
					for (StmtIterator siter = memberGraph.listStatements(); siter.hasNext(); ) {
						Statement stmt = siter.next();
						if (fMemberFilter.contains(stmt.getPredicate()))
							result.add(stmt);
					}
				}
			}
        }
		return result;
	}

	protected String fMembersQuery = null;
	protected String getMembersQuery(String containerURI)
	{
		if (fMembersQuery == null) {
	        StringBuffer sb = getBaseMembersQuery(containerURI);
	        sb.append("}");
	    	fMembersQuery = sb.toString();
	    	//System.out.println("construct query:\n" + fConstructQuery);
		}
		return fMembersQuery;
	}
	
	protected StringBuffer getBaseMembersQuery(String containerURI)
	{
		Model containerGraph = fGraphStore.getGraph(containerURI);
		Resource containerResource = containerGraph.getResource(containerURI);
		Property memberRelation = getMemberRelation(containerGraph, containerResource);
        Resource membershipResource = getMembershipResource(containerGraph, containerResource);
        StringBuffer sb = new StringBuffer("CONSTRUCT { <");
    	sb.append(membershipResource);
    	sb.append("> <");
    	sb.append(memberRelation);
    	sb.append("> ?m . } WHERE { <");
    	sb.append(membershipResource);
    	sb.append("> <");
    	sb.append(memberRelation);
    	sb.append("> ?m . ");
    	return sb;		
	}
	
	protected Property getMemberRelation(Model containerGraph, Resource containerResource)
	{
		Statement stmt = containerResource.getProperty(LDP.hasMemberRelation);
		return stmt != null ? containerGraph.getProperty(stmt.getObject().asResource().getURI()) : LDP.member;
	}
	
	protected Property getIsMemberOfRelation(Model containerGraph, Resource containerResource)
	{
		Statement stmt = containerResource.getProperty(LDP.isMemberOfRelation);
		return stmt != null ? containerGraph.getProperty(stmt.getObject().asResource().getURI()) : null;
	}

	protected Resource getMembershipResource(Model containerGraph, Resource containerResource)
	{
        Resource membershipResource = containerResource.getPropertyResourceValue(LDP.membershipResource);
        return membershipResource != null ? membershipResource : containerResource;
	}

	public static String appendURISegment(String base, String append)
	{
		return base.endsWith("/") ? base + append : base + "/" + append;
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
	
	public GraphStore getPagingGraphStore() {
		return null;
	}
	
	/**
	 * @param res
	 * @return true if rdf:type is one of: #BasicContainer, #DirectContainer or #IndirectContainer
	 */
	public static boolean isContainerType(Resource res) {
		StmtIterator stmts = res.listProperties(DC.type);
		while (stmts.hasNext()) {
			Statement stmt = stmts.next();
			for (String containerTypeURI : LDPConstants.CONTAINER_TYPES) {
				if (stmt.getObject().asResource().getURI().equals(containerTypeURI))
					return true;
			}
		}
		return false;
	}
}
