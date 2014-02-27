/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation.
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
 *     Steve Speicher - updates for recent LDP spec changes
 *     Steve Speicher - make root URI configurable 
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.LDPResourceManager;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.service.LDPService;

public class JenaLDPService extends LDPService {
	private static JenaLDPContainer rootContainer;
	private static JenaLDPResourceManager resManager;
	
	static {
		reset();
	}

	private static void reset() {
		rootContainer = JenaLDPContainer.create(ROOT_CONTAINER_URL, new TDBGraphStore(false), new TDBGraphStore());
		resManager = new JenaLDPResourceManager(rootContainer.getGraphStore(), rootContainer.getPagingGraphStore());
	}
	
	public static JenaLDPContainer getJenaRootContainer() {
		return rootContainer;
	}

	@Override
	protected synchronized void resetContainer() {
		JenaLDPService.reset();
	}

	@Override
	public LDPContainer getRootContainer() {
		return rootContainer;
	}

	@Override
	protected LDPResourceManager getResourceManger() {
		return resManager;
	}
}
