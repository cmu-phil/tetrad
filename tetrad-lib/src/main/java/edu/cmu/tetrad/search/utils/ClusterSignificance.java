package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fas;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>Provides some methods to check significance of clusters for clustering algroithms.
 * It is assumed that for each cluster there is a single latent that is a parent of all the nodes in the cluster.
 * Significance of these models is returned.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ClusterSignificance {
    // The variables in the model.
    private final List<Node> variables;
    // The data model.
    private final DataModel dataModel;
    // The type of check to perform.
    private CheckType checkType = CheckType.Clique;

    /**
     * Constructs a new cluster significance object.
     *
     * @param variables The variables in the model.
     * @param dataModel The data model.
     */
    public ClusterSignificance(List<Node> variables, DataModel dataModel) {
        if (variables == null) throw new NullPointerException("Variable null.");
        if (dataModel == null) throw new NullPointerException("Data model null.");
        this.variables = variables;
        this.dataModel = dataModel;
    }

    /**
     * Converts a list of indices into a list of Nodes representing a cluster.
     *
     * @param cluster   The indices of the variables.
     * @param variables The variables.
     * @return The extracted node list.
     */
    public static List<Node> variablesForIndices(List<Integer> cluster, List<Node> variables) {
        List<Node> _cluster = new ArrayList<>();

        for (int c : cluster) {
            _cluster.add(variables.get(c));
        }

        return _cluster;
    }

    /**
     * Converts a list of indices into a list of Nodes representing a cluster.
     *
     * @param clusters   The indices of the variables.
     * @param _variables The variables.
     * @return The extracted node list.
     */
    public static List<List<Node>> variablesForIndices2(Set<List<Integer>> clusters, List<Node> _variables) {
        List<List<Node>> variables = new ArrayList<>();

        for (List<Integer> cluster : clusters) {
            variables.add(variablesForIndices(cluster, _variables));
        }

        return variables;
    }

    /**
     * Converts a list of indices into a list of Integers representing a cluster.
     *
     * @param indices The indices of the variables.
     * @return The extracted index list.
     */
    public static List<Integer> getInts(int[] indices) {
        List<Integer> cluster = new ArrayList<>();
        for (int i : indices) cluster.add(i);
        return cluster;
    }

    private static int dofHarman(int n) {
        int dof = n * (n - 5) / 2 + 1;
        if (dof < 0) dof = 0;
        return dof;
    }

    /**
     * Prints the p-values for the given clusters.
     *
     * @param clusters The clusters.
     */
    public void printClusterPValues(Set<List<Integer>> clusters) {
        NumberFormat nf = new DecimalFormat("0.000");

        for (List<Integer> _out : clusters) {
            ClusterSignificance clusterSignificance = new ClusterSignificance(variables, dataModel);

            try {
                double p = clusterSignificance.significance(new ArrayList<>(_out));
                TetradLogger.getInstance().forceLogMessage("OUT: " + variablesForIndices(new ArrayList<>(_out), variables)
                                                           + " p = " + nf.format(p));
            } catch (Exception e) {
                TetradLogger.getInstance().forceLogMessage("OUT: " + variablesForIndices(new ArrayList<>(_out), variables)
                                                           + " p = EXCEPTION");
            }
        }
    }

    /**
     * Sets the type of check to perform.
     *
     * @param checkType The type of check.
     * @see CheckType
     */
    public void setCheckType(CheckType checkType) {
        this.checkType = checkType;
    }

    /**
     * Returns the p-value for the given cluster.
     *
     * @param cluster The cluster.
     * @param alpha   The alpha level.
     * @return The p-value.
     */
    public boolean significant(List<Integer> cluster, double alpha) {
        if (checkType == CheckType.None) {
            return true;
        } else if (checkType == CheckType.Significance) {
            return significance(cluster) > alpha;
        } else if (checkType == CheckType.Clique) {
            return clique(cluster, alpha);
        } else {
            throw new IllegalArgumentException("Unexpected check type: " + checkType);
        }
    }

    /**
     * Returns the p-value for the given cluster.
     *
     * @param clusters The clusters.
     * @return The p-value.
     */
    public double getModelPValue(List<List<Integer>> clusters) {
        SemIm im = estimateModel(clusters);
        return im.getPValue();
    }

    private double significance(List<Integer> cluster) {
        double chisq = getClusterChiSquare(cluster);

        // From "Algebraic factor analysis: tetrads, triples and beyond" Drton et al.
        int n = cluster.size();
        int dof = dofHarman(n);
        double q = ProbUtils.chisqCdf(chisq, dof);
        return 1.0 - q;
    }

    private boolean clique(List<Integer> cluster, double alpha) {
        List<Node> _cluster = new ArrayList<>();
        for (int i : cluster) _cluster.add(variables.get(i));

        if (dataModel instanceof DataSet) {
            Fas fas = new Fas(new IndTestFisherZ((DataSet) dataModel, alpha));
            Graph g = fas.search(_cluster);
            return GraphUtils.isClique(_cluster, g);
        } else if (dataModel instanceof ICovarianceMatrix) {
            Fas fas = new Fas(new IndTestFisherZ((ICovarianceMatrix) dataModel, alpha));
            Graph g = fas.search(_cluster);
            return GraphUtils.isClique(_cluster, g);
        } else {
            throw new IllegalArgumentException("Expecting a data set or a covariance matrix.");
        }
    }

    private double getClusterChiSquare(List<Integer> cluster) {
        SemIm im = estimateClusterModel(cluster);
        return im.getChiSquare();
    }

    private SemIm estimateClusterModel(List<Integer> quartet) {
        Graph g = new EdgeListGraph();
        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);
        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);

        for (Integer integer : quartet) {
            Node n = variables.get(integer);
            g.addNode(n);
            g.addDirectedEdge(l1, n);
            g.addDirectedEdge(l2, n);
        }

        SemPm pm = new SemPm(g);

        SemEstimator est;

        if (dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    private SemIm estimateModel(List<List<Integer>> clusters) {
        Graph g = new EdgeListGraph();

        List<Node> upperLatents = new ArrayList<>();
        List<Node> lowerLatents = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Integer> cluster = clusters.get(i);
            Node l1 = new GraphNode("L1." + (i + 1));
            l1.setNodeType(NodeType.LATENT);

            Node l2 = new GraphNode("L2." + (i + 1));
            l2.setNodeType(NodeType.LATENT);

            upperLatents.add(l1);
            lowerLatents.add(l2);

            g.addNode(l1);
            g.addNode(l2);

            for (Integer integer : cluster) {
                Node n = variables.get(integer);
                g.addNode(n);
                g.addDirectedEdge(l1, n);
                g.addDirectedEdge(l2, n);
            }
        }

        for (int i = 0; i < upperLatents.size(); i++) {
            for (int j = i + 1; j < upperLatents.size(); j++) {
                g.addDirectedEdge(upperLatents.get(i), upperLatents.get(j));
                g.addDirectedEdge(lowerLatents.get(i), lowerLatents.get(j));
            }
        }

        for (int i = 0; i < upperLatents.size(); i++) {
            for (int j = 0; j < lowerLatents.size(); j++) {
                if (i == j) continue;
                g.addDirectedEdge(upperLatents.get(i), lowerLatents.get(j));
            }
        }

        SemPm pm = new SemPm(g);

        for (Node node : upperLatents) {
            Parameter p = pm.getParameter(node, node);
            p.setFixed(true);
            p.setStartingValue(1.0);
        }

        for (Node node : lowerLatents) {
            Parameter p = pm.getParameter(node, node);
            p.setFixed(true);
            p.setStartingValue(1.0);
        }

        SemEstimator est;

        if (dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    /**
     * Gives the options for checking significance of clusters--could check the significance using a regression model,
     * or could check to see if the cluster is a clique, or could not do the check.
     */
    public enum CheckType {

        /**
         * Check the significance using a regression model.
         */
        Significance,

        /**
         * Check to see if the cluster is a clique.
         */
        Clique,

        /**
         * Do not do the check.
         */
        None
    }
}
