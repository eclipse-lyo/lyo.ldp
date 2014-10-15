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
 *	   Tori Santonil  - pull common code into utility class
 *	   Samuel Padgett - add Link header with interaction model on POST
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.wink.client.ClientConfig;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.RestClient;
import org.apache.wink.client.handlers.BasicAuthSecurityHandler;

/**
 * Common functions for the different loaders.
 */
public class Loader {
	static BasicAuthSecurityHandler getCredentials(String[] args){
		BasicAuthSecurityHandler basicAuthSecHandler = new BasicAuthSecurityHandler();
		argsCheck(args);
		String auth = args[1];
		if (auth.contains(":")) {
			String[] split = auth.split(":");
			if (split.length == 2) {
				basicAuthSecHandler.setUserName(split[0]); 
				basicAuthSecHandler.setPassword(split[1]); 
				
			} else {
				System.err.println("ERROR: Usage: invalid basic authentication credentials");
			}
		} else {
			System.err.println("ERROR: Usage: invalid basic authentication credentials");
		}
		return basicAuthSecHandler;
	}
	
	private static void argsCheck(String[] args){
		if (args.length != 2) {
			System.err.println("Usage: java com.eclipse.lyo.ldp.sample.CreateTestSuiteContainers <root_container_url> <username:password>");
			System.exit(1);
		}

	}
	
	static RestClient createClient(String[] args) {
		BasicAuthSecurityHandler basicAuthSecHandler = getCredentials(args);
		ClientConfig config = new ClientConfig();
		config.handlers(basicAuthSecHandler);
		return new RestClient(config);		
	}

	/**
	 * Change the way the requestEntity is created, so that the request can be
	 * repeatable in the case of an authentication challenge
	 */
	static String resource(String type, String resource) {
		try {
			InputStream in = CreateTestSuiteContainers.class.getClassLoader().getResourceAsStream(type + resource);
			return IOUtils.toString(in, "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	static String getRootContainerURI(String[] args) {
		return args[0];
	}
	
	static String post(RestClient client, String uri, Object requestEntity, String slug) {
		return post(client, uri, requestEntity, slug, null);
	}

	static String post(RestClient client, String uri, Object requestEntity, String slug, String type) {
		org.apache.wink.client.Resource resource = 
					client.resource(uri).contentType("text/turtle").header("Slug", slug);
		
		if (type != null) {
			resource.header("Link", "<" + type + ">; rel=\"type\"");
		}
		
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
}
