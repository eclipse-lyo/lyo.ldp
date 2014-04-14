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

import org.eclipse.lyo.ldp.server.ILDPResource;
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
	public void put(ILDPResource ldpr, boolean overwrite) {
		gs.createGraph(ldpr.getURI(), "r", null);
	}

	public ILDPResource get(String resourceURI) {
		Model graph = gs.getGraph(resourceURI);
		if (graph == null) return null;
		Resource r = graph.getResource(resourceURI);
		if (r.hasProperty(RDF.type, LDP.DirectContainer)) {
			return new JenaLDPDirectContainer(resourceURI, gs, ps, null);
		} else if (r.hasProperty(RDF.type, LDP.BasicContainer)) {
			return new JenaLDPBasicContainer(resourceURI, gs, ps, null);			
		} else if (r.hasProperty(RDF.type, LDP.Container)) {
			// TODO: SPEC: Should only rdf:type of #Container be treated as RDF Source or error?  Probably an error
			System.err.println("Received type of ldp:Container but treating as ldp:RDFSource.");
		}
		return new JenaLDPRDFSource(resourceURI, gs, ps, null);
	}

	public static boolean isContainer(Resource r) {
	    return r.hasProperty(RDF.type, LDP.Container) ||
				r.hasProperty(RDF.type, LDP.BasicContainer) ||
				r.hasProperty(RDF.type, LDP.DirectContainer) ||
				r.hasProperty(RDF.type, LDP.IndirectContainer);
    }
}
