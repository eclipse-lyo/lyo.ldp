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
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

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
import javax.ws.rs.core.Response.Status;

import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

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
public class JenaLDPContainer extends LDPContainer
{
	protected String fResourceURIPrefix; // New resource name template, default is "res" + N
	protected int fPageSize; // members per LDP-C page
	protected boolean fMemberInfo; // include member info in container representation
	protected Set<Property> fMemberFilter; // filtered list of members to include
	protected RDFList fSortPredicates; // sort predicates for paged representation
	protected String fConfigGraphURI;
	protected String fContainerMetaURI;
	
    public static final String NON_MEMBER_PROPERTIES = "?_meta";
    public static final String ADMIN = "?_admin";
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
		super(containerURI, graphStore);
		fGraphStore = graphStore;
		fPageStore = pageStore;
		fConfigGraphURI = fURI + ADMIN;
		fContainerMetaURI = fURI + NON_MEMBER_PROPERTIES;
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
	        model.read(config, fURI, lang);
			fGraphStore.putGraph(fConfigGraphURI, model); // store config info with special side graph.
			model.close();
		} else {
	        Model model = ModelFactory.createDefaultModel();
	        fGraphStore.putGraph(fConfigGraphURI, model);
		}

		Model configGraph = fGraphStore.getGraph(fConfigGraphURI);
		if (configGraph == null) return; // TODO: Handle error
        Resource containerResource = configGraph.getResource(fURI);

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
		fResourceURIPrefix = appendURISegment(fURI, stmt != null ? stmt.getObject().asLiteral().getString() : DEFAULT_RESOURCE_PREFIX);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.impl.ILDPContainer#get(java.io.OutputStream, java.lang.String)
	 */
	public void get(OutputStream outStream, String contentType)
	{
		get(fURI, outStream, contentType);
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
		// HACK: Not sure why we are using this baseURI, either web a) add mbr triples or b) we don't.  Also, what container should PUT-created resource go?  For now, no where.
		String baseURI = resourceURI.equals(fContainerMetaURI) ? fURI : resourceURI;
		if (fGraphStore.getGraph(resourceURI) == null)
			addResource(resourceURI, false, stream, contentType, user);
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
		String baseURI = resourceURI.equals(fContainerMetaURI) ? fURI : resourceURI;
		if (fGraphStore.getGraph(resourceURI) == null)
			addResource(resourceURI, true, stream, contentType, user);
		else
			patchResource(resourceURI, baseURI, stream, contentType, user);
	}

	private static String UNSPECIFIED_USER = "http://unspecified.user"; // TODO: How to handle this properly?
	
	/**
	 * Create resource and add membership triples
	 * @param resourceURI The NEW resource being added (including any query params, etc)
	 * @param addMembership If true, will add membership and containment triples
	 * @param stream Bytes from the request
	 * @param contentType Suggested type of the stream (turtle, rdf/xml, json, ...)
	 * @param user dcterms:creator name
	 * @return
	 */
	private String addResource(String resourceURI, boolean addMembership, InputStream stream, String contentType, String user)
	{
        Model model = readModel(resourceURI, stream, contentType);
        Resource subject = model.getResource(resourceURI);

       // Add membership triple
       if (addMembership) {
        	Model container = fGraphStore.getGraph(fURI);
        	Model ldpSR = container;
        	Property membershipPredicate = getMembershipPredicate(container);
        	Resource membershipSubject = getMembershipSubject(container);
        	
        	// Put membership triples in LDP-SR
        	if (!membershipSubject.asResource().getURI().equals(fURI)) {
        		ldpSR = fGraphStore.getGraph(membershipSubject.asResource().getURI());
        		if (ldpSR == null) {
        			ldpSR = container;
        		} else {
        			// Need to move to LDP-SR's model
        			membershipSubject = ldpSR.getResource(membershipSubject.asResource().getURI());
        			membershipPredicate = ldpSR.getProperty(membershipPredicate.asResource().getURI());        			
        		}
        	}
        	ldpSR.add(membershipSubject, membershipPredicate, subject);
        	
        	// Put containment triples in container
        	container.add(container.getResource(fURI), container.getProperty(LDPConstants.PROP_CONTAINS), subject);
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
		if (resourceURI.startsWith(fURI)) {
			String suffix = resourceURI.substring(fURI.length());
			if (suffix.startsWith("?") && !NON_MEMBER_PROPERTIES.equals(suffix))
				return getPage(suffix, outStream, contentType);
		}
		
		Model graph = null;
		if (fURI.equals(resourceURI)) {
			graph = ModelFactory.createDefaultModel();
			graph.add(fGraphStore.construct(getMembersQuery()));
			graph.add(fGraphStore.getGraph(fURI));
		}
		else {
			graph = fGraphStore.getGraph(resourceURI);
			if (graph == null)
				throw new IllegalArgumentException();
		}
		
		if (fMemberInfo)
			graph = addMemberInformation(graph);
		
		if (LDPConstants.CT_APPLICATION_JSON.equals(contentType)) {
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
				json = compactMethod.invoke(null, json, context, options);

				//JSONUtils.writePrettyPrint(new PrintWriter(outStream), json);
				Method writePrettyPrintMethod = jsonldUtilsClass.getMethod("writePrettyPrint", Writer.class, Object.class);
				writePrettyPrintMethod.invoke(null, new PrintWriter(outStream), json);
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

		String pageURI = fURI + page;
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
		resultModel.add(fGraphStore.getGraph(fURI));

		if (fMemberInfo)
			resultModel = addMemberInformation(resultModel);

		String lang = WebContent.contentTypeToLang(contentType).getName();
		resultModel.write(outStream, lang);
		return nextPage;
	}

	private void computePages()
	{
        String currentPageURI = fURI + FIRST_PAGE;
		Model currentPageModel = ModelFactory.createDefaultModel();
        Resource containerResource = currentPageModel.getResource(fURI);
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
				nextPageURI = fPageStore.createGraph(fURI + NTH_PAGE, null, null);
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
		Model containerGraph = fGraphStore.getGraph(fURI);
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
        Resource containerResource = containerGraph.getResource(fURI);
		Statement stmt = containerResource.getProperty(LDP.membershipPredicate);
		return stmt != null ? containerGraph.getProperty(stmt.getObject().asResource().getURI()) : RDFS.member;
	}

	private Resource getMembershipSubject(Model containerGraph)
	{
        Resource containerResource = containerGraph.getResource(fURI);
        Statement stmt = containerResource.getProperty(LDP.membershipSubject);
        return stmt != null ? stmt.getObject().asResource() : containerResource;
	}

	private String appendURISegment(String base, String append)
	{
		return base.endsWith("/") ? base + append : base + "/" + append;
	}

	/*
	 * Check if the jsonld-java library is present.
	 */
	private boolean isJSONLDPresent() {
		try {
			Class.forName("com.github.jsonldjava.core.JSONLD");
			Class.forName("com.github.jsonldjava.impl.JenaRDFParser");
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
