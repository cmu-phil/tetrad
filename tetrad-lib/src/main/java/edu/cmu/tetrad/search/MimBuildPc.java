///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemOptimizerEm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Implements Generalized MimBuild, as specified on page 362 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition. More details are provided in Silva (2002).</p> <p>This implementation assumes
 * the measurement model is given in the Knowledge object passed by the constructor. We assume that each latent has at
 * least two measures.</p> <p>References:</p> <p>Silva, R. (2002). "The structure of the unobserved". Technical report
 * CMU-CALD-02-102, Center for Automated Learning and Discovery, Carnegie Mellon University.</p>
 *
 * @author Ricardo Silva
 */

public final class MimBuildPc {
    public static final String LATENT_PREFIX = "_L";

    private List<Node> latents;
    private IndTestMimBuild indTest;
    private IKnowledge knowledge;
    private double alpha = 0.001;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();
    private Graph structuralModel;
    private boolean verbose = false;

    public MimBuildPc(IndTestMimBuild indTest, IKnowledge knowledge) {
        this.latents = new ArrayList<Node>();
        this.indTest = indTest;
        this.knowledge = knowledge;
    }

    public Graph search() {
        if (getIndTest() == null) {
            throw new NullPointerException();
        }

        int type = getIndTest().getAlgorithmType();

        if (type == IndTestMimBuild.MIMBUILD_GES_ABIC || type == IndTestMimBuild.MIMBUILD_GES_SBIC) {
            Graph graph = mimBuildGesSearch();
            this.logger.log("graph", "\nReturning this graph: " + graph);
            return graph;
        } else {
            Graph graph = mimBuildPcSearch();
            this.logger.log("graph", "\nReturning this graph: " + graph);
            return graph;
        }
    }

    public static String[] getTestDescriptions() {
        String tests[] = new String[2];
        tests[0] = "Gaussian maximum likelihood";
        tests[1] = "Two-stage least squares";
        return tests;
    }

    public static String[] getAlgorithmDescriptions() {
        String labels[] = new String[2];
        labels[0] = "GES";
        labels[1] = "PC";
        return labels;
    }

    public static List<String> generateLatentNames(int total) {
        List<String> output = new ArrayList<String>();
        for (int i = 0; i < total; i++) {
            output.add(LATENT_PREFIX + (i + 1));
        }
        return output;
    }

    /**
     * Perform MIMBuild with PC Search and Peter's independence test
     */
    private Graph mimBuildPcSearch() {
        Graph graph = new EdgeListGraph(getIndTest().getVariableList());
        startMeasurementModel(graph);
        MimAdjacencySearch adj = new MimAdjacencySearch(graph, getIndTest(),
                getKnowledge(), this.latents);
        SepsetMap sepset = adj.adjSearch();

        //Create new graph composed only of the edges among latent variables
        Graph latent_graph = new EdgeListGraph(this.latents);

        for (int i = 0; i < this.latents.size(); i++) {
            for (int j = i + 1; j < this.latents.size(); j++) {
                latent_graph.addUndirectedEdge(latents.get(i), latents.get(j));
            }
        }

//        SearchGraphUtils.pcOrient(sepset, getKnowledge(), latent_graph,
        TetradLogger.getInstance().log("info", "Starting PC Orientation.");

        SearchGraphUtils.pcOrientbk(getKnowledge(), latent_graph, graph.getNodes());
        SearchGraphUtils.orientCollidersUsingSepsets(sepset, getKnowledge(), latent_graph, verbose);
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.orientImplied(latent_graph);

        TetradLogger.getInstance().log("info", "Finishing PC Orientation");

        //Put the orientations back to 'graph'
        for (Node current : this.latents) {
            for (Node ad_node : latent_graph.getNodesInTo(current, Endpoint.ARROW)) {
                graph.setEndpoint(ad_node, current, Endpoint.ARROW);
            }
        }

        return graph;
    }

    /**
     * Perform MIMBuild with GES search and BIC score.
     */
    private Graph mimBuildGesSearch() {
        System.out.println("A");

        double score, newScore;

        // Form the graph over measured and latent variables with a directed
        // edge from each latent to each of its measurements and a complete
        // undirected graph over the latents.
        Graph graph = new EdgeListGraph(getIndTest().getVariableList());
        startMeasurementModel(graph);

        System.out.println("A2");

        // Make a list of all of the variables names in this graph plus all
        // of the latent variable names in this graph.
        String varNames[] = new String[graph.getNumNodes()];
        for (int i = 0; i < varNames.length; i++) {
            varNames[i] = graph.getNodes().get(i).toString();
        }
        String latentVarNames[] = new String[this.latents.size()];
        for (int i = 0; i < this.latents.size(); i++) {
            latentVarNames[i] = this.latents.get(i).toString();
        }

        System.out.println("A3");

        // This is the covariance matrix over the measured variables.
        ICovarianceMatrix covMatrix = getIndTest().getCovMatrix();


        System.out.println("A4");

        // Get a DAG in the graph 'graph', respecting knowledge.
//        DagInPatternIterator iterator = new DagInPatternIterator(graph);
//        System.out.println("A4a");
//        iterator.setKnowledge(getKnowledge());
//        System.out.println("A4b");
//        graph = iterator.next();

//        PatternToDag pd = new PatternToDag(new Pattern(graph));
//        graph = pd.patternToDagMeek();
//        SearchGraphUtils.pdagToDag(graph);

        System.out.println("A5");

        SemOptimizerEm optimizer = new SemOptimizerEm();

        SemEstimator estimator = new SemEstimator(covMatrix, new SemPm(graph), optimizer);
        estimator.estimate();
        newScore = Double.NEGATIVE_INFINITY; // scoreModel(estimator.getEstimatedSem());

        System.out.println("B");

        do {
            List<Node> continuousVariables = DataUtils.createContinuousVariables(varNames);
            TetradMatrix oldExpectedCovariance = optimizer.getExpectedCovarianceMatrix();
            int sampleSize = covMatrix.getSampleSize();

            ICovarianceMatrix expectedCovarianceMatrix = new CovarianceMatrix(continuousVariables,
                    oldExpectedCovariance, sampleSize);
            ICovarianceMatrix newCovMatrix = expectedCovarianceMatrix.getSubmatrix(latentVarNames);

            score = newScore;
            System.out.println("C");

            System.out.println("alpha = " + getAlpha());
//
//            Ges ges = new Ges(newCovMatrix);
//            ges.setKnowledge(getKnowledge());
//            Graph newStructuralModel = ges.search();

            Jpc jpc = new Jpc(new IndTestFisherZ(newCovMatrix, getAlpha()));
            jpc.setKnowledge(getKnowledge());
            jpc.setAggressivelyPreventCycles(true);
            Graph newStructuralModel = jpc.search();

            this.structuralModel = newStructuralModel;

            for (Edge edge : new ArrayList<Edge>(newStructuralModel.getEdges())) {
                if (Edges.isBidirectedEdge(edge)) {
                    newStructuralModel.removeEdge(edge);
//                    newStructuralModel.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }

            System.out.println("D");

            if (getKnowledge().isViolatedBy(newStructuralModel)) {
                System.out.println("VIOLATED1!");
            }

            System.out.println(newStructuralModel.toString());
            Graph directedStructuralModel = new EdgeListGraph(newStructuralModel);

            if (getKnowledge().isViolatedBy(directedStructuralModel)) {
                System.out.println("VIOLATED2!");
            }

            System.out.println("E");

            DagInPatternIterator iterator = new DagInPatternIterator(directedStructuralModel, getKnowledge(), false, true);
            directedStructuralModel = iterator.next();

//            SearchGraphUtils.pdagToDag(directedStructuralModel);

            if (getKnowledge().isViolatedBy(directedStructuralModel)) {
                System.out.println("VIOLATED3!");
            }

            System.out.println(directedStructuralModel);

            Graph newCandidate = getUpdatedGraph(graph, directedStructuralModel);

            if (getKnowledge().isViolatedBy(newCandidate)) {
                System.out.println("VIOLATED4!");
            }

            System.out.println("F");

            estimator = new SemEstimator(covMatrix, new SemPm(newCandidate), optimizer);
            estimator.estimate();

            System.out.println("G");

            newScore = scoreModel(estimator.getEstimatedSem());


            System.out.println("H");

            if (newScore > score) {
                graph = getUpdatedGraph(graph, newStructuralModel);

                if (getKnowledge().isViolatedBy(graph)) {
                    System.out.println("VIOLATED5!");
                }
            }
        } while (newScore > score);
        System.out.println("Yes, I got here!!!");

        System.out.println(graph);

        return graph;
    }

    private Graph getUpdatedGraph(Graph graph, Graph structuralModel) {
        Graph output = new EdgeListGraph(graph);
        List<Edge> edgesToRemove = new ArrayList<Edge>();
        for (Edge nextEdge : output.getEdges()) {
            if (nextEdge.getNode1().getNodeType() == NodeType.LATENT &&
                    nextEdge.getNode2().getNodeType() == NodeType.LATENT) {
                edgesToRemove.add(nextEdge);
            }
        }
        output.removeEdges(edgesToRemove);
        for (Edge nextEdge : structuralModel.getEdges()) {
            Node node1 = output.getNode(nextEdge.getNode1().toString());
            Node node2 = output.getNode(nextEdge.getNode2().toString());
            output.setEndpoint(node2, node1, nextEdge.getEndpoint1());
            output.setEndpoint(node1, node2, nextEdge.getEndpoint2());
        }
        return output;
    }

    private double scoreModel(SemIm semIm) {
//        return -semIm.getBicScore();
//        return semIm.getPValue();
        return -semIm.getScore();


//        double fml = semIm.getFml();
//        int freeParams = semIm.getNumFreeParams();
//        int sampleSize = semIm.getSampleSize();
//
//        return -fml - (freeParams * Math.log(sampleSize));
    }

    /**
     * Initialize the measurement model. It will look at the Knowledge object and get information about which edges are
     * required. Nodes that lie at the tails of edges are considered to be latent and added to the latents list.
     */
    private void startMeasurementModel(Graph graph) {

        // Add the arrows from latents to measured variables according to
        // specific background knowledge included on the independence checker.
        Iterator<KnowledgeEdge> it = getIndTest().getMeasurements().requiredEdgesIterator();

        while (it.hasNext()) {
            KnowledgeEdge edge = it.next();

            Node x = graph.getNode(edge.getFrom());
            Node y = graph.getNode(edge.getTo());

            graph.addDirectedEdge(x, y);

            if (!latents.contains(x)) {
                latents.add(x);
                x.setNodeType(NodeType.LATENT);
            }
        }

        /*
         * Now connect latent variables according
         */
        int size = latents.size();
        Iterator<Node> itl = latents.iterator();
        Node[] nodes = new Node[size];
        int count = 0;
        while (itl.hasNext()) {
            nodes[count++] = itl.next();
        }
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                if (!knowledge.isForbidden(nodes[i].getName(), nodes[j].getName())) {
//                    graph.addUndirectedEdge(nodes[i], nodes[j]);
//                    graph.addDirectedEdge(nodes[i], nodes[j]);
                }
            }
        }
    }

    private IndTestMimBuild getIndTest() {
        return indTest;
    }

    private IKnowledge getKnowledge() {
        return knowledge;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public Graph getStructuralModel() {
        return structuralModel;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


