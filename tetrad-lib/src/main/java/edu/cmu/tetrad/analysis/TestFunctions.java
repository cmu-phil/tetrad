package edu.cmu.tetrad.analysis;

import cern.colt.function.DoubleFunction;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.Ricf;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Runs algorithm on data set to find alpha value.
 *
 * @author dmalinsky 2016.06.22
 */
public class TestFunctions {

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
//        String path = "/Users/dmalinsky/Documents/research/data/garmit";
        String path = "/Users/dmalinsky/Documents/research/data/test";

        File dir = new File(path);
        File[] files = dir.listFiles();

        if (files == null) throw new NullPointerException("No files in " + path);
        for (File file : files) {

            if (file.getName().startsWith("1data") && file.getName().endsWith("moth.txt")) {
//                Path dataFile = Paths.get(path.concat("/").concat(file.getName()));
//                Character delimiter = '\t';
                try {
                    DataReader reader = new DataReader();
                    cov = reader.parseCovariance(file);
                    System.out.println("Using covariance matrix from data file : " + file.getName());
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Covariance matrix is : " + cov);
        List<Node> nodes = cov.getVariables();
        Graph mag = new EdgeListGraphSingleConnections(nodes);
        mag.addDirectedEdge(nodes.get(2),nodes.get(3));
        mag.addDirectedEdge(nodes.get(3),nodes.get(4));
        mag.addUndirectedEdge(nodes.get(1),nodes.get(2));
        mag.addBidirectedEdge(nodes.get(3),nodes.get(0));
        mag.addBidirectedEdge(nodes.get(4),nodes.get(0));

        System.out.println("Mag is : " + mag);

        double score = scoreMag(mag);
        System.out.println("Score is : " + score);
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
//        int n = lagdata.getNumRows();
        int n = cov.getSampleSize();
        double logn = Math.log(n);
//        int p = lagdata.getNumColumns();
        int p = cov.getDimension();
        Ricf.RicfResult result = new Ricf().ricf2(mag, cov, 0.001);
        DoubleMatrix2D Shat = result.getShat();
        System.out.println("Sigma hat is : " + Shat);
        System.out.println("Number of non-zero entries = " + numNonZero(Shat));
        int numVar = numNonZero(Shat);
        return score(Shat, n, logn, p, numVar);
    }

    ArrayList<Integer> nlist = new ArrayList<>();
    ArrayList<Integer> plist = new ArrayList<>();
    ArrayList<Integer> numVarlist = new ArrayList<>();
    // Calculates the -2BIC score. Note: assuming data is mean zero. This score is to be minimized.
    private double score(DoubleMatrix2D Shat, int n, double logn, int p, int numVar) {
//        DoubleMatrix2D S = cov(lagdata.getDoubleData());
        DoubleMatrix2D mat = new DenseDoubleMatrix2D(cov.getMatrix().toArray());
        System.out.println("mat is : " + mat);
        double factor = ((double) n-1)/n;
        DoubleMatrix2D S = scatter(mat,factor);
        System.out.println("S is : " + S);
        double logL = loglik(S, Shat, n);
        System.out.println("n is " + n);
        System.out.println("logn is " + logn);
        System.out.println("p is " + p);
        System.out.println("Likelihood is : " + logL);
        nlist.add(n);
        plist.add(p);
        numVarlist.add(numVar);
        return (-2.0 * logL + (numVar + p + 1.0) * logn);
    }

    ArrayList<Double> likList = new ArrayList<>();
    private double loglik(DoubleMatrix2D S, DoubleMatrix2D Shat, int n) {
//        double factor = (n-1)/n;
//        TetradMatrix Snew = cov.getMatrix().scalarMult(factor);
//        DoubleMatrix2D Snew2 = new DenseDoubleMatrix2D(Snew.toArray());
        Algebra algebra = new Algebra();
        DoubleMatrix2D Si = algebra.inverse(Shat);
        DoubleMatrix2D SiS = algebra.mult(Si, S);
//        double con = n * cov.getDimension() * 0.5 * Math.log(2 * Math.PI);
        double con = 0.0;
        likList.add(-(n * 0.5) * Math.log(algebra.det(Shat)) - (n * 0.5) * algebra.trace(SiS) - con);
        System.out.println("First term in loglik : " + -(n * 0.5) * Math.log(algebra.det(Shat)));
        System.out.println("Second term in loglik : " + -(n * 0.5) * algebra.trace(SiS));
        System.out.println("Sigma hat inverse : " + Si);
        System.out.println("Si*S : " + SiS);
        System.out.println("Scatter matrix : " + S);
        return (-(n * 0.5) * Math.log(algebra.det(Shat)) - (n * 0.5) * algebra.trace(SiS) - con); // add constant?
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

    public static DoubleMatrix2D scatter(DoubleMatrix2D data, double factor) {
        System.out.println("data = " + data);
        for (int i = 0; i < data.rows(); i++) {
            for (int j = 0; j < data.columns(); j++) {
                System.out.println("data.get(i,j) = " + data.get(i,j));
                System.out.println("factor = " + factor);
                data.set(i, j, data.get(i, j) * factor);
                System.out.println("Setting element : " + data.get(i,j) + " to : " + data.get(i, j) * factor);
            }
        }
        return data;
    }


//    public static DoubleMatrix2D cov(TetradMatrix data) {
//        for (int j = 0; j < data.columns(); j++) {
//            double sum = 0.0;
//
//            for (int i = 0; i < data.rows(); i++) {
//                sum += data.get(i, j);
//            }
//
//            double mean = sum / data.rows();
//
//            for (int i = 0; i < data.rows(); i++) {
//                data.set(i, j, data.get(i, j) - mean);
//            }
//        }
//
//        RealMatrix q = data.getRealMatrix();
//
//        RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
//        RealMatrix q2 = times(q1, q);
////        DoubleMatrix2D prod = new DoubleMatrix2D(q2, q.getColumnDimension(), q.getColumnDimension());
//        DoubleMatrix2D prod = new DenseDoubleMatrix2D(q2.getData());
//
//        double factor = 1.0 / (data.rows()); // removed -1 so it is 1/n not 1/(n-1)
//
//        for (int i = 0; i < prod.rows(); i++) {
//            for (int j = 0; j < prod.columns(); j++) {
//                prod.set(i, j, prod.get(i, j) * factor);
//            }
//        }
//
//        return prod;
//    }
//
//    private static RealMatrix times(final RealMatrix m, final RealMatrix n) {
//        if (m.getColumnDimension() != n.getRowDimension()) throw new IllegalArgumentException("Incompatible matrices.");
//
//        final int rowDimension = m.getRowDimension();
//        final int columnDimension = n.getColumnDimension();
//
//        final RealMatrix out = new BlockRealMatrix(rowDimension, columnDimension);
//
//        final int NTHREADS = Runtime.getRuntime().availableProcessors();
//
//        ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();
//
//        for (int t = 0; t < NTHREADS; t++) {
//            final int _t = t;
//
//            Runnable worker = new Runnable() {
//                @Override
//                public void run() {
//                    int chunk = rowDimension / NTHREADS + 1;
//                    for (int row = _t * chunk; row < Math.min((_t + 1) * chunk, rowDimension); row++) {
//                        if ((row + 1) % 100 == 0) System.out.println(row + 1);
//
//                        for (int col = 0; col < columnDimension; ++col) {
//                            double sum = 0.0D;
//
//                            int commonDimension = m.getColumnDimension();
//
//                            for (int i = 0; i < commonDimension; ++i) {
//                                sum += m.getEntry(row, i) * n.getEntry(i, col);
//                            }
//
////                            double sum = m.getRowVector(row).dotProduct(n.getColumnVector(col));
//                            out.setEntry(row, col, sum);
//                        }
//                    }
//                }
//            };
//
//            pool.submit(worker);
//        }
//
//        while (!pool.isQuiescent()) {
//        }
//
//        return out;
//    }

    public static void main(String... args) {
        new TestFunctions().runOnData();
    }
}
