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
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.wink.client.ClientConfig;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.RestClient;
import org.apache.wink.client.handlers.BasicAuthSecurityHandler;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

public class CreateBugs {
	private static final String BT_NS = "http://example.org/vocab/bugtracker#";
	private static final Property RELATED_BUG = ResourceFactory.createProperty(BT_NS, "relatedBug");

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
		System.out.println("Populating sample bug tracker data to LDP container: " + rootContainer);
		
		String btContainer = post(rootContainer, resource("bugTracker.ttl"), "bugTracker");
		System.out.println("Created Bug Tracker container at " + btContainer);
		
		String productA = post(btContainer, resource("productA.ttl"), "productA");
		System.out.println("Created Product A container at " + productA);

		String productB = post(btContainer, resource("productB.ttl"), "productB");
		System.out.println("Created Product B container at " + productB);
		
		// Create Product A bugs.
		String bug1 = post(productA, resource("bug1.ttl"), "bug1");
		System.out.println("Created Bug 1 at " + bug1);
		String bug2 = post(productA, resource("bug2.ttl"), "bug2");
		System.out.println("Created Bug 2 at " + bug2);

		// Create Product B bugs.
		String bug10 = post(productB, resource("bug10.ttl"), "bug10");
		System.out.println("Created Bug 10 at " + bug10);
		String bug11 = post(productB, resource("bug11.ttl"), "bug11");
		System.out.println("Created Bug 11 at " + bug11);
		String bug12 = post(productB, resource("bug12.ttl"), "bug12");
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

	/**
	 * Change the way the requestEntity is created, so that the request can be
	 * repeatable in the case of an authentication challenge
	 */
	private static String resource(String resource) {
		try {
			InputStream in = CreateBugs.class.getClassLoader().getResourceAsStream("bugs/"+resource);
			return IOUtils.toString(in, "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
