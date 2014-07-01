/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation.
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
 *	   Steve Speicher - initial implementation
 *	   Samuel Padgett - add Link and Accept-Patch headers
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.service;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.eclipse.lyo.ldp.server.LDPConstants;

@Provider
public class HttpHeaderResponseFilter implements ContainerResponseFilter {

	@Override
	public void filter(ContainerRequestContext requestContext,
			ContainerResponseContext responseContext) throws IOException {
		// TODO: Are there other responses that shouldn't have the ldp:Resource Link header? HTTP DELETE? Other 4xx errors?
		if (responseContext.getStatus() != HttpServletResponse.SC_NOT_FOUND) {
			responseContext.getHeaders().add(LDPConstants.HDR_LINK, "<"+LDPConstants.CLASS_RESOURCE+">; " + LDPConstants.HDR_LINK_TYPE);
		}

		// If POST is allowed, add in an Accept-Post header.
		if (responseContext.getAllowedMethods().contains(HttpMethod.POST)) {
			responseContext.getHeaders().putSingle(LDPConstants.HDR_ACCEPT_POST,
					LDPService.ACCEPT_POST_CONTENT_TYPES_STR);
		}

		// If PATCH is allowed, add in an Accept-Patch header.
		if (responseContext.getAllowedMethods().contains("PATCH")) {
			responseContext.getHeaders().putSingle(LDPConstants.HDR_ACCEPT_PATCH,
					LDPService.ACCEPT_PATCH_CONTENT_TYPES_STR);
		}
	}
}
