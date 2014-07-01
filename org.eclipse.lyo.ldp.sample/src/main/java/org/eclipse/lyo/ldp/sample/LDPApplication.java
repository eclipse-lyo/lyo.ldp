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
 *	   Frank Budinsky - initial API and implementation
 *	   Steve Speicher - initial API and implementation
 *	   Samuel Padgett - initial API and implementation
 *	   Samuel Padgett - add HttpHeaderResponseFilter to sample application
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.eclipse.lyo.ldp.server.jena.JenaLDPService;
import org.eclipse.lyo.ldp.server.service.HttpHeaderResponseFilter;

public class LDPApplication extends Application {
	
	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> classes = new HashSet<Class<?>>();
		classes.add(JenaLDPService.class);
		classes.add(VisualizationService.class);
		classes.add(HttpHeaderResponseFilter.class);
		return classes;
	}

}
