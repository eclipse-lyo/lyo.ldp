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
package org.eclipse.lyo.ldp.server.jena;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.github.jsonldjava.core.JSONLD;
import com.github.jsonldjava.core.JSONLDProcessingError;
import com.github.jsonldjava.core.JSONLDTripleCallback;
import com.github.jsonldjava.core.Options;
import com.github.jsonldjava.impl.JenaRDFParser;
import com.github.jsonldjava.impl.JenaTripleCallback;
import com.github.jsonldjava.utils.JSONUtils;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFList;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

/**
 * This class implements a Linked Data Profile Container (LDP-C) using an RDF Graph Store.
 * The LDP specification can be found at www.w3.org/2012/ldp/hg/ldp.html.
 */
public class JenaLDPContainer implements LDPContainer
{
	protected final String fContainerURI; // URI of the BPC

	protected String fResourceURIPrefix; // URI prefix of POSTED resource URIs
	protected int fPageSize; // members per LDP-C page
	protected boolean fMemberInfo; // include member info in container representation
	protected Set<Property> fMemberFilter; // filtered list of members to include
	protected RDFList fSortPredicates; // sort predicates for paged representation

    public static final String NON_MEMBER_PROPERTIES = "?non-member-properties";
    public static final String FIRST_PAGE = "?firstPage";
    public static final String NTH_PAGE = "?p=";
	public static final int DEFAULT_PAGE_SIZE = 100;
	public static final String DEFAULT_RESOURCE_PREFIX = "res";

	protected final GraphStore fGraphStore; // GraphStore in which to store the container and member resources
	protected final GraphStore fPageStore; // GraphStore for page graphs

	protected boolean fComputePages = true; // true when paged representation needs to be recomputed.

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
		return create(containerURI, graphStore, pageStore, null);
	}

	protected JenaLDPContainer(String containerURI, GraphStore graphStore, GraphStore pageStore, InputStream config)
	{
		fContainerURI = containerURI;
		fGraphStore = graphStore;
		fPageStore = pageStore;
		setConfigParameters(config, "text/turtle");
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#setConfigParameters(java.io.InputStream, java.lang.String)
	 */
	public void setConfigParameters(InputStream config, String contentType)
	{
		if (config != null) {
	        Model model = ModelFactory.createDefaultModel();
			String lang = WebContent.contentTypeToLang(contentType).getName();
	        model.read(config, fContainerURI, lang);
			fGraphStore.putGraph(null, model); // store config info in (hidden) default graph.
			model.close();
		}

		Model configGraph = fGraphStore.getGraph(null);
        Resource containerResource = configGraph.getResource(fContainerURI);

        // Get page size int value
		Statement stmt = containerResource.getProperty(JenaLDPImpl.pageSize);
        fPageSize = stmt != null ? stmt.getObject().asLiteral().getInt() : DEFAULT_PAGE_SIZE;

        // Get member info boolean value
        stmt = containerResource.getProperty(JenaLDPImpl.memberInfo);
        fMemberInfo = stmt != null ? stmt.getObject().asLiteral().getBoolean() : false;

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

        // Get sort predicate values
        stmt = containerResource.getProperty(JenaLDPImpl.pageSortPredicates);
        fSortPredicates = stmt != null ? stmt.getObject().as(RDFList.class) : null;

        // Get resource URI prefix string value
		stmt = containerResource.getProperty(JenaLDPImpl.resourceURIPrefix);
		fResourceURIPrefix = appendURISegment(fContainerURI, stmt != null ? stmt.getObject().asLiteral().getString() : DEFAULT_RESOURCE_PREFIX);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#get(java.io.OutputStream, java.lang.String)
	 */
	public void get(OutputStream outStream, String contentType)
	{
		get(fContainerURI, outStream, contentType);
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
		put(fContainerURI + NON_MEMBER_PROPERTIES, stream, contentType);
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
		return post(stream, contentType, null);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#post(java.io.InputStream, java.lang.String, java.lang.String)
	 */
	public String post(InputStream stream, String contentType, String user)
	{
		String resourceURI = fGraphStore.createGraph(fResourceURIPrefix);
		return addResource(resourceURI, resourceURI, stream, contentType, user);
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
		String baseURI = resourceURI.equals(fContainerURI + NON_MEMBER_PROPERTIES) ? fContainerURI : resourceURI;
		if (fGraphStore.getGraph(resourceURI) == null)
			addResource(resourceURI, baseURI, stream, contentType, user);
		else
			updateResource(resourceURI, baseURI, stream, contentType, user);
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
		String baseURI = resourceURI.equals(fContainerURI + NON_MEMBER_PROPERTIES) ? fContainerURI : resourceURI;
		if (fGraphStore.getGraph(resourceURI) == null)
			addResource(resourceURI, baseURI, stream, contentType, user);
		else
			patchResource(resourceURI, baseURI, stream, contentType, user);
	}

	private static String UNSPECIFIED_USER = "http://unspecified.user"; // TODO: How to handle this properly?
	
	private String addResource(String resourceURI, String baseURI, InputStream stream, String contentType, String user)
	{
        Model model = readModel(baseURI, stream, contentType);
        Resource subject = model.getResource(baseURI);

        // Add membership triple
        if (!fContainerURI.equals(baseURI)) {
        	Model container = fGraphStore.getGraph(fContainerURI + NON_MEMBER_PROPERTIES);
        	Property membershipPredicate = getMembershipPredicate(container);
        	Resource membershipSubject = getMembershipSubject(container);
        	model.add(membershipSubject, membershipPredicate, subject);
        }
        
        // Add dcterms:creator, dcterms:created, dcterms:contributor, and dcterms:modified
        if (user == null) user = UNSPECIFIED_USER;
        if (!model.contains(subject, DCTerms.creator))
        	model.add(subject, DCTerms.creator, model.createResource(user));
        if (!model.contains(subject, DCTerms.contributor))
        	model.add(subject, DCTerms.contributor, model.createResource(user));
        Calendar time = Calendar.getInstance();
        if (!model.contains(subject, DCTerms.created))
        	model.add(subject, DCTerms.created, model.createTypedLiteral(time));
        if (!model.contains(subject, DCTerms.modified))
        	model.add(subject, DCTerms.modified, model.createTypedLiteral(time));

        fGraphStore.putGraph(resourceURI, model);
        fComputePages = true;
		return resourceURI;	
	}

	private Model readModel(String baseURI, InputStream stream, String contentType) {
		final Model model;
		if (LDPConstants.CT_APPLICATION_JSON.equals(contentType) || LDPConstants.CT_APPLICATION_LD_JSON.equals(contentType)) {
			final JSONLDTripleCallback callback = new JenaTripleCallback();
	        try {
				model = (Model) JSONLD.toRDF(JSONUtils.fromInputStream(stream), callback);
			} catch (JSONLDProcessingError e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			model = ModelFactory.createDefaultModel();
			String lang = WebContent.contentTypeToLang(contentType).getName();
			model.read(stream, baseURI, lang);
		}

		return model;
	}
	
	private void updateResource(String resourceURI, String baseURI, InputStream stream, String contentType, String user)
	{
		Model model = readModel(baseURI, stream, contentType);
        Resource subject = model.getResource(resourceURI);
		
        // Update dcterms:modified
        if (user == null) user = UNSPECIFIED_USER;
        Calendar time = Calendar.getInstance();
		model.removeAll(subject, DCTerms.modified, null);
        model.add(subject, DCTerms.modified, model.createTypedLiteral(time));

		fGraphStore.putGraph(resourceURI, model);
		model.close();
		fComputePages = true;
	}
	
	private void patchResource(String resourceURI, String baseURI, InputStream stream, String contentType, String user)
	{
		Model model = readModel(baseURI, stream, contentType);
        Resource subject = model.getResource(resourceURI);
		
        // Update dcterms:modified

        Calendar time = Calendar.getInstance();
		model.removeAll(subject, DCTerms.modified, null);
        model.add(subject, DCTerms.modified, model.createTypedLiteral(time));

		// TODO: Process patch contents
       
        /*fGraphStore.putGraph(resourceURI, model);
		model.close();
		fComputePages = true; */
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#delete(java.lang.String)
	 */
	public void delete(String resourceURI)
	{
		fGraphStore.deleteGraph(resourceURI);
		fComputePages = true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#get(java.lang.String, java.io.OutputStream, java.lang.String)
	 */
	public String get(String resourceURI, OutputStream outStream, String contentType)
	{
		if (resourceURI.startsWith(fContainerURI)) {
			String suffix = resourceURI.substring(fContainerURI.length());
			if (suffix.startsWith("?") && !NON_MEMBER_PROPERTIES.equals(suffix))
				return getPage(suffix, outStream, contentType);
		}
		
		Model graph = null;
		if (fContainerURI.equals(resourceURI)) {
			graph = ModelFactory.createDefaultModel();
			graph.add(fGraphStore.construct(getMembersQuery()));
			graph.add(fGraphStore.getGraph(fContainerURI + NON_MEMBER_PROPERTIES));
		}
		else {
			graph = fGraphStore.getGraph(resourceURI);
			if (graph == null)
				throw new IllegalArgumentException();
		}
		
		if (fMemberInfo)
			graph = addMemberInformation(graph);
		
		if (LDPConstants.CT_APPLICATION_JSON.equals(contentType)) {
			try {
				Object json = JSONLD.fromRDF(graph, new JenaRDFParser());
				InputStream is = getClass().getClassLoader().getResourceAsStream("context.jsonld");
				Object context = JSONUtils.fromInputStream(is);
				json = JSONLD.compact(json, context, new Options("", true));
				JSONUtils.writePrettyPrint(new PrintWriter(outStream), json);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		} else {
			String lang = WebContent.contentTypeToLang(contentType).getName();
			graph.write(outStream, lang);
		}
		return null;
	}
	
	public GraphStore getGraphStore() {
		return fGraphStore;
	}

	private synchronized String getPage(String page, OutputStream outStream, String contentType)
	{
		if (fComputePages && FIRST_PAGE.equals(page)) {
			fComputePages = false;
			computePages();
		}

		String pageURI = fContainerURI + page;
		Model pageModel = fPageStore.getGraph(pageURI);
		if (pageModel == null)
			throw new IllegalArgumentException();

		Model resultModel;
		String nextPage = null;
		Resource pageResource = pageModel.getResource(pageURI);
		Resource nextPageResource = pageResource.getPropertyResourceValue(LDP.nextPage);
		if (!RDF.nil.equals(nextPageResource))
			nextPage = nextPageResource.getURI();
		resultModel = ModelFactory.createDefaultModel();
		resultModel.add(pageModel);
		resultModel.add(fGraphStore.getGraph(fContainerURI + NON_MEMBER_PROPERTIES));

		if (fMemberInfo)
			resultModel = addMemberInformation(resultModel);

		String lang = WebContent.contentTypeToLang(contentType).getName();
		resultModel.write(outStream, lang);
		return nextPage;
	}

	private void computePages()
	{
        String currentPageURI = fContainerURI + FIRST_PAGE;
		Model currentPageModel = ModelFactory.createDefaultModel();
        Resource containerResource = currentPageModel.getResource(fContainerURI);
		String memberQuery = fSortPredicates != null ? getSortedMembersQuery() : getMembersQuery();
		String previousPageURI = null;
		Model previousPageModel = null;

		for (long memberOffset = 0; true; memberOffset += fPageSize) {
			String pageQuery = getPagingQuery(memberQuery, memberOffset, fPageSize);
			Model pageMembers = fGraphStore.construct(pageQuery);
			currentPageModel.add(pageMembers);
			
			String nextPageURI;
			long memberCount = pageMembers.size();
			if (memberCount < fPageSize) {
				if (memberCount == 0 && memberOffset != 0) {
					fGraphStore.deleteGraph(currentPageURI);
					Resource previousPageResource = previousPageModel.getResource(previousPageURI);
					previousPageModel.removeAll(previousPageResource, LDP.nextPage, null);
					previousPageModel.add(previousPageResource, LDP.nextPage, RDF.nil);
					fPageStore.putGraph(previousPageURI, previousPageModel);
					return;
				}
				nextPageURI = null;
			}
			else {
				nextPageURI = fPageStore.createGraph(fContainerURI + NTH_PAGE);
			}

			// Add bp:nextPage triple
			Resource pageResource = currentPageModel.getResource(currentPageURI);
			currentPageModel.add(pageResource, RDF.type, LDP.Page);
			currentPageModel.add(pageResource, LDP.pageOf, containerResource);
			currentPageModel.add(pageResource, LDP.nextPage, nextPageURI != null ? currentPageModel.getResource(nextPageURI) : RDF.nil);
			
			if (fSortPredicates != null) {
				RDFList list = currentPageModel.createList(fSortPredicates.iterator());
				currentPageModel.add(pageResource, LDP.containerSortPredicates, list);
			}

			fPageStore.putGraph(currentPageURI, currentPageModel);
			if (nextPageURI == null)
				return;
			
			// Move to next page
			previousPageModel = currentPageModel;
			previousPageURI = currentPageURI;
			currentPageModel = ModelFactory.createDefaultModel();
			currentPageURI = nextPageURI;
		}
	}

	private Model addMemberInformation(Model container)
	{
		Model result = ModelFactory.createDefaultModel();
		result.add(container);

		Property membershipPredicate = getMembershipPredicate(container);
        Resource membershipSubject = getMembershipSubject(container);

		for (NodeIterator iter = container.listObjectsOfProperty(membershipSubject, membershipPredicate); iter.hasNext(); ) {
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
		return result;
	}

	/**
	 * Create the SPARQL query string that will be used to retrieve the container members for generated pages.
	 * The query has the following form:
	 * <pre>
	 *   CONSTRUCT {
	 *     <$MEMBER_SUBJECT> <$MEMBER_PREDICATE> ?m .
	 *   }
	 *   WHERE {
	 *     <$MEMBER_SUBJECT> <$MEMBER_PREDICATE> ?m .
	 *     ?m <$SORT_PREDICATE_1> ?p1 .
	 *     ?m <$SORT_PREDICATE_2> ?p2 .
	 *     ...
	 *     ?m <$SORT_PREDICATE_N> ?pN .
	 *   }
	 *   ORDER BY ?p1 ?p2 ... ?pN
	 *   OFFSET $PAGE_START LIMIT $PAGE_SIZE
	 * </pre>
	 * @param pageStart the starting offset of the page of members.
	 * @param pageSize the number of members per page.
	 * @return a SPARQL CONSTRUCT query.
	 */
	private String getPagingQuery(String baseQuery, long pageStart, long pageSize)
	{
    	StringBuffer sb = new StringBuffer(baseQuery);
    	sb.append(" OFFSET ");
    	sb.append(pageStart);
    	sb.append(" LIMIT ");
    	sb.append(pageSize);
		return sb.toString();
	}

	private String fSortedMembersQuery = null;
	private String getSortedMembersQuery()
	{
		if (fSortedMembersQuery == null) {
	        StringBuffer sb = getBaseMembersQuery();
	    	//sb.append("GRAPH ?m { ");
	    	int listSize = fSortPredicates.size();
	    	for (int i = 0; i < listSize; i++) {
	    		sb.append("?m");
	    		//sb.append("?s");
	    		//sb.append(i);
	    		sb.append(" <");
	    		sb.append(fSortPredicates.get(i).asResource().getURI());
	    		sb.append("> ?p");
	    		sb.append(i);
	    		sb.append(" . ");
	    	}
	    	//sb.append("} ");
	    	sb.append("} ORDER BY");
	    	for (int i = 0; i < listSize; i++) {
	    		sb.append(" ?p");
	    		sb.append(i);
	    	}
	    	fSortedMembersQuery = sb.toString();
	    	//System.out.println("sorted construct query:\n" + fSortedConstructQuery);
		}
		return fSortedMembersQuery;
	}

	private String fMembersQuery = null;
	private String getMembersQuery()
	{
		if (fMembersQuery == null) {
	        StringBuffer sb = getBaseMembersQuery();
	        sb.append("}");
	    	fMembersQuery = sb.toString();
	    	//System.out.println("construct query:\n" + fConstructQuery);
		}
		return fMembersQuery;
	}
	
	private StringBuffer getBaseMembersQuery()
	{
		Model containerGraph = fGraphStore.getGraph(fContainerURI + NON_MEMBER_PROPERTIES);
		Property membershipPredicate = getMembershipPredicate(containerGraph);
        Resource membershipSubject = getMembershipSubject(containerGraph);
        StringBuffer sb = new StringBuffer("CONSTRUCT { <");
    	sb.append(membershipSubject);
    	sb.append("> <");
    	sb.append(membershipPredicate);
    	sb.append("> ?m . } WHERE { <");
    	sb.append(membershipSubject);
    	sb.append("> <");
    	sb.append(membershipPredicate);
    	sb.append("> ?m . ");
    	return sb;		
	}
	
	private Property getMembershipPredicate(Model containerGraph)
	{
        Resource containerResource = containerGraph.getResource(fContainerURI);
		Statement stmt = containerResource.getProperty(LDP.membershipPredicate);
		return stmt != null ? containerGraph.getProperty(stmt.getObject().asResource().getURI()) : RDFS.member;
	}

	private Resource getMembershipSubject(Model containerGraph)
	{
        Resource containerResource = containerGraph.getResource(fContainerURI);
        Statement stmt = containerResource.getProperty(LDP.membershipSubject);
        return stmt != null ? stmt.getObject().asResource() : containerResource;
	}

	private String appendURISegment(String base, String append)
	{
		return base.endsWith("/") ? base + append : base + "/" + append;
	}

}
