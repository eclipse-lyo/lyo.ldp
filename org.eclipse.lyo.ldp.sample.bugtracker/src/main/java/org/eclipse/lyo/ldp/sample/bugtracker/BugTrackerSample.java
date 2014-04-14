/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation.
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
 *     Samuel Padgett - add more data to bug tracking sample
 *******************************************************************************/
package org.eclipse.lyo.ldp.sample.bugtracker;

import java.io.InputStream;

import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPContainer;

/**
 * Bootstrap the bug tracker example.
 * 
 * @author Samuel Padgett <spadgett@us.ibm.com>
 */
public class BugTrackerSample {
	private static final String BT_NS = "http://example.org/vocab/bugtracker#";
	private static final String HAS_BUG = BT_NS + "hasBug";
	private static final String RELATED_BUG = BT_NS + "relatedBug";

	private static InputStream asStream(String resource) {
		return BugTrackerSample.class.getClassLoader().getResourceAsStream(resource);
	}
	
/*	private static InputStream asTurtleStream(Model m) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		m.write(out, "TURTLE");
		
		byte[] data = out.toByteArray();
		return new ByteArrayInputStream(data);
	}*/
	
//	private static Model toModel(LDPContainer container, String uri) {
//		ByteArrayOutputStream out = new ByteArrayOutputStream();
//		container.get(uri, out, LDPConstants.CT_TEXT_TURTLE);
//
//		byte[] data = out.toByteArray();
//		ByteArrayInputStream in = new ByteArrayInputStream(data);
//		
//		Model m = ModelFactory.createDefaultModel();
//		m.read(in, null, "TURTLE");
//		
//		return m;
//	}
//	
/*	private static void put(LDPContainer container, String uri, Model m) {
		InputStream in = asTurtleStream(m);
		container.put(uri, in, LDPConstants.CT_TEXT_TURTLE, null);
	}
*/	
	public static void load(LDPContainer container) {
		container.setConfigParameters(asStream("btconfig.ttl"), LDPConstants.CT_TEXT_TURTLE);
		String productA = container.post(asStream("productA.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
		String productB = container.post(asStream("productB.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
		
		String john = container.post(asStream("johndoe.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
		String jane = container.post(asStream("janeroe.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
		
		String bug1 = container.post(asStream("bug1.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
//		Model bug1Model = toModel(container, bug1);
//		Resource bug1Resource = bug1Model.createResource(bug1);
//		bug1Resource.removeAll(DCTerms.creator);
//		bug1Resource.addProperty(DCTerms.creator, bug1Model.createResource(john));
//		put(container, bug1, bug1Model);

		String bug2 = container.post(asStream("bug2.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
//		Model bug2Model = toModel(container, bug2);
//		Resource bug2Resource = bug2Model.createResource(bug2);
//		bug2Resource.removeAll(DCTerms.creator);
//		bug2Resource.addProperty(DCTerms.creator, bug2Model.createResource(john));
//		bug2Resource.addProperty(bug2Model.createProperty(RELATED_BUG), bug2Model.createResource(bug1));
//		put(container, bug2, bug2Model);

		String bug10 = container.post(asStream("bug10.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
//		Model bug10Model = toModel(container, bug10);
//		Resource bug10Resource = bug10Model.createResource(bug10);
//		bug10Resource.removeAll(DCTerms.creator);
//		bug10Resource.addProperty(DCTerms.creator, bug10Model.createResource(jane));
//		bug10Resource.removeAll(DCTerms.contributor);
//		bug10Resource.addProperty(DCTerms.contributor, bug10Model.createResource(john));
//		put(container, bug10, bug10Model);

		String bug11 = container.post(asStream("bug11.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);
		String bug12 = container.post(asStream("bug12.ttl"), LDPConstants.CT_TEXT_TURTLE, null, null);

		// TODO: Make Product A and Product B LDP containers.
//		Model aModel = toModel(container, productA);
//		Resource aResource = aModel.createResource(productA);
//		aResource.addProperty(aModel.createProperty(HAS_BUG), aModel.createResource(bug1));
//		aResource.addProperty(aModel.createProperty(HAS_BUG), aModel.createResource(bug2));
//		put(container, productA, aModel);
//		
//		Model bModel = toModel(container, productB);
//		Resource bResource = bModel.createResource(productB);
//		bResource.addProperty(bModel.createProperty(HAS_BUG), bModel.createResource(bug10));
//		bResource.addProperty(bModel.createProperty(HAS_BUG), bModel.createResource(bug11));
//		bResource.addProperty(bModel.createProperty(HAS_BUG), bModel.createResource(bug12));
//		put(container, productB, bModel);
	}
}
