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
package org.eclipse.lyo.ldp.sample.networth;

import java.io.InputStream;

import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;

/**
 * Bootstrap the net worth example.
 * 
 * @author Samuel Padgett <spadgett@us.ibm.com>
 */
public class NetWorthSample {

	private static final String[] RESOURCES = {
		"a1.ttl", "a2.ttl", "a3.ttl", "a4.ttl", "a5.ttl"
	};

	public static void load(LDPContainer container) {
		for (String res : RESOURCES) {
			InputStream is = NetWorthSample.class.getClassLoader().getResourceAsStream(res);
			container.post(is, LDPConstants.CT_TEXT_TURTLE);
		}
	}
}
