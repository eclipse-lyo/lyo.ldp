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
 *     Steve Speicher - support for various container types
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.InputStream;

import org.eclipse.lyo.ldp.server.ILDPDirectContainer;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.store.GraphStore;

public class JenaLDPDirectContainer extends JenaLDPContainer implements ILDPDirectContainer {

	protected JenaLDPDirectContainer(String containerURI,
			GraphStore graphStore, GraphStore pageStore, InputStream config) {
		super(containerURI, graphStore, pageStore, config);
		fRDFType = LDPConstants.CLASS_DIRECT_CONTAINER;
	}
}
