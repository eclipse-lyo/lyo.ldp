/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
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
 *******************************************************************************/
package org.eclipse.lyo.ldp.webapp.service;

import java.io.File;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;

public class Bootstrap implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent e) {
		String datasetDir = System.getProperty(TDBGraphStore.LDP_DATASET_DIR);
		if (datasetDir == null) {
			File tempdir = (File) e.getServletContext().getAttribute("javax.servlet.context.tempdir");
			System.setProperty(TDBGraphStore.LDP_DATASET_DIR, tempdir.getPath());
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent e) {
	}
}
