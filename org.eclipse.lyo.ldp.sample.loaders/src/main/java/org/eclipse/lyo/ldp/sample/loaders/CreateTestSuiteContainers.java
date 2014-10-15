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
 *		Steve Speicher - initial implementation
 *		Samuel Padgett - use double quotes for rel="type"
 *		Samuel Padgett - add Link header with interaction model on POST
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import static org.eclipse.lyo.ldp.sample.loaders.Loader.*;

import java.io.StringWriter;

import org.apache.wink.client.ClientConfig;
import org.apache.wink.client.RestClient;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class CreateTestSuiteContainers {
	private static final String RESOURCE_TYPE = "testsuite/";
	
	public static void main(String[] args) {
		String rootContainer = getRootContainerURI(args);
		ClientConfig config = new ClientConfig();
		config.handlers(getCredentials(args));
		RestClient client = new RestClient(config);
		
		System.out
				.println("Populating test suite data to LDP container: "
						+ rootContainer);
		
		String mr1 = post(client, rootContainer, resource(RESOURCE_TYPE, "diffmr.ttl"), "diffmr1");
		System.out.println("Created ldp:RDFSource at: \t\t" + mr1);
		String mr2 = post(client, rootContainer, resource(RESOURCE_TYPE, "diffmr.ttl"), "diffmr2");
		System.out.println("Created ldp:RDFSource at: \t\t" + mr2);
		
		String c = post(client, rootContainer, resource(RESOURCE_TYPE, "bc.ttl"), "bc", LDPConstants.CLASS_BASIC_CONTAINER);
		System.out.println("Created ldp:BasicContainer at: \t\t" + c);

		c = post(client, rootContainer, resource(RESOURCE_TYPE, "dc-simple.ttl"), "dc-simple", LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created ldp:DirectContainer (simple) at: \t" + c);

		c = post(client, rootContainer, resource(RESOURCE_TYPE, "dc-invmbr.ttl"), "dc-invmbr", LDPConstants.CLASS_DIRECT_CONTAINER);
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

		c = post(client, rootContainer, stringWriter.toString(), "dc-diffmr", LDPConstants.CLASS_DIRECT_CONTAINER);
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

		c = post(client, rootContainer, stringWriter.toString(), "dc-invmbr-diffmr", LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created ldp:DirectContainer (diff mbrshp res + inv mbr) at: \t" + c);
		
		c = post(client, rootContainer, resource(RESOURCE_TYPE, "bc.ttl"), "bc-asres", LDPConstants.CLASS_RESOURCE);
		System.out.println("Created ldp:BasicContainer (interaction model of ldp:Resource) at: \t\t" + c);

		System.out.println("Completed successfully!");
	}

}
