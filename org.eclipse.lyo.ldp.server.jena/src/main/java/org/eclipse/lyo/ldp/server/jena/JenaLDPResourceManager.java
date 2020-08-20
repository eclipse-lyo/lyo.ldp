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
 *	   Steve Speicher - Updates for recent LDP spec changes
 *	   Samuel Padgett - Look for all LDP container types
 *	   Samuel Padgett - use TDB transactions
 *	   Samuel Padgett - add support for LDP Non-RDF Source
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.lyo.ldp.server.ILDPResource;
import org.eclipse.lyo.ldp.server.LDPResourceManager;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;
import org.eclipse.lyo.ldp.server.jena.vocabulary.Lyo;
import org.eclipse.lyo.ldp.server.service.LDPService;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

public class JenaLDPResourceManager implements LDPResourceManager {

	public static final String CONFIG_PARAM = "?_config";
	public static final String ASSOCIATED_LDP_RS_PARAM = "?_rdf";

	TDBGraphStore gs;

	public JenaLDPResourceManager(TDBGraphStore gs) {
		this.gs = gs;
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
			if (!isResourceInteractionModel(resourceURI)) {
				if (r.hasProperty(RDF.type, LDP.DirectContainer)) {
					return new JenaLDPDirectContainer(resourceURI, gs);
				} else if (r.hasProperty(RDF.type, LDP.BasicContainer)) {
					return new JenaLDPBasicContainer(resourceURI, gs);
				} else if (r.hasProperty(RDF.type, LDP.Container)) {
					// TODO: SPEC: Should only rdf:type of #Container be treated as RDF Source or error?  Probably an error
					System.err.println("Received type of ldp:Container but treating as ldp:RDFSource.");
				}
			}
			return new JenaLDPRDFSource(resourceURI, gs);
		} finally {
			gs.end();
		}
	}

	public static String mintConfigURI(String uri) {
		return	uri + CONFIG_PARAM;
	}
	
	public static String mintAssociatedRDFSourceURI(String uri) {
		return	uri + ASSOCIATED_LDP_RS_PARAM;
	}

	public static String mintUserURI(String user) {
		return UriBuilder.fromPath(LDPService.ROOT_APP_URL).path("user").path(user).build().toString();
	}

	public static boolean isConfigURI(String uri) {
		return uri.endsWith(CONFIG_PARAM);
	}

	public static boolean isAssociatedRDFSource(String uri) {
		return uri.endsWith(ASSOCIATED_LDP_RS_PARAM);
	}
	
	/**
	 * Is this resource a companion of another resource? These resources are
	 * managed specially. For instance, they probably should not be deleted
	 * without deleting the other resource.
	 * 
	 * @param uri the resource URI
	 * @return true iff this is a companion resource to another resource (such as a config graph)
	 */
	public static boolean isCompanion(String uri) {
		return isConfigURI(uri) || isAssociatedRDFSource(uri);
	}
	
	public static boolean isContainer(Resource r) {
		return r.hasProperty(RDF.type, LDP.Container) ||
				r.hasProperty(RDF.type, LDP.BasicContainer) ||
				r.hasProperty(RDF.type, LDP.DirectContainer) ||
				r.hasProperty(RDF.type, LDP.IndirectContainer);
	}
	
	public boolean isResourceInteractionModel(String resourceURI) {
		Model graph = gs.getGraph(mintConfigURI(resourceURI));
		if (graph == null) return false;
		
		Resource r = graph.getResource(resourceURI);
		if (r == null) return false;
		
		return r.hasLiteral(Lyo.isResourceInteractionModel, true);
	}
}
