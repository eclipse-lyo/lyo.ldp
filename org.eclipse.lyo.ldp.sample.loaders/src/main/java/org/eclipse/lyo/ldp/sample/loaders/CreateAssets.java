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
 *	   Frank Budinsky - initial API and implementation
 *	   Steve Speicher - initial API and implementation
 *	   Samuel Padgett - initial API and implementation
 *	   Samuel Padgett - add Link header with interaction model on POST
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import static org.eclipse.lyo.ldp.sample.loaders.Loader.*;

import java.io.StringWriter;

import org.apache.wink.client.RestClient;
import org.eclipse.lyo.ldp.server.LDPConstants;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class CreateAssets {
	private static final String RESOURCE_TYPE = "networth/";

	private static final String[] ASSETS = {
		"a1", "a2", "a3", "a4", "a5"
	};

	public static void main(String[] args) {
		RestClient client = createClient(args);
		String rootContainer = getRootContainerURI(args);
		
		System.out.println("Populating sample net worth data to LDP container: " + rootContainer);
		
		String netWorth = post(client, rootContainer,
				resource(RESOURCE_TYPE, "nw1.ttl"), "netWorth",
				LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created membership resource at " + netWorth);
		
		Model m = ModelFactory.createDefaultModel();
		Resource assetContainer = m.getResource("");
		assetContainer.addProperty(RDF.type, m.getResource("http://www.w3.org/ns/ldp#DirectContainer"));
		assetContainer.addProperty(m.createProperty("http://www.w3.org/ns/ldp#membershipResource"), m.getResource(netWorth));
		assetContainer.addProperty(m.createProperty("http://www.w3.org/ns/ldp#hasMemberRelation"), m.getResource("http://example.org/ontology/asset"));

		StringWriter stringWriter = new StringWriter();
		m.write(stringWriter, "TURTLE", "");
		
		String assetContainerURL = post(client, rootContainer,
				stringWriter.toString(), "assetContainer",
				LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created asset container at " + assetContainerURL);
		
		for (String asset : ASSETS) {
			String uri = post(client, assetContainerURL, resource(RESOURCE_TYPE, asset + ".ttl"), asset);
			System.out.println("Created asset " + uri);
		}

		System.out.println("Done!");
	}
}
