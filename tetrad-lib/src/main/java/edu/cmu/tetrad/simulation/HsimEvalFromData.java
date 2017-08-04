package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.PatternToDag;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by ekummerfeld on 1/26/2017.
 */
public class HsimEvalFromData {

    public static void main(String[] args) {
        long timestart = System.nanoTime();
        System.out.println("Beginning Evaluation");
        String nl = System.lineSeparator();
        String output = "Simulation edu.cmu.tetrad.study output comparing Fsim and Hsim on predicting graph discovery accuracy" + nl;
        int iterations = 100;

        int vars = 20;
        int cases = 500;
        int edgeratio = 3;

        List<Integer> hsimRepeat = Arrays.asList(40);
        List<Integer> fsimRepeat = Arrays.asList(40);

        List<PRAOerrors>[] fsimErrsByPars = new ArrayList[fsimRepeat.size()];
        int whichFrepeat = 0;
        for (int frepeat : fsimRepeat) {
            fsimErrsByPars[whichFrepeat] = new ArrayList<PRAOerrors>();
            whichFrepeat++;
        }
        List<PRAOerrors>[][] hsimErrsByPars = new ArrayList[1][hsimRepeat.size()];
        //System.out.println(resimSize.size()+" "+hsimRepeat.size());
        int whichHrepeat;
        whichHrepeat = 0;
        for (int hrepeat : hsimRepeat) {
            //System.out.println(whichrsize+" "+whichHrepeat);
            hsimErrsByPars[0][whichHrepeat] = new ArrayList<PRAOerrors>();
            whichHrepeat++;
        }

        //!(*%(@!*^!($%!^ START ITERATING HERE !#$%(*$#@!^(*!$*%(!$#
        try {
            for (int iterate = 0; iterate < iterations; iterate++) {
                System.out.println("iteration " + iterate);
                //@#$%@$%^@$^@$^@%$%@$#^ LOADING THE DATA AND GRAPH @$#%%*#^##*^$#@%$
                DataSet data1;
                Graph graph1 = GraphUtils.loadGraphTxt(new File("graph/graph.1.txt"));
                Dag odag = new Dag(graph1);

                Set<String> eVars = new HashSet<String>();
                eVars.add("MULT");
                Path dataFile = Paths.get("data/data.1.txt");

                TabularDataReader dataReader = new ContinuousTabularDataFileReader(dataFile.toFile(), Delimiter.TAB);

                data1 = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData(eVars));
                vars = data1.getNumColumns();
                cases = data1.getNumRows();
                edgeratio = 3;

                //!#@^$@&%^!#$!&@^ CALCULATING TARGET ERRORS $%$#@^@!%!#^$!%$#%
                ICovarianceMatrix newcov = new CovarianceMatrixOnTheFly(data1);
                SemBicScore oscore = new SemBicScore(newcov);
                Fges ofgs = new Fges(oscore);
                ofgs.setVerbose(false);
                ofgs.setNumPatternsToStore(0);
                Graph oFGSGraph = ofgs.search();//***********This is the original FGS output on the data
                PRAOerrors oErrors = new PRAOerrors(HsimUtils.errorEval(oFGSGraph, odag), "target errors");

                //**then step 1: full resim. iterate through the combinations of estimator parameters (just repeat num)
                for (whichFrepeat = 0; whichFrepeat < fsimRepeat.size(); whichFrepeat++) {
                    ArrayList<PRAOerrors> errorsList = new ArrayList<PRAOerrors>();
                    for (int r = 0; r < fsimRepeat.get(whichFrepeat); r++) {
                        PatternToDag pickdag = new PatternToDag(oFGSGraph);
                        Graph fgsDag = pickdag.patternToDagMeek();
                        Dag fgsdag2 = new Dag(fgsDag);
                        //then fit an IM to this dag and the data. GeneralizedSemEstimator seems to bug out
                        //GeneralizedSemPm simSemPm = new GeneralizedSemPm(fgsdag2);
                        //GeneralizedSemEstimator gsemEstimator = new GeneralizedSemEstimator();
                        //GeneralizedSemIm fittedIM = gsemEstimator.estimate(simSemPm, oData);

                        SemPm simSemPm = new SemPm(fgsdag2);
                        //BayesPm simBayesPm = new BayesPm(fgsdag2, bayesPm);
                        SemEstimator simSemEstimator = new SemEstimator(data1, simSemPm);
                        SemIm fittedIM = simSemEstimator.estimate();

                        DataSet simData = fittedIM.simulateData(data1.getNumRows(), false);
                        //after making the full resim data (simData), run FGS on that
                        ICovarianceMatrix simcov = new CovarianceMatrixOnTheFly(simData);
                        SemBicScore simscore = new SemBicScore(simcov);
                        Fges simfgs = new Fges(simscore);
                        simfgs.setVerbose(false);
                        simfgs.setNumPatternsToStore(0);
                        Graph simGraphOut = simfgs.search();
                        PRAOerrors simErrors = new PRAOerrors(HsimUtils.errorEval(simGraphOut, fgsdag2), "Fsim errors " + r);
                        errorsList.add(simErrors);
                    }
                    PRAOerrors avErrors = new PRAOerrors(errorsList, "Average errors for Fsim at repeat=" + fsimRepeat.get(whichFrepeat));
                    //if (verbosity>3) System.out.println(avErrors.allToString());
                    //****calculate the squared errors of prediction, store all these errors in a list
                    double FsimAR2 = (avErrors.getAdjRecall() - oErrors.getAdjRecall())
                            * (avErrors.getAdjRecall() - oErrors.getAdjRecall());
                    double FsimAP2 = (avErrors.getAdjPrecision() - oErrors.getAdjPrecision())
                            * (avErrors.getAdjPrecision() - oErrors.getAdjPrecision());
                    double FsimOR2 = (avErrors.getOrientRecall() - oErrors.getOrientRecall())
                            * (avErrors.getOrientRecall() - oErrors.getOrientRecall());
                    double FsimOP2 = (avErrors.getOrientPrecision() - oErrors.getOrientPrecision())
                            * (avErrors.getOrientPrecision() - oErrors.getOrientPrecision());
                    PRAOerrors Fsim2 = new PRAOerrors(new double[]{FsimAR2, FsimAP2, FsimOR2, FsimOP2},
                            "squared errors for Fsim at repeat=" + fsimRepeat.get(whichFrepeat));
                    //add the fsim squared errors to the appropriate list
                    fsimErrsByPars[whichFrepeat].add(Fsim2);
                }
                //**then step 2: hybrid sim. iterate through combos of params (repeat num, resimsize)
                for (whichHrepeat = 0; whichHrepeat < hsimRepeat.size(); whichHrepeat++) {
                    HsimRepeatAC study = new HsimRepeatAC(data1);
                    PRAOerrors HsimErrors = new PRAOerrors(study.run(1, hsimRepeat.get(whichHrepeat)), "Hsim errors"
                            + "at rsize=" + 1 + " repeat=" + hsimRepeat.get(whichHrepeat));
                    //****calculate the squared errors of prediction
                    double HsimAR2 = (HsimErrors.getAdjRecall() - oErrors.getAdjRecall())
                            * (HsimErrors.getAdjRecall() - oErrors.getAdjRecall());
                    double HsimAP2 = (HsimErrors.getAdjPrecision() - oErrors.getAdjPrecision())
                            * (HsimErrors.getAdjPrecision() - oErrors.getAdjPrecision());
                    double HsimOR2 = (HsimErrors.getOrientRecall() - oErrors.getOrientRecall())
                            * (HsimErrors.getOrientRecall() - oErrors.getOrientRecall());
                    double HsimOP2 = (HsimErrors.getOrientPrecision() - oErrors.getOrientPrecision())
                            * (HsimErrors.getOrientPrecision() - oErrors.getOrientPrecision());
                    PRAOerrors Hsim2 = new PRAOerrors(new double[]{HsimAR2, HsimAP2, HsimOR2, HsimOP2},
                            "squared errors for Hsim, rsize=" + 1 + " repeat=" + hsimRepeat.get(whichHrepeat));
                    hsimErrsByPars[0][whichHrepeat].add(Hsim2);
                }
            }

            //Average the squared errors for each set of fsim/hsim params across all iterations
            PRAOerrors[] fMSE = new PRAOerrors[fsimRepeat.size()];
            PRAOerrors[][] hMSE = new PRAOerrors[1][hsimRepeat.size()];
            String[][] latexTableArray = new String[1 * hsimRepeat.size() + fsimRepeat.size()][5];
            for (int j = 0; j < fMSE.length; j++) {
                fMSE[j] = new PRAOerrors(fsimErrsByPars[j], "MSE for Fsim at vars=" + vars + " edgeratio=" + edgeratio
                        + " cases=" + cases + " frepeat=" + fsimRepeat.get(j) + " iterations=" + iterations);
                //if(verbosity>0){System.out.println(fMSE[j].allToString());}
                output = output + fMSE[j].allToString() + nl;
                latexTableArray[j] = prelimToPRAOtable(fMSE[j]);
            }
            for (int j = 0; j < hMSE.length; j++) {
                for (int k = 0; k < hMSE[j].length; k++) {
                    hMSE[j][k] = new PRAOerrors(hsimErrsByPars[j][k], "MSE for Hsim at vars=" + vars + " edgeratio=" + edgeratio
                            + " cases=" + cases + " rsize=" + 1 + " repeat=" + hsimRepeat.get(k) + " iterations=" + iterations);
                    //if(verbosity>0){System.out.println(hMSE[j][k].allToString());}
                    output = output + hMSE[j][k].allToString() + nl;
                    latexTableArray[fsimRepeat.size() + j * hMSE[j].length + k] = prelimToPRAOtable(hMSE[j][k]);
                }
            }
            //record all the params, the base error values, and the fsim/hsim mean squared errors
            String latexTable = HsimUtils.makeLatexTable(latexTableArray);

            PrintWriter writer = new PrintWriter("latexTable.txt", "UTF-8");
            writer.println(latexTable);
            writer.close();

            PrintWriter writer2 = new PrintWriter("HvsF-SimulationEvaluation.txt", "UTF-8");
            writer2.println(output);
            writer2.close();

            long timestop = System.nanoTime();
            System.out.println("Evaluation Concluded. Duration: " + (timestop - timestart) / 1000000000 + "s");
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }

    //******************Private Methods************************//
    private static String[] prelimToPRAOtable(PRAOerrors input) {
        String[] output = new String[5];
        double[] values = input.toArray();
        String[] vStrings = HsimUtils.formatErrorsArray(values, "%7.4e");
        output[0] = input.getName();
        for (int i = 1; i < output.length; i++) {
            output[i] = vStrings[i - 1];
        }
        return output;
    }
}
