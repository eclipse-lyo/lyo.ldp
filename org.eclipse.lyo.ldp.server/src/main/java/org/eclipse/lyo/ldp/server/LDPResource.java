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
 *	   Steve Speicher - Updates for recent LDP spec changes
 *	   Steve Speicher - make root URI configurable 
 *	   Samuel Padgett - add Allow header to GET responses
 *	   Samuel Padgett - return accurate Allow header values for HTTP OPTIONS
 *******************************************************************************/
package org.eclipse.lyo.ldp.server;


public abstract class LDPResource implements ILDPResource {
	
	protected String fURI;
	protected Object fModel;
	protected String fRDFType;
	
	public LDPResource(String resourceURI, Object model) {
		this.fURI = resourceURI;
		this.fModel = model;
		this.fRDFType = LDPConstants.CLASS_RESOURCE;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.ILDPResource#getURI()
	 */
	@Override
	public String getURI() { return fURI; }
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.ILDPResource#setURI(java.lang.String)
	 */
	@Override
	public void setURI(String resourceURI)
	{ this.fURI = resourceURI; }

	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.ILDPResource#getModel()
	 */
	@Override
	public Object getModel() { return fModel; }
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.ILDPResource#setModel(java.lang.Object)
	 */
	@Override
	public void setModel(Object model)
	{ this.fModel = model; }
	
	/* (non-Javadoc)
	 * @see org.eclipse.lyo.ldp.server.ILDPResource#getTypeURI()
	 */
	@Override
	public String getTypeURI() {
		return this.fRDFType; }
}
