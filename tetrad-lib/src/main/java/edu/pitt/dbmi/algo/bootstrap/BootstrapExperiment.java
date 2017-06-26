package edu.pitt.dbmi.algo.bootstrap;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.bootstrap.task.BootstrapSearchAction;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahdi on 1/16/17.
 * 
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 4/5/2017
 * 
 */
public class BootstrapExperiment {

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    private DataSet data;
    private String outputDir = null;
    private String probFileName = null;
    private String probFileNameDirectedPath = null;
    private String truthFileName = null;
    private String truthFileNameDirectedPath = null;
    private Graph trueGraph = null;

    private final BootstrapSearch bootstrapSearch;

    private Parameters parameters;

    private boolean runParallel = true;
    private BootstrapAlgName algName = BootstrapAlgName.RFCI;
    private List<Graph> PAGs;
    private Graph estGraph = null;
    private boolean verbose = false;
    private boolean overrideResults = true;

    public void setOverrideResults(boolean overrideResults) {
	this.overrideResults = overrideResults;
    }

    public void setParallelMode(boolean runParallel) {
	this.runParallel = runParallel;
    }

    public void setTrueGraph(Graph trueGraph) {
	this.trueGraph = trueGraph;
    }

    public void setVerbose(boolean verbose) {
	this.verbose = verbose;
    }

    public Parameters getParameters() {
	return parameters;
    }

    public void setParameters(Parameters parameters) {
	this.parameters = parameters;
    }

    public BootstrapExperiment(DataSet data, BootstrapAlgName algName,
	    int numBootstrapSamples) {
	this.data = data;
	this.algName = algName;
	bootstrapSearch = new BootstrapSearch(data);
	bootstrapSearch.setNumOfBootstrap(numBootstrapSamples);
    }

    public void run() {
	long start, stop;
	if (checkProbFileExists() == -1) {
	    return;
	}

	BootstrapSearchAction runner = new BootstrapSearchAction(0, 1, algName,
		parameters, null, verbose);

	estGraph = runner.learnGraph(data);
	if (trueGraph != null) {
	    estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
	}

	start = System.currentTimeMillis();

	bootstrapSearch.setAlgorithm(algName);
	bootstrapSearch.setRunningMode(runParallel);
	bootstrapSearch.setVerbose(verbose);
	bootstrapSearch.setParameters(parameters);

	PAGs = bootstrapSearch.search();

	if (trueGraph != null) {
	    List<Graph> PAGs2 = new ArrayList<Graph>();
	    for (Graph pag : PAGs) {
		pag = GraphUtils.replaceNodes(pag, trueGraph.getNodes());
		PAGs2.add(pag);
	    }
	    PAGs = PAGs2;
	}

	if (verbose) {
	    this.logger.log("info", "Bootstrap size is : " + PAGs.size());
	}
	stop = System.currentTimeMillis();
	if (verbose) {
	    this.logger.log("info", "processing time of total bootstrapping : "
		    + (stop - start) / 1000.0 + " sec");
	}

	start = System.currentTimeMillis();
	writeProbFile();
	stop = System.currentTimeMillis();
	if (verbose) {
	    System.out.println("probDistribution finished in " + (stop - start)
		    + " ms");
	}

	start = System.currentTimeMillis();
	writeProbFileBasedOnDirectedPath();
	stop = System.currentTimeMillis();
	if (verbose) {
	    System.out
		    .println("probDistribution based on Directed Path finished in "
			    + (stop - start) + " ms");
	}

	if (trueGraph != null) {
	    start = System.currentTimeMillis();
	    writeTruth(trueGraph);
	    stop = System.currentTimeMillis();
	    if (verbose) {
		System.out.println("Writing true graph finished in "
			+ (stop - start) + " ms");
	    }

	    start = System.currentTimeMillis();
	    writeTruthBasedOnDirectedPath(trueGraph);
	    stop = System.currentTimeMillis();
	    if (verbose) {
		System.out
			.println("Writing true graph based on Directed Path finished in "
				+ (stop - start) + " ms");
	    }

	}
    }

    private int writeTruth(Graph trueGraph) {
	Path truthFile = Paths.get(outputDir, truthFileName);

	try (OutputStream out = new BufferedOutputStream(
		Files.newOutputStream(truthFile))) {
	    // Write Header:
	    String header = "A, B, truth={0-7}" + " \n";
	    byte data[] = header.getBytes();
	    out.write(data, 0, data.length);

	    Graph complete = new EdgeListGraph(trueGraph.getNodes());
	    complete.fullyConnect(Endpoint.TAIL);

	    for (Edge e : complete.getEdges()) {
		int trueType = 0;

		Node n1 = e.getNode1();
		Node n2 = e.getNode2();

		// compute true edge type for each pair of nodes

		if (trueGraph.getEdge(n1, n2) == null) {
		    trueType = 0;
		} else {
		    Endpoint p1 = trueGraph.getEdge(n1, n2).getEndpoint1();
		    Endpoint p2 = trueGraph.getEdge(n1, n2).getEndpoint2();

		    // A -> B
		    if (p1 == Endpoint.TAIL && p2 == Endpoint.ARROW)
			trueType = 1;

		    // A <- B
		    else if (p1 == Endpoint.ARROW && p2 == Endpoint.TAIL)
			trueType = 2;

		    // A o-> B
		    else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.ARROW)
			trueType = 3;

		    // A <-o B
		    else if (p1 == Endpoint.ARROW && p2 == Endpoint.CIRCLE)
			trueType = 4;

		    // A o-o B
		    else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.CIRCLE)
			trueType = 5;

		    // A <-> B
		    else if (p1 == Endpoint.ARROW && p2 == Endpoint.ARROW)
			trueType = 6;

		    // A -- B
		    else if (p1 == Endpoint.TAIL && p2 == Endpoint.TAIL)
			trueType = 7;

		}

		String line = "" + n1 + ", " + n2 + ", " + trueType + "\n";
		data = line.getBytes();
		out.write(data, 0, data.length);
	    }

	} catch (IOException e) {
	    e.printStackTrace();
	    return -1;
	}

	return 0;
    }

    private int writeTruthBasedOnDirectedPath(Graph trueGraph) {
	Path truthFileDirectedPath = Paths.get(outputDir,
		truthFileNameDirectedPath);

	try (OutputStream out = new BufferedOutputStream(
		Files.newOutputStream(truthFileDirectedPath))) {

	    // Write Header:
	    String header = "A, B, truth={0-3}" + " \n";
	    byte data[] = header.getBytes();
	    out.write(data, 0, data.length);

	    Graph complete = new EdgeListGraph(trueGraph.getNodes());
	    complete.fullyConnect(Endpoint.TAIL);

	    for (Edge e : complete.getEdges()) {
		int trueType = 0;

		Node n1 = e.getNode1();
		Node n2 = e.getNode2();

		// compute true edge type for each pair of nodes
		if (trueGraph.existsDirectedPathFromTo(n1, n2)) {
		    if (trueGraph.existsDirectedPathFromTo(n2, n1)) {
			// There is a bidirectional causal relation between two
			// nodes according to the true PAG
			trueType = 3;
		    } else {
			trueType = 1;
		    }
		} else if (trueGraph.existsDirectedPathFromTo(n2, n1)) {
		    trueType = 2;
		} else {
		    // There is no causal relation between the two nodes based
		    // on
		    // the true PAG
		    trueType = 0;
		}

		String line = "" + n1 + ", " + n2 + ", " + trueType + "\n";
		data = line.getBytes();
		out.write(data, 0, data.length);

	    }

	} catch (IOException e) {
	    e.printStackTrace();
	    return -1;
	}

	return 0;
    }

    private int writeProbFile() {
	Path probFile = Paths.get(outputDir, probFileName);

	try (OutputStream out = new BufferedOutputStream(
		Files.newOutputStream(probFile))) {

	    // Write Header
	    String header = "A, B, freq(A-nil-B), freq(A --> B), freq(B --> A), freq(A o-> B), freq(B o-> A), "
		    + "freq(A o-o B), freq(A <-> B), freq(A --- B), "
		    + algName
		    + "_out={0-7}" + " \n";
	    byte data[] = header.getBytes();
	    out.write(data, 0, data.length);

	    System.out.println("MP: on top of complete Graph!");

	    Graph complete = new EdgeListGraph(estGraph.getNodes());
	    complete.fullyConnect(Endpoint.TAIL);

	    System.out.println("MP: Start Edging!");

	    for (Edge e : complete.getEdges()) {
		double AnilB = 0.0;
		double AtoB = 0.0;
		double BtoA = 0.0;
		double ACtoB = 0.0;
		double BCtoA = 0.0;
		double AccB = 0.0;
		double AbB = 0.0;
		double AuB = 0.0;
		int estType = 0;

		Node n1 = e.getNode1();
		Node n2 = e.getNode2();

		// compute probability for each edge type
		Edge e1 = new Edge(n1, n2, Endpoint.NULL, Endpoint.NULL);
		AnilB = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
		AtoB = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
		BtoA = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
		ACtoB = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
		BCtoA = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
		AccB = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
		AbB = getProbability(e1);

		e1 = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
		AuB = getProbability(e1);

		if (estGraph.getEdge(n1, n2) == null)
		    estType = 0;

		else {

		    Endpoint p1 = estGraph.getEdge(n1, n2).getEndpoint1();
		    Endpoint p2 = estGraph.getEdge(n1, n2).getEndpoint2();

		    if (p1 == Endpoint.TAIL && p2 == Endpoint.ARROW) // A -> B
			estType = 1;

		    else if (p1 == Endpoint.ARROW && p2 == Endpoint.TAIL) // A
									  // <-
									  // B
			estType = 2;

		    else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.ARROW) // A
									    // o->
									    // B
			estType = 3;

		    else if (p1 == Endpoint.ARROW && p2 == Endpoint.CIRCLE) // A
									    // <-o
									    // B
			estType = 4;

		    else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.CIRCLE) // A
									     // o-o
									     // B
			estType = 5;

		    else if (p1 == Endpoint.ARROW && p2 == Endpoint.ARROW) // A
									   // <->
									   // B
			estType = 6;

		    else if (p1 == Endpoint.TAIL && p2 == Endpoint.TAIL) // A --
									 // B
			estType = 7;
		}

		String line = "" + n1 + ", " + n2 + ", " + AnilB + ", " + AtoB
			+ ", " + BtoA + ", " + ACtoB + ", " + BCtoA + ", "
			+ AccB + ", " + AbB + ", " + AuB + ", " + estType
			+ "\n";
		data = line.getBytes();
		out.write(data, 0, data.length);

	    }

	} catch (IOException e) {
	    e.printStackTrace();
	    return -1;
	}

	return 0;

    }

    private int writeProbFileBasedOnDirectedPath() {
	Path probFileDirectedPath = Paths.get(outputDir,
		probFileNameDirectedPath);

	try (OutputStream out = new BufferedOutputStream(
		Files.newOutputStream(probFileDirectedPath))) {
	    // Write Header
	    String header = "A, B, freq(A-->B), freq(B-->A), freq(A<->B), "
		    + algName + " \n";
	    byte data[] = header.getBytes();
	    out.write(data, 0, data.length);

	    if (verbose) {
		System.out.println("MP: on top of complete Graph!");
	    }

	    Graph complete = new EdgeListGraph(estGraph.getNodes());
	    complete.fullyConnect(Endpoint.TAIL);
	    if (verbose) {
		System.out.println("MP: Start Edging!");
	    }

	    for (Edge e : complete.getEdges()) {
		double AtoB = 0.0;
		double BtoA = 0.0;
		double AbB = 0.0;

		Node n1 = e.getNode1();
		Node n2 = e.getNode2();

		// compute probability for each causal edge type
		Edge e1;
		e1 = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
		AtoB = getProbabilityBasedOnDirectedPath(e1);

		e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
		BtoA = getProbabilityBasedOnDirectedPath(e1);

		e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
		AbB = getProbabilityBasedOnDirectedPath(e1);

		// MP: Starting of the part that should be edited
		int estType = 0;
		if (estGraph.existsDirectedPathFromTo(n1, n2)) {
		    if (estGraph.existsDirectedPathFromTo(n2, n1)) {
			// There is a bidirectional causal relation between two
			// nodes according to the estimated Graph
			estType = 3;
		    } else {
			estType = 1;
		    }
		} else if (estGraph.existsDirectedPathFromTo(n2, n1)) {
		    estType = 2;
		} else {
		    // There is no causal relation between two nodes based on
		    // estimated Graph (i.e., generated by algorithm e.g., FGES,
		    // RFCI, etc.)
		    estType = -1;
		}

		String line = "" + n1 + ", " + n2 + ", " + AtoB + ", " + BtoA
			+ ", " + AbB + ", " + estType + "\n";
		data = line.getBytes();
		out.write(data, 0, data.length);

	    }

	} catch (IOException e) {
	    e.printStackTrace();
	    return -1;
	}

	return 0;
    }

    public void setOut(String dirName, String baseFileName) {
	// Here just create file names and store them in the instance variables
	// Ad check availability to check if prob file exist then ignore
	// running!

	File dir = new File(dirName);
	dir.mkdirs();
	outputDir = dirName;

	// probFileName: In this file causality is defined based on direct
	// connectivity
	probFileName = "probs_" + baseFileName + ".csv";

	// probFileName_directedPath: In this file causality is defined based on
	// the existence of directed path
	probFileNameDirectedPath = "probs_" + baseFileName + "_directedPath"
		+ ".csv";

	// In this file causality is defined based on the existence of direct
	// connectivity(edge) in the true PAG
	truthFileName = "truth_" + baseFileName + ".csv";

	// In this file causality is defined based on the existence of directed
	// path in the true PAG
	truthFileNameDirectedPath = "truth_" + baseFileName + "_directedPath"
		+ ".csv";
    }

    public int checkProbFileExists() {
	File dir = new File(outputDir);
	dir.mkdirs();
	File probFile = new File(dir, probFileName);
	if (probFile.exists()) {
	    if (overrideResults) {
		if (verbose) {
		    System.out
			    .println("warning: probFile already exists and it will be overriden!");
		}
	    } else {
		System.out
			.println("Erorr: probFile already exists and it cannot be overriden "
				+ "until you turn on the overrideResults flag!");
		return -1;
	    }
	}
	return 0;
    }

    private double getProbability(Edge e) {
	int count = 0;

	if (!PAGs.get(0).containsNode(e.getNode1()))
	    throw new IllegalArgumentException();
	if (!PAGs.get(0).containsNode(e.getNode2()))
	    throw new IllegalArgumentException();

	for (Graph g : PAGs) {
	    if (e.getEndpoint1() == Endpoint.NULL
		    || e.getEndpoint2() == Endpoint.NULL) {
		if (!g.isAdjacentTo(e.getNode1(), e.getNode2()))
		    count++;
	    } else {
		if (g.containsEdge(e))
		    count++;
	    }
	}

	return count / (double) PAGs.size();
    }

    private double getProbabilityBasedOnDirectedPath(Edge e) {
	int count = 0;

	if (!PAGs.get(0).containsNode(e.getNode1()))
	    throw new IllegalArgumentException();
	if (!PAGs.get(0).containsNode(e.getNode2()))
	    throw new IllegalArgumentException();

	for (Graph g : PAGs) {
	    if (e.getEndpoint1() == Endpoint.TAIL
		    && e.getEndpoint2() == Endpoint.ARROW) {
		if (g.existsDirectedPathFromTo(e.getNode1(), e.getNode2()))
		    count++;

	    } else if (e.getEndpoint1() == Endpoint.ARROW
		    && e.getEndpoint2() == Endpoint.TAIL) {
		if (g.existsDirectedPathFromTo(e.getNode2(), e.getNode1()))
		    count++;

	    } else if (e.getEndpoint1() == Endpoint.ARROW
		    && e.getEndpoint2() == Endpoint.ARROW) {
		if (g.existsDirectedPathFromTo(e.getNode1(), e.getNode2())
			&& g.existsDirectedPathFromTo(e.getNode2(),
				e.getNode1()))
		    count++;

	    } else if (e.getEndpoint1() == Endpoint.NULL
		    || e.getEndpoint2() == Endpoint.NULL) {
		if (!g.isAdjacentTo(e.getNode1(), e.getNode2()))
		    count++;

	    } else {
		if (g.containsEdge(e))
		    count++;
	    }
	}
	return count / (double) PAGs.size();
    }

}