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
 *	   Steve Speicher - updates for recent LDP spec changes
 *	   Steve Speicher - make root URI configurable 
 *	   Samuel Padgett - use TDB transactions
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.service.LDPService;

public class JenaLDPService extends LDPService {
	private static JenaLDPContainer rootContainer;
	private static TDBGraphStore graphStore = new TDBGraphStore(false);
	private static JenaLDPResourceManager resManager;
	
	static {
		reset();
	}
	
	public JenaLDPService() {
	}

	private synchronized static void reset() {
		rootContainer = JenaLDPContainer.create(ROOT_CONTAINER_URL, graphStore);
		resManager = new JenaLDPResourceManager(graphStore);
	}
	
	@Override
	protected void resetContainer() {
		reset();
	}

	@Override
	public JenaLDPContainer getRootContainer() {
		return rootContainer;
	}

	@Override
	protected JenaLDPResourceManager getResourceManger() {
		return resManager;
	}
	
	public static TDBGraphStore getStore() {
		return graphStore;
	}
}
