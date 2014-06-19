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
 *     Samuel Padgett - add support for LDP Non-RDF Source
 *     Samuel Padgett - support Prefer header
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpStatus;
import org.eclipse.lyo.ldp.server.ILDPContainer;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
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

	/**
	 * Create a LDPContainer instance for the specified URI and with the default configuration parameters}.
	 * @see #LDPContainer(String, String, GraphStore, GraphStore, InputStream)
	 */
	public static synchronized JenaLDPContainer create(String containerURI, TDBGraphStore graphStore)
	{
		// Order is important here, need to see if the graph store does NOT an instance of the container
		// then create a bootstrap container.
		Model graphModel;
		JenaLDPContainer rootContainer;

		graphStore.readLock();
		try {
			graphModel = graphStore.getGraph(containerURI);
			rootContainer = new JenaLDPContainer(containerURI, graphStore);
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
			rootContainer.putCreate(containerURI, new ByteArrayInputStream(out.toByteArray()), LDPConstants.CT_TEXT_TURTLE, null, null);
		}
		return rootContainer;
	}

	protected JenaLDPContainer(String containerURI, TDBGraphStore graphStore)
	{
		super(containerURI, graphStore);
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

		// Get resource URI prefix string value
		String prefix = DEFAULT_RESOURCE_PREFIX;
		Statement stmt = containerResource.getProperty(JenaLDPImpl.resourceURIPrefix);
		if (stmt != null) {
			prefix = stmt.getObject().asLiteral().getString();
		}
		fResourceURIPrefix = appendURISegment(fURI, prefix);
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
			fGraphStore.createCompanionGraph(resourceURI, JenaLDPResourceManager.mintConfigURI(resourceURI));
			String result = addResource(resourceURI, true, stream, contentType, user);
			fGraphStore.commit();
			return result;
		} finally {
			fGraphStore.end();
		}
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPResource#putUpdate(java.io.InputStream, java.lang.String, java.lang.String)
	 */
	@Override
	public void putUpdate(InputStream stream, String contentType, String user, HttpHeaders requestHeaders)
	{
		fGraphStore.writeLock();
		try {
			if (fGraphStore.getGraph(getURI()) != null) {
				updateResource(stream, contentType, user, requestHeaders);
			}
			fGraphStore.commit();
		} finally {
			fGraphStore.end();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#putCreate(java.lang.String, java.io.InputStream, java.lang.String, java.lang.String)
	 */
	@Override
	public boolean putCreate(String resourceURI, InputStream stream, String contentType, String user, HttpHeaders requestHeaders)
	{
		boolean create = false;

		fGraphStore.writeLock();
		try {
			if (fGraphStore.getGraph(resourceURI) == null) {
				Model configModel = fGraphStore.getGraph(JenaLDPResourceManager.mintConfigURI(resourceURI));
				if (configModel != null) {
					// Attempting to reuse a URI, fail the request.
					throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity(
							"Can not create a resource for URI that has already been used for a deleted resource at: "+resourceURI).build());
				}
				fGraphStore.createCompanionGraph(resourceURI, JenaLDPResourceManager.mintConfigURI(resourceURI));
				addResource(resourceURI, false, stream, contentType, user);	
				create = true;
			} else {
				updateResource(stream, contentType, user, requestHeaders);
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
		Calendar time = Calendar.getInstance(); // to update dcterms:modified

		// Add membership triple
		if (addMembership) {
			addMembership(resourceURI, model, time);
		}

		// Add dcterms:creator, dcterms:created, dcterms:contributor, and dcterms:modified
		Resource subject = model.getResource(resourceURI);
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

	protected void addMembership(String resourceURI, Model model, Calendar time) {
	    String containerURI = getContainerURIForResource(resourceURI);
	    Model containerModel = fGraphStore.getGraph(containerURI);
	    Resource containerResource = containerModel.getResource(containerURI);
	    Property memberRelation = getMemberRelation(containerModel, containerResource);
	    Property memberOfRelation = getIsMemberOfRelation(containerModel, containerResource);
	    Resource membershipResource = getMembershipResource(containerModel, containerResource);

	    // model is null for non-RDF source
	    if (memberOfRelation != null && model != null) {
	    	model.add(model.getResource(resourceURI), memberOfRelation, membershipResource);
	    }

	    // If membership resource is NOT the same as the container
	    if (!membershipResource.asResource().getURI().equals(containerURI)) {
	    	Model membershipResourceModel = fGraphStore.getGraph(membershipResource.asResource().getURI());
	    	if (membershipResourceModel == null) {
	    		membershipResourceModel = containerModel;
	    	}

	    	Resource subject = membershipResourceModel.createResource(resourceURI);
	    	membershipResource = membershipResourceModel.getResource(membershipResource.asResource().getURI());
	    	if (memberRelation != null) {
	    		memberRelation = membershipResourceModel.getProperty(memberRelation.asResource().getURI());        			
	    	}

	    	// Update dcterms:modified
	    	membershipResource.removeAll(DCTerms.modified);
	    	membershipResource.addLiteral(DCTerms.modified, membershipResourceModel.createTypedLiteral(time));
	    	membershipResource.addProperty(memberRelation, subject);
	    	if (memberOfRelation == null) {
	    		membershipResource.addProperty(memberRelation, subject);
	    	}
	    } else if (memberOfRelation == null) {
	    	membershipResource.addProperty(memberRelation, containerModel.createResource(resourceURI));
	    }

	    // Put containment triples in container
	    containerResource.addProperty(LDP.contains, containerModel.createResource(resourceURI));
	    containerResource.removeAll(DCTerms.modified);
	    containerResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));
    }
	
	protected void updateResource(InputStream stream, String contentType, String user, HttpHeaders requestHeaders)
	{
		Model model = readModel(getURI(), stream, contentType);
		Resource newResource = model.getResource(getURI());

		Model before = fGraphStore.getGraph(getURI());
		// We shouldn't have gotten this far but to be safe
		if (before == null) throw new WebApplicationException(HttpStatus.SC_NOT_FOUND);
		
		// Make sure we're not updating any containment triples.
		List<Statement> originalContainmentTriples = before.listStatements(before.getResource(getURI()), LDP.contains, (RDFNode) null).toList();
		List<Statement> newContainmentTriples = newResource.listProperties(LDP.contains).toList();
		if (newContainmentTriples.size() != originalContainmentTriples.size()) {
			throw new WebApplicationException(HttpStatus.SC_CONFLICT);
		}

		for (Statement s : originalContainmentTriples) {
			if (!newResource.hasProperty(s.getPredicate(), s.getResource())) {
				throw new WebApplicationException(HttpStatus.SC_CONFLICT);
			}
		}
		updateResource(model, newResource, user, requestHeaders, getETag(before));
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
	public void delete()
	{
		// TODO: Need to determine if we need any other Container-specific
		super.delete();
	}
	

	@Override
	protected Model amendResponseGraph(Model container, MultivaluedMap<String, String> preferences)
	{
        // Create a copy of the container graph. We don't want to modify what is
        // saved in TDB, only amend the response graph.
		final Model result = ModelFactory.createDefaultModel();
		result.add(container);

		// Determine whether to include containment triples from the Prefer header.
        if (!includeContainment(preferences)) {
            result.getResource(fURI).removeAll(LDP.contains);
        }
        
		return result;
	}
	
	protected boolean isReturnRepresentationPreferenceApplied(MultivaluedMap<String, String> preferences) {
	    // Return true if any recognized include or omit preferences are in the request.
	    final List<String> include = preferences.get(LDPConstants.PREFER_INCLUDE);
	    final List<String> omit = preferences.get(LDPConstants.PREFER_OMIT);
	    
	    if (include != null
	            && (include.contains(LDPConstants.PREFER_MINIMAL_CONTAINER) || include.contains(LDPConstants.PREFER_CONTAINMENT))) {
	        return true;
	    }

	    return omit != null && omit.contains(LDPConstants.PREFER_CONTAINMENT);
	}

	@Override
    protected void amendResponse(ResponseBuilder response,
            MultivaluedMap<String, String> preferences) {
	    if (isReturnRepresentationPreferenceApplied(preferences)) {
	        response.header(LDPConstants.HDR_PREFERENCE_APPLIED, LDPConstants.PREFER_RETURN_REPRESENTATION);
	    }
        super.amendResponse(response, preferences);
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

	@Override
    public Set<String> getAllowedMethods() {
		Set<String> allow = super.getAllowedMethods();
		allow.add(HttpMethod.POST);
	    return allow;
    }

	@Override
    public Response postNonRDFSource(InputStream content, String stripCharset, String slug) {
		fGraphStore.writeLock();
		try {
			String uri = fGraphStore.mintURI(fURI, fResourceURIPrefix, slug);
			
			// Config graph for internal metadata (e.g., tracking resource deletion)
			String configURI = JenaLDPResourceManager.mintConfigURI(uri);
			Model configModel = fGraphStore.createCompanionGraph(uri, configURI);

			// LDP-NR associated RDF source
			String associatedURI = JenaLDPResourceManager.mintAssociatedRDFSourceURI(uri);
			Model associatedModel = fGraphStore.createCompanionGraph(uri, associatedURI);

			JenaLDPNonRdfSource.save(content, uri);

			addMembership(uri, null, Calendar.getInstance());
			configModel.add(configModel.getResource(configURI), Lyo.memberOf, configModel.getResource(fURI));

			Resource associatedResource = associatedModel.getResource(associatedURI);
			if (stripCharset != null) {
				Resource mediaType = associatedModel.createResource(null,  associatedModel.createResource(DCTerms.NS + "IMT"));
				mediaType.addProperty(RDF.value, stripCharset);
				associatedResource.addProperty(DCTerms.format, mediaType);
			}

			if (slug != null) {
				associatedResource.addProperty(Lyo.slug, slug);
			}
			
			fGraphStore.commit();

			return Response
					.status(Status.CREATED)
					.header(HttpHeaders.LOCATION, uri)
					.header(LDPConstants.HDR_LINK, "<" + getTypeURI() + ">; " + LDPConstants.HDR_LINK_TYPE)
					.header(LDPConstants.HDR_LINK, "<" + associatedURI + ">; " + LDPConstants.HDR_LINK_DESCRIBEDBY)
					.build();
        } finally {
			fGraphStore.end();
		}
    }

    protected boolean includeContainment(MultivaluedMap<String, String> preferences) {
        final List<String> include = preferences.get(LDPConstants.PREFER_INCLUDE);
        final List<String> omit = preferences.get(LDPConstants.PREFER_OMIT);
        
        final boolean omitContainment = (omit != null && omit.contains(LDPConstants.PREFER_CONTAINMENT));
        if (include != null) {
            if (include.contains(LDPConstants.PREFER_CONTAINMENT)) {
                return true;
            }
    
            if (include.contains(LDPConstants.PREFER_MINIMAL_CONTAINER) ||
                    include.contains(LDPConstants.DEPRECATED_PREFER_EMPTY_CONTAINER)) {
                return false;
            }
        }
        
        return !omitContainment;
     }
}
