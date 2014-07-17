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
\ *		Steve Speicher - initial implementation
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.wink.client.ClientConfig;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.RestClient;
import org.apache.wink.client.handlers.BasicAuthSecurityHandler;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class CreateTestSuiteContainers {
	private static final String userPass = "ldpuser";
	private static ClientConfig config = new ClientConfig();
	private static BasicAuthSecurityHandler basicAuthSecHandler = new BasicAuthSecurityHandler(userPass, userPass);
	static {
		basicAuthSecHandler.setUserName(userPass); 
		basicAuthSecHandler.setPassword(userPass); 
		config.handlers(basicAuthSecHandler);
	}
	private static RestClient client = new RestClient(config);	

	public static void main(String[] args) {
		String rootContainer = getRootContainerURI(args);
		System.out
				.println("Populating test suite data to LDP container: "
						+ rootContainer);
		
		String mr1 = post(rootContainer, resource("diffmr.ttl"), "diffmr1", false);
		System.out.println("Created ldp:RDFSource at: \t\t" + mr1);
		String mr2 = post(rootContainer, resource("diffmr.ttl"), "diffmr2", false);
		System.out.println("Created ldp:RDFSource at: \t\t" + mr2);
		
		String c = post(rootContainer, resource("bc.ttl"), "bc/", false);
		System.out.println("Created ldp:BasicContainer at: \t\t" + c);

		c = post(rootContainer, resource("dc-simple.ttl"), "dc-simple/", false);
		System.out.println("Created ldp:DirectContainer (simple) at: \t" + c);

		c = post(rootContainer, resource("dc-invmbr.ttl"), "dc-invmbr/", false);
		System.out.println("Created ldp:DirectContainer (inverse mbr) at: \t" + c);

		Model m = ModelFactory.createDefaultModel();
		Resource r = m.getResource("");
		r.addProperty(RDF.type, LDP.DirectContainer);
		r.addProperty(RDF.type, LDP.Container);
		r.addProperty(RDF.type, LDP.RDFSource);
		r.addProperty(LDP.membershipResource, m.getResource(mr1));
		r.addProperty(LDP.hasMemberRelation, LDP.member);

		StringWriter stringWriter = new StringWriter();
		m.write(stringWriter, "TURTLE", "");

		c = post(rootContainer, stringWriter.toString(), "dc-diffmr/", false);
		System.out.println("Created ldp:DirectContainer (diff mbrshp res) at: \t" + c);

		m = ModelFactory.createDefaultModel();
		r = m.getResource("");
		r.addProperty(RDF.type, LDP.DirectContainer);
		r.addProperty(RDF.type, LDP.Container);
		r.addProperty(RDF.type, LDP.RDFSource);
		r.addProperty(LDP.membershipResource, m.getResource(mr2));
		r.addProperty(LDP.isMemberOfRelation, m.createResource("http://www.w3.org/2004/02/skos/core#inScheme"));

		stringWriter = new StringWriter();
		m.write(stringWriter, "TURTLE", "");

		c = post(rootContainer, stringWriter.toString(), "dc-invmbr-diffmr/", false);
		System.out.println("Created ldp:DirectContainer (diff mbrshp res + inv mbr) at: \t" + c);
		
		c = post(rootContainer, resource("bc.ttl"), "bc-asres/", true);
		System.out.println("Created ldp:BasicContainer (interaction model of ldp:Resource) at: \t\t" + c);

		System.out.println("Completed successfully!");
	}

	private static String post(String uri, Object requestEntity, String slug, boolean asRes) {
		org.apache.wink.client.Resource resource = 
					client.resource(uri).contentType("text/turtle").header("Slug", slug);
		
		if (asRes)
			resource.header("Link", "<http://www.w3.org/ns/ldp#Resource>; rel='type'");
			
		ClientResponse response = resource.post(requestEntity);
		if (response.getStatusCode() != HttpStatus.SC_CREATED) {
			System.err.println("ERROR: Failed to create resource. Status: "
					+ response.getStatusCode());
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
			System.err
					.println("Usage: java com.eclipse.lyo.ldp.sample.CreateTestSuiteContainers <root_container_url>");
			System.exit(1);
		}

		return args[0];
	}

	/**
	 * Change the way the requestEntity is created, so that the request can be
	 * repeatable in the case of an authentication challenge
	 */
	private static String resource(String resource) {
		try {
			InputStream in = CreateAssets.class.getClassLoader().getResourceAsStream("testsuite/"+resource);
			return IOUtils.toString(in, "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
