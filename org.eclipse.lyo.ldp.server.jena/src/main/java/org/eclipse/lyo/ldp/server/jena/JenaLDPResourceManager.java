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
 *     Samuel Padgett - use TDB transactions
 *     Samuel Padgett - add support for LDP Non-RDF Source
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import org.eclipse.lyo.ldp.server.ILDPResource;
import org.eclipse.lyo.ldp.server.LDPResourceManager;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class JenaLDPResourceManager implements LDPResourceManager {

	public static final String CONFIG_PARAM = "?_config";

	TDBGraphStore gs, ps;

	public JenaLDPResourceManager(TDBGraphStore gs, TDBGraphStore ps) {
		this.gs = gs;
		this.ps = ps;
	}

	@Override
	public void put(ILDPResource ldpr, boolean overwrite) {
		gs.createGraph(ldpr.getURI(), "r", null);
	}

	public ILDPResource get(String resourceURI) {
		gs.readLock();
		try {
			Model graph = gs.getGraph(resourceURI);
			if (graph == null) {
				if (JenaLDPNonRdfSource.isLDPNR(resourceURI)) {
					return new JenaLDPNonRdfSource(resourceURI, gs);
				}
				return null;
			}
			Resource r = graph.getResource(resourceURI);
			if (r.hasProperty(RDF.type, LDP.DirectContainer)) {
				return new JenaLDPDirectContainer(resourceURI, gs, ps);
			} else if (r.hasProperty(RDF.type, LDP.BasicContainer)) {
				return new JenaLDPBasicContainer(resourceURI, gs, ps);			
			} else if (r.hasProperty(RDF.type, LDP.Container)) {
				// TODO: SPEC: Should only rdf:type of #Container be treated as RDF Source or error?  Probably an error
				System.err.println("Received type of ldp:Container but treating as ldp:RDFSource.");
			}
			return new JenaLDPRDFSource(resourceURI, gs, ps);
		} finally {
			gs.end();
		}
	}

	public static String mintConfigURI(String uri) {
		return 	uri + JenaLDPResourceManager.CONFIG_PARAM;
	}
	
	public static boolean isContainer(Resource r) {
	    return r.hasProperty(RDF.type, LDP.Container) ||
				r.hasProperty(RDF.type, LDP.BasicContainer) ||
				r.hasProperty(RDF.type, LDP.DirectContainer) ||
				r.hasProperty(RDF.type, LDP.IndirectContainer);
    }
}
