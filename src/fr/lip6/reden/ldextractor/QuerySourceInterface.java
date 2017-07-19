package fr.lip6.reden.ldextractor;

import java.util.List;

import org.apache.jena.query.Query;
import org.apache.jena.query.ResultSet;

/**
 * Interface that must be implemented for querying a particular source.
 * @author Brando & Frontini
 */
public interface QuerySourceInterface {
		
	/**
	 * Prepare the SPARQL query.
	 * @param domain configuration
	 * @param firstleter, optional filtering for queries
	 * @return the query
	 */
	Query formulateSPARQLQuery(List<TopicExtent> domainParams, String firstleter, 
			String outDictionnaireDir);
		
	/**
	 * Execute query in SPARQL endpoint.
	 * @param query, query to execute
	 * @param sparqlendpoint, URL of the SPARQL endpoint
	 * @param timeout, query timeout
	 * @return the result
	 */
	ResultSet executeQuery(Query query, String sparqlendpoint, String timeout, 
			String outDictionnaireDir, String letter);
	
	/**
	 * Process and write results.
	 * @param res, query results
	 * @param outDictionnaireDir, name of the folder where to write the dictionary file
	 * @param prefixDictionnaireFile, prefix of the dico files
	 * @param optional parameter in the presence of large repos
	 */
	void processResults(ResultSet res, String outDictionnaireDir, String letter, List<TopicExtent> domainParams);
	
}
