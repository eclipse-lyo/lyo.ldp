/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation.
 *
 *	All rights reserved. This program and the accompanying materials
 *	are made available under the terms of the Eclipse Public License v1.0
 *	and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *	
 *	The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *	and the Eclipse Distribution License is available at
 *	http://www.eclipse.org/org/documents/edl-v10.php.
 *	
 *	Contributors:
 *	
 *	   Steve Speicher - Tests in support of method LDPService.wrapPathSeg()
 *******************************************************************************/
package org.eclipse.lyo.ldp.server;

import static org.junit.Assert.*;
import junit.framework.TestSuite;

import org.eclipse.lyo.ldp.server.service.LDPService;
import org.junit.Test;

public class URITests extends TestSuite {
	
	@Test
	public void testURITools1 () {		
		String res = LDPService.wrapPathSeg("abc");
		assertEquals("/abc/", res);
	}
	
	@Test
	public void testURITools2 () {
		String res = LDPService.wrapPathSeg("////");
		assertEquals("", res);
	}
	
	@Test
	public void testURITools3 () {
		String res = LDPService.wrapPathSeg("foo/bar//");
		assertEquals("/foo/bar/", res);
	}
	
	@Test
	public void testLinkSimple() {
		// typical success case
		assertTrue("<http://www.w3.org/ns/ldp#Resource>; rel=\"type\"".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
	}
		
	@Test
	public void testLinkExtraSegments() {
		assertTrue("<http://www.w3.org/ns/ldp#Resource>; rel=\"type\"; title=\"I have a title\"".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
	}
		
	@Test
	public void testLinkNoQuotes() {
		assertTrue("<http://www.w3.org/ns/ldp#Resource>;rel=type".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
	}
	
	@Test
	public void testLinkNoQuotesExtraSegments() {
		assertTrue("<http://www.w3.org/ns/ldp#Resource>;rel=type;title=mytitle".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
	}
		
	@Test
	public void testLinkMultipleRelations() {
		// multiple link relations
		assertTrue("<http://www.w3.org/ns/ldp#Resource>; rel=\"type http://example.com/some/other/link\"".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
		assertTrue("<http://www.w3.org/ns/ldp#Resource>; rel=\"http://example.com/some/other/link type\"".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
	}
	
	@Test
	public void testLinkTypeFailures() {
		// wrong URL type
		assertFalse("<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));

		// wrong rel value of 'next'
		assertFalse("<http://www.w3.org/ns/ldp#Resource>; rel=\"next\"".matches(LDPService.LINK_TYPE_RESOURCE_REGEX));
	}
}
