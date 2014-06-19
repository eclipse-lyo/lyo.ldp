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
 *     Samuel Padgett - support Prefer header
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.eclipse.lyo.ldp.server.ILDPDirectContainer;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

public class JenaLDPDirectContainer extends JenaLDPContainer implements ILDPDirectContainer {

    protected JenaLDPDirectContainer(String containerURI, TDBGraphStore graphStore) {
        super(containerURI, graphStore);
        fRDFType = LDPConstants.CLASS_DIRECT_CONTAINER;
    }

    protected boolean includeMembers(MultivaluedMap<String, String> preferences) {
        final List<String> include = preferences.get(LDPConstants.PREFER_INCLUDE);
        final List<String> omit = preferences.get(LDPConstants.PREFER_OMIT);
        
        final boolean omitMembership = (omit != null && omit.contains(LDPConstants.PREFER_MEMBERSHIP));
        if (include != null) {
            if (include.contains(LDPConstants.PREFER_MEMBERSHIP)) {
                return true;
            }

            if (include.contains(LDPConstants.PREFER_MINIMAL_CONTAINER) ||
                     include.contains(LDPConstants.DEPRECATED_PREFER_EMPTY_CONTAINER)) {
                return false;
            }
        }
        
        return !omitMembership;
    }
	
    @Override
    protected boolean isReturnRepresentationPreferenceApplied(MultivaluedMap<String, String> preferences) {
        if (super.isReturnRepresentationPreferenceApplied(preferences)) {
            return true;
        }

        final List<String> include = preferences.get(LDPConstants.PREFER_INCLUDE);
        final List<String> omit = preferences.get(LDPConstants.PREFER_OMIT);
        
        if (include != null && include.contains(LDPConstants.PREFER_MEMBERSHIP)) {
            return true;
        }

        return omit != null && omit.contains(LDPConstants.PREFER_MEMBERSHIP);
    }

    @Override
    protected Model amendResponseGraph(Model container, MultivaluedMap<String, String> preferences)
    {
        // The super implementation makes a copy we can modify.
        Model result = super.amendResponseGraph(container, preferences);
        final Resource containerResource = container.getResource(fURI);
        final Resource membershipResource = getMembershipResource(container, containerResource);
        final String memberURI = membershipResource.getURI();
        final Property isMemberOfRelation = getIsMemberOfRelation(container, containerResource);

        // Determine whether to include membership triples from the Prefer header.
        if (includeMembers(preferences)) {
            if (isMemberOfRelation != null) {
                // Handling ldp:isMemberOfRelation, where all membership triples are stored in member resource graphs
                Model globalModel = fGraphStore.getGraph("urn:x-arq:UnionGraph"); 
                result.add(globalModel.listStatements(null, isMemberOfRelation, membershipResource));
            } else if (!fURI.equals(memberURI)) {
                // Add in the membership resource 
                Model memberGraph = fGraphStore.getGraph(memberURI);
                Property memberRelation = getMemberRelation(container, containerResource);
                result.add(memberGraph.listStatements(memberGraph.getResource(memberURI), memberRelation, (RDFNode) null));
            }
        } else if (isMemberOfRelation == null && fURI.equals(memberURI)) {
            // If the container itself holds the member properties, we need to remove them.
            // (For all other cases, we simply don't add them.)
            Property memberRelation = getMemberRelation(container, containerResource);
            result.removeAll(containerResource, memberRelation, null);
        }

        return result;
    }
}
