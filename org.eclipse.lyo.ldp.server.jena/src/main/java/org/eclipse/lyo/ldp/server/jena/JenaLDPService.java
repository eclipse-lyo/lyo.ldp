/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
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
package org.eclipse.lyo.ldp.server.jena;

import java.io.ByteArrayInputStream;

import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;
import org.eclipse.lyo.ldp.server.service.LDPService;

public class JenaLDPService extends LDPService {
	private static JenaLDPContainer rootContainer;

	static {
		reset();
		// Create an empty container.
		String stuff="<"+ROOT_CONTAINER_URL+"> a <" + LDP.Container.getURI() + ">.";
		rootContainer.put(new ByteArrayInputStream( stuff.getBytes() ), LDPConstants.CT_TEXT_TURTLE);
	}

	private static void reset() {
		rootContainer = JenaLDPContainer.create(ROOT_CONTAINER_URL, new TDBGraphStore(), new TDBGraphStore());
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
}
