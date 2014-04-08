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
 *     Steve Speicher - updates for recent LDP spec changes
 *     Steve Speicher - make root URI configurable 
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.networth;

import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.JenaLDPContainer;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.junit.Test;

public class NetWorthTestCase {

	private static final String ASSET_CONTAINER_URI = "http://example.org/netWorth/nw1/assetContainer/";
	private static final String NW_URI = "http://example.org/netWorth/nw1";
	private static final String ASSET_CONTAINER_CONFIG = "config.ttl";
	
	private static final String ASSET_CONTAINER_REP = "assetContainer.ttl";
	private static final String NW1_REP = "nw1.ttl";
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

		assetContainer.put(NW_URI, NetWorthTestCase.class.getClassLoader().getResourceAsStream(NW1_REP), "text/turtle");
		
		System.out.println("######## Net Worth resource added: " + NW_URI);
		assetContainer.get(NW_URI, "text/turtle"/*"application/rdf+xml"*/);
		
		String resourceURI;
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A1_REP), "text/turtle");
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A2_REP), "text/turtle");
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A3_REP), "text/turtle");
		
		/* TODO: Fix paging
		System.out.println("######## Final Container (stage 1): " + ASSET_CONTAINER_URI + "?firstPage");
		String nextPage = assetContainer.get(ASSET_CONTAINER_URI + "?firstPage", System.out, "text/turtle");
		while (nextPage != null) {
			System.out.println("######## Page: " + nextPage);
			nextPage = assetContainer.get(nextPage, System.out, "text/turtle");
		} */
		
		/* TODO: Fix non-member props	
		System.out.println("######## Container non-members-properties: " + ASSET_CONTAINER_URI + "?_meta");
		assetContainer.get(ASSET_CONTAINER_URI + "?_meta", System.out, "text/turtle");
		*/
		
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A4_REP), "text/turtle");
		resourceURI = assetContainer.post(NetWorthTestCase.class.getClassLoader().getResourceAsStream(A5_REP), "text/turtle");

		/* TODO: Added back paging tests when paging is working
		System.out.println("######## Final Container (stage 2): " + ASSET_CONTAINER_URI + "?firstPage");
		nextPage = assetContainer.get(ASSET_CONTAINER_URI + "?firstPage", System.out, "text/turtle");
		while (nextPage != null) {
			System.out.println("######## Page: " + nextPage);
			nextPage = assetContainer.get(nextPage, System.out, "text/turtle");
		} */
		
		assetContainer.delete(resourceURI);
	}
	
}
