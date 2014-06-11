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
 *     Samuel Padgett - add support for LDP Non-RDF Source
 *******************************************************************************/
package org.eclipse.lyo.ldp.server.jena;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.eclipse.lyo.ldp.server.LDPConstants;
import org.eclipse.lyo.ldp.server.LDPNonRDFSource;
import org.eclipse.lyo.ldp.server.jena.store.TDBGraphStore;
import org.eclipse.lyo.ldp.server.jena.vocabulary.LDP;
import org.eclipse.lyo.ldp.server.jena.vocabulary.Lyo;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.DCTerms;

public class JenaLDPNonRdfSource extends LDPNonRDFSource {
	protected final TDBGraphStore fGraphStore; // GraphStore in which to store the container and member resources   

	/**
	 * Directory for non-RDF resources.
	 */
	public static final String LDP_NR_DIR = "ldp.nr.dir";
	
	public JenaLDPNonRdfSource(String resourceURI, TDBGraphStore graphStore) {
		super(resourceURI, null);
		fGraphStore = graphStore;
	}

	@Override
	public boolean put(String resourceURI, InputStream stream,
	        String contentType, String user, HttpHeaders requestHeaders) {
		fGraphStore.writeLock();
		try {
			String configURI = JenaLDPResourceManager.mintConfigURI(resourceURI);
			Model configGraph = fGraphStore.getGraph(configURI);

			File file = toFile(resourceURI);
			if (!file.isFile()) {
				throw new WebApplicationException(Response.Status.NOT_FOUND);
			}
			
			// Check ETag header.
			if (requestHeaders != null) {
				String ifMatch = requestHeaders.getHeaderString(HttpHeaders.IF_MATCH);
				if (ifMatch == null) {
					// condition required
					throw new WebApplicationException(428);
				}
				String originalETag = getETag(file);
				// FIXME: Does not handle wildcards or comma-separated values...
				if (!originalETag.equals(ifMatch)) {
					throw new WebApplicationException(HttpStatus.SC_PRECONDITION_FAILED);
				}
			}

			// Update the file contents.
			writeToFile(stream, file);
			
			// Update config graph with new content type.
			Resource configResource = configGraph.getResource(configURI);
			configResource.removeAll(Lyo.ldpNRContentType);
			if (contentType != null) {
				configResource.addProperty(Lyo.ldpNRContentType, contentType);
			}
			
			fGraphStore.commit();
			return true;
		} catch (UnsupportedEncodingException e) {
	        e.printStackTrace();
	        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
	        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
	        e.printStackTrace();
	        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
			fGraphStore.end();
		}
	}

	@Override
	public void patch(String resourceURI, InputStream stream,
	        String contentType, String user) {
		throw new WebApplicationException(Status.METHOD_NOT_ALLOWED);
	}

	@Override
	public void delete(String resourceURI) {
		fGraphStore.writeLock();
		try {
			String configURI = JenaLDPResourceManager.mintConfigURI(resourceURI);
			Model configGraph = fGraphStore.getGraph(configURI);
			Statement memberOfStatement = configGraph.getProperty(configGraph.createResource(configURI), Lyo.ldpNRMemberOf);
			String containerURI = memberOfStatement.getResource().getURI();

			File file = toFile(resourceURI);
			if (!file.isFile()) {
				throw new WebApplicationException(Response.Status.NOT_FOUND);
			}
					
			boolean successful = file.delete();
			if (!successful) {
				throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
			}

			// Remove the resource from the container
			Model containerModel = fGraphStore.getGraph(containerURI);
			Resource containerResource = containerModel.getResource(containerURI);
			Model membershipResourceModel = containerModel;
			Property memberRelation = JenaLDPContainer.getMemberRelation(containerModel, containerResource);
			Resource membershipResource = JenaLDPContainer.getMembershipResource(containerModel, containerResource);
			Calendar time = Calendar.getInstance();

			if (!membershipResource.asResource().getURI().equals(containerURI)) {
				membershipResourceModel = fGraphStore.getGraph(membershipResource.asResource().getURI());
				if (membershipResourceModel == null) {
					membershipResourceModel = containerModel;
				} else {
					membershipResource = membershipResourceModel.getResource(membershipResource.asResource().getURI());
					// If isMemberOfRelation then membership triple will be nuked with the LDPR graph
					if (memberRelation != null)
						memberRelation = membershipResourceModel.getProperty(memberRelation.asResource().getURI());        			
					// Update dcterms:modified
				}
				membershipResource.removeAll(DCTerms.modified);
				membershipResource.addLiteral(DCTerms.modified, membershipResourceModel.createTypedLiteral(time));
			}
			membershipResourceModel.remove(membershipResource, memberRelation, membershipResourceModel.getResource(resourceURI));

			containerModel.remove(containerResource, LDP.contains, containerModel.getResource(resourceURI));
			containerResource.removeAll(DCTerms.modified);
			containerResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));

			// Delete the resource itself
			fGraphStore.deleteGraph(resourceURI);

			// Keep track of the deletion by logging the delete time
			configGraph.getResource(resourceURI).addLiteral(Lyo.deleted, configGraph.createTypedLiteral(time));

			fGraphStore.commit();
		} catch (UnsupportedEncodingException e) {
	        e.printStackTrace();
	        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
	        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
			fGraphStore.end();
		}
	}

	@Override
	public Response get(String uri, String contentType) {
		fGraphStore.readLock();
		try {
			String configURI = mintConfigURI(uri);
			Model configModel = fGraphStore.getGraph(configURI);
			if (configModel == null) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}
	
			File file = toFile(uri);
			if (!file.isFile()) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}
			
	        ResponseBuilder response = Response.ok(file);
			response.header(LDPConstants.HDR_LINK, "<" + LDPConstants.CLASS_NONRDFSOURCE + ">; " + LDPConstants.HDR_LINK_TYPE);
	        response.header(LDPConstants.HDR_ETAG, getETag(file));
	        
			Resource configResource = configModel.getResource(configURI);
			Statement contentTypeStatement = configResource.getProperty(Lyo.ldpNRContentType);
			if (contentTypeStatement != null) {
				// TODO: Make sure actual type is compatible with the Accept header.
				response.type(contentTypeStatement.getString());
			}
	
			Statement filenameStatement = configResource.getProperty(Lyo.ldpNRFilename);
			if (filenameStatement != null) {
				String filename = filenameStatement.getString().replace("\"", "");
				response.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
			}
	
			return response.build();
        } catch (UnsupportedEncodingException e) {
        	e.printStackTrace();
        	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } catch (NoSuchAlgorithmException e) {
        	e.printStackTrace();
        	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } catch (IOException e) {
        	e.printStackTrace();
        	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
        	fGraphStore.end();
        }
	}

	@Override
    public Set<String> getAllowedMethods() {
		HashSet<String> allowedMethods = new HashSet<String>();
		allowedMethods.add(HttpMethod.GET);
		allowedMethods.add(HttpMethod.PUT);
		allowedMethods.add(HttpMethod.DELETE);
		allowedMethods.add(HttpMethod.HEAD);
		allowedMethods.add(HttpMethod.OPTIONS);

	    return allowedMethods;
    }
	
	/**
	 * Saves an LDP-NR to the local filesystem.
	 * 
	 * @param content the content
	 * @param uri the URI of the LDP-NR
	 */
	public static void save(InputStream content, String uri) {
		try {
			File file = toFile(uri);
			writeToFile(content, file);
		} catch (IOException e) {
			e.printStackTrace();
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
	}

	public static String mintConfigURI(String uri) {
		return 	uri + JenaLDPResourceManager.CONFIG_PARAM;
	}

	private String getETag(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		try {
			return org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
		} finally {
			fis.close();
		}
	}
	
	public static boolean isLDPNR(String uri) {
        try {
	        File file = toFile(uri);
	        return file.isFile();
        } catch (UnsupportedEncodingException e) {
	        e.printStackTrace();
        	throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
        	throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
	}
	
	private static File toFile(String uri) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		String filename = toFilename(uri);
		File directory = getLDPNRDirectory();

		return new File(directory, filename);
	}
	
	/**
	 * Converts a URI of a non-RDF source resource to a unique filename with no special characters.
	 * 
	 * @param uri the non-RDF source URI
	 * @return a filename
	 * 
	 * @throws UnsupportedEncodingException
	 * @throws NoSuchAlgorithmException
	 */
	private static String toFilename(String uri) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		// Use the md5 hash of the URI to create a unique filename.
		return "ldpnr-" + org.apache.commons.codec.digest.DigestUtils.md5Hex(uri);
	}

    private static File getLDPNRDirectory() {
    	// Use LDP_NR_DIR property if set. If not, fall back to user.dir.
    	String path = System.getProperty(LDP_NR_DIR, System.getProperty("user.dir"));
    	File f = new File(path);
    	if (!f.isDirectory()) {
    		throw new IllegalArgumentException("Not directory: " + path);
    	}
    		
    	return f;
    }

    private static void writeToFile(InputStream content, File file) throws IOException {
    	FileOutputStream out = new FileOutputStream(file);
    	try {
    		IOUtils.copy(content, out);
    	} finally {
    		out.close();
    	}
    }
}
