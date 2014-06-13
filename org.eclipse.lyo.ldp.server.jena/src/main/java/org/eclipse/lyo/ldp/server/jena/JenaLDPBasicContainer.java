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
 *     Samuel Padgett - use TDB transactions
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import org.eclipse.lyo.ldp.server.ILDPBasicContainer;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;

public class JenaLDPBasicContainer extends JenaLDPContainer implements ILDPBasicContainer {

	protected JenaLDPBasicContainer(String containerURI, TDBGraphStore graphStore) {
		super(containerURI, graphStore);
		fRDFType = LDPConstants.CLASS_BASIC_CONTAINER;
	}
}
