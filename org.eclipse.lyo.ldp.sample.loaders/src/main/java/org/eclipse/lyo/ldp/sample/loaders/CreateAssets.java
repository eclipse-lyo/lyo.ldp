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
 *     Frank Budinsky - initial API and implementation
 *     Steve Speicher - initial API and implementation
 *     Samuel Padgett - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import java.io.InputStream;
import java.io.StringWriter;

import org.apache.http.HttpStatus;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.RestClient;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class CreateAssets {

	private static RestClient client = new RestClient();

	private static final String[] ASSETS = {
		"a1", "a2", "a3", "a4", "a5"
	};

	public static void main(String[] args) {
		String rootContainer = getRootContainerURI(args);
		System.out.println("Populating sample net worth data to LDP container: " + rootContainer);
		
		String netWorth = post(rootContainer, asStream("nw1.ttl"), "netWorth");
		System.out.println("Created membership resource at " + netWorth);
		
		Model m = ModelFactory.createDefaultModel();
		Resource assetContainer = m.getResource("");
		assetContainer.addProperty(RDF.type, m.getResource("http://www.w3.org/ns/ldp#DirectContainer"));
		assetContainer.addProperty(m.createProperty("http://www.w3.org/ns/ldp#membershipResource"), m.getResource(netWorth));
		assetContainer.addProperty(m.createProperty("http://www.w3.org/ns/ldp#hasMemberRelation"), m.getResource("http://example.org/ontology/asset"));

		StringWriter stringWriter = new StringWriter();
		m.write(stringWriter, "TURTLE", "");
		
		String assetContainerURL = post(rootContainer, stringWriter.toString(), "assetContainer");
		System.out.println("Created asset container at " + assetContainerURL);
		
		for (String asset : ASSETS) {
			String uri = post(assetContainerURL, asStream(asset + ".ttl"), asset);
			System.out.println("Created asset " + uri);
		}

		System.out.println("Done!");
	}
	
	private static String post(String uri, Object requestEntity, String slug) {
		org.apache.wink.client.Resource resource = client.resource(uri);
		ClientResponse response = resource.contentType("text/turtle").header("Slug", slug).post(requestEntity);
		if (response.getStatusCode() != HttpStatus.SC_CREATED) {
			System.err.println("ERROR: Failed to create resource. Status: " + response.getStatusCode());
			System.exit(1);
		}
		
		String location = response.getHeaders().getFirst("Location");
		if (location == null) {
			System.err.println("ERROR: No Location header in 201 response.");
			System.exit(1);
		}
		
		return location;
	}

	private static String getRootContainerURI(String[] args) {
	    if (args.length != 1) {
			System.err.println("Usage: java com.eclipse.lyo.ldp.sample.networth.CreateAssets <container_url>");
			System.exit(1);
		}

		return args[0];
    }
	
	private static InputStream asStream(String resource) {
		return CreateAssets.class.getClassLoader().getResourceAsStream("networth/"+resource);
	}
}
