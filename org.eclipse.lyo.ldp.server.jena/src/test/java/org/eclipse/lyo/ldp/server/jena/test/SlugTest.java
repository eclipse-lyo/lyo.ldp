/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation.
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
 *	   Samuel Padgett - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.junit.Test;

public class SlugTest {
	private static final String TEST_CONTAINER = "http://example.com/resources";
	private static final String TEST_CONTAINER_TRAILING_SLASH = TEST_CONTAINER + "/";
	private static final String TEST_PREFIX = TEST_CONTAINER_TRAILING_SLASH + "junit";

	private TDBGraphStore store = new TDBGraphStore();

	@Test
	public void testSimpleSlug() {
		final String uri = store.mintURI(TEST_CONTAINER, TEST_PREFIX, "simple");
		assertEquals("http://example.com/resources/simple", uri);
	}

	@Test
	public void testSlugContainerTrailingSlash() {
		final String uri = store.mintURI(TEST_CONTAINER_TRAILING_SLASH, TEST_PREFIX, "simple");
		assertEquals("http://example.com/resources/simple", uri);
	}

	@Test
	public void testSlugDotDot() {
		final String uri = store.mintURI(TEST_CONTAINER, TEST_PREFIX, "../foo");
		assertFalse("Slug should not leave \"..\" unchanged", uri.contains(".."));
	}
	
	@Test
	public void testSlugSpecialChars() {
		final Pattern specialChars = Pattern.compile("[/?#]");
		final String uri = store.mintURI(TEST_CONTAINER, TEST_PREFIX, "foo/bar?query=value#hash");

		// Make sure none of the special characters follow the base container URL.
		final String substring = uri.substring(0, TEST_CONTAINER_TRAILING_SLASH.length());
		final Matcher m = specialChars.matcher(substring);
		assertFalse("Slug should not have special characters", m.lookingAt());
	}

	@Test
	public void testSlugSpace() {
		final String uri = store.mintURI(TEST_CONTAINER, TEST_PREFIX, "title with spaces");
		assertFalse("URI should not have spaces", uri.contains(" "));
	}
	
	@Test
	public void testNoSlug() {
		final String uri = store.mintURI(TEST_CONTAINER, TEST_PREFIX, null);
		final String regex = Pattern.quote(TEST_PREFIX) + "\\d+";
		assertTrue("URI doesn't match expected pattern when no slug provided", uri.matches(regex));
	}
}
