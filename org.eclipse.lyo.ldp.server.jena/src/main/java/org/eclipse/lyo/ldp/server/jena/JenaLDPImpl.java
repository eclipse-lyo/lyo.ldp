/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
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
 *	   Frank Budinsky - initial API and implementation
 *	   Steve Speicher - initial API and implementation
 *	   Samuel Padgett - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Basic Profile Implementation (Config) Vocabulary
 */
public class JenaLDPImpl 
{
	protected static final String NS = "http://eclipse.org/lyo/ldp#";
	
	/**
	 * The namespace of the vocabulary as a string
	 */
	public static String getURI()
	{
		return NS;
	}

	/**
	 * The namespace of the vocabulary as a resource
	 */
	public static final Resource NAMESPACE = ResourceFactory.createResource(NS);
	
	public static final Property resourceURIPrefix = property("resourceURIPrefix"); // Prefix used for POSTED resource URIs (default: "res")
	 
	protected static final Property property(String local)
	{ 
		return ResourceFactory.createProperty(NS, local);
	}

}
