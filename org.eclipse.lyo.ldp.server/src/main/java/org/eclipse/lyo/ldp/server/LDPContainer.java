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
 *	   Samuel Padgett - allow implementations to set response headers using Response
 *******************************************************************************/
package org.eclipse.lyo.ldp.server;

public abstract class LDPContainer extends LDPRDFSource implements ILDPContainer{
	
	/**
	 * Often a LDPcontainer will want to have some counter to
	 * help with LDPR name assignment, such as: res1, res2, ..., res87.
	 */
	int resourceSuffixCount=0;

	public LDPContainer(String resourceURI, Object model) {
		super(resourceURI, model);
		this.fRDFType = LDPConstants.CLASS_CONTAINER;
	}

}