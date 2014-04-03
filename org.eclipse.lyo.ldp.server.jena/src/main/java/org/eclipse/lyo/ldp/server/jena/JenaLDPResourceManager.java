/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation.
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
 *     Steve Speicher - Updates for recent LDP spec changes
 *     Samuel Padgett - Look for all LDP container types
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import org.eclipse.lyo.ldp.server.LDPRDFResource;
import org.eclipse.lyo.ldp.server.LDPResource;
import org.eclipse.lyo.ldp.server.LDPResourceManager;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class JenaLDPResourceManager implements LDPResourceManager {
	
	GraphStore gs, ps;

	public JenaLDPResourceManager(GraphStore gs, GraphStore ps) {
		this.gs = gs;
		this.ps = ps;
	}

	@Override
	public void put(LDPResource ldpr, boolean overwrite) {
		gs.createGraph(ldpr.getURI(), "r", null);
	}

	@Override
	public LDPResource get(String resourceURI) {
		Model graph = gs.getGraph(resourceURI);
		if (graph == null) return null;
		Resource r = graph.getResource(resourceURI);
		if (isContainer(r)) {
			return new JenaLDPContainer(resourceURI, gs, ps, null);
		}
		return new LDPRDFResource(resourceURI, graph);
	}

	public boolean isContainer(Resource r) {
	    return r.hasProperty(RDF.type, LDP.Container) ||
				r.hasProperty(RDF.type, LDP.BasicContainer) ||
				r.hasProperty(RDF.type, LDP.DirectContainer) ||
				r.hasProperty(RDF.type, LDP.IndirectContainer);
    }
}
