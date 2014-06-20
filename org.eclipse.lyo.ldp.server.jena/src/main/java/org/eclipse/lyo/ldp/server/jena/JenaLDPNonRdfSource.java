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
import javax.ws.rs.core.MultivaluedMap;
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
import com.hp.hpl.jena.vocabulary.RDF;

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
	public void putUpdate(InputStream stream,
	        String contentType, String user, HttpHeaders requestHeaders) {
		fGraphStore.writeLock();
		try {
			String associatedURI = JenaLDPResourceManager.mintAssociatedRDFSourceURI(getURI());
			Model associatedModel = fGraphStore.getGraph(associatedURI);

			File file = toFile(getURI());
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
			Resource associatedResource = associatedModel.getResource(associatedURI);
			associatedResource.removeAll(DCTerms.format);
			if (contentType != null) {
				Resource mediaType = associatedModel.createResource(null,  associatedModel.createResource(DCTerms.NS + "IMT"));
				mediaType.addProperty(RDF.value, contentType);
				associatedResource.addProperty(DCTerms.format, mediaType);
			}

			Calendar time = Calendar.getInstance();
			associatedResource.removeAll(DCTerms.modified);
			associatedResource.addLiteral(DCTerms.modified, associatedModel.createTypedLiteral(time));
			
			fGraphStore.commit();
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
	public void delete() {
		fGraphStore.writeLock();
		try {
			// Delete the file.
			final File file = toFile(getURI());
			if (!file.isFile()) {
				throw new WebApplicationException(Response.Status.NOT_FOUND);
			}
	
			if (!file.delete()) {
				throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
			}

			// FIXME: Move this logic into JenaLDPContainer and subclasses
			final String configURI = JenaLDPResourceManager.mintConfigURI(getURI());
			final Model configGraph = fGraphStore.getGraph(configURI);
			final Resource configResource = configGraph.getResource(configURI);
			final String containerURI = configResource.getPropertyResourceValue(Lyo.memberOf).getURI();
			final Model containerModel = fGraphStore.getGraph(containerURI);
			final Resource containerResource = containerModel.getResource(containerURI);
			final Calendar time = Calendar.getInstance();
			
			// First remove the membership triples
			final Property memberRelation = JenaLDPDirectContainer.getMemberRelation(containerModel, containerResource);
			if (memberRelation != null) {
			    final String membershipResourceURI = JenaLDPDirectContainer.getMembershipResourceURI(containerModel, containerResource);
			    final Model membershipResourceModel = (membershipResourceURI.equals(containerURI)) ? containerModel : fGraphStore.getGraph(membershipResourceURI);
			    final Resource membershipResource = membershipResourceModel.getResource(membershipResourceURI);
			    membershipResource.removeAll(DCTerms.modified);
			    membershipResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));
			    membershipResourceModel.remove(membershipResource, memberRelation, containerModel.getResource(getURI()));
			}

			// Next remove the containment triples
			containerModel.remove(containerResource, LDP.contains, containerModel.getResource(getURI()));
			containerResource.removeAll(DCTerms.modified);
			containerResource.addLiteral(DCTerms.modified, containerModel.createTypedLiteral(time));

			// Delete the resource itself
			fGraphStore.deleteGraph(JenaLDPResourceManager.mintAssociatedRDFSourceURI(getURI()));

			// Keep track of the deletion by logging the delete time
			configGraph.getResource(getURI()).addLiteral(Lyo.deleted, configGraph.createTypedLiteral(time));

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
	public Response get(String contentType, MultivaluedMap<String, String> preferences) {
		fGraphStore.readLock();
		try {
			String associatedURI = JenaLDPResourceManager.mintAssociatedRDFSourceURI(getURI());
			Model associatedModel = fGraphStore.getGraph(associatedURI);
			if (associatedModel == null) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}
	
			File file = toFile(getURI());
			if (!file.isFile()) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}
	
	        ResponseBuilder response = Response.ok(file);
			response.header(LDPConstants.HDR_LINK, "<" + LDPConstants.CLASS_NONRDFSOURCE + ">; " + LDPConstants.HDR_LINK_TYPE);
			response.header(LDPConstants.HDR_LINK, "<" + associatedURI + ">; " + LDPConstants.HDR_LINK_DESCRIBEDBY);
	        response.header(LDPConstants.HDR_ETAG, getETag(file));
	        
			Resource configResource = associatedModel.getResource(associatedURI);
			Statement contentTypeStatement = configResource.getProperty(DCTerms.format);
			if (contentTypeStatement != null) {
				// TODO: Make sure actual type is compatible with the Accept header.
				Statement value = contentTypeStatement.getResource().getProperty(RDF.value);
				if (value != null) {
					response.type(value.getString());
				}
			}
	
			Statement filenameStatement = configResource.getProperty(Lyo.slug);
			if (filenameStatement != null) {
				String filename = filenameStatement.getString().replace("\"", "");
				response.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
			}
	
			return response.allow(getAllowedMethods()).build();
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
	public Response options() {
		String associatedURI = JenaLDPResourceManager.mintAssociatedRDFSourceURI(fURI);
		return Response
				.ok()
				.allow(getAllowedMethods())
				.header(LDPConstants.HDR_LINK, "<" + getTypeURI() + ">; " + LDPConstants.HDR_LINK_TYPE)
				.header(LDPConstants.HDR_LINK, "<" + associatedURI + ">; " + LDPConstants.HDR_LINK_DESCRIBEDBY)
				.build();
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

	private String getETag(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		try {
			return "\"" + org.apache.commons.codec.digest.DigestUtils.md5Hex(fis) + "\"";
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
