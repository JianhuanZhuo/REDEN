# REDEN
Unsupervised graph-based tool for disambiguation and linking of named entities to Linked Data sets for digital editions in XML-TEI, it includes a Linked Data data extractor for building the dictionary of potential NE candidates

The main Java class to launch REDEN is fr.lip6.reden.MainNELApp.java, REDEN configuration file is located in config/config.properties, it should be properly configured before execution. There are two modes to launch REDEN 

The first mode allows the downloading and the constitution of a dictionary of potential NE candidates from Linked Data, so you must be connected to the Internet (when passing through a proxy use the following JVM parameters: -DproxySet=true -DproxyHost=IP_address -DproxyPort=port). So far, the place and the person (author) dictionaries are created from French DBpedia and the French National Library (BnF) Linked Data sets. Please contact us for helping you add a new LD source.

The only parameters are:  

config-file.properties -createDico bnf|dbpediafr|all

Once the dictionary has been built, the second mode allows the annotation of an input TEI-XML file, only the first time you launch REDEN you must be connected to the Internet. The parameters are:

config-file.properties TEI-fileName.xml -printEval -createIndex -relsFile\=file -outDir\=dir

where:

config-file.properties (mandatory): the name of the properties file containing the parameters for configuring REDEN and the LD extractor, see config/config.properties

TEI-fileName.xml (mandatory): the name of the TEI file, include the file path if necessary

-printEval (optional): if an already annotated TEI file is available (i.e. a gold standard), REDEN will compare this file with the resulting annotated file and will provide accuracy measures, the name of gold file must match the name of the input file and end by "-gold.xml", for instance, of the input file is "apollinaire_heresiarque-et-cie.xml", the gold file should be named "apollinaire_heresiarque-et-cie-gold.xml" 

-createIndex (optional): REDEN creates Lucene indexes for improving access to the dictionary files. When executing REDEN the first time or when the dictionary has changed, it is mandatory to launch it using this flag, otherwise you can leave it out

-relsFile\=file (optional): file name listing the RDF predicates and their corresponding weights 

-outDir\=dir (optional): name of the folder where REDEN will output files: the annotated XML-TEI and other files which provide execution information

# How to cite this work
Brando, C., Frontini, F., Ganascia, J.G. (2015): Disambiguation of named entities in cultural heritage texts using linked data sets. In: Proceedings of the First International Workshop on Semantic Web for Cultural Heritage in Conjunction with 19th East-European Conference on Advances in Databases and Information Systems, New Trends in Databases and Information Systems, Springer, 539, Poitiers, France, http://link.springer.com/chapter/10.1007%2F978-3-319-23201-0_51 

Frontini F, Brando C, Ganascia J-G, (2015) Semantic Web based Named Entity Linking for digital humanities and heritage texts, in Proceedings of the First International Workshop Semantic Web for Scientific Heritage at the 12th ESWC 2015 Conference, Portorož, Slovenia, June 1st, 2015, pp. 77-88, URL: http://ceur-ws.org/Vol-1364/paper9.pdf 

REDEN uses the following frameworks: 
- indexes are implemented with Lucene (https://lucene.apache.org/core/)
- RDF data is processed with the Apache Jena API (https://jena.apache.org/) 
- graphs are manipulated by the JgraphT API (http://jgrapht.org)
- implementation of centrality measures are available in the Social Network analysis tool JgraphT-SNA (https://bitbucket.org/sorend/jgrapht-sna).