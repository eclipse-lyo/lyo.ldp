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
 *     Samuel Padgett - add different LDP container types
 *     Samuel Padgett - add ldp:contains property
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena.vocabulary;

import org.eclipse.lyo.ldp.server.LDPConstants;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

/**
 * Linked Data Platform Vocabulary
 */
public class LDP 
{
	/**
	 * The namespace of the vocabulary as a resource
	 */
	public static final Resource NAMESPACE = ResourceFactory.createResource(LDPConstants.getNSURI());
	
	public static final Property contains = property(LDPConstants.PROP_CONTAINS);
	public static final Property member = property(LDPConstants.PROP_MEMBER);
	public static final Property membershipResource = property(LDPConstants.PROP_MEMBERSHIP_RESOURCE);
	public static final Property hasMemberRelation = property(LDPConstants.PROP_HAS_MEMBER_RELATION);
	public static final Property pageOf = property(LDPConstants.PROP_PAGEOF);
	public static final Property nextPage = property(LDPConstants.PROP_NEXTPAGE);
	public static final Property containerSortPredicates = property(LDPConstants.PROP_CONTAINER_SORT_PREDICATE);
	 
	public static final Resource Container = resource(LDPConstants.CLASS_CONTAINER);
	public static final Resource BasicContainer = resource(LDPConstants.CLASS_BASIC_CONTAINER);
	public static final Resource DirectContainer = resource(LDPConstants.CLASS_DIRECT_CONTAINER);
	public static final Resource IndirectContainer = resource(LDPConstants.CLASS_INDIRECT_CONTAINER);
	public static final Resource Page = resource(LDPConstants.CLASS_PAGE);

    protected static final Resource resource(String name)
    {
    	return ResourceFactory.createResource(name); 
    }

    protected static final Property property(String name)
    { 
    	return ResourceFactory.createProperty(name);
    }

}
