package edu.pitt.dbmi.algo.resampling;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mahdi on 1/16/17.
 * 
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 9/13/2018
 * 
 */
public class GeneralResamplingTest {

	private PrintStream out = System.out;

	private final GeneralResamplingSearch resamplingSearch;

	private Parameters parameters;

	private boolean runParallel = true;

	private Algorithm algorithm = null;
	
	private MultiDataSetAlgorithm multiDataSetAlgorithm = null;
	
	private List<Graph> PAGs;

	private boolean verbose = false;

	/**
	 * Specification of forbidden and required edges.
	 */
	private IKnowledge knowledge = new Knowledge2();

	private ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Preserved;

	/**
	 * An initial graph to start from.
	 */
	private Graph initialGraph = null;

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

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
        Object obj = parameters.get("printStream");
        if (obj instanceof PrintStream) {
            setOut((PrintStream) obj);
        }
	}

	public void setPercentResampleSize(double percentResampleSize) {
		this.resamplingSearch.setPercentResampleSize(percentResampleSize);
	}

	public void setResamplingWithReplacement(boolean ResamplingWithReplacement) {
		this.resamplingSearch.setResamplingWithReplacement(ResamplingWithReplacement);
	}

	public void setNumberResampling(int numberResampling) {
		this.resamplingSearch.setNumberResampling(numberResampling);
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

	public ResamplingEdgeEnsemble getEdgeEnsemble() {
		return edgeEnsemble;
	}

	public void setEdgeEnsemble(ResamplingEdgeEnsemble edgeEnsemble) {
		this.edgeEnsemble = edgeEnsemble;
	}

	public void setEdgeEnsemble(String edgeEnsemble) {
		if(edgeEnsemble.equalsIgnoreCase("Highest")){
			this.edgeEnsemble = ResamplingEdgeEnsemble.Highest;
		}else if(edgeEnsemble.equalsIgnoreCase("Majority")){
			this.edgeEnsemble = ResamplingEdgeEnsemble.Majority;
		}
	}

	/**
	 * Sets the initial graph.
	 */
	public void setInitialGraph(Graph initialGraph) {
		this.initialGraph = initialGraph;
	}

	public void setSeed(long seed) {
		RandomUtil.getInstance().setSeed(seed);
	}

	public GeneralResamplingTest(DataSet data, Algorithm algorithm) {
		this.algorithm = algorithm;
		resamplingSearch = new GeneralResamplingSearch(data);
	}

	public GeneralResamplingTest(DataSet data, Algorithm algorithm, int numberResampling) {
		this.algorithm = algorithm;
		resamplingSearch = new GeneralResamplingSearch(data);
		resamplingSearch.setNumberResampling(numberResampling);
	}

	public GeneralResamplingTest(List<DataSet> dataSets, MultiDataSetAlgorithm multiDataSetAlgorithm) {
		this.multiDataSetAlgorithm = multiDataSetAlgorithm;
		resamplingSearch = new GeneralResamplingSearch(dataSets);
	}

	public GeneralResamplingTest(List<DataSet> dataSets, MultiDataSetAlgorithm multiDataSetAlgorithm, int numberResampling) {
		this.multiDataSetAlgorithm = multiDataSetAlgorithm;
		resamplingSearch = new GeneralResamplingSearch(dataSets);
		resamplingSearch.setNumberResampling(numberResampling);
	}

	public Graph search() {
		long start, stop;

		start = System.currentTimeMillis();

		if(algorithm != null){
			resamplingSearch.setAlgorithm(algorithm);
		}else{
			resamplingSearch.setMultiDataSetAlgorithm(multiDataSetAlgorithm);
		}
		resamplingSearch.setRunParallel(runParallel);
		resamplingSearch.setVerbose(verbose);
		resamplingSearch.setParameters(parameters);
		
		if(!knowledge.isEmpty()){
			resamplingSearch.setKnowledge(knowledge);
		}
		
		if(initialGraph != null){
			resamplingSearch.setInitialGraph(initialGraph);
		}

		if (verbose) {
			out.println("Resampling on the " + algorithm.getDescription());
		}

		PAGs = resamplingSearch.search();

		if (verbose) {
			out.println("Resampling number is : " + PAGs.size());
		}
		stop = System.currentTimeMillis();
		if (verbose) {
			out.println("Processing time of total resamplings : " + (stop - start) / 1000.0 + " sec");
		}

		start = System.currentTimeMillis();
		Graph graph = generateSamplingGraph();
		stop = System.currentTimeMillis();
		if (verbose) {
			out.println("Final Resampling Search Result:");
			out.println(GraphUtils.graphToText(graph));
			out.println();
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

	private Graph generateSamplingGraph() {
		Graph pag = null;
		if(verbose){
			out.println("PAGs: " + PAGs.size());
			out.println("Ensemble: " + edgeEnsemble);
			out.println();
		}
		if(PAGs == null || PAGs.size() == 0)return new EdgeListGraph();
		
		int i=0;
		for (Graph g : PAGs) {
			if (g != null) {
				if(pag == null){
					pag = g;
				}
				if(verbose){
					out.println("Resampling Search Result (" + i + "):");
					out.println(GraphUtils.graphToText(g));
					out.println();
					i++;
				}
			}
		}
		if(pag==null)return new EdgeListGraph();
		
		// Sort nodes by its name for fixing the edge orientation
		List<Node> nodes = pag.getNodes();
		Collections.sort(nodes);
		
		Graph complete = new EdgeListGraph(nodes);
		complete.fullyConnect(Endpoint.TAIL);

		Graph graph = new EdgeListGraph();
		addNodeToGraph(graph, complete.getNodes(), 0, complete.getNodes().size() - 1);

		for (Edge e : complete.getEdges()) {

			Node n1 = e.getNode1();
			Node n2 = e.getNode2();
			
			// Test new probability method
			List<EdgeTypeProbability> edgeTypeProbabilities = getProbability(n1, n2);
			EdgeTypeProbability chosen_edge_type = null;
			double max_edge_prob = 0;
			double no_edge_prob = 0;
			if(edgeTypeProbabilities != null) {
				for(EdgeTypeProbability etp : edgeTypeProbabilities) {
					EdgeType edgeType = etp.getEdgeType();
					double prob = etp.getProbability();
					//out.println(edgeType + ": " + prob);
					if(edgeType != EdgeType.nil && prob > max_edge_prob) {
						if(prob > max_edge_prob) {
							chosen_edge_type = etp;
							max_edge_prob = prob;
						}
					}else {
						no_edge_prob = prob;
					}
				}			
			}
			
			if(chosen_edge_type != null) {
				Edge edge = null;
				EdgeType edgeType = chosen_edge_type.getEdgeType();
				switch (edgeType) {
				case ta:
					edge = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
					break;
				case at:
					edge = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
					break;
				case ca:
					edge = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
					break;
				case ac:
					edge = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
					break;
				case cc:
					edge = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
					break;
				case aa:
					edge = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
					break;
				case tt:
					edge = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
					break;
				default:
					// Do nothing
				}
				
				for(Property property : chosen_edge_type.getProperties()) {
					edge.addProperty(property);
				}
				
				switch (edgeEnsemble) {
				case Highest:
					if (no_edge_prob > max_edge_prob) {
						edge = null;
					}
					break;
				case Majority:
					if (no_edge_prob > max_edge_prob || max_edge_prob < .5) {
						edge = null;
					}
					break;
				default:
					// Do nothing
				}
				
				if (edge != null) {
					if(verbose){
						out.println("Final result: " + edge + " : " + max_edge_prob);
					}
					
					for(EdgeTypeProbability etp : edgeTypeProbabilities) {
						edge.addEdgeTypeProbability(etp);
					}
					
					graph.addEdge(edge);
				}
			}
			
		}

		return graph;
	}

	private List<EdgeTypeProbability> getProbability(Node node1, Node node2){
		Map<String, Integer> edgeDist = new HashMap<>();
		int no_edge_num = 0;
		for (Graph g : PAGs) {
			Edge e = g.getEdge(node1, node2);
			if(e != null) {				
				String edgeString = e.toString();
				if(e.getEndpoint1() == e.getEndpoint2() && node1.compareTo(e.getNode1()) != 0) {
					Edge edge = new Edge(node1, node2, e.getEndpoint1(), e.getEndpoint2());
					for(Property property : e.getProperties()) {
						edge.addProperty(property);
					}
					edgeString = edge.toString();
				}
				Integer num_edge = edgeDist.get(edgeString);
				if(num_edge == null) {
					num_edge = 0;
				}
				num_edge = num_edge.intValue() + 1;
				edgeDist.put(edgeString, num_edge);	
			}else {
				no_edge_num++;
			}
		}
		int n = PAGs.size();
		// Normalization
		List<EdgeTypeProbability> edgeTypeProbabilities = edgeDist.size()==0?null:new ArrayList<>();
		for(String edgeString : edgeDist.keySet()) {
			int edge_num = edgeDist.get(edgeString);
			double probability = (double) edge_num/n;
			
			String[] token = edgeString.split("\\s+");
			String n1 = token[0];
			String arc = token[1];
			String n2 = token[2];
			
			char end1 = arc.charAt(0);
            char end2 = arc.charAt(2);

            Endpoint _end1, _end2;

            if (end1 == '<') {
                _end1 = Endpoint.ARROW;
            } else if (end1 == 'o') {
                _end1 = Endpoint.CIRCLE;
            } else if (end1 == '-') {
                _end1 = Endpoint.TAIL;
            } else {
                throw new IllegalArgumentException();
            }

            if (end2 == '>') {
                _end2 = Endpoint.ARROW;
            } else if (end2 == 'o') {
                _end2 = Endpoint.CIRCLE;
            } else if (end2 == '-') {
                _end2 = Endpoint.TAIL;
            } else {
                throw new IllegalArgumentException();
            }
            
            if(node1.getName().equalsIgnoreCase(n2) && node2.getName().equalsIgnoreCase(n1)) {
            	Endpoint tmp = _end1;
            	_end1 = _end2;
            	_end2 = tmp;
            }
            
            EdgeType edgeType = EdgeType.nil;
            
            if(_end1 == Endpoint.TAIL && _end2 == Endpoint.ARROW) {
            	edgeType = EdgeType.ta;
            }
            if(_end1 == Endpoint.ARROW && _end2 == Endpoint.TAIL) {
            	edgeType = EdgeType.at;
            }
            if(_end1 == Endpoint.CIRCLE && _end2 == Endpoint.ARROW) {
            	edgeType = EdgeType.ca;
            }
            if(_end1 == Endpoint.ARROW && _end2 == Endpoint.CIRCLE) {
            	edgeType = EdgeType.ac;
            }
            if(_end1 == Endpoint.CIRCLE && _end2 == Endpoint.CIRCLE) {
            	edgeType = EdgeType.cc;
            }
            if(_end1 == Endpoint.ARROW && _end2 == Endpoint.ARROW) {
            	edgeType = EdgeType.aa;
            }
            if(_end1 == Endpoint.TAIL && _end2 == Endpoint.TAIL) {
            	edgeType = EdgeType.tt;
            }
            
            EdgeTypeProbability etp = new EdgeTypeProbability(edgeType, probability);
            
            // Edge's properties
            if(token.length > 3) {
            	for(int i=3;i<token.length;i++) {
            		etp.addProperty(Edge.Property.valueOf(token[i]));
            	}
            }
            edgeTypeProbabilities.add(etp);
		}
		
		if(no_edge_num < n && edgeTypeProbabilities != null) {
			edgeTypeProbabilities.add(new EdgeTypeProbability(EdgeType.nil, (double)no_edge_num/n));
		}
		
		return edgeTypeProbabilities;
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