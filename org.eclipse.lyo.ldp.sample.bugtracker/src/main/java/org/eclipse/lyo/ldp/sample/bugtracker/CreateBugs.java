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
package org.eclipse.lyo.ldp.sample.bugtracker;

import java.io.InputStream;
import java.io.StringWriter;

import javax.ws.rs.core.Response.Status.Family;

import org.apache.http.HttpStatus;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.RestClient;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

public class CreateBugs {
	private static final String BT_NS = "http://example.org/vocab/bugtracker#";
	private static final Property RELATED_BUG = ResourceFactory.createProperty(BT_NS, "relatedBug");

	private static RestClient client = new RestClient();

	public static void main(String[] args) {
		String rootContainer = getRootContainerURI(args);
		System.out.println("Populating sample bug tracker data to LDP container: " + rootContainer);
		
		String btContainer = post(rootContainer, asStream("bugTracker.ttl"), "bugTracker");
		System.out.println("Created Bug Tracker container at " + btContainer);
		
		String productA = post(btContainer, asStream("productA.ttl"), "productA");
		System.out.println("Created Product A container at " + productA);

		String productB = post(btContainer, asStream("productB.ttl"), "productB");
		System.out.println("Created Product B container at " + productB);
		
		// Create Product A bugs.
		String bug1 = post(productA, asStream("bug1.ttl"), "bug1");
		System.out.println("Created Bug 1 at " + bug1);
		String bug2 = post(productA, asStream("bug2.ttl"), "bug2");
		System.out.println("Created Bug 2 at " + bug2);

		// Create Product B bugs.
		String bug10 = post(productB, asStream("bug10.ttl"), "bug10");
		System.out.println("Created Bug 10 at " + bug10);
		String bug11 = post(productB, asStream("bug11.ttl"), "bug11");
		System.out.println("Created Bug 11 at " + bug11);
		String bug12 = post(productB, asStream("bug12.ttl"), "bug12");
		System.out.println("Created Bug 12 at " + bug12);
		
		// Create some bt:relatedBug links.
		linkBugs(bug2, bug1);

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
			System.err.println("Usage: java com.eclipse.lyo.ldp.sample.bugtracker.CreateBugs <container_url>");
			System.exit(1);
		}

		return args[0];
    }
	
	private static void linkBugs(String sourceURI, String targetURI) {
		// Get the bug.
		org.apache.wink.client.Resource resource = client.resource(sourceURI);
		InputStream is = resource.accept("text/turtle").get(InputStream.class);
		Model model = read(is, sourceURI);
		
		// Update the property.
		Resource r = model.getResource(sourceURI);
		r.addProperty(RELATED_BUG, model.getResource(targetURI));

		// Put it back.
		ClientResponse response = resource.contentType("text/turtle").put(toTurtle(model));
		if (response.getStatusType().getFamily() != Family.SUCCESSFUL) {
			System.err.println("Could not PUT bug " + sourceURI);
			System.exit(1);
		}
	}
	
	private static Model read(InputStream is, String base) {
		Model model = ModelFactory.createDefaultModel();
		model.read(asStream("bug1.ttl"), base, "TURTLE");
		
		return model;
	}

	private static String toTurtle(Model model) {
		StringWriter stringWriter = new StringWriter();
		model.write(stringWriter, "TURTLE", "");

		return stringWriter.toString();
	}

	private static InputStream asStream(String resource) {
		return CreateBugs.class.getClassLoader().getResourceAsStream(resource);
	}
}
