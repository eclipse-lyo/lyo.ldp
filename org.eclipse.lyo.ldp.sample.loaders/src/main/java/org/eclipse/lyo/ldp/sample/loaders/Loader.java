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
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.loaders;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.wink.client.handlers.BasicAuthSecurityHandler;

public class Loader {
	
	protected static BasicAuthSecurityHandler getCredentials(String[] args){
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
	
	/**
	 * Change the way the requestEntity is created, so that the request can be
	 * repeatable in the case of an authentication challenge
	 */
	protected static String resource(String type, String resource) {
		try {
			InputStream in = CreateTestSuiteContainers.class.getClassLoader().getResourceAsStream(type + resource);
			return IOUtils.toString(in, "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected static String getRootContainerURI(String[] args) {
		return args[0];
	}
}
