package edu.cmu.tetrad.analysis;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.Ricf;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs algorithms on data set to find alpha value.
 *
 * @author dmalinsky 2016.06.22
 */
public class AnalysisScriptAlpha {

    DataSet lagdata;
    ICovarianceMatrix cov;

    private void runOnData() {
        // specify data directory like in Comparison2, load in data
        // create lag data from input data
        // run algorithm on lag data with proper settings, alpha = high
        // find MAG in PAG
        // score MAG
        // repeat, decreasing alpha level
        // return model and alpha with best score

        /** Set path to the data directory **/
        String path = "/Users/dmalinsky/Documents/research/data/garmit";

        File dir = new File(path);
        File[] files = dir.listFiles();

        if (files == null) throw new NullPointerException("No files in " + path);
        DataSet dataSet = null;
        for (File file : files) {

            if (file.getName().startsWith("data") && file.getName().endsWith(".txt")) {
                Path dataFile = Paths.get(path.concat("/").concat(file.getName()));
                Character delimiter = '\t';
                try {
                    edu.cmu.tetrad.io.DataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
                    dataSet = dataReader.readInData();
                    System.out.println("Using data file : " + file.getName());
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        lagdata = TimeSeriesUtils.createLagData(dataSet, 1);
//        cov = new CovarianceMatrixOnTheFly(lagdata);
        cov = new CovarianceMatrix(lagdata);
        List<Double> alphas = new ArrayList(Arrays.asList(0.25,0.20,0.19,0.18,0.17,0.16,0.15,0.14,0.13,0.12,
                0.11,0.10,0.09,0.08,0.07,0.06,0.05,0.04,0.03,0.02,0.01,0.009,0.008,0.007,0.006,0.005,0.004,0.003,0.002,0.001));
        ArrayList<Graph> graphlist = new ArrayList<>();
        ArrayList<Double> scorelist = new ArrayList<>();
        ArrayList<Double> alphalist = new ArrayList<>();
        for(double alpha : alphas) {
            IndependenceTest test = new IndTestFisherZ(lagdata, alpha);
            TsFci fci = new TsFci(test);
            fci.setKnowledge(lagdata.getKnowledge());
            fci.setCompleteRuleSetUsed(true);
            fci.setPossibleDsepSearchDone(true);
            fci.setMaxPathLength(-1);
            fci.setDepth(-1);
            Graph graph = fci.search();
            System.out.println("Alpha set to : " + alpha);
            System.out.println("Search result : " + graph);
            alphalist.add(alpha);
            graphlist.add(graph);
            Graph mag = MagInPag(graph);
            double score = scoreMag(mag);
            System.out.println("Score is : " + score);
            scorelist.add(score);
        }

        double maxScore = Collections.min(scorelist);
        int index = scorelist.indexOf(maxScore);
        double maxAlpha = alphalist.get(index);
        Graph bestGraph = graphlist.get(index);

        System.out.println("Best alpha value : " + maxAlpha);
        System.out.println("Best graph : " + bestGraph);

        System.out.println("score list : " + scorelist);
        System.out.println("index is : " + index);
        System.out.println("likelihood list : " + likList);
//        System.out.println("graph list : " + graphlist);
    }

    /** Creates a MAG in the equivalence class represented by a PAG.
     * First create a Tail Augmented Graph (TAG) by turning all o-> edges into -->.
     * Then orient the remaining circle component (o-o edges) into a DAG.
     * The resulting graph is a MAG in the equivalence class according to Zhang (2006, Lemma 3.3.4). **/

    public Graph MagInPag(Graph pag){
        Graph mag = new EdgeListGraphSingleConnections(pag);
        ArrayList<Edge> circleComp = new ArrayList<>();
        List<Node> nodesList = new ArrayList<>();
        for (Edge edge : mag.getEdges()) {
            if (Edges.isDirectedEdge(edge) || Edges.isUndirectedEdge(edge)
                    || Edges.isBidirectedEdge(edge)) {
                continue;
            }

            if (Edges.isPartiallyOrientedEdge(edge)){
                // create Tail Augmented Graph (TAG)
                if (edge.getEndpoint1()== Endpoint.CIRCLE){
                    mag.setEndpoint(edge.getNode2(),edge.getNode1(),Endpoint.TAIL);
                    continue;
                } else {
                    mag.setEndpoint(edge.getNode1(),edge.getNode2(),Endpoint.TAIL);
                    continue;
                }
            }

            if (Edges.isNondirectedEdge(edge)){
                circleComp.add(edge);
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();
                if (!nodesList.contains(node1)) nodesList.add(node1);
                if (!nodesList.contains(node2)) nodesList.add(node2);
                // might be more efficient to do these with a loop?
            }
        }

//        System.out.println("Circle component is : " + circleComp);

        if (!circleComp.isEmpty()) {

            Graph pattern = new EdgeListGraphSingleConnections(nodesList);
            for (Edge edge : circleComp) {
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();
                pattern.addUndirectedEdge(node1, node2);
            }

//            System.out.println("Pattern is : " + pattern);

            DagInPatternIterator DagIterator = new DagInPatternIterator(pattern, lagdata.getKnowledge(), false, false);
            Graph dag = DagIterator.next();

//            System.out.println("Dag is : " + dag);

            for (Edge edge : dag.getEdges()) {
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();
                mag.removeEdge(node1, node2);
                mag.addEdge(edge);
            }
        }
//        System.out.println("Mag is : " + mag);
        return mag;
    }

    public Double scoreMag(Graph mag){
        if (cov == null) throw new NullPointerException("No covariance matrix");
        int n = lagdata.getNumRows();
        double logn = Math.log(n);
        int p = lagdata.getNumColumns();
        Ricf.RicfResult result = new Ricf().ricf2(mag, cov, 0.001);
        DoubleMatrix2D Shat = result.getShat();
//        System.out.println("Sigma hat is : " + Shat);
        System.out.println("Number of non-zero entries = " + numNonZero(Shat));
        int numVar = numNonZero(Shat);
        return score(Shat, n, logn, p, numVar);
    }

    // Calculates the -2BIC score. Note: assuming data is mean zero. This score is to be minimized.
    private double score(DoubleMatrix2D Shat, int n, double logn, int p, int numVar) {
        DoubleFactory2D factory = DoubleFactory2D.dense;
        DoubleMatrix2D K = factory.diagonal(factory.diagonal(Shat)); // is this the right way to calculate K?? (S in paper)?
//        DoubleMatrix2D K2 = lagdata.getCovarianceMatrix();
        double L = lik(K, Shat, n);
        System.out.println("Likelihood is : " + L);
        return (-2 * Math.log(L) + (numVar + p + 1) * logn);
    }

    ArrayList<Double> likList = new ArrayList<>();
    private double lik(DoubleMatrix2D K, DoubleMatrix2D Shat, int n) {
        Algebra algebra = new Algebra();
        DoubleMatrix2D Si = algebra.inverse(Shat);
        DoubleMatrix2D SK = algebra.mult(Si, K);
        likList.add(Math.abs(-(n * 0.5) * Math.log(algebra.det(Shat)) - (n * 0.5) * algebra.trace(SK)));
        return Math.abs(-(n * 0.5) * Math.log(algebra.det(Shat)) - (n * 0.5) * algebra.trace(SK));
//        return (algebra.trace(SK) - Math.log(algebra.det(SK)) - k) * n;
    }

    private int numNonZero(DoubleMatrix2D m){
        int count = 0;
        for (int j = 0; j <= m.columns()-1; j++){
            for (int i = 0; i <= j; i++){
                if(m.get(i,j) != 0) count++;
            }
        }
        return count;
    }

    public static void main(String... args) {
        new AnalysisScriptAlpha().runOnData();
    }
}
