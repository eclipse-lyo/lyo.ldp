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
 *     Samuel Padgett - fix NPEx creating root container on first launch
 *     Samuel Padgett - use TDB transactions
 *     Samuel Padgett - add Allow header to GET responses
 *     Samuel Padgett - reject PUT requests that modify containment triples
 *     Samuel Padgett - check If-Match header on PUT requests
 *     Samuel Padgett - fix null resource prefix for root container
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpStatus;
import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.ILDPContainer;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;
import org.eclipse.lyo.ldp.server.service.LDPService;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * This class implements a Linked Data Profile Container (LDP-C) using an RDF Graph Store.
 * The LDP specification can be found at www.w3.org/2012/ldp/hg/ldp.html.
 */
public class JenaLDPContainer extends JenaLDPRDFSource implements ILDPContainer
{
    public static final String DEFAULT_RESOURCE_PREFIX = "res";

	protected String fResourceURIPrefix; // New resource name template, default is "res" + N
	protected boolean fMemberInfo; // include member info in container representation
	protected Set<Property> fMemberFilter; // filtered list of members to include


	/**
	 * Create a LDPContainer instance for the specified URI and with the default configuration parameters}.
	 * @see #LDPContainer(String, String, GraphStore, GraphStore, InputStream)
	 */
	public static synchronized JenaLDPContainer create(String containerURI, TDBGraphStore graphStore, GraphStore pageStore)
	{
		// Order is important here, need to see if the graph store does NOT an instance of the container
		// then create a bootstrap container.
		Model graphModel;
		JenaLDPContainer rootContainer;

		graphStore.readLock();
		try {
			graphModel = graphStore.getGraph(containerURI);
			rootContainer = new JenaLDPContainer(containerURI, graphStore, pageStore);
		} finally {
			graphStore.end();
		}


		if (graphModel == null) {
			Model containerModel = ModelFactory.createDefaultModel();
			Resource containerResource = containerModel.createResource(LDPService.ROOT_CONTAINER_URL);
			containerResource.addProperty(RDF.type, LDP.DirectContainer);
			containerResource.addProperty(LDP.membershipResource, containerResource);
			containerResource.addProperty(LDP.hasMemberRelation, LDP.member);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			containerModel.write(out, "TURTLE");
			rootContainer.put(containerURI, new ByteArrayInputStream(out.toByteArray()), LDPConstants.CT_TEXT_TURTLE, null, null);
		}
		return rootContainer;
	}

	protected JenaLDPContainer(String containerURI, TDBGraphStore graphStore, GraphStore pageStore)
	{
		super(containerURI, graphStore, pageStore);
		fRDFType = LDPConstants.CLASS_CONTAINER;
		setConfigParameters();
	}

	public void setConfigParameters()
	{
		Model configGraph = getConfigModel();
		if (configGraph == null) {
			fResourceURIPrefix = appendURISegment(fURI, DEFAULT_RESOURCE_PREFIX);
			return;
		}

		Resource containerResource = configGraph.getResource(fConfigGraphURI);

		// Get member info boolean value
		Statement stmt = containerResource.getProperty(JenaLDPImpl.memberInfo);
		if (stmt != null) {
			fMemberInfo = stmt.getObject().asLiteral().getBoolean();
		} else {
			fMemberInfo = false;
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
		String prefix = DEFAULT_RESOURCE_PREFIX;
		stmt = containerResource.getProperty(JenaLDPImpl.resourceURIPrefix);
		if (stmt != null) {
			prefix = stmt.getObject().asLiteral().getString();
		}
		fResourceURIPrefix = appendURISegment(fURI, prefix);
		configGraph.close();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#query(java.io.OutputStream, java.lang.String, java.lang.String)
	 */
	public void query(OutputStream outStream, String queryString, String resultsFormat)
	{
		fGraphStore.query(outStream, queryString, resultsFormat);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#post(java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public String post(InputStream stream, String contentType, String user, String nameHint)
	{
		fGraphStore.writeLock();
		try {
			String resourceURI = fGraphStore.createGraph(fURI, fResourceURIPrefix, nameHint);
			fGraphStore.createConfigGraph(resourceURI, mintConfigURI(resourceURI));
			String result = addResource(resourceURI, true, stream, contentType, user);
			fGraphStore.commit();
			return result;
		} finally {
			fGraphStore.end();
		}
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#put(java.lang.String, java.io.InputStream, java.lang.String, java.lang.String)
	 */
	@Override
	public boolean put(String resourceURI, InputStream stream, String contentType, String user, HttpHeaders requestHeaders)
	{
		boolean create = false;

		fGraphStore.writeLock();
		try {
			if (fGraphStore.getGraph(resourceURI) == null) {
				Model configModel = fGraphStore.getGraph(mintConfigURI(resourceURI));
				if (configModel != null) {
					// Attempting to reuse a URI, fail the request.
					throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity(
							"Can not create a resource for URI that has already been used for a deleted resource at: "+resourceURI).build());
				}
				fGraphStore.createConfigGraph(resourceURI, mintConfigURI(resourceURI));
				addResource(resourceURI, false, stream, contentType, user);	
				create = true;
			} else {
				updateResource(resourceURI, resourceURI, stream, contentType, user, requestHeaders);
			}
			fGraphStore.commit();
		} finally {
			fGraphStore.end();
		}
		
		return create;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#patch(java.lang.String, java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public void patch(String resourceURI, InputStream stream, String contentType, String user)
	{
		String baseURI = resourceURI.equals(fConfigGraphURI) ? fURI : resourceURI;
		fGraphStore.writeLock();
		try {
			if (fGraphStore.getGraph(resourceURI) == null)
				addResource(resourceURI, true, stream, contentType, user);
			else
				patchResource(resourceURI, baseURI, stream, contentType, user);
			fGraphStore.commit();
		} finally {
			fGraphStore.end();
		}
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

		// Add membership triple
		if (addMembership) {
			String containerURI = getContainerURIForResource(resourceURI);
			Model containerModel = fGraphStore.getGraph(containerURI);
			Resource containerResource = containerModel.getResource(containerURI);
			Property memberRelation = getMemberRelation(containerModel, containerResource);
			Property memberOfRelation = getIsMemberOfRelation(containerModel, containerResource);
			Resource membershipResource = getMembershipResource(containerModel, containerResource);

			if (memberOfRelation != null) {
				model.add(subject, memberOfRelation, model.getResource(containerResource.getURI()));
			}

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
					membershipResource.addProperty(memberRelation, subject);
					if (memberOfRelation == null) {
						membershipResource.addProperty(memberRelation, subject);
					}
				}
			} else if (memberOfRelation == null) {
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
	
	protected void updateResource(String resourceURI, String baseURI, InputStream stream, String contentType, String user, HttpHeaders requestHeaders)
	{
		Model model = readModel(baseURI, stream, contentType);
		Resource subject = model.getResource(resourceURI);

		Model before = fGraphStore.getGraph(resourceURI);
		if (resourceURI.equals(fURI)) {
			// Make sure we're not updating any containment triples.
			List<Statement> originalContainmentTriples = before.listStatements(before.getResource(resourceURI), LDP.contains, (RDFNode) null).toList();
			List<Statement> newContainmentTriples = subject.listProperties(LDP.contains).toList();
			if (newContainmentTriples.size() != originalContainmentTriples.size()) {
				throw new WebApplicationException(HttpStatus.SC_CONFLICT);
			}
	
			for (Statement s : originalContainmentTriples) {
				if (!subject.hasProperty(s.getPredicate(), s.getResource())) {
					throw new WebApplicationException(HttpStatus.SC_CONFLICT);
				}
			}
		}
		
		// Check the If-Match request header.
		if (requestHeaders != null) {
			String ifMatch = requestHeaders.getHeaderString(HttpHeaders.IF_MATCH);
			if (ifMatch == null) {
				// condition required
				throw new WebApplicationException(428);
			}
			String originalETag = getETag(before.getResource(resourceURI));
			// FIXME: Does not handle wildcards or comma-separated values...
			if (!originalETag.equals(ifMatch)) {
				throw new WebApplicationException(HttpStatus.SC_PRECONDITION_FAILED);
			}
		}

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
		// TODO: Need to determine if we need any other Container-specific
		super.delete(resourceURI);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#get(java.lang.String, java.io.OutputStream, java.lang.String)
	 */
	protected void amendResponseGraph(Model graph)
	{
		Resource r = graph.getResource(fURI);
			// TODO: Determine if getMembersQuery() is needed
			// graph.add(fGraphStore.construct(getMembersQuery(resourceURI)));
			if (fMemberInfo)
				graph = addMemberInformation(graph, r);
	}

	protected Model addMemberInformation(Model container, Resource containerResource)
	{
		Model result = ModelFactory.createDefaultModel();
		result.add(container);

    	Property isMemberOfRelation = getIsMemberOfRelation(container, containerResource);
        Resource membershipResource = getMembershipResource(container, containerResource);

        if (isMemberOfRelation != null) {
        	// Handling ldp:isMemberOfRelation, where all membership triples are stored in member resource graphs
        	Model globalModel = fGraphStore.getGraph("urn:x-arq:UnionGraph"); 
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
	
	public static Property getMemberRelation(Model containerGraph, Resource containerResource)
	{
		Statement stmt = containerResource.getProperty(LDP.hasMemberRelation);
		return stmt != null ? containerGraph.getProperty(stmt.getObject().asResource().getURI()) : LDP.member;
	}
	
	public static Property getIsMemberOfRelation(Model containerGraph, Resource containerResource)
	{
		Statement stmt = containerResource.getProperty(LDP.isMemberOfRelation);
		return stmt != null ? containerGraph.getProperty(stmt.getObject().asResource().getURI()) : null;
	}

	public static Resource getMembershipResource(Model containerGraph, Resource containerResource)
	{
        Resource membershipResource = containerResource.getPropertyResourceValue(LDP.membershipResource);
        return membershipResource != null ? membershipResource : containerResource;
	}

	public static String appendURISegment(String base, String append)
	{
		return base.endsWith("/") ? base + append : base + "/" + append;
	}

	public TDBGraphStore getPagingGraphStore() {
		return null;
	}
	
	@Override
    public Set<String> getAllowedMethods() {
		Set<String> allow = super.getAllowedMethods();
		allow.add(HttpMethod.POST);
	    return allow;
    }
}
