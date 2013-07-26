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
package org.eclipse.lyo.ldp.sample.networth;

import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.JenaLDPContainer;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.junit.Test;

public class NetWorthTestCase {

	private static final String ASSET_CONTAINER_URI = "http://example.org/netWorth/nw1/assetContainer";
	private static final String ASSET_CONTAINER_CONFIG = "config.ttl";
	
	private static final String ASSET_CONTAINER_REP = "assetContainer.ttl";
	private static final String A1_REP = "a1.ttl";
	private static final String A2_REP = "a2.ttl";
	private static final String A3_REP = "a3.ttl";
	private static final String A4_REP = "a4.ttl";
	private static final String A5_REP = "a5.ttl";

	@Test
	public void testNetWorthExample() {
		LDPContainer assetContainer = JenaLDPContainer.create(ASSET_CONTAINER_URI, 
				new TDBGraphStore(), 
				new TDBGraphStore(), 
				NetWorthSample.class.getClassLoader().getResourceAsStream(ASSET_CONTAINER_CONFIG));
		assetContainer.put(NetWorthTestCase.class.getClassLoader().getResourceAsStream(ASSET_CONTAINER_REP), "text/turtle");

		System.out.println("######## Initial Container: " + ASSET_CONTAINER_URI);
		assetContainer.get(System.out, "text/turtle"/*"application/rdf+xml"*/);
		
		String resourceURI;
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A1_REP), "text/turtle");
		System.out.println("######## POSTed resource: " + resourceURI);
		assetContainer.get(resourceURI, System.out, "text/turtle"/*"application/rdf+xml"*/);
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A2_REP), "text/turtle");
		System.out.println("######## POSTed resource: " + resourceURI);
		assetContainer.get(resourceURI, System.out, "text/turtle"/*"application/rdf+xml"*/);
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A3_REP), "text/turtle");
		System.out.println("######## POSTed resource: " + resourceURI);
		assetContainer.get(resourceURI, System.out, "text/turtle"/*"application/rdf+xml"*/);
		
		System.out.println("######## Final Container (stage 1): " + ASSET_CONTAINER_URI + "?firstPage");
		String nextPage = assetContainer.get(ASSET_CONTAINER_URI + "?firstPage", System.out, "text/turtle"/*"application/rdf+xml"*/);
		while (nextPage != null) {
			System.out.println("######## Page: " + nextPage);
			nextPage = assetContainer.get(nextPage, System.out, "text/turtle"/*"application/rdf+xml"*/);
		}
		
		System.out.println("######## Container non-members-properties: " + ASSET_CONTAINER_URI + "?non-member-properties");
		assetContainer.get(ASSET_CONTAINER_URI + "?non-member-properties", System.out, "text/turtle"/*"application/rdf+xml"*/);
		
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A4_REP), "text/turtle");
		System.out.println("######## POSTed resource (stage 2): " + resourceURI);
		assetContainer.get(resourceURI, System.out, "text/turtle"/*"application/rdf+xml"*/);
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A5_REP), "text/turtle");
		System.out.println("######## POSTed resource (stage 2): " + resourceURI);
		assetContainer.get(resourceURI, System.out, "text/turtle"/*"application/rdf+xml"*/);

		System.out.println("######## Final Container (stage 2): " + ASSET_CONTAINER_URI + "?firstPage");
		nextPage = assetContainer.get(ASSET_CONTAINER_URI + "?firstPage", System.out, "text/turtle"/*"application/rdf+xml"*/);
		while (nextPage != null) {
			System.out.println("######## Page: " + nextPage);
			nextPage = assetContainer.get(nextPage, System.out, "text/turtle"/*"application/rdf+xml"*/);
		}
		
		System.out.println("######## Delete resource: " + resourceURI);
		assetContainer.delete(resourceURI);
		try {
			assetContainer.get(resourceURI, System.out, "text/turtle"/*"application/rdf+xml"*/);
			System.out.println("ERROR: shouldn't get here!");
		} catch (IllegalArgumentException e) { System.out.println("Deleted resource not found."); }

		System.out.println("######## Final Container (no paging): " + ASSET_CONTAINER_URI);
		//assetContainer.get(System.out, "text/turtle"/*"application/rdf+xml"*/);
		assetContainer.get(ASSET_CONTAINER_URI, System.out, "text/turtle"/*"application/rdf+xml"*/);
	}
	
}
