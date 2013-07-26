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
package org.eclipse.lyo.ldp.sample;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.lyo.ldp.sample.bugtracker.BugTrackerSample;
import org.eclipse.lyo.ldp.sample.networth.NetWorthSample;
import org.eclipse.lyo.ldp.server.LDPContainer;
import org.eclipse.lyo.ldp.server.jena.JenaLDPService;

public class Bootstrap implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent e) {
		LDPContainer container = JenaLDPService.getJenaRootContainer();
		NetWorthSample.load(container);
		BugTrackerSample.load(container);
	}

	@Override
	public void contextDestroyed(ServletContextEvent e) {
	}
}
