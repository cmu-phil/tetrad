package edu.pitt.dbmi.algo.bootstrap;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahdi on 1/16/17.
 * 
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 4/5/2017
 * 
 */
public class BootstrapTest {

	private PrintStream out = System.out;

	private final BootstrapSearch bootstrapSearch;

	private Parameters parameters;

	private boolean runParallel = true;

	private BootstrapAlgName algName = BootstrapAlgName.RFCI;

	private List<Graph> PAGs;

	private boolean verbose = false;

	/**
	 * Specification of forbidden and required edges.
	 */
	private IKnowledge knowledge = new Knowledge2();

	private BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Preserved;

	/**
	 * An initial graph to start from.
	 */
	private Graph initialGraph;

	public void setParallelMode(boolean runParallel) {
		this.runParallel = runParallel;
	}

	/**
	 * Sets whether verbose output should be produced.
	 */
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Sets the output stream that output (except for log output) should be sent
	 * to. By detault System.out.
	 */
	public void setOut(PrintStream out) {
		this.out = out;
	}

	/**
	 * @return the output stream that output (except for log output) should be
	 *         sent to.
	 */
	public PrintStream getOut() {
		return out;
	}

	public Parameters getParameters() {
		return parameters;
	}

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public void setNumBootstrapSamples(int numBootstrapSamples) {
		this.bootstrapSearch.setNumOfBootstrap(numBootstrapSamples);
	}

	/**
	 * @return the background knowledge.
	 */

	public IKnowledge getKnowledge() {
		return knowledge;
	}

	/**
	 * Sets the background knowledge.
	 *
	 * @param knowledge
	 *            the knowledge object, specifying forbidden and required edges.
	 */
	public void setKnowledge(IKnowledge knowledge) {
		if (knowledge == null)
			throw new NullPointerException();
		this.knowledge = knowledge;
	}

	public BootstrapEdgeEnsemble getEdgeEnsemble() {
		return edgeEnsemble;
	}

	public void setEdgeEnsemble(BootstrapEdgeEnsemble edgeEnsemble) {
		this.edgeEnsemble = edgeEnsemble;
	}

	public void setEdgeEnsemble(String edgeEnsemble) {
		if(edgeEnsemble.equalsIgnoreCase("Highest")){
			this.edgeEnsemble = BootstrapEdgeEnsemble.Highest;
		}else if(edgeEnsemble.equalsIgnoreCase("Majority")){
			this.edgeEnsemble = BootstrapEdgeEnsemble.Majority;
		}
	}

	/**
	 * @return the initial graph for the search. The search is initialized to
	 *         this graph and proceeds from there.
	 */
	public Graph getInitialGraph() {
		return initialGraph;
	}

	/**
	 * Sets the initial graph.
	 */
	public void setInitialGraph(Graph initialGraph) {
		this.initialGraph = initialGraph;
	}

	public BootstrapTest(DataSet data, String algName) {
		this.algName = BootstrapAlgName.FGES;
		if(algName.equalsIgnoreCase("GFCI")){
			this.algName = BootstrapAlgName.GFCI;
		}else if(algName.equalsIgnoreCase("RFCI")){
			this.algName = BootstrapAlgName.RFCI;
		}
		bootstrapSearch = new BootstrapSearch(data);
	}
	
	public BootstrapTest(DataSet data, BootstrapAlgName algName) {
		this.algName = algName;
		bootstrapSearch = new BootstrapSearch(data);
	}

	public BootstrapTest(DataSet data, BootstrapAlgName algName, int numBootstrapSamples) {
		this.algName = algName;
		bootstrapSearch = new BootstrapSearch(data);
		bootstrapSearch.setNumOfBootstrap(numBootstrapSamples);
	}

	public Graph search() {
		long start, stop;

		start = System.currentTimeMillis();

		bootstrapSearch.setAlgorithm(algName);
		bootstrapSearch.setRunningMode(runParallel);
		bootstrapSearch.setVerbose(verbose);
		bootstrapSearch.setParameters(parameters);

		if (verbose) {
			out.println("Bootstrapping on the " + algName + " algorithm");
		}

		PAGs = bootstrapSearch.search();

		if (verbose) {
			out.println("Bootstrap size is : " + PAGs.size());
		}
		stop = System.currentTimeMillis();
		if (verbose) {
			out.println("Processing time of total bootstrapping : " + (stop - start) / 1000.0 + " sec");
		}

		start = System.currentTimeMillis();
		Graph graph = generateBootstrapGraph();
		stop = System.currentTimeMillis();
		if (verbose) {
			out.println("probDistribution finished in " + (stop - start) + " ms");
		}

		return graph;
	}

	private static void addNodeToGraph(Graph graph, List<Node> nodes, int start, int end) {
		if (start == end) {
			graph.addNode(nodes.get(start));
		} else if (start < end) {
			int mid = (start + end) / 2;
			addNodeToGraph(graph, nodes, start, mid);
			addNodeToGraph(graph, nodes, mid + 1, end);
		}
	}

	private Graph generateBootstrapGraph() {
		Graph pag = null;
		for (Graph g : PAGs) {
			if (g != null) {
				pag = g;
				break;
			}
		}
		Graph complete = new EdgeListGraph(pag.getNodes());
		complete.fullyConnect(Endpoint.TAIL);

		Graph graph = new EdgeListGraph();
		addNodeToGraph(graph, complete.getNodes(), 0, complete.getNodes().size() - 1);

		for (Edge e : complete.getEdges()) {
			double AnilB = 0.0;
			double AtoB = 0.0;
			double BtoA = 0.0;
			double ACtoB = 0.0;
			double BCtoA = 0.0;
			double AccB = 0.0;
			double AbB = 0.0;
			double AuB = 0.0;

			Node n1 = e.getNode1();
			Node n2 = e.getNode2();

			// if(!graph.containsNode(n1))graph.addNode(n1);
			// if(!graph.containsNode(n2))graph.addNode(n2);

			Edge edge = null;
			double maxEdgeProb = 0;

			// compute probability for each edge type
			Edge eNil = new Edge(n1, n2, Endpoint.NULL, Endpoint.NULL);
			AnilB = getProbability(eNil);

			Edge eTA = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
			AtoB = getProbability(eTA);
			if (AtoB > maxEdgeProb) {
				edge = eTA;
				maxEdgeProb = AtoB;
			}

			Edge eAT = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
			BtoA = getProbability(eAT);
			if (BtoA > maxEdgeProb) {
				edge = eAT;
				maxEdgeProb = BtoA;
			}

			Edge eCA = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
			ACtoB = getProbability(eCA);
			if (ACtoB > maxEdgeProb) {
				edge = eCA;
				maxEdgeProb = ACtoB;
			}

			Edge eAC = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
			BCtoA = getProbability(eAC);
			if (BCtoA > maxEdgeProb) {
				edge = eAC;
				maxEdgeProb = BCtoA;
			}

			Edge eCC = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
			AccB = getProbability(eCC);
			if (AccB > maxEdgeProb) {
				edge = eCC;
				maxEdgeProb = AccB;
			}

			Edge eAA = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
			AbB = getProbability(eAA);
			if (AbB > maxEdgeProb) {
				edge = eAA;
				maxEdgeProb = AbB;
			}

			Edge eTT = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
			AuB = getProbability(eTT);
			if (AuB > maxEdgeProb) {
				edge = eTT;
				maxEdgeProb = AuB;
			}

			switch (edgeEnsemble) {
			case Highest:
				if (AnilB > maxEdgeProb) {
					edge = null;
				}
				break;
			case Majority:
				if (AnilB > maxEdgeProb || maxEdgeProb < .5) {
					edge = null;
				}
				break;
			default:
				// Do nothing
			}

			if (edge != null) {
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.nil, AnilB));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.ta, AtoB));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.at, BtoA));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.ca, ACtoB));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.ac, BCtoA));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.cc, AccB));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.aa, AbB));
				edge.addEdgeTypeProbability(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.tt, AuB));

				graph.addEdge(edge);
			}

		}

		return graph;
	}

	private double getProbability(Edge e) {
		int count = 0;
		int n = PAGs.size();
		for (Graph g : PAGs) {
			if (!g.containsNode(e.getNode1())) {
				throw new IllegalArgumentException();
			}
			if (!g.containsNode(e.getNode2())) {
				throw new IllegalArgumentException();
			}

			if (e.getEndpoint1() == Endpoint.NULL || e.getEndpoint2() == Endpoint.NULL) {
				if (!g.isAdjacentTo(e.getNode1(), e.getNode2()))
					count++;
			} else {
				if (g.containsEdge(e))
					count++;
			}
		}

		return count / (double) n;
	}

	public static int[][] getAdjConfusionMatrix(Graph truth, Graph estimate) {
		Graph complete = new EdgeListGraph(estimate.getNodes());
		complete.fullyConnect(Endpoint.TAIL);
		List<Edge> edges = new ArrayList<>(complete.getEdges());
		int numEdges = edges.size();

		// Adjacency Confusion Matrix
		int[][] adjAr = new int[2][2];

		countAdjConfMatrix(adjAr, edges, truth, estimate, 0, numEdges - 1);

		return adjAr;
	}

	private static void countAdjConfMatrix(int[][] adjAr, List<Edge> edges, Graph truth, Graph estimate, int start,
			int end) {
		if (start == end) {
			Edge edge = edges.get(start);
			Node n1 = truth.getNode(edge.getNode1().toString());
			Node n2 = truth.getNode(edge.getNode2().toString());
			Node nn1 = estimate.getNode(edge.getNode1().toString());
			Node nn2 = estimate.getNode(edge.getNode2().toString());

			// Adjacency Count
			int i = truth.getEdge(n1, n2) == null ? 0 : 1;
			int j = estimate.getEdge(nn1, nn2) == null ? 0 : 1;
			adjAr[i][j]++;

		} else if (start < end) {
			int mid = (start + end) / 2;
			countAdjConfMatrix(adjAr, edges, truth, estimate, start, mid);
			countAdjConfMatrix(adjAr, edges, truth, estimate, mid + 1, end);
		}
	}

	public static int[][] getEdgeTypeConfusionMatrix(Graph truth, Graph estimate) {
		Graph complete = new EdgeListGraph(estimate.getNodes());
		complete.fullyConnect(Endpoint.TAIL);
		List<Edge> edges = new ArrayList<>(complete.getEdges());
		int numEdges = edges.size();

		// Edge Type Confusion Matrix
		int[][] edgeAr = new int[8][8];

		countEdgeTypeConfMatrix(edgeAr, edges, truth, estimate, 0, numEdges - 1);

		return edgeAr;
	}

	private static void countEdgeTypeConfMatrix(int[][] edgeAr, List<Edge> edges, Graph truth, Graph estimate,
			int start, int end) {
		if (start == end) {
			Edge edge = edges.get(start);
			Node n1 = truth.getNode(edge.getNode1().toString());
			Node n2 = truth.getNode(edge.getNode2().toString());
			Node nn1 = estimate.getNode(edge.getNode1().toString());
			Node nn2 = estimate.getNode(edge.getNode2().toString());

			int i = 0, j = 0;

			Edge eTA = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
			if (truth.containsEdge(eTA)) {
				i = 1;
			}
			eTA = new Edge(nn1, nn2, Endpoint.TAIL, Endpoint.ARROW);
			if (estimate.containsEdge(eTA)) {
				j = 1;
			}
			Edge eAT = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
			if (truth.containsEdge(eAT)) {
				i = 2;
			}
			eAT = new Edge(nn1, nn2, Endpoint.ARROW, Endpoint.TAIL);
			if (estimate.containsEdge(eAT)) {
				j = 2;
			}
			Edge eCA = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
			if (truth.containsEdge(eCA)) {
				i = 3;
			}
			eCA = new Edge(nn1, nn2, Endpoint.CIRCLE, Endpoint.ARROW);
			if (estimate.containsEdge(eCA)) {
				j = 3;
			}
			Edge eAC = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
			if (truth.containsEdge(eAC)) {
				i = 4;
			}
			eAC = new Edge(nn1, nn2, Endpoint.ARROW, Endpoint.CIRCLE);
			if (estimate.containsEdge(eAC)) {
				j = 4;
			}
			Edge eCC = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
			if (truth.containsEdge(eCC)) {
				i = 5;
			}
			eCC = new Edge(nn1, nn2, Endpoint.CIRCLE, Endpoint.CIRCLE);
			if (estimate.containsEdge(eCC)) {
				j = 5;
			}
			Edge eAA = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
			if (truth.containsEdge(eAA)) {
				i = 6;
			}
			eAA = new Edge(nn1, nn2, Endpoint.ARROW, Endpoint.ARROW);
			if (estimate.containsEdge(eAA)) {
				j = 6;
			}
			Edge eTT = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
			if (truth.containsEdge(eTT)) {
				i = 7;
			}
			eTT = new Edge(nn1, nn2, Endpoint.TAIL, Endpoint.TAIL);
			if (estimate.containsEdge(eTT)) {
				j = 7;
			}

			edgeAr[i][j]++;

		} else if (start < end) {
			int mid = (start + end) / 2;
			countEdgeTypeConfMatrix(edgeAr, edges, truth, estimate, start, mid);
			countEdgeTypeConfMatrix(edgeAr, edges, truth, estimate, mid + 1, end);
		}
	}

}