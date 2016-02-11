package fr.lip6.reden;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

/**
 * This class implements methods related to the RDF data manipulation.
 * 
 * @author @author Brando & Frontini - Labex OBVIL - Université Paris-Sorbonne - UPMC
 *         LIP6
 */
public class RDFProcessingNEL {

	private static Logger logger = Logger.getLogger(RDFProcessingNEL.class);
	
	/**
	 * Decodes URI.
	 * 
	 * @param s, the URI
	 * @return the new URI
	 */
	public static String decompose(String s) {
		try {
			if (s.startsWith("http:")) {
				return URLDecoder.decode(s, "UTF-8");
			} else {
				return s;
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * If necessary it downloads RDF data by URI from the Web of Data.
	 * @param uri, concerned URI
	 * @param baseURL
	 * @param model, RDF model where to store the data
	 * @param dir, physical location of the folder where to store RDF files
	 * (to avoid downloading files every time)
	 * @return the raw RDF graph
	 */
	public static void retrieveRDF(String uri, String baseURL,
			String dir) {

		try {
			File f = new File(dir + "/file" + replaceNonAlphabeticCharacters(uri) + ".n3");
			// to go faster (remove f.exists if we want
			// to update local triples)
			if (!f.exists() || FileUtils.readFileToString(f).trim().isEmpty()) {
				Model model = ModelFactory.createDefaultModel();
				if (uri.contains("dbpedia")) {
					InputStream in = FileManager.get().open(uri+".ntriples");
					if (in != null) {
						model.read(in, null, "N3");	
					} else {
						logger.info("skip URI: " + uri);
						return;
					}
				} else {
					model.read(uri);
				}

				OutputStream fileOutputStream = new FileOutputStream(f);
				OutputStreamWriter out = new OutputStreamWriter(
						fileOutputStream, "UTF-8");
				model.write(out, "N3");
				logger.info("downloaded from uri: " + uri + " and file " + dir + "/file"
						+ replaceNonAlphabeticCharacters(uri) + ".n3");				
				out.close();
				fileOutputStream.close();				

			} else {				
			}
		} catch (Exception ignore) {
			logger.info("problem with URI: " + uri); //not found or bad syntax, etc. so ignore
		}
	}

	/**
	 * inject all sameAs references.
	 * @param mentionsWithURIs, mentions and their candidates
	 * @param baseURIS
	 * @param model, raw RDF model
	 * @param dir, where to find the model
	 * @return the update raw RDF model
	 */
	public static Model injectSameAsInformation(
			Map<String, List<List<String>>> mentionsWithURIs,
			String[] baseURIS, Model model, String dir, String crawlSameAs, 
			String sameAsproperty) {

		Model modelout = ModelFactory.createDefaultModel();
		Property prop = model
				.getProperty(sameAsproperty);
		for (List<List<String>> uriLists : mentionsWithURIs.values()) {
			for (List<String> uriList : uriLists) {
				for (String uri : uriList) {
					for (String baseURL2 : baseURIS) {
						String baseURL = baseURL2.trim();
						if (uri.contains(baseURL)) {
							Resource individualSameAs = model.getResource(uri);
							SimpleSelector ss = new SimpleSelector(
									individualSameAs, prop, (RDFNode) null);
							ExtendedIterator<Statement> iter = model.listStatements(ss);
							while (iter.hasNext()) {
								Statement stmt = iter.next();
								RDFNode object = stmt.getObject();
								if (!crawlSameAs.equalsIgnoreCase("ALL")) {
									if (object.toString().startsWith(crawlSameAs)) {
										retrieveRDF(decompose(object.toString()), baseURL, dir);
										if (new File(dir + "/file" + replaceNonAlphabeticCharacters(decompose(object.toString()))+ ".n3").exists()) {
												modelout.read(dir + "/file"
														+ replaceNonAlphabeticCharacters(decompose(object.toString())) + ".n3");
										}
									}
								} else {
									retrieveRDF(decompose(object.toString()), baseURL, dir);
									if (new File(dir + "/file" + replaceNonAlphabeticCharacters(decompose(object.toString()))+ ".n3").exists()) {
											modelout.read(dir + "/file"
													+ replaceNonAlphabeticCharacters(decompose(object.toString())) + ".n3");
									}
								}
							}
						}
					}
				}
			}
		}
		return modelout;
	}

	/**
	 * For every paragraph, it builds the RDF sub-subgraph corresponding to the
	 * mentions thanks to URIs.
	 * 
	 * @param dir
	 *            , the name of folder where to store data
	 * @param mentionsWithURIs
	 *            , URIs information
	 * @param mentionsPerParagraph
	 *            , mentions found of paragraph
	 * @param baseURIS
	 *            , the base names of the URIs
	 */
	public static Model aggregateRDFSubGraphsFromURIs(String dir,
			Map<String, List<List<String>>> mentionsWithURIs,
			List<String> mentionsofParagraph, String[] baseURIS, 
			String crawlSameAs, String sameAsproperty) {
		Date start = new Date();
		File dirF = new File(dir);
		if (!dirF.exists())
			dirF.mkdir();
		// store RDF files into a local folder
		List<String> alreadyProcessedURI = new ArrayList<String>();
		for (List<List<String>> uriLists : mentionsWithURIs.values()) {
			for (List<String> uriList : uriLists) {
				for (String uri : uriList) {
					for (String baseURL2 : baseURIS) {
						String baseURL = baseURL2.trim();
						//only allow uris from the KBs configured in the config.properties (baseURIs)
						if (uri.contains(baseURL)) { 
							if (!alreadyProcessedURI.contains(uri)) {
								retrieveRDF(uri, baseURL, dir);
								alreadyProcessedURI.add(uri);
							}
						}
					}
				}
			}
		}

		// This is the raw RDF graph
		// Load subgraphs for mentions in paragraph
		alreadyProcessedURI = new ArrayList<String>(0);
		Model model = ModelFactory.createDefaultModel();
		for (int l = 0; l < mentionsofParagraph.size(); l++) {
			String mention = mentionsofParagraph.get(l);
			List<List<String>> uriLists = mentionsWithURIs.get(mention);
			if (uriLists != null) {
				for (List<String> uriList : uriLists) {
					for (String uri : uriList) {
						for (String baseURL2 : baseURIS) {
							String baseURL = baseURL2.trim();
							//only allow uris from the KBs configured in the config.properties (baseURIs)
							if (uri.contains(baseURL)) { 
								if (!alreadyProcessedURI.contains(uri)) {
									if (new File(dir + "/file" + replaceNonAlphabeticCharacters(uri)+ ".n3").exists()) {
										model.read(dir + "/file"
												+ replaceNonAlphabeticCharacters(uri) + ".n3");
										alreadyProcessedURI.add(uri);
									}
								}

							}
						}
					}
				}
			}
		}
		// add sameAs information into the model
		Model modelout = injectSameAsInformation(mentionsWithURIs, baseURIS,
				model, dir, crawlSameAs, sameAsproperty);
		model.add(modelout);
		Date end = new Date();
		logger.info("Finished createRDFSubGraphsFromURIs in "
				+ (end.getTime() - start.getTime()) / 60 + "secs");
		return model;

	}
	
	public static String replaceNonAlphabeticCharacters(String in) {
			Pattern p = Pattern.compile("\\s|'|-");
			Matcher m = p.matcher(in);
			String texteRemplace = m.replaceAll("").replaceAll("/", "-").replaceAll(":", "");
			return texteRemplace.toLowerCase();
	}

}
