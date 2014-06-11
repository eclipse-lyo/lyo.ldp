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
 *     Steve Speicher - updates for recent LDP spec changes
 *     Steve Speicher - make root URI configurable 
 *     Samuel Padgett - add support for LDP Non-RDF Source
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena.store;

import java.io.OutputStream;

import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.jena.JenaLDPResourceManager;
import org.eclipse.lyo.ldp.server.jena.vocabulary.Lyo;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.tdb.base.block.FileMode;
import com.hp.hpl.jena.tdb.sys.SystemTDB;
import com.hp.hpl.jena.vocabulary.DCTerms;

/**
 * This class implements a Graph Store using a Jena TDB dataset.
 */
public class TDBGraphStore implements GraphStore
{
	public static final String LDP_DATASET_DIR = "ldp.dataset.dir";

	static String fDatasetDir=null;
	static {
		fDatasetDir = System.getProperty(LDP_DATASET_DIR);
		TDB.getContext().set(TDB.symUnionDefaultGraph, true);
		TDB.setOptimizerWarningFlag(false);
        SystemTDB.setFileMode(FileMode.direct) ;
        System.out.println("Using dataset directory: " + fDatasetDir);
	}
	
	protected Dataset fDataset; // Dataset to store the graphs

	public TDBGraphStore(Dataset dataset)
	{
		fDataset = dataset;
	}

	public TDBGraphStore() // Use in-memory Dataset. For testing.
	{
		fDataset = TDBFactory.createDataset();
	}
	
	public TDBGraphStore(boolean inMemory) {
		if (inMemory || fDatasetDir == null) {
			fDataset = TDBFactory.createDataset();
		} else {
			fDataset = TDBFactory.createDataset(fDatasetDir);
			if (fDatasetDir == null) {
				System.err.println("Jena TDB failed to create dataset for directory: "+fDatasetDir+", switching to inmemory dataset.");
			}
		}		
	}
	
	public void readLock() {
		fDataset.begin(ReadWrite.READ);
	}
	
	public void writeLock() {
		fDataset.begin(ReadWrite.WRITE);
	}
	
	public void commit() {
		fDataset.commit();
	}
	
	public void abort() {
		fDataset.abort();
	}
	
	public void end() {
		fDataset.end();
	}
	
	public Model getDefaultModel() {
		return fDataset.getDefaultModel();
	}

	public void putGraph(String graphURI, Model model)
	{
		Model graphModel = graphURI != null ? fDataset.getNamedModel(graphURI) : fDataset.getDefaultModel();
		graphModel.removeAll();
		graphModel.add(model);
	}

	public Model getGraph(String graphURI)
	{
		if (graphURI != null) {
			return fDataset.containsNamedModel(graphURI) ? fDataset.getNamedModel(graphURI) : null;
		}
		return fDataset.getDefaultModel();
	}

	public void deleteGraph(String graphURI)
	{
		Model model = fDataset.getNamedModel(graphURI);
		Resource resource = model.getResource(graphURI);
		fDataset.asDatasetGraph().removeGraph(resource.asNode());
	}

	public String createGraph(String containerURI, String graphURIPrefix, String nameHint)
	{
		String graphURI = mintURI(containerURI, graphURIPrefix, nameHint);
		// Add a dummy triple, just to allocate the graph
		Model model = fDataset.getNamedModel(graphURI);
		Resource graphResource = model.getResource(graphURI);
		model.add(graphResource, DCTerms.description, "Graph Placeholder");
		return graphURI;
	}

	public String mintURI(String containerURI, String graphURIPrefix, String nameHint) {
	    String graphURI = null;
		if (nameHint != null && nameHint.length() > 0) {
			graphURI = appendURISegment(containerURI,  nameHint);
			if (previouslyUsed(graphURI)) graphURI = null;
		} 
		if (graphURI == null) {
			// TODO: Use count # from container so we don't have to always start at 1
			for (long count = 1; ; ++count) {
				graphURI = graphURIPrefix + count;
				if (!previouslyUsed(graphURI)) break;
			}
		}
	    return graphURI;
    }
	
	/**
	 * Companion graph to the one identified by uri
	 * @param uri  The primary resource being managed
	 * @param configURI A side resource, in support of uri
	 */
	public Model createConfigGraph(String uri, String configURI) {
		Model model = fDataset.getNamedModel(configURI);
		Resource graphResource = model.getResource(uri);
		Resource configResource = model.getResource(configURI);
		model.add(configResource, Lyo.describes, graphResource);
		return model;
	}
	
	/**
	 * Given uri, determine if a resource graph or config graph exists (further testing could be done)
	 * @param uri
	 * @return true if found an old config graph
	 */
	public boolean previouslyUsed(String uri) {
		return fDataset.containsNamedModel(uri) || fDataset.containsNamedModel(JenaLDPResourceManager.mintConfigURI(uri));
	}

	public void query(OutputStream outStream, String queryString)
	{
		query(outStream, queryString, null);
	}

	@Override
	public void query(OutputStream outStream, String queryString, String resultsFormat) {
		readLock();
		try {
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, fDataset);
			ResultSet result = qexec.execSelect();
			try {
				if (resultsFormat != null && resultsFormat.equals(LDPConstants.CT_APPLICATION_SPARQLRESULTSJSON)) {
					ResultSetFormatter.outputAsJSON(outStream, result);
				} else {
					ResultSetFormatter.outputAsXML(outStream, result);
				}
			} finally { qexec.close(); }
		} finally { end(); }
	}

	public Model construct(String queryString)
	{
		readLock();
		try {
			Query query = QueryFactory.create(queryString);
			QueryExecution qexec = QueryExecutionFactory.create(query, fDataset);
			return qexec.execConstruct();
		} finally { end(); }
	}


	public static String appendURISegment(String base, String append)
	{
		return base.endsWith("/") ? base + append : base + "/" + append;
	}
}
