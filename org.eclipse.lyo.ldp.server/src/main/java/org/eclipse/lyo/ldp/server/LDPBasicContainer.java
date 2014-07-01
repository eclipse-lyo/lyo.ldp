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
 *	   Steve Speicher - adding support for basic containers
 *******************************************************************************/
package org.eclipse.lyo.ldp.server;

public abstract class LDPBasicContainer extends LDPContainer implements ILDPBasicContainer {

	public LDPBasicContainer(String resourceURI, Object model) {
		super(resourceURI, model);
		this.fRDFType = LDPConstants.CLASS_BASIC_CONTAINER;
	}

}
