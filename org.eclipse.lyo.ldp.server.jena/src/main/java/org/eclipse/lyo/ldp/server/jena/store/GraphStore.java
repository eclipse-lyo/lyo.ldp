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
 *	   Frank Budinsky - initial API and implementation
 *	   Steve Speicher - initial API and implementation
 *	   Samuel Padgett - initial API and implementation
 *	   Steve Speicher - updates for recent LDP spec changes
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena.store;

import java.io.OutputStream;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * Interface to an RDF Graph Store.
 */
public interface GraphStore
{
	void putGraph(String graphURI, Model model);
	Model getGraph(String graphURI);
	void deleteGraph(String graphURI);
	String createGraph(String containerURI, String graphURIPrefix, String nameHint);
	void query(OutputStream outStream, String queryString);
	void query(OutputStream outStream, String queryString, String contentType);
	Model construct(String queryString);
}
