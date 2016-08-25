package fr.ign.georeden.algorithms.graph.matching;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.ontology.OntTools.Path;
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
import org.apache.jena.util.ResourceUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import fr.ign.georeden.algorithms.string.StringComparisonDamLev;
import fr.ign.georeden.algorithms.string.TokenWiseSimilarity;
import fr.ign.georeden.kb.ToponymType;
import fr.ign.georeden.utils.RDFUtil;

/**
 * The graph matching class.
 */
public class GraphMatching {

	/** The logger. */
	private static Logger logger = Logger.getLogger(GraphMatching.class);

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
	private final Model kbSubgraph; // Sous graphe de kbSource contenant
									// uniquement les statements du type nord,
									// sud, ouest etc.

	/** The subjects of subgraph. */
	private final List<Resource> subjectsOfSubgraph; // Liste des resources qui
														// sont sujets d'un
														// statement de
														// kbSubgraph

	/** The resources index APSP. */
	private final Map<Resource, Integer> resourcesIndexAPSP; // index des
																// resources de
																// subjectsOfSubgraph.
																// Utilisé pour
																// accélérer la
																// recherche des
																// plus court
																// chemins

	/** The toponyms. */
	//private final Set<Toponym> toponyms; // toponyms du TEI avec leurs candidats

	/** The prop fr NS. */
	private static final String PROP_FR_NS = "http://fr.dbpedia.org/property/";
	private static final String GEO_NS = "http://www.w3.org/2003/01/geo/wgs84_pos#";

	/** The dbo NS. */
	private static final String DBO_NS = "http://dbpedia.org/ontology/";
	private static final String IGN_NS = "http://example.com/namespace/";
	private static final String RLSP_NS = "http://data.ign.fr/def/relationsspatiales#";

	/** The prop nord. */
	private static final Property propNord = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "nord");

	/** The prop nord est. */
	private static final Property propNordEst = ModelFactory.createDefaultModel()
			.createProperty(PROP_FR_NS + "nordEst");

	/** The prop nord ouest. */
	private static final Property propNordOuest = ModelFactory.createDefaultModel()
			.createProperty(PROP_FR_NS + "nordOuest");

	/** The prop sud. */
	private static final Property propSud = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "sud");

	/** The prop sud est. */
	private static final Property propSudEst = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "sudEst");

	/** The prop sud ouest. */
	private static final Property propSudOuest = ModelFactory.createDefaultModel()
			.createProperty(PROP_FR_NS + "sudOuest");

	/** The prop est. */
	private static final Property propEst = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "est");
	private static final Property propDepartement = ModelFactory.createDefaultModel()
			.createProperty(PROP_FR_NS + "département");
	private static final Property propRegion = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "région");
	private static final Property propProvince = ModelFactory.createDefaultModel()
			.createProperty(PROP_FR_NS + "province");
	private static final Property dboDepartement = ModelFactory.createDefaultModel()
			.createProperty(DBO_NS + "department");
	private static final Property dboRegion = ModelFactory.createDefaultModel().createProperty(DBO_NS + "region");
	private static final Property dboProvince = ModelFactory.createDefaultModel().createProperty(DBO_NS + "province");

	/** The prop ouest. */
	private static final Property propOuest = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "ouest");
	private static final Property rlspNorthOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "northOf");
	private static final Property rlspNorthEastOf = ModelFactory.createDefaultModel()
			.createProperty(RLSP_NS + "northEastOf");
	private static final Property rlspNorthWestOf = ModelFactory.createDefaultModel()
			.createProperty(RLSP_NS + "northWestOf");
	private static final Property rlspSouthOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "southOf");
	private static final Property rlspSouthEastOf = ModelFactory.createDefaultModel()
			.createProperty(RLSP_NS + "southEastOf");
	private static final Property rlspSouthWestOf = ModelFactory.createDefaultModel()
			.createProperty(RLSP_NS + "southWestOf");
	private static final Property rlspEastOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "eastOf");
	private static final Property rlspWestOf = ModelFactory.createDefaultModel().createProperty(RLSP_NS + "westOf");

	/** The spatial reference. */
	private static final Property spatialReference = ModelFactory.createDefaultModel()
			.createProperty("http://data.ign.fr/def/itineraires#spatialReference");
	private static final Property linkSameRoute = ModelFactory.createDefaultModel()
			.createProperty(IGN_NS + "linkSameRoute");
	private static final Property linkSameSequence = ModelFactory.createDefaultModel()
			.createProperty(IGN_NS + "linkSameSequence");
	private static final Property linkSameBag = ModelFactory.createDefaultModel()
			.createProperty(IGN_NS + "linkSameBag");

	private static final Property propLat = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "latitude");
	private static final Property propLong = ModelFactory.createDefaultModel().createProperty(PROP_FR_NS + "longitude");
	private static final Property geoLat = ModelFactory.createDefaultModel().createProperty(GEO_NS + "lat");
	private static final Property geoLong = ModelFactory.createDefaultModel().createProperty(GEO_NS + "long");
	
	private static final String PREFIXES = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
			+ "PREFIX prop-fr: <http://fr.dbpedia.org/property/>" + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
			+ "PREFIX xml: <https://www.w3.org/XML/1998/namespace>" + "PREFIX dbo: <http://dbpedia.org/ontology/>"
			+ "PREFIX ign: <http://example.com/namespace/>" + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
			+ "PREFIX ign: <http://example.com/namespace/>" + "PREFIX iti:<http://data.ign.fr/def/itineraires#> ";

	/** The comparator query solution entry. */
	private final Comparator<QuerySolutionEntry> comparatorQuerySolutionEntry = (a, b) -> {
		Resource r1 = a.getSpatialReference() != null ? a.getSpatialReference() : a.getSpatialReferenceAlt();
		Resource r2 = b.getSpatialReference() != null ? b.getSpatialReference() : b.getSpatialReferenceAlt();
		String subR1 = r1.toString().substring(r1.toString().lastIndexOf('/') + 1);
		String subR2 = r2.toString().substring(r2.toString().lastIndexOf('/') + 1);
		float fR1 = subR1.indexOf('_') != -1 ? Float.parseFloat(subR1.substring(0, subR1.indexOf('_'))) + 0.1f
				: Float.parseFloat(subR1);
		float fR2 = subR2.indexOf('_') != -1 ? Float.parseFloat(subR2.substring(0, subR2.indexOf('_'))) + 0.1f
				: Float.parseFloat(subR2);
		return Float.compare(fR1, fR2);
	};

	private final String serializationDirectory;
	private final Map<Resource, DijkstraSP> shortestPaths;

	private final Set<String> rlspCalculous;

	private final Set<Resource> resourcesToUseForSP;
	private final List<SubstitutionCostResult> scrList;

	private final float labelWeight;
	private final float rlspWeight;
	private final float linkWeight;
	private final float typeWeight;
	private final String workingDirectory;
	
	private final List<Candidate> candidatesFromKB;
	
	private final Resource nil;

	/**
	 * Instantiates a new graph matching.
	 *
	 * @param teiRdfPath
	 *            the tei rdf path
	 * @param dbPediaRdfFilePath
	 *            the db pedia rdf file path
	 * @param numberOfCandidate
	 *            the number of candidate
	 * @param candidateSelectionThreshold
	 *            the candidate selection threshold
	 */
	public GraphMatching(Document teiSource, String dbPediaRdfFilePath, int numberOfCandidate,
			float candidateSelectionThreshold, String serializationDirectory, float labelWeight, float rlspWeight,
			float linkWeight, float typeWeight, String workingDirectory) {
		this.serializationDirectory = serializationDirectory;
		this.workingDirectory = workingDirectory;
		this.labelWeight = labelWeight;
		this.rlspWeight = rlspWeight;
		this.linkWeight = linkWeight;
		this.typeWeight = typeWeight;
		this.teiRdf = RDFUtil.getModel(teiSource); // BUG EN RELEASE
		// this.teiRdf = ModelFactory.createDefaultModel().read(workingDirectory
		// + "temp7.n3");
		this.toponymsTEI = getToponymsFromTei(teiRdf);
		logger.info(toponymsTEI.size() + " toponyms in the TEI RDF graph");

		logger.info("Chargement de la KB : " + dbPediaRdfFilePath);
		this.kbSource = ModelFactory.createDefaultModel().read(dbPediaRdfFilePath);

		logger.info("Création du sous graphe de la KB contenant uniquement les relations spatiales");

		this.kbSubgraph = getSubGraphWithResources(kbSource);
		// Model ville = ModelFactory.createDefaultModel().read(workingDirectory
		// +
		// "dev\\java\\calculRelationsSpatialesAcRivieres\\rivieresEtVilles.rdf");
		// Model sourceCopy = cloneModel(kbSource);
		// sourceCopy.add(this.kbSubgraph.listStatements().toList());
		// sourceCopy.add(ville.listStatements().toList());
		// // completeWithSymetricsRLSP() // OPTIONEL. Augmente le nombre de
		// // statement, et facilite le la vérification des chemains
		// saveModelToFile(workingDirectory + "dbpedia_fr_with_rlsp_V3.n3",
		// sourceCopy, "N3")
		logger.info("Création de l'index des plus courts chemins.");
		this.subjectsOfSubgraph = kbSubgraph.listSubjects().toList().stream()
				.sorted((a, b) -> a.toString().compareTo(b.toString())).collect(Collectors.toList());
		this.resourcesIndexAPSP = new ConcurrentHashMap<>();
		for (int i = 0; i < subjectsOfSubgraph.size(); i++) {
			resourcesIndexAPSP.put(subjectsOfSubgraph.get(i), i + 1);
		}

		logger.info("Récupérations des candidats de la KB");
		this.candidatesFromKB = getCandidatesFromKB(this.kbSource);

		//this.toponyms = 
		getCandidatesSelectionV2(this.toponymsTEI, candidatesFromKB, numberOfCandidate,
				candidateSelectionThreshold);
		
//		toponymsTEI.forEach(t -> {
//			logger.info(t.getName());
//			if (t.getScoreCriterionToponymCandidate() == null || t.getScoreCriterionToponymCandidate().isEmpty())
//				logger.info("Pas de candidat");
//			else
//				t.getScoreCriterionToponymCandidate().forEach(s -> logger.info(s.getCandidate().getResource() + " (" + s.getValue() + ")"));
//		});
		
		this.shortestPaths = new ConcurrentHashMap<>();

		this.rlspCalculous = new HashSet<>(); // utilié à revoir
		this.resourcesToUseForSP = new HashSet<>();
		this.scrList = new ArrayList<>();
		this.nil = kbSubgraph.createResource("http://data.ign.fr/id/propagation/Place/nil");
	}
	private class Tmp{
		String candidateResource;
		String candidateName;
		String candidateLabel;
		
		double scoreToken;
		double scoreDamLev;
	}
	public void TestFunction() {
		Map<String, List<Tmp>> map = new HashMap<>();
		double slev = 0.0;
		for (Toponym topo : toponymsTEI) {		
			String topoName = topo.getName();	
			if (!map.containsKey(topoName)) {
				List<Tmp> tmpList = new ArrayList<>();
				for (Candidate candidate : candidatesFromKB) {
					String candidateName = candidate.getName();
					String candidateLabel = candidate.getLabel();
					if (candidateLabel != null || candidateName != null) {
						Tmp tmp = new Tmp();
						tmp.candidateResource = candidate.getResource().toString();
						tmp.candidateLabel = candidate.getLabel();
						tmp.candidateName = candidate.getName();
						tmp.scoreToken = Double.POSITIVE_INFINITY;
						tmp.scoreDamLev = Double.POSITIVE_INFINITY;
						if (candidateLabel != null) {
							StringComparisonDamLev scdl = new StringComparisonDamLev();
							tmp.scoreDamLev = scdl.computeSimilarity(topoName, candidateLabel);
							TokenWiseSimilarity tws = new TokenWiseSimilarity(topoName, candidateLabel, slev);
							tmp.scoreToken = tws.calcule();
						}
						if (candidateName != null) {
							StringComparisonDamLev scdl = new StringComparisonDamLev();
							tmp.scoreDamLev = Double.max(scdl.computeSimilarity(topoName, candidateName), tmp.scoreDamLev);
							TokenWiseSimilarity tws = new TokenWiseSimilarity(topoName, candidateName, slev);
							tmp.scoreToken = Double.max(tws.calcule(), tmp.scoreToken);
						}
						tmpList.add(tmp);
					}
				}
				if (!tmpList.isEmpty())
					map.put(topoName, tmpList);
			}
		}
		long limit = 100;
		for (Entry<String, List<Tmp>> e : map.entrySet()) {
			List<Tmp> listDamLev = e.getValue().stream().sorted((a, b) -> Double.compare(b.scoreDamLev, a.scoreDamLev)).limit(limit).collect(Collectors.toList());
			List<Tmp> listToken = e.getValue().stream().sorted((a, b) -> Double.compare(b.scoreToken, a.scoreToken)).limit(limit).collect(Collectors.toList());
			for (int i = 0; i < limit; i++) {
				Tmp damLev = listDamLev.get(i);
				Tmp token = listToken.get(i);
				logger.info(e.getKey() + " : " + damLev.candidateResource + "(" + damLev.scoreDamLev + "/" + damLev.scoreToken + ")" + "\t "
						 + token.candidateResource + "(" + token.scoreDamLev + "/" + token.scoreToken + ")");
			}
		}
	}
	
	/**
	 * Compute.
	 */
	public Set<Toponym> compute() {
		deleteUselessAlts(toponymsTEI, teiRdf);

		logger.info("Préparation de la création des mini graphes pour chaques séquences");
		List<QuerySolution> querySolutions = getGraphTuples(teiRdf);
		List<QuerySolutionEntry> querySolutionEntries = getQuerySolutionEntries(querySolutions).stream()
				.sorted(comparatorQuerySolutionEntry).collect(Collectors.toList());
		List<Resource> sequences = querySolutionEntries.stream().map(q -> q.getSequence()).distinct()
				.collect(Collectors.toList());

		computeAlgorithm(sequences, querySolutionEntries, teiRdf, kbSubgraph, kbSource, toponymsTEI);

		return toponymsTEI;
	}

	private float getLatitude(Resource r) {
		float result = 0f;
		Statement s = kbSource.getProperty(r, propLat);

		if (s == null) {
			s = kbSource.getProperty(r, geoLat);
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
	
	private boolean hasLatAndLong(Resource r) {
		Statement sLat = kbSource.getProperty(r, propLat);
		if (sLat == null) {
			sLat = kbSource.getProperty(r, geoLat);
		}
		Statement sLong = kbSource.getProperty(r, propLong);
		if (sLong == null) {
			sLong = kbSource.getProperty(r, geoLong);
		}
		return sLat != null && sLong != null;
	}

	private float getLongitude(Resource r) {
		float result = 0f;
		Statement s = kbSource.getProperty(r, propLong);

		if (s == null) {
			s = kbSource.getProperty(r, geoLong);
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
	private Model addCoordinates(Model original, Resource resource) {
		if (!hasLatAndLong(resource))
			return original;
		float lat = getLatitude(resource);
		float longitude = getLongitude(resource);
		Model model = cloneModel(original);
		Statement latStatement = model.createLiteralStatement(resource, propLat, lat);
		Statement longStatement = model.createLiteralStatement(resource, propLong, longitude);
		model.add(latStatement);
		model.add(longStatement);
		return model;
	}
	
	/**
	 * Compute the heart of the algorithm. Main function.
	 *
	 * @param sequences
	 *            the sequences
	 * @param querySolutionEntries
	 *            the query solution entries
	 * @param teiRdf
	 *            the tei rdf
	 * @param kbSubgraph
	 *            the kb subgraph
	 * @param kbSource
	 *            the kb source
	 * @param toponymsTEI
	 *            the toponyms TEI
	 */
	private void computeAlgorithm(List<Resource> sequences, List<QuerySolutionEntry> querySolutionEntries, Model teiRdf,
			Model kbSubgraph, Model kbSource, Set<Toponym> toponymsTEI) {
		logger.info("V3");
		logger.info("Traitement des mini graphes des séquences");
		int seqCount = 1;
		List<List<Model>> altsBySeq = new ArrayList<>();
		for (Resource sequence : sequences) {
			logger.info("Traitement de la séquence " + seqCount + "/" + sequences.size());
			seqCount++;
			Model currentModel = getModelsFromSequence(querySolutionEntries, sequence);
//			saveModelToFile(workingDirectory + "testAlt_" + seqCount + "_original.n3", currentModel, "N3");
			currentModel = addRlsp(currentModel, teiRdf);
			List<Model> alts = explodeAltsV5(currentModel);
//			int g = 0;
//			for (Model model : alts) {
//				saveModelToFile(workingDirectory + "testAlt_" + seqCount + "_" + g + ".n3", model, "N3");
//				g++;
//			}
			altsBySeq.add(alts);
		}

		List<MatchingResult> results = new ArrayList<>();
		seqCount = 1;
		for (List<Model> alts : altsBySeq.stream()
				 .sorted((l1, l2) -> Integer
				 .compare(l2.get(0).listStatements().toList().size(),
				 l1.get(0).listStatements().toList().size()))
//				 .skip(8)
//				 .limit(2)
				 .collect(Collectors.toList())) {
			logger.info("Traitement de la séquence " + seqCount + "/" + altsBySeq.size());
			logger.info(alts.size() + " mini graphes à traiter pour cette séquence.");

			List<Statement> statements = alts.stream().map(l -> l.listStatements().toList()).flatMap(l -> l.stream())
					.collect(Collectors.toList());
			getResourcesToUseForSP(statements);
			logger.info("Chemins à charger : " + resourcesToUseForSP.size());
			resourcesToUseForSP.parallelStream().forEach(resourceSP -> getSP(resourceSP));
			List<MatchingResult> resultsForCurrentSeq = new ArrayList<>();
			logger.info("Chemins chargé.");
			for (Model miniGraph : alts) {
				List<IPathMatching> path = graphMatching(kbSubgraph, miniGraph, toponymsTEI, kbSource);
				if (path != null && !path.isEmpty())
					resultsForCurrentSeq.add(new MatchingResult(miniGraph, path, totalCostPath(path)));
			}
			if (!resultsForCurrentSeq.isEmpty()) {
				MatchingResult bestPath = getBestPath(resultsForCurrentSeq);
				
				// On corrige les référents, car les rdf:Alt peuvent poser problème
				List<IPathMatching> path = bestPath.getEditionPath();
				for (IPathMatching iPathMatching : path) {
					if (iPathMatching.getClass() == Substitution.class) {
						// on récupère le toponym
						// on récupère son éventuel jumeau d'alt
						// on met le referent du jumeau à null
						Substitution sub = (Substitution)iPathMatching;
						Toponym resolvedTopo = toponymsTEI.stream().filter(t -> areResourcesEqual(t.getResource(), sub.getDeletedNode())).findFirst().get();
						if (toponymsTEI.stream().filter(t -> t.getXmlId() == resolvedTopo.getXmlId()).count() > 1) {
							// il a un jumeau
							Toponym twin = toponymsTEI.stream().filter(t -> t.getXmlId() == resolvedTopo.getXmlId() && !areResourcesEqual(t.getResource(), sub.getDeletedNode())).findFirst().get();
							twin.setReferent(null);
							twin.setSubstitutionCostResult(null);
						}
					} else if (iPathMatching.getClass() == Deletion.class) {
						// on met le referent à nil
						Deletion del = (Deletion)iPathMatching;
						Toponym resolvedTopo = toponymsTEI.stream().filter(t -> areResourcesEqual(t.getResource(), del.getDeletedNode())).findFirst().get();
						resolvedTopo.setReferent(nil);
						if (toponymsTEI.stream().filter(t -> t.getXmlId() == resolvedTopo.getXmlId()).count() > 1) {
							// il a un jumeau
							Toponym twin = toponymsTEI.stream().filter(t -> t.getXmlId() == resolvedTopo.getXmlId() && !areResourcesEqual(t.getResource(), del.getDeletedNode())).findFirst().get();
							twin.setReferent(null);
							twin.setSubstitutionCostResult(null);
						}
					}
				}
				
				//saveModelToFile(workingDirectory + "seq_original_" + seqCount + ".n3", bestPath.getModel(), "N3");
				//updateAndSaveModelWithResultsV2(bestPath.getModel(), workingDirectory + "seq_" + seqCount + ".n3");
				results.add(bestPath);
				// logger.info(bestPath.getCostEdition());
				// bestPath.getEditionPath().forEach(step -> {
				// if (step.getClass() != Insertion.class)
				// logger.info(step);
				// });
			}
			// On vide la liste des plus courts chemins après chaque séquence
			// pour éviter les problèmes de RAM
			shortestPaths.clear();
			seqCount++;
		}
		rlspCalculous.stream().sorted().forEach(logger::info);
		Model teiCopy = cloneModel(teiRdf);
		for (Toponym toponym : toponymsTEI) {
			String score = "";
			SubstitutionCostResult scr = toponym.getSubstitutionCostResult();
			if (scr != null) {
				score = "(" + scr.getLabelCost() + " / " + scr.getLinkCost() + " / " + scr.getRLSPCost() + ")";
			}
			logger.info(toponym.getResource() + " (" + toponym.getName() + ")" + " -> " + toponym.getReferent() + " "
					+ score);
		}
		updateAndSaveModelWithResults(teiCopy, workingDirectory + "teiCopy.n3");
	}

	/**
	 * Update and save the model the with results.
	 *
	 * @param graph the graph
	 * @param fileName the file name
	 */
	private void updateAndSaveModelWithResults(Model graph, String fileName) {
		Model graphCopy = cloneModel(graph);
		
		graphCopy = removesAltFromFinalTei(graphCopy);
		
		for (Toponym toponym : toponymsTEI) {
			if (toponym.getReferent() != null) {
				renameResource(graphCopy, toponym.getResource(), toponym.getReferent().toString());
				graphCopy = addCoordinates(graphCopy, toponym.getReferent());
			}
		}
		
		
		saveModelToFile(fileName, graphCopy, "N3");
	}
	
	/**
	 * Update and save the mini graph model with the results of the desambiguisation. V 2.
	 *
	 * @param graph the graph
	 * @param fileName the file name
	 */
	private void updateAndSaveModelWithResultsV2(Model graph, String fileName) {
		//TODO fonction non utilisée pour le moment car ne marche pas
		// elle censé remplacer les resources place/0 etc par le leur référent et supprimer dans les alts ceux qui ne sont pas sélectionnés
		Model graphCopy = cloneModel(graph);
		Map<String, List<Toponym>> toponymsById = toponymsTEI.stream()
				.collect(Collectors.groupingBy((Toponym t) -> t.getXmlId().toString()));
		for (Entry<String, List<Toponym>> entry : toponymsById.entrySet()) {
			if (entry.getValue().size() == 1 && entry.getValue().get(0).getReferent() != null)
				renameResource(graphCopy, entry.getValue().get(0).getResource(), entry.getValue().get(0).getReferent().toString());
			else {
				//TODO revoir cette fonction elle pose pb
				Optional<Toponym> toponym = entry.getValue().stream().filter(t -> t.getReferent() != null).findFirst();
				Optional<Toponym> toponymToRemove = entry.getValue().stream().filter(t -> t.getReferent() == null).findFirst();
				if (toponym.isPresent() && toponym.get().getReferent() != null && toponymToRemove.isPresent()) {
					deleteResource(graphCopy, toponymToRemove.get().getResource());
					List<Statement> sToreplace = graphCopy.listStatements(null, graphCopy.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"), (RDFNode)toponym.get().getResource()).toList();
					sToreplace.addAll(graphCopy.listStatements(null, graphCopy.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2"), (RDFNode)toponym.get().getResource()).toList());
					List<Statement> sToAdd = new ArrayList<>();
					//TODO modifier sToreplace car tjs vide
					for (Statement statement : sToreplace) {
						sToAdd.add(graphCopy.createStatement(statement.getSubject(), statement.getPredicate(), toponym.get().getReferent()));
					}
					renameResource(graphCopy, toponym.get().getResource(), toponym.get().getReferent().toString());
					graphCopy.remove(sToreplace);
					graphCopy.add(sToAdd);
				}
			}
		}
		for (Toponym toponym : toponymsTEI) {
			if (toponym.getReferent() != null)
				renameResource(graphCopy, toponym.getResource(), toponym.getReferent().toString());
		}
		
		
		saveModelToFile(fileName, graphCopy, "N3");
	}

	private void saveModelToFile(String fileName, Model model, String lang) {
		File file = new File(fileName);
		try {
			model.write(new java.io.FileOutputStream(file), lang);
		} catch (FileNotFoundException e) {
			logger.error(e);
		}
	}

	/**
	 * Gets the resources to use for SP.
	 *
	 * @param statements
	 *            the statements
	 * @return the resources to use for SP
	 */
	private Set<Resource> getResourcesToUseForSP(List<Statement> statements) {
		// Liste des chemin (start = subject du statement, end = object du
		// statement) qu'il faudra calculer à un moment ou un autre
		List<Statement> statementsToProcess = new ArrayList<>();
		for (Statement statement : statements) {
			Optional<Toponym> subjectOpt = toponymsTEI.stream()
					.filter(t -> areResourcesEqual(t.getResource(), statement.getSubject())).findFirst();
			Optional<Toponym> objectOpt = toponymsTEI.stream()
					.filter(t -> areResourcesEqual(t.getResource(), (Resource) statement.getObject())).findFirst();
			if (!subjectOpt.isPresent() || ! objectOpt.isPresent())
				continue;
			Toponym subject = subjectOpt.get();
			Toponym object = objectOpt.get();
			for (CriterionToponymCandidate critSubj : subject.getScoreCriterionToponymCandidate()) {
				for (CriterionToponymCandidate critObj : object.getScoreCriterionToponymCandidate()) {
					statementsToProcess.add(kbSubgraph.createStatement(critSubj.getCandidate().getResource(),
							statement.getPredicate(), critObj.getCandidate().getResource()));
				}
			}
		}
		Map<Resource, List<Statement>> statementsBySubject = statementsToProcess.stream()
				.collect(Collectors.groupingBy((Statement s) -> s.getSubject()));
		Map<Resource, List<Statement>> statementsByObject = statementsToProcess.stream()
				.collect(Collectors.groupingBy((Statement s) -> (Resource) s.getObject()));

		for (Entry<Resource, List<Statement>> e : statementsBySubject.entrySet()) {
			if (statementsByObject.containsKey(e.getKey())) {
				List<Statement> subjStatements = e.getValue();
				List<Statement> objStatements = statementsByObject.get(e.getKey());
				subjStatements.addAll(objStatements);
			}
		}
		// statementsBySubject contient maintenant les statements dont la clé
		// est sujet ou objet
		resourcesToUseForSP.clear();
		Set<Statement> statementsToProcessSet = new HashSet<>(statementsToProcess);
		// on suprime de statementsToProcess les statements des resources qui en
		// ont le plus à celles qui en ont le moins, pour voir combien on doit
		// en charger en mémoire pour avoir accès à tout
		for (Entry<Resource, List<Statement>> e : statementsBySubject.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
				.collect(Collectors.toList())) {
			if (statementsToProcessSet.isEmpty())
				break; // on a terminé
			List<Statement> statementsToRemove = e.getValue();
			if (statementsToProcessSet.removeAll(statementsToRemove)) {
				resourcesToUseForSP.add(e.getKey());
			}
		}
		return resourcesToUseForSP;
	}

	// private Entry<Float, List<IPathMatching>> getBestPath(Map<Float,
	// List<IPathMatching>> resultsForCurrentSeq) {
	// Float min =
	// resultsForCurrentSeq.keySet().stream().min(Float::compare).get();
	// return resultsForCurrentSeq.entrySet().stream().filter(e -> e.getKey() ==
	// min).findFirst().get();
	// }
	private MatchingResult getBestPath(List<MatchingResult> matchingResults) {
		return matchingResults.stream().min((a, b) -> Float.compare(a.getCostEdition(), b.getCostEdition())).get();
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
	 * !!!! Instantiates a new graph matching. This constructor is used only for tests purpose. !!!!
	 *
	 * @param workingDirectory the working directory
	 */
	public GraphMatching(String workingDirectory) {
		this.candidatesFromKB = null;
		this.workingDirectory = workingDirectory;
		toponymsTEI = null;
		teiRdf = null;
		subjectsOfSubgraph = null;
		shortestPaths = null;
		serializationDirectory = null;
		scrList = null;
		rlspWeight = 0f;
		rlspCalculous = null;
		resourcesToUseForSP = null;
		resourcesIndexAPSP = null;
		nil = null;
		linkWeight = 0f;
		labelWeight = 0f;
		typeWeight = 0f;
		kbSubgraph = null;
		kbSource = null;
		Model model = ModelFactory.createDefaultModel().read("C:\\modelOriginal - Copie.n3");		
		explodeAltsV5(model);
	}
	public GraphMatching(Document teiSource, String dbPediaRdfFilePath) {
		this.teiRdf = RDFUtil.getModel(teiSource); // BUG EN RELEASE
		// this.teiRdf = ModelFactory.createDefaultModel().read(workingDirectory
		// + "temp7.n3");
		this.toponymsTEI = getToponymsFromTei(teiRdf);
		this.kbSource = ModelFactory.createDefaultModel().read(dbPediaRdfFilePath);
		this.candidatesFromKB = getCandidatesFromKB(this.kbSource);
		this.workingDirectory = null;
		subjectsOfSubgraph = null;
		shortestPaths = null;
		serializationDirectory = null;
		scrList = null;
		rlspWeight = 0f;
		rlspCalculous = null;
		resourcesToUseForSP = null;
		resourcesIndexAPSP = null;
		nil = null;
		linkWeight = 0f;
		labelWeight = 0f;
		typeWeight = 0f;
		kbSubgraph = null;
		TestFunction();
	}
	
	private Model transformModelByKeepingR1(Model original, String r1, String r2) {
		// F(model, r1, r2, altsR1R2)
		//  clone <- clone de model
		Model model = cloneModel(original);
		// on supprime de clone tous les statements de r2
		Resource r2Resource = model.getResource(r2);
		List<Statement> statementsToRemove = model.listStatements(r2Resource, null, (RDFNode)null).toList();
		statementsToRemove.addAll(model.listStatements(null, null, (RDFNode)r2Resource).toList());
		model.remove(statementsToRemove);
		// on récupère les statements des alts de r1
		Resource r1Resource = model.getResource(r1);
		List<Resource> alts = model.listStatements(null, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"), (RDFNode)r1Resource).toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		List<Statement> altsSubject = new ArrayList<>();
		for (Resource alt : alts) {
			altsSubject.addAll(model.listStatements(alt, null, (RDFNode)null).toList());
		}
		List<Statement> altsObject = new ArrayList<>();
		for (Resource alt : alts) {
			altsObject.addAll(model.listStatements(null, null, (RDFNode)alt).toList());
		}
		// il faut garder uniquement les statements de altsSubject et altsObject qui nous intéressent (qui sont lié à d'autres resources) et supprimer les autres
		List<Statement> newStatements = new ArrayList<>();
		for (Statement statement : altsSubject) {
			if (statement.getPredicate().getNameSpace().equals(IGN_NS)) {
				Statement newStatement = model.createStatement(r1Resource, statement.getPredicate(), statement.getObject());
				newStatements.add(newStatement);
			}
		}
		for (Statement statement : altsObject) {
			if (statement.getPredicate().getNameSpace().equals(IGN_NS)) {
				Statement newStatement = model.createStatement(statement.getSubject(), statement.getPredicate(), r1Resource);
				newStatements.add(newStatement);
			}
		}
		// par mesure de sécurité on remet les statements qui impliquent r1 ? 		
		// on supprime ces statements de clone et on les rajoute en changeant le sujet ou l'objet (l'alt) par r1
		model.remove(altsObject);
		model.remove(altsSubject);
		model.add(newStatements);
		// on supprime r1 type alt de clone
		
		
		return model;
		
	}
	private Model transformModelByKeepingWaypoint1(Model original, String r1, String r2) {
		// F(model, r1, r2, altsR1R2)
		//  clone <- clone de model
		Model model = cloneModel(original);
		// on supprime de clone tous les statements de r2
		Resource r2Resource = model.getResource(r2);
		List<Statement> statementsToRemove = model.listStatements(r2Resource, null, (RDFNode)null).toList();
		statementsToRemove.addAll(model.listStatements(null, null, (RDFNode)r2Resource).toList());
		model.remove(statementsToRemove);
		// on récupère les statements des alts de r1
		Resource r1Resource = model.getResource(r1);
		Resource waypoint1 = model.listStatements(null, spatialReference, (RDFNode)r1Resource).toList().stream().map(s -> s.getSubject()).findFirst().get();//.collect(Collectors.toList());
		List<Resource> alts = model.listStatements(null, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"), (RDFNode)waypoint1).toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		List<Statement> altsSubject = new ArrayList<>();
		for (Resource alt : alts) {
			altsSubject.addAll(model.listStatements(alt, null, (RDFNode)null).toList());
		}
		List<Statement> altsObject = new ArrayList<>();
		for (Resource alt : alts) {
			altsObject.addAll(model.listStatements(null, null, (RDFNode)alt).toList());
		}
		// il faut garder uniquement les statements de altsSubject et altsObject qui nous intéressent (qui sont lié à d'autres resources) et supprimer les autres
		List<Statement> newStatements = new ArrayList<>();
//		for (Statement statement : altsSubject) {
//			if (statement.getPredicate().getNameSpace().equals(IGN_NS)) {
//				Statement newStatement = model.createStatement(waypoint1, statement.getPredicate(), statement.getObject());
//				newStatements.add(newStatement);
//			}
//		}
		for (Statement statement : altsObject) {
			if (statement.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1") || statement.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2")) {
				Statement newStatement = model.createStatement(statement.getSubject(), statement.getPredicate(), waypoint1);
				newStatements.add(newStatement);
			}
		}
		// par mesure de sécurité on remet les statements qui impliquent r1 ? 		
		// on supprime ces statements de clone et on les rajoute en changeant le sujet ou l'objet (l'alt) par r1
		model.remove(altsObject);
		model.remove(altsSubject);
		model.add(newStatements);
		// on supprime r1 type alt de clone
		
		
		return model;
		
	}
	
	
	private Model transformModelByKeepingR2(Model original, String r1, String r2) {
		// F(model, r1, r2, altsR1R2)
		//  clone <- clone de model
		Model model = cloneModel(original);
		// on supprime de clone tous les statements de r2
		Resource r1Resource = model.getResource(r1);
		List<Statement> statementsToRemove = model.listStatements(r1Resource, null, (RDFNode)null).toList();
		statementsToRemove.addAll(model.listStatements(null, null, (RDFNode)r1Resource).toList());
		model.remove(statementsToRemove);
		// on récupère les statements des alts de r1
		Resource r2Resource = model.getResource(r2);
		List<Resource> alts = model.listStatements(null, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2"), (RDFNode)r2Resource).toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		List<Statement> altsSubject = new ArrayList<>();
		for (Resource alt : alts) {
			altsSubject.addAll(model.listStatements(alt, null, (RDFNode)null).toList());
		}
		List<Statement> altsObject = new ArrayList<>();
		for (Resource alt : alts) {
			altsObject.addAll(model.listStatements(null, null, (RDFNode)alt).toList());
		}
		// il faut garder uniquement les statements de altsSubject et altsObject qui nous intéressent (qui sont lié à d'autres resources) et supprimer les autres
		List<Statement> newStatements = new ArrayList<>();
		for (Statement statement : altsSubject) {
			if (statement.getPredicate().getNameSpace().equals(IGN_NS)) {
				Statement newStatement = model.createStatement(r2Resource, statement.getPredicate(), statement.getObject());
				newStatements.add(newStatement);
			}
		}
		for (Statement statement : altsObject) {
			if (statement.getPredicate().getNameSpace().equals(IGN_NS)) {
				Statement newStatement = model.createStatement(statement.getSubject(), statement.getPredicate(), r2Resource);
				newStatements.add(newStatement);
			}
		}
		// par mesure de sécurité on remet les statements qui impliquent r1 ? 		
		// on supprime ces statements de clone et on les rajoute en changeant le sujet ou l'objet (l'alt) par r1
		model.remove(altsObject);
		model.remove(altsSubject);
		model.add(newStatements);
		// on supprime r1 type alt de clone
		
		
		return model;
		
	}
	private Model transformModelByKeepingWaypoint2(Model original, String r1, String r2) {
		// F(model, r1, r2, altsR1R2)
		//  clone <- clone de model
		Model model = cloneModel(original);
		// on supprime de clone tous les statements de r2
		Resource r1Resource = model.getResource(r1);
		List<Statement> statementsToRemove = model.listStatements(r1Resource, null, (RDFNode)null).toList();
		statementsToRemove.addAll(model.listStatements(null, null, (RDFNode)r1Resource).toList());
		model.remove(statementsToRemove);
		// on récupère les statements des alts de r1
		Resource r2Resource = model.getResource(r2);
		Resource waypoint2 = model.listStatements(null, spatialReference, (RDFNode)r2Resource).toList().stream().map(s -> s.getSubject()).findFirst().get();//.collect(Collectors.toList());
		List<Resource> alts = model.listStatements(null, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2"), (RDFNode)waypoint2).toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		List<Statement> altsSubject = new ArrayList<>();
		for (Resource alt : alts) {
			altsSubject.addAll(model.listStatements(alt, null, (RDFNode)null).toList());
		}
		List<Statement> altsObject = new ArrayList<>();
		for (Resource alt : alts) {
			altsObject.addAll(model.listStatements(null, null, (RDFNode)alt).toList());
		}
		// il faut garder uniquement les statements de altsSubject et altsObject qui nous intéressent (qui sont lié à d'autres resources) et supprimer les autres
		List<Statement> newStatements = new ArrayList<>();
//		for (Statement statement : altsSubject) {
//			if (statement.getPredicate().getNameSpace().equals(IGN_NS)) {
//				Statement newStatement = model.createStatement(waypoint2, statement.getPredicate(), statement.getObject());
//				newStatements.add(newStatement);
//			}
//		}
		for (Statement statement : altsObject) {
			if (statement.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1") || statement.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2")) {
				Statement newStatement = model.createStatement(statement.getSubject(), statement.getPredicate(), waypoint2);
				newStatements.add(newStatement);
			}
		}
		// par mesure de sécurité on remet les statements qui impliquent r1 ? 		
		// on supprime ces statements de clone et on les rajoute en changeant le sujet ou l'objet (l'alt) par r1
		model.remove(altsObject);
		model.remove(altsSubject);
		model.add(newStatements);
		// on supprime r1 type alt de clone
		
		
		return model;
		
	}

	/**
	 * Removes alt from final tei.
	 *
	 * @param tei the tei
	 * @return the model
	 */
	private Model removesAltFromFinalTei(Model tei) {
		Model model = cloneModel(tei);
		saveModelToFile(workingDirectory + "TEIbeforeRemovingAlts.n3", model, "N3");
		List<Resource> alts = model.listStatements(null, null, (RDFNode)model.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Alt")).toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		if (alts == null || alts.isEmpty())
			return model;
		Map<String, String> r1AndR2 = new HashMap<>();
		Map<String, Resource> r1Alt = new HashMap<>();
		for (Resource alt : alts) {
			Resource waypoint1 = (Resource) model.listStatements(alt, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"), (RDFNode)null).toList().stream().findFirst().get().getObject();
			Resource waypoint2 = (Resource) model.listStatements(alt, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2"), (RDFNode)null).toList().stream().findFirst().get().getObject();
			
			if (waypoint1.hasProperty(spatialReference) && waypoint2.hasProperty(spatialReference)) {
				Resource r1 = (Resource) model.listStatements(waypoint1, spatialReference, (RDFNode)null).toList().stream().findFirst().get().getObject();
				Resource r2 = (Resource) model.listStatements(waypoint2, spatialReference, (RDFNode)null).toList().stream().findFirst().get().getObject();
				r1AndR2.put(r1.toString(), r2.toString());
				r1Alt.put(r1.toString(), alt);
			}
		}
		for (Entry<String, String> entry : r1AndR2.entrySet()) {
			String r1 = entry.getKey();
			String r2 = entry.getValue();
			if (r1 == null || r1.isEmpty())
				continue;
			// il faut savoir qui de r1 ou de r2 on gardera et donc rechercher parmis les topo du tei
			//logger.info(r1);
			Toponym t1 = toponymsTEI.stream().filter(t -> t.getResource() != null && t.getResource().toString().equals(r1)).findFirst().get();
//			Toponym t2 = toponymsTEI.stream().filter(t -> t.getResource().toString().equals(r2)).findFirst().get();
			boolean keepR1RemoveR2;
			//TODO vérifier que c'est bien r1 et r2 (resource du topo) qu'il faut mettre et pas le référent
			if (t1.getReferent() != null)
				keepR1RemoveR2 = true;
			else
				keepR1RemoveR2 = false;
			if (keepR1RemoveR2) {
				model = transformModelByKeepingWaypoint1(model, r1, r2);
			} else {
				model = transformModelByKeepingWaypoint2(model, r1, r2);
			}
		}
		return model;
	}
	private List<Model> explodeAltsV5(Model model) {
		//saveModelToFile(workingDirectory + "original.n3", model, "N3");
		List<Model> results = new ArrayList<>();
		results.add(model);
		List<Resource> alts = model.listStatements(null, null, model.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#Alt")).toList().stream().map(s -> s.getSubject()).collect(Collectors.toList());
		if (alts == null || alts.isEmpty())
			return results;
		Map<String, String> r1AndR2 = new HashMap<>();
		for (Resource alt : alts) {
			Resource r1 = (Resource) model.listStatements(alt, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_1"), (RDFNode)null).toList().stream().findFirst().get().getObject();
			Resource r2 = (Resource) model.listStatements(alt, model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#_2"), (RDFNode)null).toList().stream().findFirst().get().getObject();
			r1AndR2.put(r1.toString(), r2.toString());
		}
		for (Entry<String, String> entry : r1AndR2.entrySet()) {
			String r1 = entry.getKey();
			String r2 = entry.getValue();
			List<Model> resultsTmp = new ArrayList<>();
			for (Model model2 : results) {
				Model newModel = transformModelByKeepingR1(model2, r1, r2);		
				resultsTmp.add(newModel);
			}
			for (Model model2 : results) {
				Model newModel = transformModelByKeepingR2(model2, r1, r2);				
				resultsTmp.add(newModel);		
			}
			results.clear();
			results.addAll(resultsTmp);
		}
		
//		int i = 0;
//		for (Model model2 : results) {
//			saveModelToFile(workingDirectory + i + ".n3", model2, "N3");
//			i++;
//		}
		
		return results;
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
					List<Statement> statements = results.get(0).listStatements().toList().stream().filter(
							p -> (p.getSubject().toString().equals(alt.toString()) || (p.getObject().isResource()
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
					List<Statement> statements = results.get(0).listStatements().toList().stream().filter(
							p -> (p.getSubject().toString().equals(alt.toString()) || (p.getObject().isResource()
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
				if (currentAlt != null && currentAlt.getSpatialReferenceAlt() != null
						&& current.getSpatialReferenceAlt() != null) { // current
																		// est
																		// une
																		// Alt
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
	 * Transform the QuerySolutions from getGraphTuples to usable objects
	 * (QuerySolutionEntry).
	 *
	 * @param querySolutions
	 *            the query solutions
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
			querySolutionEntries.add(new QuerySolutionEntry(sequence, route, bag, waypoint, spatialReferenceResource,
					spatialReferenceAlt, id));
		}
		return querySolutionEntries;
	}

	/**
	 * Query the TEI graph for sequence, route, bag, waypoint, spatial reference
	 * and id.
	 *
	 * @param teiModel
	 *            the tei model
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
	 * @param result
	 *            the result
	 * @param teiRdf
	 *            the tei rdf
	 */
	private void deleteUselessAlts(Set<Toponym> result, Model teiRdf) {
		// certaines Alt on une de leur possibilité qui n'a pas de candidat.
		// Elle seront tjrs préférées aux possibilités avec candidats.
		// Il faut donc les supprimer de Set<Toponym> result et de Model teiRdf
		for (Entry<Integer, List<Toponym>> toponymEntry : result.stream()
				.collect(Collectors.groupingBy((Toponym t) -> t.getXmlId())).entrySet().stream()
				.filter(e -> e.getValue().size() > 1
						&& e.getValue().stream().anyMatch(p -> p.getScoreCriterionToponymCandidate().isEmpty())
						&& e.getValue().stream().anyMatch(p -> !p.getScoreCriterionToponymCandidate().isEmpty()))
				.collect(Collectors.toList())) {
			Toponym toponymToRemove = toponymEntry.getValue().stream()
					.filter(f -> f.getScoreCriterionToponymCandidate().isEmpty()).findFirst().get();
			Toponym toponymToKeep = toponymEntry.getValue().stream()
					.filter(f -> !f.getScoreCriterionToponymCandidate().isEmpty()).findFirst().get();
			List<Statement> statementsFromNodeToKeep = teiRdf
					.listStatements(null, spatialReference, toponymToKeep.getResource()).toList();
			Resource blankNodeOfNodeToKeep = statementsFromNodeToKeep.get(0).getSubject();
			if (toponymToRemove.getScoreCriterionToponymCandidate().isEmpty() && result.remove(toponymToRemove)) {
				Optional<Statement> sOpt = teiRdf.listStatements(null, null, toponymToRemove.getResource()).toList()
						.stream().findFirst();
				if (sOpt.isPresent()) {
					Statement s = sOpt.get();
					Optional<Statement> altStatement = teiRdf.listStatements(null, null, s.getSubject()).toList()
							.stream().findFirst();
					if (altStatement.isPresent()) {
						Alt alt = teiRdf.getAlt(altStatement.get().getSubject());
						List<Statement> statementsToRemove = teiRdf.listStatements(null, null, alt).toList();
						List<Statement> statementsToAdd = new ArrayList<>();
						for (Statement statement : statementsToRemove) {
							statementsToAdd.add(teiRdf.createStatement(statement.getSubject(), statement.getPredicate(),
									blankNodeOfNodeToKeep));
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
	 * @param model
	 *            the model
	 * @param resource
	 *            the resource
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
	 * @param toponymsTEI
	 *            the toponyms tei
	 * @param candidatesFromKB
	 *            the candidates from kb
	 * @param numberOfCandidate
	 *            the number of candidate
	 * @param threshold
	 *            the threshold
	 * @return the candidates selection
	 */
	private Set<Toponym> getCandidatesSelection(Set<Toponym> toponymsTEI, List<Candidate> candidatesFromKB,
			Integer numberOfCandidate, float threshold) {
		logger.info("Sélection des candidats (nombre de candidats : " + numberOfCandidate + ")");
		List<Candidate> candidatesFromKBCleared = candidatesFromKB.stream()
				.filter(c -> c != null && c.getTypes() != null && (c.getName() != null || c.getLabel() != null))
				.collect(Collectors.toList());

		Map<String, List<Candidate>> candidatesByType = new ConcurrentHashMap<>();
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
			final Map<ToponymType, List<Toponym>> toponymsByType = toponymsWithLabel.getValue().stream()
					.collect(Collectors.groupingBy((Toponym s) -> s.getType()));
			computeCandidates(toponymsWithLabel, toponymsByType.entrySet(), candidatesByType);
			for (Toponym toponym : toponymsWithLabel.getValue()) {
				toponym.clearAndAddAllScoreCriterionToponymCandidate(
						toponym.getScoreCriterionToponymCandidate().stream().filter(s -> s != null)
								.filter(t -> t.getValue() >= threshold)
								.sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
								.limit(Math.min(numberOfCandidate, toponym.getScoreCriterionToponymCandidate().size()))
								.collect(Collectors.toList()));
				result.add(toponym);
			}
			logger.info((count.getAndIncrement() + 1) + " / " + total);
		});
		
//		Toponym topo = result.stream().filter(r -> r.getXmlId() == 4).findFirst().get();
//		topo.getScoreCriterionToponymCandidate().forEach(p -> logger.info(p.getCandidate().getResource() + " " +p.getValue()));
		
		return result;
	}
	
	/**
	 * Gets the candidates selection for each toponym. V 2. -> don't use the type
	 *
	 * @param toponymsTEI the toponyms TEI
	 * @param candidatesFromKB the candidates from KB
	 * @param numberOfCandidate the number of candidate
	 * @param threshold the threshold
	 * @return the candidates selection V 2
	 */
	private Set<Toponym> getCandidatesSelectionV2(Set<Toponym> toponymsTEI, List<Candidate> candidatesFromKB,
			Integer numberOfCandidate, float threshold) {
		//TODO j'ai l'impression que certains candidats continuent de passer à la trape. Problème dû au parallélisme ?
		logger.info("Sélection des candidats (nombre de candidats : " + numberOfCandidate + ")");
		List<Candidate> candidatesFromKBCleared = Collections.synchronizedList(candidatesFromKB.stream()
				.filter(c -> c != null && c.getTypes() != null && (c.getName() != null || c.getLabel() != null))
				.collect(Collectors.toList()));

		Set<Toponym> result = Collections.synchronizedSet(new HashSet<>());
		final AtomicInteger count = new AtomicInteger();
		// calculs des scores pour chaque candidat de chaque toponyme
		// aggrégation des toponymes sur leur labal
		Map<String, List<Toponym>> toponymsByLabel = Collections.synchronizedMap(toponymsTEI.stream()
				.collect(Collectors.groupingBy((Toponym s) -> s.getName())));
		Criterion criterion = Criterion.scoreText;
		final int total = toponymsByLabel.size();
		toponymsByLabel.entrySet().parallelStream().forEach(toponymsWithLabel -> {
			List<CriterionToponymCandidate> criterionToponymCandidateList = new ArrayList<>();
			final String topoLabel = toponymsWithLabel.getKey();
			Map<String, Float> scoreByLabel = new ConcurrentHashMap<>();
			for (Candidate candidate : candidatesFromKBCleared) {
				// candidatesToCheck.parallelStream().forEach(candidate -> {
				StringComparisonDamLev sc = new StringComparisonDamLev();
				float score = 0f;
				if (candidate.getName() != null && scoreByLabel.containsKey(candidate.getName())) {
					score = scoreByLabel.get(candidate.getName());
				} else if (candidate.getLabel() != null && scoreByLabel.containsKey(candidate.getLabel())) {
					score = scoreByLabel.get(candidate.getLabel());
				} else {
					float score1 = (float) sc.computeSimilarity(topoLabel, candidate.getName());
					float score2 = (float) sc.computeSimilarity(topoLabel, candidate.getLabel());
					if (score1 > score2 && candidate.getName() != null) {
						score = score1;
						scoreByLabel.put(candidate.getName(), score1);
					} else if (candidate.getLabel() != null) {
						score = score2;
						scoreByLabel.put(candidate.getLabel(), score2);
					}
				}
				criterionToponymCandidateList.add(new CriterionToponymCandidate(candidate, score, criterion));
			}
			criterionToponymCandidateList = criterionToponymCandidateList.stream().filter(s -> s != null).filter(t -> t.getValue() >= threshold)
					.sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
					.limit(numberOfCandidate)
					.collect(Collectors.toList());
			for (Toponym toponym : toponymsWithLabel.getValue()) {
				for (CriterionToponymCandidate crit : criterionToponymCandidateList) {
					toponym.addScoreCriterionToponymCandidate(crit);
				}
			}
//			for (Toponym toponym : toponymsWithLabel.getValue()) {
//				toponym.clearAndAddAllScoreCriterionToponymCandidate(toponym.getScoreCriterionToponymCandidate()
//						.stream().filter(s -> s != null).filter(t -> t.getValue() >= threshold)
//						.sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
//						.limit(Math.min(numberOfCandidate, toponym.getScoreCriterionToponymCandidate().size()))
//						.collect(Collectors.toList()));
//				result.add(toponym);
//			}
			logger.info((count.getAndIncrement() + 1) + " / " + total);
		});


		return result;
	}

	/**
	 * Compute candidates.
	 *
	 * @param toponymsWithLabel
	 *            the toponyms with label
	 * @param entries
	 *            the entries
	 * @param candidatesByType
	 *            the candidates by type
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
				for (Candidate candidate : candidatesToCheck) {
				//candidatesToCheck.parallelStream().forEach(candidate -> {
					float score = 0f;
					if (candidate.getName() != null && scoreByLabel.containsKey(candidate.getName())) {
						score = scoreByLabel.get(candidate.getName());
					} else if (candidate.getLabel() != null && scoreByLabel.containsKey(candidate.getLabel())) {
						score = scoreByLabel.get(candidate.getLabel());
					} else {
						float score1 = (float) sc.computeSimilarity(toponymsWithLabel.getKey(), candidate.getName());
						float score2 = (float) sc.computeSimilarity(toponymsWithLabel.getKey(), candidate.getLabel());
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
								new CriterionToponymCandidate(candidate, score, criterion));
					}
				}//);
			}
		}
	}

	/**
	 * Gets the KB type from the TEI type.
	 *
	 * @param type
	 *            the type
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
		DijkstraSP.computeAndSerializeAllPairShortestPath(subjectsOfSubgraph, resourcesIndexProcessed, kbSubgraph,
				serializationDirectory);
	}

	/**
	 * Gets the toponyms from tei.
	 *
	 * @param teiRdf
	 *            the tei rdf
	 * @return the toponyms from tei
	 */
	private Set<Toponym> getToponymsFromTei(Model teiRdf) {
		Set<Toponym> results = new HashSet<>();
		logger.info("Récupération des toponymes du TEI");
		List<QuerySolution> qSolutionsTEI = new ArrayList<>();
		try {
			qSolutionsTEI.addAll(RDFUtil.getQuerySelectResults(teiRdf, PREFIXES + "" + "SELECT DISTINCT * WHERE {"
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
			model = RDFUtil.getQueryConstruct(kbSource,
					PREFIXES + "CONSTRUCT {?s ?p ?o} WHERE {" + "  ?s ?p ?o."
							+ "  FILTER((?p=prop-fr:nord||?p=prop-fr:nordEst||?p=prop-fr:nordOuest||?p=prop-fr:sud"
							+ "  ||?p=prop-fr:sudEst||?p=prop-fr:sudOuest||?p=prop-fr:est||?p=prop-fr:ouest) && !isLiteral(?o))."
							+ "}",
					null);
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

	private Resource renameResource(Model m, final Resource old, final String uri) {
		if (old == null)
			return null;
		Resource oldResource = m.getResource(old.toString());
		if (oldResource == null)
			return null;
		return ResourceUtils.renameResource(oldResource, uri);
	}

	/**
	 * Complete kbSubgraph with symetrics RLSP. eg if a nordEst b then b
	 * sudOuest a
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
	 * @param kbSource
	 *            the kb source
	 * @return the candidates from kb
	 */
	private List<Candidate> getCandidatesFromKB(Model kbSource) {
		List<QuerySolution> qSolutionsKB = new ArrayList<>();
		List<Candidate> result = new ArrayList<>();

		final Model kbSourceFinal = kbSource;
		try {
			qSolutionsKB.addAll(RDFUtil.getQuerySelectResults(kbSourceFinal,
					PREFIXES + "" + "SELECT DISTINCT * WHERE {" + "  OPTIONAL { ?s a ?type . } "
							+ "  OPTIONAL { ?s foaf:name ?name . }" + "  OPTIONAL { ?s rdfs:label ?label . }"
							+ " FILTER (STRSTARTS(STR(?type), 'http://dbpedia.org/ontology/')) . " + "}"));
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
		Map<Resource, List<Candidate>> candidatesByResource = result.stream()
				.filter(p -> p != null && p.getResource() != null)
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

	private List<IPathMatching> graphMatching(Model kbSubgraph, Model miniGraph, Set<Toponym> toponymsTEI,
			Model completeKB) {
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
		// liste des chemins à traiter
		List<List<IPathMatching>> open = new ArrayList<>();

		logger.info("Sélection du premier noeud à traiter");
		// sélection du premier noeud à traiter
		Toponym firstToponym = getNextNodeToProcess(usedSourceNodes, toponymsSeq, miniGraph);
		if (firstToponym == null)
			return new ArrayList<>();
		Resource firstSourceNode = firstToponym.getResource();
		List<IPathMatching> pathDeletion = new ArrayList<>();
		// le topo doit avoir 1 en cout de suppression.
		// Si on mettais 0 en cas d'absence de candidats et s'il est dans une
		// alt, l'autre alt ne sera jamais choisie
		float deletionCostFirstToponym = 1f;
		for (CriterionToponymCandidate candidateCriterion : firstToponym.getScoreCriterionToponymCandidate()) {
			Resource targetNode = candidateCriterion.getCandidate().getResource();
			SubstitutionCostResult cost = getSubstitutionCost(firstToponym, candidateCriterion, toponymsSeq, miniGraph,
					kbSubgraph, completeKB);
			List<IPathMatching> path = new ArrayList<>();
			path.add(new Substitution(firstSourceNode, targetNode, cost.getTotalCost()));
			open.add(path);
		}
		pathDeletion.add(new Deletion(firstSourceNode, deletionCostFirstToponym));
		open.add(pathDeletion);
		List<IPathMatching> pMin = null;
		logger.info("Noeud sélectionné : " + firstSourceNode);
		while (true) {
			pMin = getMinCostPath(open, kbSubgraph, miniGraph, toponymsSeq, sourceNodes, targetNodes, completeKB);
			// if (!pMin.isEmpty() && pMin.get(pMin.size() - 1).getClass() ==
			// Substitution.class &&
			// ((Substitution)pMin.get(pMin.size() -
			// 1)).getDeletedNode().toString().equals("http://data.ign.fr/id/propagation/Place/29"))
			// {
			// logger.info("Confolens qui pose problème");
			// }
			updateToponyms(pMin, toponymsSeq);
			if (isCompletePath(pMin, sourceNodes, targetNodes)) {
				break;
			} else {
				open.clear(); // on vide la liste, car le chemin pMin est
								// forcément le meilleur
				if (pMin.size() < sourceNodes.size()) {
					Toponym currentToponym = getNextNodeToProcess(usedSourceNodes, toponymsSeq, miniGraph);
					Resource currentSourceNode = currentToponym.getResource();
					//logger.info("Noeud sélectionné : " + currentSourceNode);
					// if
					// (currentSourceNode.toString().equals("http://data.ign.fr/id/propagation/Place/29"))
					// {
					// logger.info("Confolens qui pose problème");
					// }
					float deletionCost = 1f;
					for (CriterionToponymCandidate candidateCriterion : currentToponym
							.getScoreCriterionToponymCandidate()) {
						Resource resourceFromTarget = candidateCriterion.getCandidate().getResource();
						List<IPathMatching> newPath = new ArrayList<>(pMin);
						SubstitutionCostResult cost = getSubstitutionCost(currentToponym, candidateCriterion,
								toponymsSeq, miniGraph, kbSubgraph, completeKB);
						newPath.add(new Substitution(currentSourceNode, resourceFromTarget, cost.getTotalCost()));
						open.add(newPath);
					}
					List<IPathMatching> newPathDeletion = new ArrayList<>(pMin);
					newPathDeletion.add(new Deletion(currentSourceNode, deletionCost));
					open.add(newPathDeletion);
				} else {
					// resources du graphe cible non utilisées dans ce chemin
					Set<Resource> unusedResourcesFromTarget = getTargetUnusedResources(pMin, targetNodes);
					for (Resource resource : unusedResourcesFromTarget) {
						List<IPathMatching> newPath = new ArrayList<>(pMin);
						newPath.add(new Insertion(resource, getInsertionCost(resource)));
						open.add(newPath);
					}
					break;
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
		Comparator<Toponym> byRLSPSize = (t1, t2) -> Integer.compare(
				keepOnlyRLSP(getProperties(t2.getResource(), miniGraph)).size(),
				keepOnlyRLSP(getProperties(t1.getResource(), miniGraph)).size());
		Comparator<Toponym> byNonRLSPSize = (t1, t2) -> Integer.compare(
				removeRLSP(getProperties(t2.getResource(), miniGraph)).size(),
				removeRLSP(getProperties(t1.getResource(), miniGraph)).size());
		Optional<Toponym> optTopo = toponymsSeq.stream()
				.filter(t -> !usedSourceNodes.stream().anyMatch(r -> areResourcesEqual(r, t.getResource())))
				.sorted(byRLSPSize.thenComparing(byNonRLSPSize)).findFirst();
		if (optTopo.isPresent()) {
			usedSourceNodes.add(optTopo.get().getResource());
			return optTopo.get();
		}
		// getProperties(place, miniGraph)
		List<Statement> statements = miniGraph.listStatements().toList();
		// on veut choisir le noeud qui a le moins de liens pour accélérer les
		// calculs
		// OBSOLETE -> plus de liens car cela augmente les chances de trouver le
		// bon candidat
		Map<RDFNode, Integer> nbLinks = new HashMap<>();
		for (Statement statement : statements) {
			Resource s = statement.getSubject();
			RDFNode o = statement.getObject();
			if (nbLinks.containsKey(s)) {
				Integer i = nbLinks.get(s);
				i++;
				nbLinks.put(s, i);
			} else
				nbLinks.put(s, 1);
			if (nbLinks.containsKey(o)) {
				Integer i = nbLinks.get(o);
				i++;
				nbLinks.put(o, i);
			} else
				nbLinks.put(o, 1);
		}
		Optional<Entry<RDFNode, Integer>> optN = nbLinks.entrySet().stream()
				.filter(p -> !usedSourceNodes.contains(p.getKey()))
				.sorted((a, b) -> Integer.compare(a.getValue(), b.getValue())).limit(1).findFirst();
		if (optN.isPresent()) {
			RDFNode n = optN.get().getKey();
			for (Toponym toponym : toponymsSeq) {
				Resource resourceToCheck = toponym.getResource();
				if (areResourcesEqual(resourceToCheck, (Resource) n)) {
					usedSourceNodes.add(resourceToCheck);
					return toponym;
				}
			}
		}
		return null;
	}

	private SubstitutionCostResult getSubstitutionCost(Toponym nodeToRemove,
			CriterionToponymCandidate candidateCriterion, Set<Toponym> toponymsTEI, Model teiRdf,
			Model kbWithInterestingProperties, Model completeKB) {
		SubstitutionCostResult scr;
		if (SubstitutionCostResult.contains(scrList, nodeToRemove.getResource(),
				candidateCriterion.getCandidate().getResource())) {
			scr = SubstitutionCostResult.get(scrList, nodeToRemove.getResource(),
					candidateCriterion.getCandidate().getResource());
		} else {
			float scoreType = scoreType(nodeToRemove, candidateCriterion);
			float scoreLabel = 1 - candidateCriterion.getValue();
			float scoreLink = scoreLink(nodeToRemove, candidateCriterion, teiRdf, toponymsTEI);
			float scoreRlsp = scoreRlsp(nodeToRemove, candidateCriterion, teiRdf, toponymsTEI,
					kbWithInterestingProperties, completeKB);
			rlspCalculous.add(nodeToRemove.getResource() + " (" + nodeToRemove.getName() + ")" + " -> "
					+ candidateCriterion.getCandidate().getResource() + " (" + scoreLabel + "/" + scoreLink + "/"
					+ scoreRlsp + "/" + scoreType + ")");
			float totalCost = labelWeight * scoreLabel + rlspWeight * scoreRlsp + linkWeight * scoreLink + typeWeight * scoreType;
			scr = new SubstitutionCostResult(nodeToRemove.getResource(),
					candidateCriterion.getCandidate().getResource(), scoreLabel, scoreLink, scoreRlsp, totalCost, scoreType);
			scrList.add(scr);
		}
		return scr;
	}
	private float scoreType(Toponym nodeToRemove, CriterionToponymCandidate candidateCriterion) {
		// on récupère le type du topo au format de la KB
		if (nodeToRemove.getType().toString().endsWith("Place"))
			return 0f;
		String topoType = getTEITypeToKBType(nodeToRemove.getType().toString());
		if (candidateCriterion.getCandidate().getTypes().contains(topoType))
			return 0f;
		return 1f;
	}
	private float scoreLink(Toponym toponym, CriterionToponymCandidate criterion, Model teiRdf,
			Set<Toponym> toponymsTEI) {
		Resource nodeToRemove = toponym.getResource();
		Resource nodeToInsert = criterion.getCandidate().getResource();
		float result = 0f;
		List<Statement> statements = getProperties(nodeToRemove, teiRdf);
		statements = removeRLSP(statements);
		if (statements.isEmpty())
			return 1f;
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
				candidates.addAll(getCandidates(m, toponymsTEI)); // ici il faut
																	// prendre
																	// les
																	// candidats
																	// seulement
																	// si le
																	// topo n'a
																	// pas été
																	// désambiguisé,
																	// sinon on
																	// utilise
																	// son
																	// référent
			Map<CriterionToponymCandidate, Integer> pathLengths = new ConcurrentHashMap<>();
			//for (CriterionToponymCandidate criterionToponymCandidate : candidates) {
				 candidates.parallelStream().forEach(criterionToponymCandidate -> {
				Resource end = criterionToponymCandidate.getCandidate().getResource();
				final Resource nodeToInsertCopy = nodeToInsert;
				int pathLength = getLinkPathLength(nodeToInsertCopy, end, 0);
				pathLengths.put(criterionToponymCandidate, pathLength);
			}  );
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

	private int getLinkPathLength(Resource nodeToInsertCopy, Resource end, int recursiveCounter) {
		int pathLength = -1;
		if (recursiveCounter > 3)
			return pathLength;
		if (shortestPaths.containsKey(nodeToInsertCopy)) {
			DijkstraSP sp = getSP(nodeToInsertCopy);
			if (sp != null && sp.hasPathTo(end)) {
				pathLength = sp.distTo(end);
			}
		} else if (shortestPaths.containsKey(end)) {
			DijkstraSP sp = getSP(end);
			pathLength = sp != null && sp.hasPathTo(nodeToInsertCopy) ? sp.distTo(nodeToInsertCopy) : -1;
		}
		if (pathLength == -1) {
			// on regarde s'il y a une région et on s'en sert pour calculer
			// chemin
			// réflechir à comment utiliser ça dans les 2 précédents if
			if (shortestPaths.containsKey(nodeToInsertCopy)) {
				Resource deptOrRegion = getDepartementOrRegion(end);
				if (deptOrRegion != null) {
					pathLength = getLinkPathLength(nodeToInsertCopy, deptOrRegion, recursiveCounter + 1);
				}
			}
			if (pathLength == -1 && shortestPaths.containsKey(end)) {
				Resource deptOrRegion = getDepartementOrRegion(nodeToInsertCopy);
				if (deptOrRegion != null) {
					pathLength = getLinkPathLength(deptOrRegion, end, recursiveCounter + 1);
				}
			}
		}
		return pathLength;
	}

	private Resource getDepartementOrRegion(Resource r) {
		List<Statement> statements = kbSource.listStatements(r, null, (RDFNode) null).toList();
		if (statements == null || statements.isEmpty())
			return null;
		Stream<Statement> stream = null;
		if (statements.stream().anyMatch(s -> s.getPredicate().getURI().equals(propDepartement.getURI()))) {
			stream = statements.stream().filter(s -> s.getPredicate().getURI().equals(propDepartement.getURI()));
		} else if (statements.stream().anyMatch(s -> s.getPredicate().getURI().equals(dboDepartement.getURI()))) {
			stream = statements.stream().filter(s -> s.getPredicate().getURI().equals(dboDepartement.getURI()));
		} else if (statements.stream().anyMatch(s -> s.getPredicate().getURI().equals(propRegion.getURI()))) {
			stream = statements.stream().filter(s -> s.getPredicate().getURI().equals(propRegion.getURI()));
		} else if (statements.stream().anyMatch(s -> s.getPredicate().getURI().equals(dboRegion.getURI()))) {
			stream = statements.stream().filter(s -> s.getPredicate().getURI().equals(dboRegion.getURI()));
		} else if (statements.stream().anyMatch(s -> s.getPredicate().getURI().equals(propProvince.getURI()))) {
			stream = statements.stream().filter(s -> s.getPredicate().getURI().equals(propProvince.getURI()));
		} else if (statements.stream().anyMatch(s -> s.getPredicate().getURI().equals(dboProvince.getURI()))) {
			stream = statements.stream().filter(s -> s.getPredicate().getURI().equals(dboProvince.getURI()));
		}
		if (stream != null) {
			Optional<Statement> optR = stream.findFirst();
			return optR.isPresent() && optR.get().getObject().isResource() ? (Resource) optR.get().getObject() : null;
		}
		return null;
	}

	private DijkstraSP getSP(Resource r) {
		if (r != null && resourcesIndexAPSP.containsKey(r)) { // ce noeud a un
																// plus court
																// chemin
			if (shortestPaths.containsKey(r)) { // ce noeud a un plus court
												// chemin
				return shortestPaths.get(r);
			}
			DijkstraSP sp = DijkstraSP.deserialize(serializationDirectory + resourcesIndexAPSP.get(r));
			if (sp != null)
				return shortestPaths.put(r, sp);
		}
		return null;
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

	/**
	 * Gets the path that costs the less and remove it from OPEN.
	 *
	 * @param open
	 *            the open
	 * @return the min cost path
	 */
	private List<IPathMatching> getMinCostPath(List<List<IPathMatching>> open, Model kbSubgraph, Model miniGraph,
			Set<Toponym> toponymsSeq, Set<Resource> sourceNodes, Set<Resource> targetNodes, Model completeKB) {
		float min = Float.MAX_VALUE;
		List<IPathMatching> pMin = null;
		for (List<IPathMatching> path : open) {
			Set<Resource> unusedSourceNodes = getSourceUnusedResources(path, sourceNodes);
			Set<Resource> unusedTargetNodes = getTargetUnusedResources(path, targetNodes);
			float g = totalCostPath(path);
			float h = heuristicCostPath(path, kbSubgraph, miniGraph, toponymsSeq, unusedSourceNodes, unusedTargetNodes,
					completeKB);
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
			Set<Toponym> toponymsTEI, Set<Resource> unusedSourceNodes, Set<Resource> unusedTargetNodes,
			Model completeKB) {
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
				SubstitutionCostResult scr = getSubstitutionCost(unusedToponym, criterion, toponymsTEI, miniGraph,
						kbSubgraph, completeKB);
				substitutionCostsCurrentToponym.add(scr.getTotalCost());
			}
			if (!substitutionCostsCurrentToponym.isEmpty())
				substitutionCosts.add(substitutionCostsCurrentToponym.stream().sorted((a, b) -> Float.compare(b, a))
						.findFirst().get());
		}
		result += substitutionCosts.stream().sorted((a, b) -> Float.compare(b, a)).limit(Integer.min(n1, n2))
				.mapToDouble(i -> i).sum();
		// On suprime le cout des insertions, car lorsqu'il y a un doublon ds la
		// sequence,
		// un seul candidat est utilisé pr 2 topo et donc il faut ajouter une
		// insertion de plus que
		// si 2 candidats étaient utilisé. Or on veut priviligié le meme
		// candidat pour 2 topo dans la gestion des doublons.
		// result += (float) Integer.max(0, n1 - n2) + (float) Integer.max(0, n2
		// - n1);
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

	private List<CriterionToponymCandidate> getCandidates(Resource r, Set<Toponym> toponymsTEI) {
		List<CriterionToponymCandidate> results = new ArrayList<>();
		Optional<Toponym> toponym = toponymsTEI.stream().filter(t -> areResourcesEqual(t.getResource(), r)).findFirst();
		if (toponym.isPresent()) {
			return toponym.get().getScoreCriterionToponymCandidate();

		}
		return results;
	}
//	class TempClass {
//		List<Statement> rlspStatements;
//		Map<Statement, TempClass2> contentMap;
//		public TempClass(List<Statement> rlspStatements, Toponym nodeToRemove) {
//			this.rlspStatements = rlspStatements;
//			this.contentMap = new HashMap<>();
//			for (Statement statement : this.rlspStatements) {
//				TempClass2 tmpc2 = new TempClass2(statement, nodeToRemove);
//				this.contentMap.put(statement, tmpc2);
//			}
//		}
//	}
//	class TempClass2 {		
//		Statement statement;
//		Resource m;
//		List<Property> properties;
//		List<Property> reversedProperties;
//		Map<CriterionToponymCandidate, Integer> pathLengths;
//		List<CriterionToponymCandidate> candidates; // de m
//		
//		public TempClass2(Statement statement, Toponym nodeToRemove) {
//			this.statement = statement;
//			if (areResourcesEqual(statement.getSubject(), nodeToRemove.getResource())) {
//				this.m = (Resource) statement.getObject();
//			} else {
//				this.m = statement.getSubject();
//			}
//			Property statementProperty = statement.getPredicate();
//			this.properties = getCorrespondingProperties(statementProperty);
//			this.reversedProperties = getCorrespondingReversedProperties(statementProperty);
//			this.pathLengths = new ConcurrentHashMap<>();
//			this.candidates = getReferent(m, toponymsTEI);
//			if (candidates.isEmpty())
//				candidates.addAll(getCandidates(m, toponymsTEI));
//		}
//	}
	private float scoreRlsp(Toponym nodeToRemove, CriterionToponymCandidate nodeToInsert, Model teiRdf,
			Set<Toponym> toponymsTEI, Model kbWithInterestingProperties, Model completeKB) {
		List<Statement> statements = getProperties(nodeToRemove.getResource(), teiRdf);
		List<Statement> rlspStatements = keepOnlyRLSP(statements);
		if (rlspStatements.isEmpty())
			return 1f;
		float result = 0f;
		
		for (Statement statement : rlspStatements) {
			Resource m;
			boolean reverseTmp = false;
			if (areResourcesEqual(statement.getSubject(), nodeToRemove.getResource())) {
				m = (Resource) statement.getObject();
			} else {
				m = statement.getSubject();
				reverseTmp = true;
			}
			final boolean reverse = reverseTmp;
			Property statementProperty = statement.getPredicate();
			List<Property> properties = getCorrespondingProperties(statementProperty);
			List<Property> reversedProperties = getCorrespondingReversedProperties(statementProperty);
			Map<CriterionToponymCandidate, Integer> pathLengths = new ConcurrentHashMap<>();
			List<CriterionToponymCandidate> candidates = getReferent(m, toponymsTEI);
			if (candidates.isEmpty())
				candidates.addAll(getCandidates(m, toponymsTEI));
			//for (CriterionToponymCandidate criterionToponymCandidate : candidates) {
				 candidates.parallelStream().forEach(criterionToponymCandidate -> {
//				if (areResourcesEqual(criterionToponymCandidate.getCandidate().getResource(), kbWithInterestingProperties.getResource("http://fr.dbpedia.org/resource/Rochefort_(Charente-Maritime)")) && 
//						areResourcesEqual(nodeToInsert.getCandidate().getResource(), kbWithInterestingProperties.getResource("http://fr.dbpedia.org/resource/Ruffec_(Charente)"))) {
//					logger.info("Bingo");
//					logger.info(tmpC2.getValue().statement);
//				}
				Resource end = criterionToponymCandidate.getCandidate().getResource();
				Resource nodeToInsertCopy = nodeToInsert.getCandidate().getResource();
				int pathLength = getRLSPPathLength(nodeToInsertCopy, end, properties, reversedProperties,
						kbWithInterestingProperties, reverse);

				pathLengths.put(criterionToponymCandidate, pathLength);
			}  );
			Integer maxPathLength = getMaxValue(pathLengths);
			float min = 1f;
			for (Entry<CriterionToponymCandidate, Integer> entry : pathLengths.entrySet()) {
				if (entry.getValue() > 0) {
					float scoreTmp = ((float) entry.getValue()) / ((float) maxPathLength);
					min = scoreTmp < min ? scoreTmp : min;
					if (areResourcesEqual(nodeToInsert.getCandidate().getResource(), kbWithInterestingProperties
							.createResource("http://fr.dbpedia.org/resource/Ruffec_(Charente)"))) {
						logger.info("RLSP -> " + nodeToRemove.getResource() + " "
								+ nodeToInsert.getCandidate().getResource() + " -> "
								+ entry.getKey().getCandidate().getResource() + " : " + entry.getValue() + " / "
								+ maxPathLength + " (" + scoreTmp + ")");
					} else if (areResourcesEqual(nodeToInsert.getCandidate().getResource(), kbWithInterestingProperties
							.createResource("http://fr.dbpedia.org/resource/Ruffec_(Indre)"))) {
						logger.info("RLSP -> " + nodeToRemove.getResource() + " "
								+ nodeToInsert.getCandidate().getResource() + " -> "
								+ entry.getKey().getCandidate().getResource() + " : " + entry.getValue() + " / "
								+ maxPathLength + " (" + scoreTmp + ")");
					}
				}
			}
			result += min;

		}

		return result / ((float) rlspStatements.size());
	}

	private int getRLSPPathLength(Resource nodeToInsertCopy, Resource end, List<Property> properties,
			List<Property> reversedProperties, Model kbWithInterestingProperties, boolean reverse) {
		Resource a = nodeToInsertCopy;
		Resource b = end;
		if (reverse) {
			a = end;
			b = nodeToInsertCopy;
		}
		int pathLength = -1;
		if (shortestPaths.containsKey(a)) {
			DijkstraSP sp = getSP(a);
			if (sp != null && sp.hasPathTo(b)) {
				Path p = sp.pathTo(b, kbWithInterestingProperties);
				int countGoodProperties = 0;
				if (p != null) {
					for (Statement s : p)
						countGoodProperties += properties.contains(s.getPredicate()) ? 1 : 0;
					if (countGoodProperties > (p.size() / 2))
						pathLength = sp.distTo(b);
				}
			}
		} else if (shortestPaths.containsKey(b)) {
			DijkstraSP sp = getSP(b);
			if (sp != null && sp.hasPathTo(a)) {
				Path p = sp.pathTo(a, kbWithInterestingProperties);
				if (p != null) {

					int countGoodProperties = 0;
					for (Statement s : p)
						countGoodProperties += reversedProperties.contains(s.getPredicate()) ? 1 : 0;
					if (countGoodProperties > (p.size() / 2))
						pathLength = sp.distTo(end);
				}
			}
		}
		return pathLength;
	}

	private List<Statement> keepOnlyRLSP(List<Statement> statements) {
		if (statements == null || statements.isEmpty())
			return new ArrayList<>();
		return statements.stream().filter(s -> s.getPredicate().getNameSpace().equals(RLSP_NS))
				.collect(Collectors.toList());
	}

	private List<CriterionToponymCandidate> getReferent(Resource r, Set<Toponym> toponymsTEI) {
		List<CriterionToponymCandidate> results = new ArrayList<>();
		Optional<Toponym> toponym = toponymsTEI.stream().filter(t -> areResourcesEqual(t.getResource(), r)).findFirst();
		if (toponym.isPresent() && toponym.get().getReferent() != null) {
			Resource ref = toponym.get().getReferent();
			results.addAll(toponym.get().getScoreCriterionToponymCandidate().stream()
					.filter(p -> areResourcesEqual(ref, p.getCandidate().getResource())).collect(Collectors.toList()));

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

	private List<Property> getCorrespondingReversedProperties(Property teiProperty) {
		List<Property> properties = new ArrayList<>(); // propriétés autorisées
		if (teiProperty.getURI().equalsIgnoreCase(rlspSouthOf.getURI())) {
			properties.add(propSud);
			properties.add(propSudEst);
			properties.add(propSudOuest);
			properties.add(propEst);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspSouthWestOf.getURI())) {
			properties.add(propSud);
			properties.add(propSudOuest);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspSouthEastOf.getURI())) {
			properties.add(propSud);
			properties.add(propSudEst);
			properties.add(propEst);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspNorthOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordEst);
			properties.add(propNordOuest);
			properties.add(propEst);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspNorthWestOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordOuest);
			properties.add(propOuest);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspNorthEastOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordEst);
			properties.add(propEst);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspWestOf.getURI())) {
			properties.add(propNord);
			properties.add(propNordOuest);
			properties.add(propSudOuest);
			properties.add(propOuest);
			properties.add(propSud);
		} else if (teiProperty.getURI().equalsIgnoreCase(rlspEastOf.getURI())) {
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
		return statements.stream().filter(s -> !s.getPredicate().getNameSpace().equals(RLSP_NS))
				.collect(Collectors.toList());
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
				SubstitutionCostResult scr = SubstitutionCostResult.get(scrList, deletedNode, insertedNode);
				topo.setSubstitutionCostResult(scr);
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

	public void test2() {
		Resource ruffecCharente = kbSubgraph.getResource("http://fr.dbpedia.org/resource/Ruffec_(Charente)");
		Resource rochefortCharenteMaritime = kbSubgraph
				.getResource("http://fr.dbpedia.org/resource/Rochefort_(Charente-Maritime)");
		Resource aulnayCharenteMaritime = kbSubgraph
				.getResource("http://fr.dbpedia.org/resource/Aulnay_(Charente-Maritime)");
		int ruffecIndex = resourcesIndexAPSP.get(ruffecCharente);
		DijkstraSP ruffecSP = DijkstraSP.deserialize(serializationDirectory + ruffecIndex);
		testDijkstra(ruffecSP, rochefortCharenteMaritime);
		testDijkstra(ruffecSP, aulnayCharenteMaritime);
	}

	private void testDijkstra(DijkstraSP sp, Resource rToSearch) {
		logger.info(sp.getNode(kbSource));
		logger.info(sp.hasPathTo(rToSearch));
		if (sp.hasPathTo(rToSearch)) {
			Iterator<Statement> iterator = sp.pathTo(rToSearch, kbSource).iterator();
			int pathLength = 0;
			while (iterator.hasNext()) {
				Statement statement = (Statement) iterator.next();
				logger.info(statement);
				pathLength++;
			}
			logger.info(pathLength);
		}
	}
}
