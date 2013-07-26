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

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;

public class JenaLDPServer {
	
    protected JenaLDPServer() throws Exception {
        JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
        serverFactory.setResourceClasses(JenaLDPService.class);
	    serverFactory.setResourceProvider(JenaLDPService.class, 
	      new SingletonResourceProvider(new JenaLDPService()));
        serverFactory.setAddress("http://localhost:8080/");
        serverFactory.create();
    }
	
    public static void main(String args[]) throws Exception {
	   new JenaLDPServer();
	   System.out.println("Server starting up...");
	
	   Thread.sleep(300000);
	   System.out.println("Server shutting down...");
       System.exit(0);
    }

}
