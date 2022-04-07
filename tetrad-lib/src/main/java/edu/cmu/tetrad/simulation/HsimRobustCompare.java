package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * generate data from random graph, generated from parameters.
 * calculate errors from FGES output for the data and graph
 * create resimulated data and hybrid resimulated data with various parameters
 * calculate errors of FGES on the resimulated and hsim data
 * compare errors across all data sets. which simulated data errors are closest to original?
 *
 * Created by Erich on 6/19/2016.
 */
public class HsimRobustCompare {

    //*************Public Methods*****************8//
    public static List<double[]> run(int numVars, double edgesPerNode, int numCases, double penaltyDiscount,
                                     int resimSize, int repeat, boolean verbose) {
        //public static void main(String[] args) {
        //first generate the data
        RandomUtil.getInstance().setSeed(1450184147770L);
        final char delimiter = ',';//'\t';
        int numEdges = (int) (numVars * edgesPerNode);

        List<Node> vars = new ArrayList<>();
        double[] oErrors = new double[5];
        double[] hsimErrors = new double[5];
        double[] simErrors = new double[5];
        List<double[]> output = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph odag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);

        BayesPm bayesPm = new BayesPm(odag, 2, 2);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        //oData is the original data set, and odag is the original dag.
        DataSet oData = bayesIm.simulateData(numCases, false);
        //System.out.println(oData);
        //System.out.println(odag);

        //then run FGES
        BDeuScore oscore = new BDeuScore(oData);
        Fges fges = new Fges(oscore);
        fges.setVerbose(false);
        Graph oGraphOut = fges.search();
        if (verbose) System.out.println(oGraphOut);

        //calculate FGES errors
        oErrors = new double[5];
        oErrors = HsimUtils.errorEval(oGraphOut, odag);
        if (verbose) System.out.println(oErrors[0] + " " + oErrors[1] + " " + oErrors[2] +
                " " + oErrors[3] + " " + oErrors[4]);

        //create various simulated data sets

        ////let's do the full simulated data set first: a dag in the FGES CPDAG fit to the data set.
        Graph fgesDag = SearchGraphUtils.dagFromCPDAG(oGraphOut);

        Dag fgesdag2 = new Dag(fgesDag);
        BayesPm simBayesPm = new BayesPm(fgesdag2, bayesPm);
        DirichletBayesIm simIM = DirichletBayesIm.symmetricDirichletIm(simBayesPm, 1.0);
        DirichletEstimator simEstimator = new DirichletEstimator();
        DirichletBayesIm fittedIM = DirichletEstimator.estimate(simIM, oData);
        DataSet simData = fittedIM.simulateData(numCases, false);

        ////next let's do a schedule of small hsims
        HsimRepeatAutoRun study = new HsimRepeatAutoRun(oData);
        hsimErrors = study.run(resimSize, repeat);

        //calculate errors for all simulated output graphs
        ////full simulation errors first
        BDeuScore simscore = new BDeuScore(simData);
        Fges simfges = new Fges(simscore);
        simfges.setVerbose(false);
        Graph simGraphOut = simfges.search();
        //simErrors = new double[5];
        simErrors = HsimUtils.errorEval(simGraphOut, fgesdag2);
        //System.out.println("Full resim errors are: " + simErrors[0] + " " + simErrors[1] + " " + simErrors[2] + " " + simErrors[3] + " " + simErrors[4]);

        //compare errors. perhaps report differences between original and simulated errors.
        //first, let's just see what the errors are.
        if (verbose) System.out.println("Original erors are: " + oErrors[0] + " " + oErrors[1] +
                " " + oErrors[2] + " " + oErrors[3] + " " + oErrors[4]);
        if (verbose) System.out.println("Full resim errors are: " + simErrors[0] + " " + simErrors[1] +
                " " + simErrors[2] + " " + simErrors[3] + " " + simErrors[4]);
        if (verbose) System.out.println("HSim errors are: " + hsimErrors[0] + " " + hsimErrors[1] +
                " " + hsimErrors[2] + " " + hsimErrors[3] + " " + hsimErrors[4]);

        //then, let's try to squeeze these numbers down into something more tractable.

        output.add(oErrors);
        output.add(simErrors);
        output.add(hsimErrors);

        return output;
    }
}