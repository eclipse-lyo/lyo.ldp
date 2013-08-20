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
package org.eclipse.lyo.ldp.server;

import javax.ws.rs.core.MediaType;

public class LDPConstants
{
	
	protected static final String NS = "http://www.w3.org/ns/ldp#";
	
	/**
	 * The namespace of the vocabulary as a string
	 */
	public static String getNSURI()
	{
		return NS;
	}
	
	// Content types
	public static final String CT_APPLICATION_JSON = MediaType.APPLICATION_JSON;
	public static final String CT_APPLICATION_LD_JSON = "application/ld+json";
	public static final String CT_APPLICATION_RDFXML = "application/rdf+xml";
	public static final String CT_APPLICATION_XTURTLE = "application/x-turtle";
	public static final String CT_APPLICATION_SPARQLQUERY = "application/sparql-query";
	public static final String CT_APPLICATION_SPARQLRESULTSXML = "application/sparql-results+xml";
	public static final String CT_APPLICATION_SPARQLRESULTSJSON = "application/sparql-results+json";
	public static final String CT_TEXT_HTML = MediaType.TEXT_HTML;
	public static final String CT_TEXT_TURTLE = "text/turtle";
	public static final String CT_TEXT_TRIG = "text/trig";
	
	// HTTP Headers
	public static final String HDR_ACCEPT_POST = "Accept-Post";
	
	// RDF Property names both namespace and local
	public static final String PROP_LNAME_MEMBERSHIP_SUBJECT = "membershipSubject";
	public static final String PROP_MEMBERSHIP_SUBJECT = nsName(PROP_LNAME_MEMBERSHIP_SUBJECT);
	public static final String PROP_LNAME_MEMBERSHIP_PREDICATE = "membershipPredicate";
	public static final String PROP_MEMBERSHIP_PREDICATE = nsName(PROP_LNAME_MEMBERSHIP_PREDICATE);
	public static final String PROP_LNAME_MEMBERSHIP_OBJECT = "membershipObject";
	public static final String PROP_MEMBERSHIP_OBJECT = nsName(PROP_LNAME_MEMBERSHIP_OBJECT);
	public static final String PROP_LNAME_PAGEOF = "pageOf";
	public static final String PROP_PAGEOF = nsName(PROP_LNAME_PAGEOF);
	public static final String PROP_LNAME_NEXTPAGE = "nextPage";
	public static final String PROP_NEXTPAGE = nsName(PROP_LNAME_NEXTPAGE);
	public static final String PROP_LNAME_CONTAINER_SORT_PREDICATE = "containerSortPredicate";
	public static final String PROP_CONTAINER_SORT_PREDICATE = nsName(PROP_LNAME_CONTAINER_SORT_PREDICATE);
	
	// RDF Classes both namespace and local
	public static final String CLASS_LNAME_PAGE = "Page";
	public static final String CLASS_PAGE = nsName(CLASS_LNAME_PAGE);
	public static final String CLASS_LNAME_CONTAINER = "Container";
	public static final String CLASS_CONTAINER = nsName(CLASS_LNAME_CONTAINER);
	public static final String CLASS_LNAME_RESOURCE = "Resource";
	public static final String CLASS_RESOURCE = nsName(CLASS_LNAME_RESOURCE);
	
	public static String nsName(String local) {
		return NS + local;
	}
}
