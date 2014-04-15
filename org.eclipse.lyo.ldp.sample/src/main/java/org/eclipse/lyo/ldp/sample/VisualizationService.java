/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
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
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample;

import java.util.HashMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.service.LDPService;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.RDF;

@Path("/visualization")
public class VisualizationService {
	    
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getVisualization() {
		TDBGraphStore store = new TDBGraphStore(false);
		store.readLock();
		try {
			// Get the union of all graphs.
			Model m = store.getGraph("urn:x-arq:UnionGraph");

			// Return the nodes and links in the format expected by D3.js for visualization.
			JsonObject response = new JsonObject();
			JsonArray nodes = new JsonArray();
			response.put("nodes", nodes);
			JsonArray links = new JsonArray();
			response.put("links", links);
			HashMap<String, Integer> resourceToNodeIndex = new HashMap<String, Integer>();
			HashMap<String, Integer> typeToGroup = new HashMap<String, Integer>();
			int i = 0;
			int nextGroupID = 0;

			// Find all the nodes.
			ResIterator subjects = m.listSubjects();
			while (subjects.hasNext()) {
				Resource subject = subjects.next();
				JsonObject node = new JsonObject();
				nodes.add(node);
				String name = getNodeName(subject);
				resourceToNodeIndex.put(name, i);
				node.put("name", getNodeName(subject));
				Resource type = subject.getPropertyResourceValue(RDF.type);
				String typeString = (type == null) ? "none" : type.getURI();
				Integer group = typeToGroup.get(typeString);
				if (group == null) {
					typeToGroup.put(typeString, nextGroupID);
					node.put("group", nextGroupID);
					nextGroupID++;
				} else {
					node.put("group", group.intValue());
				}
				i++;
			}

			// Iterate a second time to find the links.
			subjects = m.listSubjects();
			while (subjects.hasNext()) {
				Resource subject = subjects.next();
				StmtIterator statements = subject.listProperties();
				while (statements.hasNext()) {
					Statement s = statements.next();
					RDFNode n = s.getObject();
					if (n instanceof Resource) {
						Resource object = (Resource) n;
						Integer targetIndex = resourceToNodeIndex.get(getNodeName(object));
						if (targetIndex != null) {
							JsonObject link = new JsonObject();
							Integer sourceIndex = resourceToNodeIndex.get(getNodeName(subject));
							link.put("source", sourceIndex.intValue());
							link.put("target", targetIndex.intValue());
							link.put("value", 1);
							links.add(link);
						}
					}
				}
			}

			return response.toString();
		} finally {
			store.end();
		}
	}
	
	private String getNodeName(Resource r) {
		String uri = r.getURI();
		if (uri == null) {
			uri = r.getId().getLabelString();
		}
		if (uri.startsWith(LDPService.ROOT_APP_URL)) {
			return uri.substring(LDPService.ROOT_APP_URL.length());
		}
		return uri;
	}
}
