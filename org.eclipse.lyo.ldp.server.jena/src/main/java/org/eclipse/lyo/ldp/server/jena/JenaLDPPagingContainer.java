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
 *     Steve Speicher - factored out paging into this class from JenaLDPContainer 
 *     Samuel Padgett - update for new container interface
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.jena.riot.WebContent;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFList;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.RDF;

// TODO: Consider better extensions mechanism for LDP (namely this paging)

public class JenaLDPPagingContainer extends JenaLDPContainer {

	public static final int DEFAULT_PAGE_SIZE = 100;
	public static final String FIRST_PAGE = "?firstPage";
	public static final String NTH_PAGE = "?p=";
	protected boolean fComputePages = true; // true when paged representation needs to be recomputed.
	protected int fPageSize; // members per LDP-C page
	protected final GraphStore fPageStore; // GraphStore for page graphs
	protected RDFList fSortPredicates; // sort predicates for paged representation

	protected JenaLDPPagingContainer(String containerURI,
			GraphStore graphStore, GraphStore pageStore, InputStream config) {
		super(containerURI, graphStore, pageStore, config);
		fPageStore = pageStore;
	}

	private void computePages(String containerURI)
	{
	    String currentPageURI = containerURI + FIRST_PAGE;
		Model currentPageModel = ModelFactory.createDefaultModel();
	    Resource containerResource = currentPageModel.getResource(containerURI);
		String memberQuery = fSortPredicates != null ? getSortedMembersQuery(containerURI) : getMembersQuery(containerURI);
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
				nextPageURI = fPageStore.createGraph(containerURI + NTH_PAGE, null, null);
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

	private synchronized Response getPage(String page, String contentType, String containerURI)
	{
		if (fComputePages && FIRST_PAGE.equals(page)) {
			fComputePages = false;
			computePages(containerURI);
		}
	
		String pageURI = containerURI + page;
		Model pageModel = fPageStore.getGraph(pageURI);
		if (pageModel == null)
			throw new IllegalArgumentException();
	
		Model resultModel;
		resultModel = ModelFactory.createDefaultModel();
		resultModel.add(pageModel);
		resultModel.add(fGraphStore.getGraph(containerURI));
	
		if (fMemberInfo) {
			Resource containerResource = resultModel.getResource(containerURI);
			resultModel = addMemberInformation(resultModel, containerResource);
		}
	
		final String lang = WebContent.contentTypeToLang(contentType).getName();
		final Model responseModel = resultModel;
		StreamingOutput out = new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				responseModel.write(output, lang);
			}
		};

		return Response.ok(out).build();
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
	
	@Override
	protected String addResource(String resourceURI, boolean addMembership,
			InputStream stream, String contentType, String user) {
		fComputePages = true;
		return super.addResource(resourceURI, addMembership, stream, contentType, user);
	}
	
	@Override
	protected void updateResource(String resourceURI, String baseURI,
			InputStream stream, String contentType, String user) {
		fComputePages = true;
		super.updateResource(resourceURI, baseURI, stream, contentType, user);
	}
	
	@Override
	public Response get(String resourceURI, String contentType) {
		// TODO: This is saying if (query param != _meta) then it must be paging, NOT!?!?
		String containerURI = getContainerURIForResource(resourceURI);
		if (resourceURI.startsWith(containerURI)) {
			String suffix = resourceURI.substring(containerURI.length());
			if (suffix.startsWith("?") && !CONFIG_PARAM.equals(suffix))
				return getPage(suffix, contentType, containerURI);
		} 
		
		return super.get(resourceURI, contentType);
	}
	
	@Override
	public void delete(String resourceURI) {
		super.delete(resourceURI);
		fComputePages = true;
	}

	protected String fSortedMembersQuery = null;
	protected String getSortedMembersQuery(String containerURI)
	{
		if (fSortedMembersQuery == null) {
	        StringBuffer sb = getBaseMembersQuery(containerURI);
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
	
	@Override
	public void setConfigParameters(InputStream config, String contentType) {
		super.setConfigParameters(config, contentType);
		
		// The parent method should make sure the graph exists
		Model configGraph = fGraphStore.getGraph(fConfigGraphURI);
        Resource containerResource = configGraph.getResource(fConfigGraphURI);
        
        // Get page size int value
		Statement stmt = containerResource.getProperty(JenaLDPImpl.pageSize);
		if (stmt != null) {
			fPageSize = stmt.getObject().asLiteral().getInt();
		} else {
			fPageSize = DEFAULT_PAGE_SIZE;
			containerResource.addLiteral(JenaLDPImpl.pageSize, fPageSize);
		}
		
        // Get sort predicate values
        stmt = containerResource.getProperty(JenaLDPImpl.pageSortPredicates);
        fSortPredicates = stmt != null ? stmt.getObject().as(RDFList.class) : null;
	}
	
	@Override
	public GraphStore getPagingGraphStore() {
		return fPageStore;
	}

}
