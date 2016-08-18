package fr.ign.georeden.algorithms.graph.matching;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.ontology.OntTools;
import org.apache.jena.ontology.OntTools.Path;
import org.apache.jena.ontology.OntTools.PredicatesFilter;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RiotException;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import fr.ign.georeden.algorithms.string.StringComparisonDamLev;
import fr.ign.georeden.kb.ToponymType;
import fr.ign.georeden.utils.RDFUtil;
import fr.ign.georeden.utils.XMLUtil;

/**
 * The  graph matching class.
 */
public class GraphMatchingClean {
	
	/** The logger. */
	private static Logger logger = Logger.getLogger(GraphMatchingClean.class);
	
	/** The tei rdf. */
	private final Model teiRdf; // Graphe RDF généré par transformations (XSLT)
								// successives à partir du fichier XML annoté en
								/** The toponyms TEI. */
								// TEI
	private final Set<Toponym> toponymsTEI; // toponymes contenus dans le graphe
											
											/** The kb source. */
											// teiRDF
	private final Model kbSource; // KB construites à partir de DBpedia, puis
									
									/** The kb subgraph. */
									// complétée
	private final Model kbSubgraph; // Sous graphe de kbSource contenant uniquement les statements du type nord, sud, ouest etc.
	
	/** The subjects of subgraph. */
	private final List<Resource> subjectsOfSubgraph; // Liste des resources qui sont sujets d'un statement de kbSubgraph
	
	/** The resources index APSP. */
	private final Map<Resource, Integer> resourcesIndexAPSP; // index des resources de subjectsOfSubgraph. Utilisé pour accélérer la recherche des plus court chemins
	
	/** The toponyms. */
	private final Set<Toponym> toponyms; // toponyms du TEI avec leurs candidats

	/** The prop fr NS. */
	private static final String PROP_FR_NS = "http://fr.dbpedia.org/property/";
	
	/** The dbo NS. */
	private static final String DBO_NS = "http://dbpedia.org/ontology/";
	private static final String IGN_NS = "http://example.com/namespace/";
	private static final String RLSP_NS = "http://data.ign.fr/def/relationsspatiales#";
	private static final String GEO_NS = "http://www.w3.org/2003/01/geo/wgs84_pos#";
	
	/** The prop nord. */
	private final Property propNord = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "nord");
	
	/** The prop nord est. */
	private final Property propNordEst = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "nordEst");
	
	/** The prop nord ouest. */
	private final Property propNordOuest = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "nordOuest");
	
	/** The prop sud. */
	private final Property propSud = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "sud");
	
	/** The prop sud est. */
	private final Property propSudEst = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "sudEst");
	
	/** The prop sud ouest. */
	private final Property propSudOuest = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "sudOuest");
	
	/** The prop est. */
	private final Property propEst = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "est");
	
	/** The prop ouest. */
	private final Property propOuest = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "ouest");
	private final Property rlspNorthOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "northOf");
	private final Property rlspNorthEastOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "northEastOf");
	private final Property rlspNorthWestOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "northWestOf");
	private final Property rlspSouthOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "southOf");
	private final Property rlspSouthEastOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "southEastOf");
	private final Property rlspSouthWestOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "southWestOf");
	private final Property rlspEastOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "eastOf");
	private final Property rlspWestOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "westOf");
	private final Property propLat = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "latitude");
	private final Property propLong = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "longitude");
	private final Property geoLat = ModelFactory.createDefaultModel().createProperty(GEO_NS + "lat");
	private final Property geoLong = ModelFactory.createDefaultModel().createProperty(GEO_NS + "long");
	
	/** The spatial reference. */
	private final Property spatialReference = ModelFactory.createDefaultModel().createProperty("http://data.ign.fr/def/itineraires#spatialReference");
	private final Property linkSameRoute = ModelFactory.createDefaultModel().createProperty(IGN_NS + "linkSameRoute");
	private final Property linkSameSequence = ModelFactory.createDefaultModel().createProperty(IGN_NS + "linkSameSequence");
	private final Property linkSameBag = ModelFactory.createDefaultModel().createProperty(IGN_NS + "linkSameBag");
	
	private static final String PREFIXES = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
			+ "PREFIX prop-fr: <http://fr.dbpedia.org/property/>" + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
			+ "PREFIX xml: <https://www.w3.org/XML/1998/namespace>"
			+ "PREFIX dbo: <http://dbpedia.org/ontology/>" + "PREFIX ign: <http://example.com/namespace/>"
			+ "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
			+ "PREFIX ign: <http://example.com/namespace/>"
			+ "PREFIX iti:<http://data.ign.fr/def/itineraires#> ";
	
	/** The comparator query solution entry. */
	private final Comparator<QuerySolutionEntry> comparatorQuerySolutionEntry = (a, b) -> {
		Resource r1 = a.getSpatialReference() != null ? a.getSpatialReference() : a.getSpatialReferenceAlt();
		Resource r2 = b.getSpatialReference() != null ? b.getSpatialReference() : b.getSpatialReferenceAlt();
		String subR1 = r1.toString().substring(r1.toString().lastIndexOf('/') + 1);
		String subR2 = r2.toString().substring(r2.toString().lastIndexOf('/') + 1);
		float fR1 = subR1.indexOf('_') != -1 ? Float.parseFloat(subR1.substring(0, subR1.indexOf('_'))) + 0.1f : Float.parseFloat(subR1);
		float fR2 = subR2.indexOf('_') != -1 ? Float.parseFloat(subR2.substring(0, subR2.indexOf('_'))) + 0.1f : Float.parseFloat(subR2);
		return Float.compare(fR1, fR2);
	};

	private final String serializationDirectory;
	private final Map<Resource, DijkstraSP> shortestPaths;
	
	private int maxLengthForSP = 100; // surement à supprimer une fois fichier serialisés intégréés à l'algo
	private final Set<String> rlspCalculous;
	private final Map<String, Map<Resource, Map<Resource, Integer>>> scoresRlspTmp;
	
	/**
	 * Instantiates a new graph matching clean.
	 *
	 * @param teiRdfPath the tei rdf path
	 * @param dbPediaRdfFilePath the db pedia rdf file path
	 * @param numberOfCandidate the number of candidate
	 * @param candidateSelectionThreshold the candidate selection threshold
	 */
	public GraphMatchingClean(String teiRdfPath, String dbPediaRdfFilePath, int numberOfCandidate, float candidateSelectionThreshold, String serializationDirectory) {
		this.serializationDirectory = serializationDirectory;
		logger.info("Chargement du TEI : " + teiRdfPath);
		Document teiSource = XMLUtil.createDocumentFromFile(teiRdfPath);
		// this.teiRdf = RDFUtil.getModel(teiSource); BUG EN RELEASE
		this.teiRdf = ModelFactory.createDefaultModel().read("D:\\temp7.n3");
		this.toponymsTEI = getToponymsFromTei(teiRdf);
		logger.info(toponymsTEI.size() + " toponyms in the TEI RDF graph");

		logger.info("Chargement de la KB : " + dbPediaRdfFilePath);
		this.kbSource = ModelFactory.createDefaultModel().read(dbPediaRdfFilePath);
		logger.info("Création du sous graphe de la KB contenant uniquement les relations spatiales");
		this.kbSubgraph = getSubGraphWithResources(kbSource);
		// completeWithSymetricsRLSP() // OPTIONEL. Augmente le nombre de statement, et facilite le la vérification des chemains, mais le nombre de fichier stockés va exploser
		logger.info("Création de l'index des plus courts chemins.");
		this.subjectsOfSubgraph = kbSubgraph.listSubjects().toList().stream().sorted((a, b) -> a.toString().compareTo(b.toString())).collect(Collectors.toList());
		this.resourcesIndexAPSP = new HashMap<>();
		for (int i = 0; i < subjectsOfSubgraph.size(); i++) {
			resourcesIndexAPSP.put(subjectsOfSubgraph.get(i), i + 1);
		}

		logger.info("Récupérations des candidats de la KB");
		final List<Candidate> candidatesFromKB = getCandidatesFromKB(this.kbSource);

		this.toponyms = getCandidatesSelection(this.toponymsTEI, candidatesFromKB, numberOfCandidate, candidateSelectionThreshold);
		
		this.shortestPaths = new HashMap<>();
		
		this.rlspCalculous = new HashSet<>(); // utilié à revoir
		scoresRlspTmp = new HashMap<>();
	}
	
	/**
	 * Compute.
	 */
	public void compute() {
		deleteUselessAlts(toponyms, teiRdf);
		
		logger.info("Préparation de la création des mini graphes pour chaques séquences");
		List<QuerySolution> querySolutions = getGraphTuples(teiRdf);
		List<QuerySolutionEntry> querySolutionEntries = getQuerySolutionEntries(querySolutions).stream()
				.sorted(comparatorQuerySolutionEntry).collect(Collectors.toList());
		List<Resource> sequences = querySolutionEntries.stream().map(q -> q.getSequence()).distinct()
				.collect(Collectors.toList());

		computeAlgorithm(sequences, querySolutionEntries, teiRdf, kbSubgraph, kbSource, toponymsTEI);
	}
	
	/**
	 * Compute the heart of the algorithm. Main function.
	 *
	 * @param sequences the sequences
	 * @param querySolutionEntries the query solution entries
	 * @param teiRdf the tei rdf
	 * @param kbSubgraph the kb subgraph
	 * @param kbSource the kb source
	 * @param toponymsTEI the toponyms TEI
	 */
	private void computeAlgorithm(List<Resource> sequences, List<QuerySolutionEntry> querySolutionEntries, Model teiRdf, Model kbSubgraph, Model kbSource, Set<Toponym> toponymsTEI) {
		logger.info("V3");
		logger.info("Traitement des mini graphes des séquences");
		int seqCount = 1;
		List<List<Model>> altsBySeq = new ArrayList<>();
		for (Resource sequence : sequences) {
			logger.info("Traitement de la séquence " + seqCount + "/" + sequences.size());
			seqCount++;
			Model currentModel = getModelsFromSequence(querySolutionEntries, sequence);
			currentModel = addRlsp(currentModel, teiRdf);
			List<Model> alts = explodeAlts(currentModel);
			altsBySeq.add(alts);			
		}
		seqCount = 1;
		for (List<Model> alts : altsBySeq.stream()
				.sorted((l1, l2) -> Integer.compare(l1.get(0).listStatements().toList().size(), l2.get(0).listStatements().toList().size())).collect(Collectors.toList())) {
			logger.info("Traitement de la séquence " + seqCount + "/" + altsBySeq.size());
			seqCount++;
			Map<Float, List<IPathMatching>> resultsForCurrentSeq = new HashMap<>();
			logger.info(alts.size() + " mini graphes à traiter pour cette séquence.");
			for (Model miniGraph : alts) {
				List<IPathMatching> path = graphMatching(kbSubgraph, miniGraph, toponymsTEI, 0.4f, 0.4f, 0.2f,
						kbSource);
				resultsForCurrentSeq.put(totalCostPath(path), path);
			}
			if (!resultsForCurrentSeq.isEmpty()) {
				rlspCalculous.stream().sorted().forEach(logger::info);
				rlspCalculous.clear();
				Entry<Float, List<IPathMatching>> bestPath = getBestPath(resultsForCurrentSeq);
				logger.info(bestPath.getKey());
				bestPath.getValue().forEach(logger::info);
			}
		}
	}
	private Entry<Float, List<IPathMatching>> getBestPath(Map<Float, List<IPathMatching>> resultsForCurrentSeq) {
		Float min = resultsForCurrentSeq.keySet().stream().min(Float::compare).get();
		return resultsForCurrentSeq.entrySet().stream().filter(e -> e.getKey() == min).findFirst().get();
	}
	/**
	 * Total cost path. (g)
	 *
	 * @param path
	 *            the path
	 * @return the float
	 */
	private float totalCostPath(List<IPathMatching> path) {
		float result = 0f;
		if (path == null)
			return result;
		for (IPathMatching iPathMatching : path) {
			result += iPathMatching.getCost();
		}
		return result;
	}
	/**
	 * For each rdf:Alt, return the two corresponding models
	 *
	 * @param currentModel
	 *            the current model
	 * @return the list
	 */
	private List<Model> explodeAlts(Model currentModel) {
		Model currentModelClone = cloneModel(currentModel);
		List<Model> results = new ArrayList<>();
		List<Resource> alts = currentModelClone
				.listStatements(null, null,
						currentModelClone.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Alt"))
				.toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		results.add(currentModelClone);
		if (!alts.isEmpty()) {
			for (Resource resourceAlt : alts) {
				Alt alt = currentModelClone.getAlt(resourceAlt);
				// certaines rdf:Alt sont en fait identiques, il faut les
				// fusionner. On vérifie si les places de cet Alt ont déjà été
				// traités.
				// on vérifie uniquement ac la 1ère place
				Resource r_1 = (Resource) currentModelClone
						.getProperty(alt,
								currentModelClone.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"))
						.getObject();
				if (results.stream()
						.anyMatch(m -> m.contains(r_1, linkSameBag) || m.contains(r_1, linkSameRoute)
								|| m.contains(r_1, linkSameSequence) || m.contains(null, linkSameBag, r_1)
								|| m.contains(null, linkSameRoute, r_1) || m.contains(null, linkSameSequence, r_1))) {
					// cet rdf:Alt est un doublon
					List<Statement> statements = results.get(0).listStatements().toList().stream()
							.filter(p -> (p.getSubject().toString().equals(alt.toString())
									|| (p.getObject().isResource()
											&& ((Resource) p.getObject()).toString().equals(alt.toString())))
									&& p.getPredicate().getNameSpace().equals(IGN_NS))
							.collect(Collectors.toList());
					List<Resource> places = new ArrayList<>();
					alt.iterator().toList().stream().forEach(m -> places.add((Resource) m));
					for (Resource place : places) {
						List<Statement> currentStatements = new ArrayList<>();
						for (Statement oldStatement : statements) {
							Statement newStatement;
							if (oldStatement.getSubject().getURI() == alt.getURI()) { // alt
																						// au
																						// début
								newStatement = currentModelClone.createStatement(place, oldStatement.getPredicate(),
										oldStatement.getObject());
							} else { // alt à la fin
								newStatement = currentModelClone.createStatement(oldStatement.getSubject(),
										oldStatement.getPredicate(), place);
							}
							currentStatements.add(newStatement);
						}
						for (Model m : results) {
							if (m.contains(place, linkSameBag) || m.contains(place, linkSameRoute)
									|| m.contains(place, linkSameSequence) || m.contains(null, linkSameBag, place)
									|| m.contains(null, linkSameRoute, place)
									|| m.contains(null, linkSameSequence, place)) {
								for (Statement statement : currentStatements) {
									m.add(statement);
								}
							}
						}
					}
				} else { // cet rdf:Alt n'est pas un doublon
					List<Resource> places = new ArrayList<>();
					alt.iterator().toList().stream().forEach(m -> places.add((Resource) m));
					List<Statement> statements = results.get(0).listStatements().toList().stream()
							.filter(p -> (p.getSubject().toString().equals(alt.toString())
									|| (p.getObject().isResource()
											&& ((Resource) p.getObject()).toString().equals(alt.toString())))
									&& p.getPredicate().getNameSpace().equals(IGN_NS))
							.collect(Collectors.toList());
					List<List<Statement>> statementLists = new ArrayList<>(); // on
																				// est
																				// censé
																				// avoir
																				// 2
																				// list
																				// de
																				// statement
																				// (utant
																				// que
																				// de
																				// places)
					for (Resource place : places) {
						List<Statement> currentStatements = new ArrayList<>();
						for (Statement oldStatement : statements) {
							Statement newStatement;
							if (oldStatement.getSubject().getURI() == alt.getURI()) { // alt
																						// au
																						// début
								newStatement = currentModelClone.createStatement(place, oldStatement.getPredicate(),
										oldStatement.getObject());
							} else { // alt à la fin
								newStatement = currentModelClone.createStatement(oldStatement.getSubject(),
										oldStatement.getPredicate(), place);
							}
							currentStatements.add(newStatement);
						}
						statementLists.add(currentStatements);
					}

					List<List<Model>> resultsList = new ArrayList<>(); // autant
																		// de
																		// list
																		// de
																		// model
																		// que
																		// de
																		// places
																		// ou de
																		// list
																		// de
																		// statement
					for (int i = 0; i < places.size(); i++) {
						resultsList.add(new ArrayList<>(results));
					}
					results.clear();
					for (int i = 0; i < places.size(); i++) {
						List<Model> list = resultsList.get(i);
						List<Statement> currentStatements = statementLists.get(i);
						for (Model model : list) {
							Model newModel = cloneModel(model);
							for (Statement statement : currentStatements) {
								newModel.add(statement);
							}
							results.add(newModel);
						}
					}
				}
				for (Model model : results) {
					deleteResource(model, alt);
				}
			}
		}
		return results;
	}
	
	private Model cloneModel(Model original) {
		Model newModel = ModelFactory.createDefaultModel();
		StmtIterator iterator = original.listStatements();
		while (iterator.hasNext()) {
			Statement statement = (Statement) iterator.next();
			newModel.add(statement);
		}
		original.getNsPrefixMap().keySet()
				.forEach(prefix -> newModel.setNsPrefix(prefix, original.getNsPrefixMap().get(prefix)));
		return newModel;
	}
	
	/**
	 * Adds the rlsp to the model of the sequence.
	 *
	 * @param sequenceModel
	 *            the sequence model
	 * @param teiModel
	 *            the tei model
	 * @return the model
	 */
	private Model addRlsp(Model sequenceModel, Model teiModel) {
		List<Resource> resourcesFromCurrentSeq = sequenceModel.listSubjects().toList();
		resourcesFromCurrentSeq.addAll(sequenceModel.listObjects().toList().stream().filter(o -> o.isResource())
				.map(p -> (Resource) p).collect(Collectors.toList()));
		List<Statement> statements = new ArrayList<>();
		statements.addAll(teiModel.listStatements(null, rlspEastOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspNorthEastOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspNorthOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspNorthWestOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspSouthEastOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspSouthOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspSouthWestOf, (RDFNode) null).toList());
		statements.addAll(teiModel.listStatements(null, rlspWestOf, (RDFNode) null).toList());
		statements = statements.stream()
				.filter(p -> resourcesFromCurrentSeq.stream().anyMatch(rseq -> areResourcesEqual(p.getSubject(), rseq))
						&& resourcesFromCurrentSeq.stream()
								.anyMatch(rseq -> areResourcesEqual((Resource) p.getObject(), rseq)))
				.collect(Collectors.toList());
		if (!statements.isEmpty()) {
			sequenceModel.add(statements);
		}
		return sequenceModel;
	}
	
	private Model getModelsFromSequence(List<QuerySolutionEntry> allQuerySolutionEntries, Resource currentSequence) {
		List<QuerySolutionEntry> querySolutionEntries = allQuerySolutionEntries.stream()
				.filter(q -> areResourcesEqual(q.getSequence(), currentSequence)).sorted(comparatorQuerySolutionEntry)
				.collect(Collectors.toList());
		Model initialModel = ModelFactory.createDefaultModel();
		initialModel.setNsPrefix("ign", "http://example.com/namespace/");
		initialModel = managedBags(initialModel, querySolutionEntries);
		initialModel = managedRoutes(initialModel, querySolutionEntries);
		initialModel = managedSequences(initialModel, querySolutionEntries);
		return initialModel;
	}
	private Model managedBags(Model initialModel, List<QuerySolutionEntry> querySolutionEntries) {
		for (int i = 0; i < querySolutionEntries.size() - 1; i++) {
			QuerySolutionEntry previous = null;
			if (i > 0) {
				previous = querySolutionEntries.get(i - 1);
			}
			QuerySolutionEntry current = querySolutionEntries.get(i);
			QuerySolutionEntry currentAlt = null;
			if (current.getSpatialReferenceAlt() != null) {
				i++;
				currentAlt = querySolutionEntries.get(i);
			}
			int j = i + 1;
			if (i + 1 >= querySolutionEntries.size())
				break;
			QuerySolutionEntry next = querySolutionEntries.get(j);
			QuerySolutionEntry nextAlt = null;
			if (next.getSpatialReferenceAlt() != null && j < querySolutionEntries.size() - 1) {
				j++;
				nextAlt = querySolutionEntries.get(j);
			}
			if ((previous == null || current.getBag() != previous.getBag()) && current.getBag() == next.getBag()) {
				// current est le 1er élément d'un bag où il y a plusieurs
				// éléments
				Resource r1;
				if (currentAlt != null) { // current est une Alt
					Alt alt = initialModel.createAlt();
					alt.add(current.getSpatialReferenceAlt());
					alt.add(currentAlt.getSpatialReferenceAlt());
					r1 = alt;
				} else { // current n'est pas une alt
					r1 = current.getSpatialReference();
				}
				while (current.getBag() == next.getBag()) {
					Resource r2;
					if (nextAlt != null) { // next est une Alt
						Alt alt = initialModel.createAlt();
						alt.add(next.getSpatialReferenceAlt());
						alt.add(nextAlt.getSpatialReferenceAlt());
						r2 = alt;
					} else { // next n'est pas une alt
						r2 = next.getSpatialReference();
					}
					initialModel.add(initialModel.createStatement(r1, linkSameBag, r2));
					if (j + 1 < querySolutionEntries.size()) {
						j++;
						next = querySolutionEntries.get(j);
						nextAlt = null;
						if (next.getSpatialReferenceAlt() != null) {
							nextAlt = querySolutionEntries.get(j + 1);
						}
					} else {
						break;
					}
				}
			}
		}
		return initialModel;
	}

	private Model managedRoutes(Model initialModel, List<QuerySolutionEntry> querySolutionEntries) {
		for (int i = 0; i < querySolutionEntries.size() - 1; i++) {
			QuerySolutionEntry current = querySolutionEntries.get(i);
			QuerySolutionEntry currentAlt = null;
			if (current.getSpatialReferenceAlt() != null) {
				i++;
				currentAlt = querySolutionEntries.get(i);
			}
			if (i + 1 >= querySolutionEntries.size())
				break;
			// on récupère les éléments du bag suivant sur la même route
			Optional<Resource> optionalBag = querySolutionEntries.subList(i + 1, querySolutionEntries.size()).stream()
					.filter(p -> p.getRoute() == current.getRoute() && p.getBag() != current.getBag())
					.map(p -> p.getBag()).distinct().findFirst();
			if (optionalBag.isPresent()) {
				List<QuerySolutionEntry> bagElements = querySolutionEntries.stream()
						.filter(p -> p.getBag() == optionalBag.get()).sorted(comparatorQuerySolutionEntry)
						.collect(Collectors.toList());
				Resource r1;
				if (currentAlt != null && currentAlt.getSpatialReferenceAlt() != null && current.getSpatialReferenceAlt() != null) { // current est une Alt
					Alt alt = initialModel.createAlt();
					alt.add(current.getSpatialReferenceAlt());
					alt.add(currentAlt.getSpatialReferenceAlt());
					r1 = alt;
				} else { // current n'est pas une alt
					r1 = current.getSpatialReference();
				}
				for (int j = 0; j < bagElements.size(); j++) {
					QuerySolutionEntry next = bagElements.get(j);
					QuerySolutionEntry nextAlt = null;
					if (next.getSpatialReferenceAlt() != null && j < bagElements.size() - 1) {
						j++;
						nextAlt = bagElements.get(j);
					}
					Resource r2;
					if (nextAlt != null) { // next est une Alt
						Alt alt = initialModel.createAlt();
						alt.add(next.getSpatialReferenceAlt());
						alt.add(nextAlt.getSpatialReferenceAlt());
						r2 = alt;
					} else { // next n'est pas une alt
						r2 = next.getSpatialReference();
					}
					if (r1 != null && r2 != null)
						initialModel.add(initialModel.createStatement(r1, linkSameRoute, r2));
				}
			}
		}
		return initialModel;
	}

	private Model managedSequences(Model initialModel, List<QuerySolutionEntry> querySolutionEntries) {
		for (int i = 0; i < querySolutionEntries.size() - 1; i++) {
			QuerySolutionEntry current = querySolutionEntries.get(i);
			if (current.getSpatialReferenceAlt() != null) {
				i++;
			}
			if (i + 1 >= querySolutionEntries.size())
				break;
			// on vérifie que le bag est le dernier de la route
			QuerySolutionEntry next = querySolutionEntries.get(i + 1);
			if (next.getRoute() != current.getRoute()) {
				Resource lastBagOfCurrentRoute = current.getBag();
				Resource firstBagOfLastRoute = next.getBag();
				List<QuerySolutionEntry> lastBagElements = querySolutionEntries.stream()
						.filter(p -> p.getBag() == lastBagOfCurrentRoute).sorted(comparatorQuerySolutionEntry)
						.collect(Collectors.toList());
				List<QuerySolutionEntry> firstBagElements = querySolutionEntries.stream()
						.filter(p -> p.getBag() == firstBagOfLastRoute).sorted(comparatorQuerySolutionEntry)
						.collect(Collectors.toList());
				for (int j = 0; j < lastBagElements.size(); j++) {
					current = lastBagElements.get(j);
					QuerySolutionEntry currentAlt = null;
					if (current.getSpatialReferenceAlt() != null) {
						j++;
						currentAlt = lastBagElements.get(j);
					}
					Resource r1;
					if (currentAlt != null) { // current est une Alt
						Alt alt = initialModel.createAlt();
						alt.add(current.getSpatialReferenceAlt());
						alt.add(currentAlt.getSpatialReferenceAlt());
						r1 = alt;
					} else { // current n'est pas une alt
						r1 = current.getSpatialReference();
					}
					for (int k = 0; k < firstBagElements.size(); k++) {
						next = firstBagElements.get(k);
						QuerySolutionEntry nextAlt = null;
						if (next.getSpatialReferenceAlt() != null && k < firstBagElements.size() - 1) {
							k++;
							nextAlt = firstBagElements.get(k);
						}
						Resource r2;
						if (nextAlt != null) { // next est une Alt
							Alt alt = initialModel.createAlt();
							alt.add(next.getSpatialReferenceAlt());
							alt.add(nextAlt.getSpatialReferenceAlt());
							r2 = alt;
						} else { // next n'est pas une alt
							r2 = next.getSpatialReference();
						}
						if (r1 != null && r2 != null)
							initialModel.add(initialModel.createStatement(r1, linkSameSequence, r2));
					}
				}
			}
		}
		return initialModel;
	}
	private boolean areResourcesEqual(Resource r1, Resource r2) {
		if (r1.isAnon() && r2.isAnon()) {
			return r1 == r2;
		}
		String uri1 = r1.getURI();
		String uri2 = r2.getURI();
		return uri1.equalsIgnoreCase(uri2);
	}
	
	/**
	 * Transform the QuerySolutions from getGraphTuples to usable objects (QuerySolutionEntry).
	 *
	 * @param querySolutions the query solutions
	 * @return the query solution entries
	 */
	private List<QuerySolutionEntry> getQuerySolutionEntries(List<QuerySolution> querySolutions) {
		List<QuerySolutionEntry> querySolutionEntries = new ArrayList<>();
		for (QuerySolution querySolution : querySolutions) {
			Resource sequence = (Resource) querySolution.get("sequence");
			Resource route = (Resource) querySolution.get("route");
			Resource bag = (Resource) querySolution.get("bag");
			Resource waypoint = (Resource) querySolution.get("waypoint");
			Resource spatialReferenceResource = (Resource) querySolution.get("spatialReference");
			Resource spatialReferenceAlt = (Resource) querySolution.get("spatialReferenceAlt");
			RDFNode nodeId = querySolution.get("id");
			Integer id = -1;
			if (nodeId.isLiteral()) {
				Literal literalId = nodeId.asLiteral();
				String stringId = literalId.getLexicalForm();
				id = Integer.parseInt(stringId);
			}
			querySolutionEntries.add(
					new QuerySolutionEntry(sequence, route, bag, waypoint, spatialReferenceResource, spatialReferenceAlt, id));
		}
		return querySolutionEntries;
	}
	
	/**
	 * Query the TEI graph for sequence, route, bag, waypoint, spatial reference and id.
	 *
	 * @param teiModel the tei model
	 * @return the graph tuples
	 */
	private List<QuerySolution> getGraphTuples(Model teiModel) {
		String query = PREFIXES
				+ "SELECT ?sequence ?route ?bag ?waypoint ?spatialReference ?spatialReferenceAlt ?id WHERE { "
				+ "     ?sequence rdf:type rdf:Seq . " + "     ?sequence ?pSeq ?route . "
				+ "     ?route iti:waypoints ?waypoints . " + "     ?waypoints rdf:rest*/rdf:first ?bag . "
				+ "    ?bag ?pBag ?waypoint . " + "    OPTIONAL { "
				+ "        ?waypoint iti:spatialReference ?spatialReference . "
				+ "        ?spatialReference <http://example.com/namespace/id> ?id . " + "    }    " + "    OPTIONAL { "
				+ "        ?waypoint rdf:type rdf:Alt .    " + "        ?waypoint ?pWaypoint ?waypointBis .    "
				+ "        ?waypointBis iti:spatialReference ?spatialReferenceAlt .  "
				+ "        ?spatialReferenceAlt <http://example.com/namespace/id> ?id . " + "    }    "
				+ "     FILTER (?pSeq != rdf:type && ?pBag != rdf:type && ?pBag != rdf:first) . "
				+ "} ORDER BY ?sequence ?route ?bag ?waypoint ?id";
		List<QuerySolution> querySolutions = new ArrayList<>();
		try {
			querySolutions.addAll(RDFUtil.getQuerySelectResults(teiModel, query));
		} catch (QueryParseException | HttpHostConnectException | RiotException | MalformedURLException
				| HttpException e) {
			logger.info(e);
		}
		return querySolutions;
	}
	
	/**
	 * Delete useless alts.
	 *
	 * @param result the result
	 * @param teiRdf the tei rdf
	 */
	private void deleteUselessAlts(Set<Toponym> result, Model teiRdf) {
		// certaines Alt on une de leur possibilité qui n'a pas de candidat. Elle seront tjrs préférées aux possibilités avec candidats.
				// Il faut donc les supprimer de Set<Toponym> result et de Model teiRdf
				for (Entry<Integer, List<Toponym>> toponymEntry : result.stream().collect(Collectors.groupingBy((Toponym t) -> t.getXmlId())).entrySet().stream()
					.filter(e -> e.getValue().size() > 1 && e.getValue().stream().anyMatch(p -> p.getScoreCriterionToponymCandidate().isEmpty())
							 && e.getValue().stream().anyMatch(p -> !p.getScoreCriterionToponymCandidate().isEmpty())).collect(Collectors.toList())) {
					Toponym toponymToRemove = toponymEntry.getValue().stream().filter(f -> f.getScoreCriterionToponymCandidate().isEmpty()).findFirst().get();
					Toponym toponymToKeep = toponymEntry.getValue().stream().filter(f -> !f.getScoreCriterionToponymCandidate().isEmpty()).findFirst().get();
					List<Statement> statementsFromNodeToKeep = teiRdf.listStatements(null, spatialReference, toponymToKeep.getResource()).toList();
					Resource blankNodeOfNodeToKeep = statementsFromNodeToKeep.get(0).getSubject();
					if (toponymToRemove.getScoreCriterionToponymCandidate().isEmpty() && result.remove(toponymToRemove)) {
						Optional<Statement> sOpt = teiRdf.listStatements(null, null, toponymToRemove.getResource()).toList().stream().findFirst();
						if (sOpt.isPresent()) {
							Statement s = sOpt.get();
							Optional<Statement> altStatement = teiRdf.listStatements(null, null, s.getSubject()).toList().stream().findFirst();
							if (altStatement.isPresent()) {
								Alt alt = teiRdf.getAlt(altStatement.get().getSubject());
								List<Statement> statementsToRemove = teiRdf.listStatements(null, null, alt).toList();
								List<Statement> statementsToAdd = new ArrayList<>();
								for (Statement statement : statementsToRemove) {
									statementsToAdd.add(teiRdf.createStatement(statement.getSubject(), statement.getPredicate(), blankNodeOfNodeToKeep));
								}
								deleteResource(teiRdf, toponymToRemove.getResource());
								deleteResource(teiRdf, alt);
								teiRdf.add(statementsToAdd);
								logger.info("1 : " + toponymToRemove.getResource() + " / " + toponymToKeep.getResource());
							} else {
								logger.info("2 : " + toponymToRemove.getResource() + " / " + toponymToKeep.getResource());
							}
						}
					}
				}
	}
	
	/**
	 * Delete the resource from the model.
	 *
	 * @param model the model
	 * @param resource the resource
	 */
	private void deleteResource(Model model, Resource resource) {
		// remove statements where resource is subject
		model.removeAll(resource, null, (RDFNode) null);
		// remove statements where resource is object
		model.removeAll(null, null, resource);
	}
	
	/**
	 * Gets the candidates selection.
	 *
	 * @param toponymsTEI            the toponyms tei
	 * @param candidatesFromKB            the candidates from kb
	 * @param numberOfCandidate the number of candidate
	 * @param threshold            the threshold
	 * @return the candidates selection
	 */
	private Set<Toponym> getCandidatesSelection(Set<Toponym> toponymsTEI, List<Candidate> candidatesFromKB,
			Integer numberOfCandidate, float threshold) {
		logger.info("Sélection des candidats (nombre de candidats : " + numberOfCandidate + ")");
		List<Candidate> candidatesFromKBCleared = candidatesFromKB.stream()
				.filter(c -> c != null && c.getTypes() != null && (c.getName() != null || c.getLabel() != null))
				.collect(Collectors.toList());
		

		Map<String, List<Candidate>> candidatesByType = new HashMap<>();
		for (Candidate candidate2 : candidatesFromKBCleared) {
			for (String type : candidate2.getTypes()) {
				List<Candidate> candidates;
				if (candidatesByType.containsKey(type)) {
					candidates = candidatesByType.get(type);

				} else {
					candidates = new ArrayList<>();
				}
				candidates.add(candidate2);
				candidatesByType.put(type, candidates);
			}
		}
		Set<Toponym> result = Collections.synchronizedSet(new HashSet<>());
		final AtomicInteger count = new AtomicInteger();
		// calculs des scores pour chaque candidat de chaque toponyme
		// aggrégation des toponymes sur leur labal
		Map<String, List<Toponym>> toponymsByLabel = toponymsTEI.stream()
				.collect(Collectors.groupingBy((Toponym s) -> s.getName()));
		final int total = toponymsByLabel.size();
		toponymsByLabel.entrySet().parallelStream().forEach(toponymsWithLabel -> {
			// toponymes de l'entry aggrégés par type
			Map<ToponymType, List<Toponym>> toponymsByType = toponymsWithLabel.getValue().stream()
					.collect(Collectors.groupingBy((Toponym s) -> s.getType()));
			computeCandidates(toponymsWithLabel, toponymsByType.entrySet(), candidatesByType);
			toponymsWithLabel.getValue().stream().forEach(toponym -> {
				toponym.clearAndAddAllScoreCriterionToponymCandidate(
						toponym.getScoreCriterionToponymCandidate().stream().filter(s -> s != null)
								.sorted(Comparator.comparing(CriterionToponymCandidate::getValue).reversed())
								.filter(t -> t.getValue() >= threshold)
								.limit(Math.min(numberOfCandidate, toponym.getScoreCriterionToponymCandidate().size()))
								.collect(Collectors.toList()));
				result.add(toponym);
			});
			logger.info((count.getAndIncrement() + 1) + " / " + total);
		});
		return result;
	}
	
	/**
	 * Compute candidates.
	 *
	 * @param toponymsWithLabel the toponyms with label
	 * @param entries the entries
	 * @param candidatesByType the candidates by type
	 */
	private void computeCandidates(Entry<String, List<Toponym>> toponymsWithLabel,
			Set<Entry<ToponymType, List<Toponym>>> entries, Map<String, List<Candidate>> candidatesByType) {
		Criterion criterion = Criterion.scoreText;
		StringComparisonDamLev sc = new StringComparisonDamLev();
		Map<String, Float> scoreByLabel = new ConcurrentHashMap<>(); // utilisés
																		// pour
																		// stocker
																		// les
																		// scores
																		// déjà
																		// calculés
		for (Entry<ToponymType, List<Toponym>> toponymsTyped : entries) {
			String keyType = getTEITypeToKBType(toponymsTyped.getKey().toString());
			List<Candidate> candidatesToCheck = candidatesByType.get(keyType);
			if (candidatesToCheck != null && !candidatesToCheck.isEmpty()) {
				candidatesToCheck.parallelStream().forEach(candidate -> {
					float score = 0f;
					if (candidate.getName() != null && scoreByLabel.containsKey(candidate.getName())) {
						score = scoreByLabel.get(candidate.getName());
					} else if (candidate.getLabel() != null && scoreByLabel.containsKey(candidate.getLabel())) {
						score = scoreByLabel.get(candidate.getLabel());
					} else {
						float score1 = sc.computeSimilarity(toponymsWithLabel.getKey(), candidate.getName());
						float score2 = sc.computeSimilarity(toponymsWithLabel.getKey(), candidate.getLabel());
						if (score1 > score2 && candidate.getName() != null) {
							score = score1;
							scoreByLabel.put(candidate.getName(), score1);
						} else if (candidate.getLabel() != null) {
							score = score2;
							scoreByLabel.put(candidate.getLabel(), score2);
						}
					}
					for (Toponym toponym : toponymsTyped.getValue()) {
						toponym.addScoreCriterionToponymCandidate(
								new CriterionToponymCandidate(toponym, candidate, score, criterion));
					}
				});
			}
		}
	}
	
	/**
	 * Gets the KB type from the TEI type.
	 *
	 * @param type the type
	 * @return the TEI type to KB type
	 */
	private String getTEITypeToKBType(String type) {
		String typeToponym = type.substring(type.lastIndexOf(':') + 1);
		return DBO_NS + typeToponym;
	}
	
	/**
	 * All pair shortest path pre processing. (very long function)
	 */
	public void allPairShortestPathPreProcessing() {
		List<Integer> resourcesIndexProcessed = DijkstraSP.getResourcesIndexProcessed(serializationDirectory);
		logger.info("Resources déjà traitées : " + resourcesIndexProcessed.size());
		DijkstraSP.computeAndSerializeAllPairShortestPath(subjectsOfSubgraph, resourcesIndexProcessed, kbSubgraph, serializationDirectory);
	}

	/**
	 * Gets the toponyms from tei.
	 *
	 * @param teiRdf the tei rdf
	 * @return the toponyms from tei
	 */
	private Set<Toponym> getToponymsFromTei(Model teiRdf) {
		Set<Toponym> results = new HashSet<>();
		logger.info("Récupération des toponymes du TEI");
		List<QuerySolution> qSolutionsTEI = new ArrayList<>();
		try {
			qSolutionsTEI.addAll(RDFUtil.getQuerySelectResults(teiRdf,
					PREFIXES + "" + "SELECT DISTINCT * WHERE {"
							+ "  ?s rdfs:label ?label ." + "  ?s ign:id ?id ." + "  ?s rdf:type ?type ." + "}"));
		} catch (QueryParseException | HttpHostConnectException | RiotException | MalformedURLException
				| HttpException e) {
			logger.error(e);
		}
		for (QuerySolution querySolution : qSolutionsTEI) {
			String label = RDFUtil.getURIOrLexicalForm(querySolution, "label");
			String type = RDFUtil.getURIOrLexicalForm(querySolution, "type");
			Resource resource = teiRdf.createResource(RDFUtil.getURIOrLexicalForm(querySolution, "s"));
			Integer id = Integer.parseInt(RDFUtil.getURIOrLexicalForm(querySolution, "id"));
			results.add(new Toponym(resource, id, label, ToponymType.where(type)));
		}
		return results;
	}

	/**
	 * Gets the sub graph with only the resources (no literals) linked by
	 * prop-fr.nord, prop-fr:sud, etc.
	 *
	 * @param kbSource
	 *            the kb source
	 * @return the sub graph with resources
	 */
	private Model getSubGraphWithResources(Model kbSource) {
		logger.info("Récupération du sous graphe de la base de connaissance.");
		Model model = null;
		try {
			model = RDFUtil.getQueryConstruct(kbSource, PREFIXES
					+ "CONSTRUCT {?s ?p ?o} WHERE {" + "  ?s ?p ?o."
					+ "  FILTER((?p=prop-fr:nord||?p=prop-fr:nordEst||?p=prop-fr:nordOuest||?p=prop-fr:sud"
					+ "  ||?p=prop-fr:sudEst||?p=prop-fr:sudOuest||?p=prop-fr:est||?p=prop-fr:ouest) && !isLiteral(?o))."
					+ "}", null);
			ResIterator iter = model.listSubjects();
			Set<Resource> resources = new HashSet<>();
			while (iter.hasNext()) {
				Resource r = iter.nextResource();
				resources.add(r);
			}
			NodeIterator nIter = model.listObjects();
			while (nIter.hasNext()) {
				RDFNode rdfNode = nIter.nextNode();
				if (rdfNode.isResource()) {
					Resource r = (Resource) rdfNode;
					if (!r.isAnon()) {
						resources.add(r);
					}
				}
			}
		} catch (QueryParseException | HttpHostConnectException | RiotException | MalformedURLException
				| HttpException e) {
			logger.error(e);
		}
		return model;
	}

	/**
	 * Complete kbSubgraph with symetrics RLSP. eg if a nordEst b then b sudOuest a
	 */
	private void completeWithSymetricsRLSP() {
		for (Statement s : kbSubgraph.listStatements().toList()) {
			Property p = s.getPredicate();
			Property newProperty;
			if (p.getURI().equalsIgnoreCase(propNord.getURI())) {
				newProperty = propSud;
			} else if (p.getURI().equalsIgnoreCase(propNordEst.getURI())) {
				newProperty = propSudOuest;
			} else if (p.getURI().equalsIgnoreCase(propNordOuest.getURI())) {
				newProperty = propSudEst;
			} else if (p.getURI().equalsIgnoreCase(propSud.getURI())) {
				newProperty = propNord;
			} else if (p.getURI().equalsIgnoreCase(propSudEst.getURI())) {
				newProperty = propNordOuest;
			} else if (p.getURI().equalsIgnoreCase(propSudOuest.getURI())) {
				newProperty = propNordEst;
			} else if (p.getURI().equalsIgnoreCase(propEst.getURI())) {
				newProperty = propOuest;
			} else if (p.getURI().equalsIgnoreCase(propOuest.getURI())) {
				newProperty = propEst;
			} else {
				newProperty = null;
			}
			if (newProperty != null)
				kbSubgraph.add(kbSubgraph.createStatement((Resource) s.getObject(), newProperty, s.getSubject()));
		}
	}
	
	/**
	 * Gets the candidates from kb.
	 *
	 * @param kbSource the kb source
	 * @return the candidates from kb
	 */
	private List<Candidate> getCandidatesFromKB(Model kbSource) {
		List<QuerySolution> qSolutionsKB = new ArrayList<>();
		List<Candidate> result = new ArrayList<>();

		final Model kbSourceFinal = kbSource;
		try {
			qSolutionsKB.addAll(RDFUtil.getQuerySelectResults(kbSourceFinal,
					PREFIXES + "" + "SELECT DISTINCT * WHERE {"
							+ "  OPTIONAL { ?s a ?type . } "
							+ "  OPTIONAL { ?s foaf:name ?name . }" + "  OPTIONAL { ?s rdfs:label ?label . }" 
							+ " FILTER (STRSTARTS(STR(?type), 'http://dbpedia.org/ontology/')) . "
							+ "}"));
		} catch (QueryParseException | HttpHostConnectException | MalformedURLException | HttpException e) {
			logger.error(e);
		}
		qSolutionsKB.parallelStream().forEach(querySolution -> {
			Resource candidateResource = kbSource.createResource(RDFUtil.getURIOrLexicalForm(querySolution, "s"));
			String candidateLabel = RDFUtil.getURIOrLexicalForm(querySolution, "label");
			String candidateName = RDFUtil.getURIOrLexicalForm(querySolution, "name");
			if (candidateResource != null && (candidateLabel != null || candidateName != null)) {
				String candidateType = RDFUtil.getURIOrLexicalForm(querySolution, "type");
				Set<String> types = new HashSet<>();
				if (candidateType == null || candidateType.isEmpty()) {
					candidateType = "http://dbpedia.org/ontology/Place";
				}
				types.add(candidateType);
				result.add(new Candidate(candidateResource, candidateLabel, candidateName, types));
			}
		});
		Map<Resource, List<Candidate>> candidatesByResource = result.stream().filter(p -> p != null && p.getResource() != null)
				.collect(Collectors.groupingBy((Candidate c) -> c.getResource()));
		result.clear();
		for (Entry<Resource, List<Candidate>> entry : candidatesByResource.entrySet()) {
			Set<String> types = new HashSet<>();
			Candidate oldC = null;
			for (Candidate c : entry.getValue()) {
				types.addAll(c.getTypes());
				oldC = c;
			}
			if (oldC != null) {
				Candidate newC = new Candidate(oldC.getResource(), oldC.getLabel(), oldC.getName(), types);
				result.add(newC);
			}
		}
		logger.info(result.size() + " candidats potentiels.");
		return result;
	}
	
	/**
	 * Test.
	 */
	public void test() {
		Resource lille = kbSubgraph.getResource("http://fr.dbpedia.org/resource/Lille");
		Resource marseille = kbSubgraph.getResource("http://fr.dbpedia.org/resource/Marseille");
		int lilleIndex = resourcesIndexAPSP.get(lille);
		DijkstraSP lilleSP = DijkstraSP.deserialize(serializationDirectory + lilleIndex);
		logger.info(lilleSP.getNode(kbSource));
		logger.info(lilleSP.hasPathTo(marseille));
		if (lilleSP.hasPathTo(marseille)) {
			Iterator<Statement> iterator = lilleSP.pathTo(marseille, kbSource).iterator();
			int pathLength = 0;
			while (iterator.hasNext()) {
				Statement statement = (Statement) iterator.next();
				logger.info(statement);
				pathLength++;
			}
			logger.info(pathLength);
		}
	}
	
	
	
	
	
	private List<IPathMatching> graphMatching(Model kbSubgraph, Model miniGraph, Set<Toponym> toponymsTEI,
			float labelWeight, float rlspWeight, float linkWeight, Model completeKB) {
		// on récupère les noeuds du mini graphe
		Set<Resource> sourceNodes = new HashSet<>(miniGraph.listSubjects().toList());
		sourceNodes.addAll(miniGraph.listObjects().toList().stream().filter(o -> o.isResource()).map(m -> (Resource) m)
				.collect(Collectors.toList()));
		// On ne garde que les toponyms présents dans ce mini graphe
		Set<Toponym> toponymsSeq = new HashSet<>(toponymsTEI.stream()
				.filter(p -> sourceNodes.stream().anyMatch(res -> areResourcesEqual(p.getResource(), res)))
				.collect(Collectors.toList()));
		List<Resource> usedSourceNodes = new ArrayList<>();
		// on récupère les noeuds de dbpedia à traiter
		Set<Resource> targetNodes = new HashSet<>(toponymsSeq.stream()
				.map(m -> m.getScoreCriterionToponymCandidate().stream().map(l -> l.getCandidate().getResource())
						.collect(Collectors.toList()))
				.flatMap(l -> l.stream()).distinct().collect(Collectors.toList()));
		// on récupère les shortests paths de la séquence qui nous intéresse
		for (Resource candidate : targetNodes) {
			if (resourcesIndexAPSP.containsKey(candidate) && !shortestPaths.containsKey(candidate)) {
				int index = resourcesIndexAPSP.get(candidate);
				DijkstraSP sp = DijkstraSP.deserialize(serializationDirectory + index);
				if (sp != null) {
					shortestPaths.put(candidate, sp);
				}
			}
		}
		// liste des chemins à traiter
		List<List<IPathMatching>> open = new ArrayList<>(); 			
		
		
		logger.info("Sélection du premier noeud à traiter");
		// sélection du premier noeud à traiter
		Toponym firstToponym = getNextNodeToProcess(usedSourceNodes, toponymsSeq, miniGraph);
		Resource firstSourceNode = firstToponym.getResource();
		List<IPathMatching> pathDeletion = new ArrayList<>();
		// le topo doit avoir 1 en cout de suppression. 
		// Si on mettais 0 en cas d'absence de candidats et s'il est dans une alt, l'autre alt ne sera jamais choisie
		float deletionCostFirstToponym = 1f;
		for (CriterionToponymCandidate candidateCriterion : firstToponym.getScoreCriterionToponymCandidate()) {
			Resource targetNode = candidateCriterion.getCandidate().getResource();
			float cost = getSubstitutionCost(firstToponym, candidateCriterion, labelWeight, rlspWeight,
					linkWeight, toponymsSeq, miniGraph, kbSubgraph, completeKB);
			List<IPathMatching> path = new ArrayList<>();
			path.add(new Substitution(firstSourceNode, targetNode, cost));
			open.add(path);
		}
		pathDeletion.add(new Deletion(firstSourceNode, deletionCostFirstToponym));
		open.add(pathDeletion);
		List<IPathMatching> pMin = null;
		logger.info("Noeud sélectionné : " + firstSourceNode);
		while (true) {
			pMin = getMinCostPath(open, kbSubgraph, miniGraph, toponymsSeq, labelWeight, rlspWeight, linkWeight,
					sourceNodes, targetNodes, completeKB);
			updateToponyms(pMin, toponymsSeq);
			if (isCompletePath(pMin, sourceNodes, targetNodes)) {
				break;
			} else {
				open.clear(); // on vide la liste, car le chemin pMin est
								// forcément le meilleur
				// resources du graphe cible non utilisées dans ce chemin
				Set<Resource> unusedResourcesFromTarget = getTargetUnusedResources(pMin, targetNodes);
				if (pMin.size() < sourceNodes.size()) {
					Toponym currentToponym = getNextNodeToProcess(usedSourceNodes, toponymsSeq, miniGraph);
					Resource currentSourceNode = currentToponym.getResource();
					float deletionCost = 1f;
					for (CriterionToponymCandidate candidateCriterion : currentToponym
							.getScoreCriterionToponymCandidate()) {
						Resource resourceFromTarget = candidateCriterion.getCandidate().getResource();
						List<IPathMatching> newPath = new ArrayList<>(pMin);
						float cost = getSubstitutionCost(currentToponym, candidateCriterion, labelWeight,
								rlspWeight, linkWeight, toponymsSeq, miniGraph, kbSubgraph, completeKB);
						newPath.add(new Substitution(currentSourceNode, resourceFromTarget, cost));
						open.add(newPath);
					}
					List<IPathMatching> newPathDeletion = new ArrayList<>(pMin);
					newPathDeletion.add(new Deletion(currentSourceNode, deletionCost));
					open.add(newPathDeletion);
				} else {
					for (Resource resource : unusedResourcesFromTarget) {
						List<IPathMatching> newPath = new ArrayList<>(pMin);
						newPath.add(new Insertion(resource, getInsertionCost(resource)));
						open.add(newPath);
					}
				}
			}
		}

		return pMin;
	}
	
	/**
	 * Gets the next node of the source graph to process.
	 *
	 * @param open
	 *            the open
	 * @param toponymsSeq
	 *            the toponyms seq
	 * @return the next node to process
	 */
	private Toponym getNextNodeToProcess(List<Resource> usedSourceNodes, Set<Toponym> toponymsSeq, Model miniGraph) {
		List<Statement> statements = miniGraph.listStatements().toList();
		// on veut choisir le noeud qui a le plus de liens car cela augmente les chances de trouver le bon candidat
		Map<RDFNode, Integer> nbLinks = new HashMap<>();
		for (Statement statement : statements) {
			Resource s = statement.getSubject();
			RDFNode o = statement.getObject();
			if (nbLinks.containsKey(s)) {
				Integer i = nbLinks.get(s);
				i++;
				nbLinks.put(s, i);
			} else nbLinks.put(s, 1);
			if (nbLinks.containsKey(o)) {
				Integer i = nbLinks.get(o);
				i++;
				nbLinks.put(o, i);
			} else nbLinks.put(o, 1);
		}
		RDFNode n = nbLinks.entrySet().stream().filter(p -> !usedSourceNodes.contains(p.getKey())).sorted((a, b) ->
				Integer.compare(b.getValue(), a.getValue())).limit(1).findFirst().get().getKey();
		for (Toponym toponym : toponymsSeq) {
			Resource resourceToCheck = toponym.getResource();
			if (areResourcesEqual(resourceToCheck, (Resource)n)) {
				usedSourceNodes.add(resourceToCheck);
				return toponym;
			}
		}
		return null;
	}
	
	private float getSubstitutionCost(Toponym nodeToRemove, CriterionToponymCandidate candidateCriterion,
			float labelWeight, float rlspWeight, float linkWeight, Set<Toponym> toponymsTEI, Model teiRdf,
			Model kbWithInterestingProperties, Model completeKB) {
		float scoreLabel = 1 - candidateCriterion.getValue();
		float scoreLink = scoreLink(nodeToRemove, candidateCriterion, teiRdf, toponymsTEI);
		float scoreRlsp = 1.0f;//scoreRlsp(nodeToRemove, candidateCriterion, teiRdf, toponymsTEI, kbWithInterestingProperties, completeKB);
		rlspCalculous.add(nodeToRemove.getResource() + " (" + nodeToRemove.getName() + ")" + " -> " + candidateCriterion.getCandidate().getResource() + " ("
				+ scoreLabel + "/" + scoreLink + "/" + scoreRlsp + ")");
		return labelWeight * scoreLabel + rlspWeight * scoreRlsp + linkWeight * scoreLink;
	}

	
	private float scoreLink(Toponym toponym, CriterionToponymCandidate criterion, Model teiRdf,
			Set<Toponym> toponymsTEI) {
		Resource nodeToRemove = toponym.getResource();
		Resource nodeToInsert = criterion.getCandidate().getResource();
		float result = 0f;
		List<Statement> statements = getProperties(nodeToRemove, teiRdf);
		statements = removeRLSP(statements);
		if (statements.isEmpty())
			return result;
		// pour chaque statement (non RLSP) du noeud du TEI
		for (Statement s : statements) {
			final Statement statement = s;
			Resource m; // resource liée au noeud à supprimer
			if (areResourcesEqual(statement.getSubject(), nodeToRemove)) {
				m = (Resource) statement.getObject();
			} else {
				m = statement.getSubject();
			}
			List<CriterionToponymCandidate> candidates = getReferent(m, toponymsTEI);
			if (candidates.isEmpty())
				candidates.addAll(getCandidates(m, toponymsTEI)); // ici il faut prendre les candidats seulement si le topo n'a pas été désambiguisé, sinon on utilise son référent
			Map<CriterionToponymCandidate, Integer> pathLengths = new ConcurrentHashMap<>();
			candidates.parallelStream().forEach(criterionToponymCandidate -> {
				Resource end = criterionToponymCandidate.getCandidate().getResource();
				final Resource nodeToInsertCopy = nodeToInsert;
				int pathLength = -1;
				if (shortestPaths.containsKey(nodeToInsertCopy)) {
					DijkstraSP sp = shortestPaths.get(nodeToInsertCopy);
					if (sp.hasPathTo(end)) {
						pathLength = sp.distTo(end);
					}
				}
				if (pathLength == -1 && shortestPaths.containsKey(end)) {
					DijkstraSP sp = shortestPaths.get(end);
					if (sp.hasPathTo(nodeToInsertCopy)) {
						pathLength = sp.distTo(nodeToInsertCopy);
					}
				}
				pathLengths.put(criterionToponymCandidate, pathLength);				
			});
			Integer maxPathLength = getMaxValue(pathLengths);
			float min = 1f;
			for (Entry<CriterionToponymCandidate, Integer> entry : pathLengths.entrySet()) {
				if (entry.getValue() > 0) {
					float scoreTmp = ((float) entry.getValue()) / ((float) maxPathLength);
					min = scoreTmp < min ? scoreTmp : min;
				}
			}
			result += min;
		}

		return result / ((float) statements.size());
	}
	
	private List<Statement> getProperties(Resource place, Model teiRdf) {
		List<Statement> results = teiRdf.listStatements(place, null, (RDFNode) null).toList();
		results.addAll(teiRdf.listStatements(null, null, (RDFNode) place).toList());
		return results;
	}
	
	private Integer getMaxValue(Map<CriterionToponymCandidate, Integer> pathLength) {
		Integer result = 0;
		for (Entry<CriterionToponymCandidate, Integer> entry : pathLength.entrySet()) {
			if (entry.getValue() > result) {
				result = entry.getValue();
			}
		}
		return result;
	}
	
	private Path findShortestPath(Model m, Resource start, Resource end, Model mComplete) {
		// search
		Path solution = null;
		// On récupère les latitudes et longitudes des points d'arrivées
		Resource startResource = start;
		Resource endResource = end;
		boolean endHasCoords = hasLatAndLong(endResource, mComplete);
		boolean startHasCoords = hasLatAndLong(startResource, mComplete);
		float endLat = 0.0f;
		float endLong = 0.0f;
		float startLat = 0.0f;
		float startLong = 0.0f;
		float distanceBetweendStartAndEnd = -1.0f; // distance between start and end points
		if (endHasCoords && startHasCoords) {
			//logger.info("les 2 ont des coordonnées")
			endLat = getLatitude(endResource, mComplete);
			endLong = getLongitude(endResource, mComplete);
			startLat = getLatitude(startResource, mComplete);
			startLong = getLongitude(startResource, mComplete);
			distanceBetweendStartAndEnd = distance(endLat, endLong, startLat, startLong);
		} else  if (startHasCoords) {
			logger.info("start a des coordonnées");
			startLat = getLatitude(startResource, mComplete);
			startLong = getLongitude(startResource, mComplete);
			// il faut s'occuper de end
			Resource closestEnd = getClosestResourceWithCoordinates(m, endResource, mComplete);
			if (closestEnd != null) {
				endLat = getLatitude(closestEnd, mComplete);
				endLong = getLongitude(closestEnd, mComplete);
				distanceBetweendStartAndEnd = distance(endLat, endLong, startLat, startLong);
			}
			
		} else  if (endHasCoords) {
			logger.info("end a des coordonnées");
			endLat = getLatitude(endResource, mComplete);
			endLong = getLongitude(endResource, mComplete);
			// il faut s'occuper de start
			Resource closestStart = getClosestResourceWithCoordinates(m, startResource, mComplete);
			if (closestStart != null) {
				startLat = getLatitude(closestStart, mComplete);
				startLong = getLongitude(closestStart, mComplete);
				distanceBetweendStartAndEnd = distance(endLat, endLong, startLat, startLong);
			}
			
		} else {
			// aucun n'a de topo, il faut s'occuper start et end
			logger.info("aucun n'a de coordonnées");
			Resource closestEnd = getClosestResourceWithCoordinates(m, endResource, mComplete);
			if (closestEnd != null) {
				endLat = getLatitude(closestEnd, mComplete);
				endLong = getLongitude(closestEnd, mComplete);
			}
			Resource closestStart = getClosestResourceWithCoordinates(m, startResource, mComplete);
			if (closestStart != null) {
				startLat = getLatitude(closestStart, mComplete);
				startLong = getLongitude(closestStart, mComplete);
			}
			if (closestEnd != null && closestStart != null) {
				distanceBetweendStartAndEnd = distance(endLat, endLong, startLat, startLong);
			}
		}
		
		List<Path> forwardStatements = new LinkedList<>();
		List<Path> backwardStatements = new LinkedList<>();
		Set<Resource> forwardSeen = new HashSet<>();
		Set<Resource> backwardSeen = new HashSet<>();

		// initialise the paths
		for (Iterator<Statement> i = m.listStatements(startResource, null, (RDFNode) null); i.hasNext();) {
			forwardStatements.add(new Path().append(i.next()));
		}
		for (Iterator<Statement> i = m.listStatements(null, null, (RDFNode) endResource); i.hasNext();) {
			backwardStatements.add(new Path().append(i.next()));
		}

		
		while (solution == null && !forwardStatements.isEmpty() && !backwardStatements.isEmpty()) {
			Path forwardCandidate = selectMostPromisingPath(forwardStatements, m, endLat, endLong, forwardSeen, mComplete);
			Path backwardCandidate = selectMostPromisingPathBackward(backwardStatements, m, startLat, startLong, backwardSeen, mComplete);
			if (forwardCandidate == null || backwardCandidate == null) {
				break;
			} else if (forwardCandidate != null && endResource != null && forwardCandidate.hasTerminus(endResource)) {
				solution = forwardCandidate;
			} else if(backwardCandidate != null && ! backwardCandidate.isEmpty() && backwardCandidate.get(0) != null && backwardCandidate.get(0).getSubject() != null  
					&& areResourcesEqual(backwardCandidate.get(0).getSubject(), startResource)) {
				solution = backwardCandidate;
			} else if (forwardSeen.stream().anyMatch(p -> backwardSeen.contains(p))) { 
				// les frontières se touchent
				// il faut trouver le bon chemin et le renvoyer
				//logger.info("Fusion de frontières")
				forwardSeen.retainAll(backwardSeen);
				Optional<Resource> optRes = forwardSeen.stream().findFirst();
				if (optRes.isPresent()) {
					Resource r = optRes.get();
					Optional<Path> pF = forwardStatements.stream().filter(l -> l.stream().anyMatch(p -> areResourcesEqual(p.getSubject(), r ) || (p.getObject().isResource() && areResourcesEqual((Resource)p.getObject(), r )))).limit(1).findFirst();
					Optional<Path> pB = backwardStatements.stream().filter(l -> l.stream().anyMatch(p -> areResourcesEqual(p.getSubject(), r ) || (p.getObject().isResource() && areResourcesEqual((Resource)p.getObject(), r )))).limit(1).findFirst();
					if (pF.isPresent() && pB.isPresent()) {
						Path pathForward = pF.get();
						Path pathBackward = pB.get();
						Path newPath = new Path();
						for (Statement statement : pathForward) {
							newPath.add(statement);
							if (areResourcesEqual(statement.getSubject(), r))
								break;
						}
						boolean seen = false; // tant que l'élément commun r n'a pas été vu dans pathBackward, on n'insere pas les statements
						for (Statement statement : pathBackward) {
							if (seen)
								newPath.add(statement);
							if (statement.getObject().isResource() && areResourcesEqual((Resource)statement.getObject(), r))
								seen = true;
						}
						solution = newPath;
					}
				}
				break;
			}
			else {
				Resource terminus = forwardCandidate.getTerminalResource();
				if (terminus != null) {					
					if (wrongDistance(distanceBetweendStartAndEnd, startLat, startLong, terminus, mComplete)) {
						break;
					}
					forwardSeen.add(terminus);
					
					// breadth-first expansion
					for (Iterator<Statement> i = terminus.listProperties(); i.hasNext();) {
						Statement link = i.next();

						// no looping allowed, so we skip this link if it takes
						// us to a node we've seen
						if (!forwardSeen.contains(link.getObject()) && forwardCandidate.size() < maxLengthForSP) {
							forwardStatements.add(forwardCandidate.append(link));
						}
					}
				}
				Resource subject = backwardCandidate.get(0).getSubject();
				if (subject != null) {
					if (wrongDistance(distanceBetweendStartAndEnd, endLat, endLong, subject, mComplete)) {
						break;
					}
					backwardSeen.add(subject);
					
					// breadth-first expansion
					for (Iterator<Statement> i = m.listStatements(null, null, (RDFNode)subject); i.hasNext();) {
						Statement link = i.next();

						// no looping allowed, so we skip this link if it takes
						// us to a node we've seen
						if (!backwardSeen.contains(link.getSubject()) && backwardCandidate.size() < maxLengthForSP) {
							Path newPath = new Path();
							newPath.add(link);
							backwardCandidate.forEach(p -> newPath.add(p));
							backwardStatements.add(newPath);
						}
					}
				}
			}
		}
		return solution;
	}
	
	private boolean hasLatAndLong(Resource r, Model m) {
		Statement sLat = m.getProperty(r, propLat);
		if (sLat == null) {
			sLat = m.getProperty(r, geoLat);
		}
		Statement sLong = m.getProperty(r, propLong);
		if (sLong == null) {
			sLong = m.getProperty(r, geoLong);
		}
		return sLat != null && sLong != null;
	}
	private boolean wrongDistance(float distanceBetweendStartAndEnd, float aLat, float aLong, Resource r, Model mComplete) {
		if (distanceBetweendStartAndEnd > 0.0f && hasLatAndLong(r, mComplete)) { // si distanceBetweendStartAndEnd < 0.0f, on ne peut pas s'en servir. Cela veut qu'il nous a manqué une distance d'un des points de départ ou d'arrivée
			float bLat = getLatitude(r, mComplete);
			float bLong = getLongitude(r, mComplete);
			float distanceToCompare = distance(aLat, aLong, bLat, bLong);
			if (distanceBetweendStartAndEnd * 1.5 < distanceToCompare) 
				return true;
		}
		return false;
	}
	private float getLatitude(Resource r, Model m) {
		float result = 0f;
		Statement s = m.getProperty(r, propLat);

		if (s == null) {
			s = m.getProperty(r, geoLat);
		}
		if (s != null) {
			RDFNode latNode = s.getObject();
			if (latNode.isLiteral()) {
				Literal latLiteral = (Literal) latNode;
				Object latObject = latLiteral.getValue();
				if (latObject != null) {
					String latString = latObject.toString();
					if (latString != null) {
						try {
							result = Float.parseFloat(latString);
						} catch (NumberFormatException e) {
							logger.error(e);
						}
					}
				}
			}
		}
		return result;
	}
	
	private float getLongitude(Resource r, Model m) {
		float result = 0f;
		Statement s = m.getProperty(r, propLong);

		if (s == null) {
			s = m.getProperty(r, geoLong);
		}
		if (s != null) {
			RDFNode latNode = s.getObject();
			if (latNode.isLiteral()) {
				Literal latLiteral = (Literal) latNode;
				Object latObject = latLiteral.getValue();
				if (latObject != null) {
					String latString = latObject.toString();
					if (latString != null) {
						try {
							result = Float.parseFloat(latString);
						} catch (NumberFormatException e) {
							logger.error(e);
						}
					}
				}
			}
		}
		return result;
	}

	private float distance(float latX, float longX, float latY, float longY) {
		float latDist = Math.abs(latX - latY);
		float longDist = Math.abs(longX - longY);
		double distance = Math.sqrt(longDist * longDist + latDist * latDist);
		return (float) distance;
	}
	
	/**
	 * Select most promising path by using lat and long.
	 *
	 * @param bfs
	 *            the bfs
	 * @param m
	 *            the m
	 * @param endLat
	 *            the end lat
	 * @param endLong
	 *            the end long
	 * @param seen
	 *            the seen
	 * @param mComplete
	 *            the m complete
	 * @return the path
	 */
	private Path selectMostPromisingPath(List<Path> bfs, Model m, float endLat, float endLong, Set<Resource> seen, Model mComplete) {
		Path result = null;
		float shortestDistance = Float.MAX_VALUE;
		for (Path path : bfs) {
			Statement lastStatement = path.get(path.size() - 1);
			RDFNode object = lastStatement.getObject();
			if (!seen.contains(object) && object.isResource()) {
				Resource r = (Resource) object;
				if (hasLatAndLong(r, mComplete)) {
					float rLat = getLatitude(r, mComplete);
					float rLong = getLongitude(r, mComplete);
					float distance = distance(endLat, endLong, rLat, rLong);
					if (shortestDistance > distance) {
						shortestDistance = distance;
						result = path;
					}
				} else if (result == null) {
					// r n'a pas de l'attititude. On sélectionne son chemin seulement si result est null
					result = path;
				}
			}
		}
		if (result != null) {
			bfs.remove(result);
		}
		return result;
	}
	private Path selectMostPromisingPathBackward(List<Path> bfs, Model m, float startLat, float startLong, Set<Resource> seen, Model mComplete) {
		Path result = null;
		float shortestDistance = Float.MAX_VALUE;
		for (Path path : bfs) {
			Statement firstStatement = path.get(0);
			Resource r = firstStatement.getSubject();
			if (!seen.contains(r)) {
				if (hasLatAndLong(r, mComplete)) {
					float rLat = getLatitude(r, mComplete);
					float rLong = getLongitude(r, mComplete);
					float distance = distance(startLat, startLong, rLat, rLong);
					if (shortestDistance > distance) {
						shortestDistance = distance;
						result = path;
					}
				} else if (result == null) {
					// r n'a pas de l'attititude. On sélectionne son chemin seulement si result est null
					result = path;
				}
			}
		}
		if (result != null) {
			bfs.remove(result);
		}
		return result;
	}
	private Resource getClosestResourceWithCoordinates(Model m, Resource r, Model mComplete) {
		List<Statement> properties = m.listStatements(r, null, (RDFNode)null).toList();
		for (Statement statement : properties) {
			RDFNode n = statement.getObject();
			if (n.isResource() && hasLatAndLong((Resource)n, mComplete))
				return (Resource)n;
		}
		properties = m.listStatements(null, null, (RDFNode)r).toList();
		for (Statement statement : properties) {
			Resource n = statement.getSubject();
			if (hasLatAndLong(n, mComplete))
				return n;
		}
		return null;
	}
	/**
	 * Gets the path that costs the less and remove it from OPEN.
	 *
	 * @param open
	 *            the open
	 * @return the min cost path
	 */
	private List<IPathMatching> getMinCostPath(List<List<IPathMatching>> open, Model kbSubgraph, Model miniGraph,
			Set<Toponym> toponymsSeq, float labelWeight, float rlspWeight, float linkWeight, Set<Resource> sourceNodes,
			Set<Resource> targetNodes, Model completeKB) {
		float min = Float.MAX_VALUE;
		List<IPathMatching> pMin = null;
		for (List<IPathMatching> path : open) {
			Set<Resource> unusedSourceNodes = getSourceUnusedResources(path, sourceNodes);
			Set<Resource> unusedTargetNodes = getTargetUnusedResources(path, targetNodes);
			float g = totalCostPath(path);
			float h = heuristicCostPath(path, kbSubgraph, miniGraph, toponymsSeq, labelWeight, rlspWeight, linkWeight,
					unusedSourceNodes, unusedTargetNodes, completeKB);
			if (g + h < min) {
				min = g + h;
				pMin = path;
			}
		}
		if (pMin != null)
			open.remove(pMin);
		return pMin;
	}
	private float heuristicCostPath(List<IPathMatching> path, Model kbSubgraph, Model miniGraph,
			Set<Toponym> toponymsTEI, float labelWeight, float rlspWeight, float linkWeight,
			Set<Resource> unusedSourceNodes, Set<Resource> unusedTargetNodes, Model completeKB) {
		float result = 0f;
		/*
		 * h(o)= somme des min{n1, n2} substitutions les moins chères + max{0,
		 * n1 − n2} suppression + max{0, n2 − n1} insertions (si l’on pose
		 * n1=nombre de noeuds non traités du graphe source et n2=nombre de
		 * noeuds non traités du graphe cible).
		 */
		Integer n1 = unusedSourceNodes.size();
		Integer n2 = unusedTargetNodes.size();
		List<Float> substitutionCosts = new ArrayList<>();
		for (Resource unusedSourceNode : unusedSourceNodes) {
			List<Float> substitutionCostsCurrentToponym = new ArrayList<>();
			Toponym unusedToponym = toponymsTEI.stream()
					.filter(t -> areResourcesEqual(t.getResource(), unusedSourceNode)).findFirst().get();
			for (CriterionToponymCandidate criterion : unusedToponym.getScoreCriterionToponymCandidate()) {
				substitutionCostsCurrentToponym.add(getSubstitutionCost(unusedToponym, criterion, labelWeight,
						rlspWeight, linkWeight, toponymsTEI, miniGraph, kbSubgraph, completeKB));
			}
			if (!substitutionCostsCurrentToponym.isEmpty())
				substitutionCosts.add(substitutionCostsCurrentToponym.stream().sorted((a, b) -> Float.compare(b, a))
						.findFirst().get());
		}
		result += substitutionCosts.stream().sorted((a, b) -> Float.compare(b, a)).limit(Integer.min(n1, n2))
				.mapToDouble(i -> i).sum();
		result += (float) Integer.max(0, n1 - 2) + (float) Integer.max(0, n2 - n1);
		return result;
	}
	
	private Set<Resource> getTargetUnusedResources(List<IPathMatching> path, Set<Resource> targetResources) {
		Set<Resource> unusedResources = new HashSet<>(targetResources);
		unusedResources.removeAll(getTargetUsedResources(path));
		return unusedResources;
	}
	/**
	 * Gets the used resources of the target graph in the current path.
	 *
	 * @param path
	 *            the path
	 * @return the target resources used
	 */
	private Set<Resource> getTargetUsedResources(List<IPathMatching> path) {
		Set<Resource> usedResources = new HashSet<>();
		for (IPathMatching pathElement : path) {
			if (pathElement.getClass() == Substitution.class) {
				Substitution sub = (Substitution) pathElement;
				usedResources.add(sub.getInsertedNode());
			} else if (pathElement.getClass() == Insertion.class) {
				Insertion sub = (Insertion) pathElement;
				usedResources.add(sub.getInsertedNode());
			}
		}
		return usedResources;
	}
	private Set<Resource> getSourceUnusedResources(List<IPathMatching> path, Set<Resource> sourceResources) {
		Set<Resource> unusedResources = new HashSet<>(sourceResources);
		unusedResources.removeAll(getTargetUsedResources(path));
		return unusedResources;
	}
	private void recordLinkPath(Map<Resource, Map<Resource, Integer>> scoresLinkTmp, Resource r1, Resource r2,
			Integer value) {
		if (scoresLinkTmp.containsKey(r1)) { // r1 a déjà été traité
			Map<Resource, Integer> scoreR1 = scoresLinkTmp.get(r1);
			if (!scoreR1.containsKey(r2)) { // r1 et r2 n'ont jamais été traités
											// ensemble
				scoreR1.put(r2, value);
			}
		} else { // r1 n'a jamais été traité
			Map<Resource, Integer> scoreR1 = new HashMap<>();
			scoreR1.put(r2, value);
			scoresLinkTmp.put(r1, scoreR1);
		}
		if (scoresLinkTmp.containsKey(r2)) { // r2 a déjà été traité
			Map<Resource, Integer> scoreR2 = scoresLinkTmp.get(r2);
			if (!scoreR2.containsKey(r1)) { // r1 et r2 n'ont jamais été traités
											// ensemble
				scoreR2.put(r1, value);
			}
		} else {// r2 n'a jamais été traité
			Map<Resource, Integer> scoreR2 = new HashMap<>();
			scoreR2.put(r1, value);
			scoresLinkTmp.put(r2, scoreR2);
		}
	}
	private List<CriterionToponymCandidate> getCandidates(Resource r, Set<Toponym> toponymsTEI) {
		List<CriterionToponymCandidate> results = new ArrayList<>();
		Optional<Toponym> toponym = toponymsTEI.stream().filter(t -> areResourcesEqual(t.getResource(), r)).findFirst();
		if (toponym.isPresent()) {
				return toponym.get().getScoreCriterionToponymCandidate();

		}
		return results;
	}
	private float scoreRlsp(Toponym nodeToRemove, CriterionToponymCandidate nodeToInsert, Model teiRdf,
			Set<Toponym> toponymsTEI, Model kbWithInterestingProperties, Model completeKB) {
		List<Statement> statements = getProperties(nodeToRemove.getResource(), teiRdf);
		statements = keepOnlyRLSP(statements);
		if (statements.isEmpty())
			return 1f;
		float result = 0f;
		for (Statement statement : statements) {
			Resource m;
			if (areResourcesEqual(statement.getSubject(), nodeToRemove.getResource())) {
				m = (Resource) statement.getObject();
			} else {
				m = statement.getSubject();
			}
			Property statementProperty = statement.getPredicate();
			List<Property> properties = getCorrespondingProperties(statementProperty);
			PredicatesFilter filter = new PredicatesFilter(properties);
			Map<CriterionToponymCandidate, Integer> pathLength = new HashMap<>();
			List<CriterionToponymCandidate> candidates = getReferent(m, toponymsTEI);
			if (candidates.isEmpty())
				candidates.addAll(getCandidates(m, toponymsTEI)); // ici il faut prendre les candidats seulement si le topo n'a pas été désambiguisé, sinon on utilise son référent
			candidates.parallelStream().forEach(criterionToponymCandidate -> {
				Resource end = criterionToponymCandidate.getCandidate().getResource();
				if (scoresRlspTmp.containsKey(statementProperty.toString())
						&& scoresRlspTmp.get(statementProperty.toString()).containsKey(end)
						&& scoresRlspTmp.get(statementProperty.toString()).get(end)
								.containsKey(nodeToInsert.getCandidate().getResource())) {
					pathLength.put(criterionToponymCandidate, scoresRlspTmp.get(statementProperty.toString()).get(end)
							.get(nodeToInsert.getCandidate().getResource()));
				} else {
					final Resource nodeToInsertCopy = nodeToInsert.getCandidate().getResource();
					OntTools.Path path = null;
					path = findShortestPath(kbWithInterestingProperties, nodeToInsertCopy, end, completeKB);
					boolean erasePath = false;
					if (path != null) {
						for (Statement statement2 : path) {
							Property p = statement2.getPredicate();
							if (!properties.stream().anyMatch(p2 -> p2.getURI().equals(p.getURI()))) {
								erasePath = true;
								break;
							}
						}
					}
					if (path != null) {
						if (!erasePath) {
							pathLength.put(criterionToponymCandidate, path.size());
						} else pathLength.put(criterionToponymCandidate, -1);
						recordRlspPath(scoresRlspTmp, nodeToInsertCopy, end, path.size(),
								statementProperty.toString());
					} else {
						pathLength.put(criterionToponymCandidate, -1);
						recordRlspPath(scoresRlspTmp, nodeToInsertCopy, end, -1,
								statementProperty.toString());
					}
				}
			});
			Integer maxPathLength = getMaxValue(pathLength);
			float min = 1f;
			for (Entry<CriterionToponymCandidate, Integer> entry : pathLength.entrySet()) {
				if (entry.getValue() > 0) {
					float scoreTmp = ((float) entry.getValue()) / ((float) maxPathLength); // (1
																							// -
																							// entry.getKey().getValue())
																							// *
					if (scoreTmp < min) {
						min = scoreTmp;
					}
				}
			}
			result += min;

		}

		return result / ((float) statements.size());
	}
	private List<Statement> keepOnlyRLSP(List<Statement> statements) {
		if (statements == null || statements.isEmpty())
			return new ArrayList<>();
		return statements.stream().filter(s -> s.getPredicate().getNameSpace().equals(RLSP_NS))
				.collect(Collectors.toList());
	}
	private void recordRlspPath(Map<String, Map<Resource, Map<Resource, Integer>>> scoresRlspTmp, Resource r1,
			Resource r2, Integer value, String p) {
		Map<Resource, Map<Resource, Integer>> scoresRlspTmp2;
		if (scoresRlspTmp.containsKey(p)) {
			scoresRlspTmp2 = scoresRlspTmp.get(p);
		} else {
			scoresRlspTmp2 = new HashMap<>();
			scoresRlspTmp.put(p, scoresRlspTmp2);
		}
		recordLinkPath(scoresRlspTmp2, r1, r2, value);
	}
	private List<CriterionToponymCandidate> getReferent(Resource r, Set<Toponym> toponymsTEI) {
		List<CriterionToponymCandidate> results = new ArrayList<>();
		Optional<Toponym> toponym = toponymsTEI.stream().filter(t -> areResourcesEqual(t.getResource(), r)).findFirst();
		if (toponym.isPresent()) {
			if (toponym.get().getReferent() != null) {
				Resource ref = toponym.get().getReferent();
				results.addAll(toponym.get().getScoreCriterionToponymCandidate().stream().
						filter(p -> areResourcesEqual(ref, p.getCandidate().getResource())).collect(Collectors.toList()));
			}

		}
		return results;
	}
	/**
	 * Gets the corresponding DBpedia properties from the TEI's one.
	 *
	 * @param teiProperty
	 *            the tei property
	 * @return the corresponding properties
	 */
	private List<Property> getCorrespondingProperties(Property teiProperty) {
		List<Property> properties = new ArrayList<>(); // propriétés autorisées
		if (teiProperty.getURI().equalsIgnoreCase(rlspNorthOf.getURI())) {
			properties.add(propSud);
			properties.add(propSudEst);
			properties.add(propSudOuest);
			properties.add(propEst);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspNorthEastOf.getURI())) {
			properties.add(propSud);
			properties.add(propSudOuest);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspNorthWestOf.getURI())) {
			properties.add(propSud);
			properties.add(propSudEst);
			properties.add(propEst);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspSouthOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordEst);
			properties.add(propNordOuest);
			properties.add(propEst);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspSouthEastOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordOuest);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspSouthWestOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordEst);
			properties.add(propEst);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspEastOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordOuest);
			properties.add(propSudOuest);
			properties.add(propOuest);
			properties.add(propSud);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspWestOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordEst);
			properties.add(propSudEst);
			properties.add(propEst);
			properties.add(propSud);
		}
		return properties;
	}
	/**
	 * Removes the RLSP from the statements list.
	 *
	 * @param statements
	 *            the statements
	 * @return the list
	 */
	private List<Statement> removeRLSP(List<Statement> statements) {
		if (statements == null || statements.isEmpty())
			return new ArrayList<>();
		return statements.stream().filter(s -> !s.getPredicate().getNameSpace().equals(RLSP_NS)).collect(Collectors.toList());
	}
	private float getInsertionCost(Resource nodeToInsert) {
		return 1f;
	}
	private void updateToponyms(List<IPathMatching> pMin, Set<Toponym> toponymsSeq) {
		IPathMatching lastOperation = pMin.get(pMin.size() - 1);
		if (lastOperation.getClass() == Substitution.class) {
			Substitution s = (Substitution) lastOperation;
			Resource deletedNode = s.getDeletedNode();
			Toponym topo = toponymsSeq.stream().filter(p -> areResourcesEqual(deletedNode, p.getResource())).findFirst()
					.get();
			if (areResourcesEqual(deletedNode, topo.getResource())) {
				Resource insertedNode = s.getInsertedNode();
				topo.setReferent(insertedNode); // on ajoute le referent pour
												// limiter les calculs de
												// chemins qui implique ce noeud
												// par la suite
			}
		}
	}
	private boolean isCompletePath(List<IPathMatching> path, Set<Resource> sourceNodes, Set<Resource> targetNodes) {
		Set<Resource> usedNodesFromTarget = getTargetUsedResources(path);
		Set<Resource> usedNodesFromSource = getSourceUsedResources(path);
		return sourceNodes.stream().allMatch(usedNodesFromSource::contains)
				&& targetNodes.stream().allMatch(usedNodesFromTarget::contains);
	}
	/**
	 * Gets the used resources of the source graph in the current path.
	 *
	 * @param path
	 *            the path
	 * @return the source used resources
	 */
	private Set<Resource> getSourceUsedResources(List<IPathMatching> path) {
		Set<Resource> usedResources = new HashSet<>();
		for (IPathMatching pathElement : path) {
			if (pathElement.getClass() == Substitution.class) {
				Substitution sub = (Substitution) pathElement;
				usedResources.add(sub.getDeletedNode());
			} else if (pathElement.getClass() == Deletion.class) {
				Deletion sub = (Deletion) pathElement;
				usedResources.add(sub.getDeletedNode());
			}
		}
		return usedResources;
	}
}
