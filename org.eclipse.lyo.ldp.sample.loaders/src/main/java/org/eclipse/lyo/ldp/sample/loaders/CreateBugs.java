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
 *	   Samuel Padgett - add If-Match headers to PUT requests
 *	   Samuel Padgett - add Link header with interaction model on POST
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import static org.eclipse.lyo.ldp.sample.loaders.Loader.*;

import java.io.InputStream;
import java.io.StringWriter;

import javax.ws.rs.core.Response.Status.Family;

import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.RestClient;
import org.eclipse.lyo.ldp.server.LDPConstants;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class CreateBugs {
	private static final String BT_NS = "http://example.org/vocab/bugtracker#";
	private static final Property RELATED_BUG = ResourceFactory.createProperty(BT_NS, "relatedBug");

	private static final String RESOURCE_TYPE = "bugs/";

	public static void main(String[] args) {
		RestClient client = createClient(args);
		String rootContainer = getRootContainerURI(args);
		
		System.out.println("Populating sample bug tracker data to LDP container: " + rootContainer);
		
		String btContainer = post(client, rootContainer,
				resource(RESOURCE_TYPE, "bugTracker.ttl"), "bugTracker",
				LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created Bug Tracker container at " + btContainer);
		
		String productA = post(client, btContainer,
				resource(RESOURCE_TYPE, "productA.ttl"), "productA",
				LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created Product A container at " + productA);

		String productB = post(client, btContainer,
				resource(RESOURCE_TYPE, "productB.ttl"), "productB",
				LDPConstants.CLASS_DIRECT_CONTAINER);
		System.out.println("Created Product B container at " + productB);
		
		// Create Product A bugs.
		String bug1 = post(client, productA, resource(RESOURCE_TYPE,"bug1.ttl"), "bug1");
		System.out.println("Created Bug 1 at " + bug1);
		String bug2 = post(client, productA, resource(RESOURCE_TYPE,"bug2.ttl"), "bug2");
		System.out.println("Created Bug 2 at " + bug2);

		// Create Product B bugs.
		String bug10 = post(client, productB, resource(RESOURCE_TYPE,"bug10.ttl"), "bug10");
		System.out.println("Created Bug 10 at " + bug10);
		String bug11 = post(client, productB, resource(RESOURCE_TYPE,"bug11.ttl"), "bug11");
		System.out.println("Created Bug 11 at " + bug11);
		String bug12 = post(client, productB, resource(RESOURCE_TYPE,"bug12.ttl"), "bug12");
		System.out.println("Created Bug 12 at " + bug12);
		
		// Create some bt:relatedBug links.
		linkBugs(client, bug2, bug1);

		System.out.println("Done!");
	}
	
	private static void linkBugs(RestClient client, String sourceURI, String targetURI) {
		// Get the bug.
		org.apache.wink.client.Resource resource = client.resource(sourceURI);
		ClientResponse getResponse = resource.accept("text/turtle").get();
		if (getResponse.getStatusType().getFamily() != Family.SUCCESSFUL) {
			System.err.println("Could not GET bug " + sourceURI);
			System.exit(1);
		}

		String eTag = getResponse.getHeaders().getFirst("ETag");
		InputStream is = getResponse.getEntity(InputStream.class);
		Model model = read(is, sourceURI);
		
		// Update the property.
		Resource r = model.getResource(sourceURI);
		r.addProperty(RELATED_BUG, model.getResource(targetURI));

		// Put it back.
		ClientResponse putResponse = resource.contentType("text/turtle").header("If-Match", eTag).put(toTurtle(model));
		if (putResponse.getStatusType().getFamily() != Family.SUCCESSFUL) {
			System.err.println("Could not PUT bug " + sourceURI);
			System.exit(1);
		}
	}
	
	private static Model read(InputStream is, String base) {
		Model model = ModelFactory.createDefaultModel();
		model.read(is, base, "TURTLE");
		
		return model;
	}

	private static String toTurtle(Model model) {
		StringWriter stringWriter = new StringWriter();
		model.write(stringWriter, "TURTLE", "");

		return stringWriter.toString();
	}

}
