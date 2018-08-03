package edu.cmu.tetrad.algcomparison.algorithm.multi;

import com.mathworks.toolbox.javabuilder.MWClassID;
import com.mathworks.toolbox.javabuilder.MWException;
import com.mathworks.toolbox.javabuilder.MWNumericArray;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

/**
 * Wraps the Two Step algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "TwoStep",
        command = "twostep",
        algoType = AlgType.forbid_latent_common_causes
)
public class TwoStep implements Algorithm, HasKnowledge, UsesScoreWrapper {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public TwoStep() {

    }

    public TwoStep(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        final DataSet dataSet1 = (DataSet) dataSet;
        if (parameters.getInt("bootstrapSampleSize") < 1) {

            two_step_CD_regu.Class1 twostep = null;

            try {

                new File("~/Matlab Programs/tracemethod/").mkdirs();
                new File("~/Matlab Programs/UAI/").mkdirs();
                new File("~/Matlab Programs/UAI/sparse_Kun/lars/").mkdirs();

                twostep = new two_step_CD_regu.Class1();

                final TetradMatrix doubleData = dataSet1.getDoubleData();
                final TetradMatrix transpose = doubleData.transpose();
                final double[][] d = transpose.toArray();

                final Number[][] d2 = new Number[d.length][d[0].length];

                for (int i = 0; i < d.length; i++) {
                    for (int j = 0; j < d[0].length; j++) {
                        d2[i][j] = d[i][j];
                    }
                }

                MWNumericArray _z = new MWNumericArray(d2);
                MWNumericArray betathr = new MWNumericArray(parameters.getDouble("tsbetathr"));
                MWNumericArray tstheta = new MWNumericArray(parameters.getDouble("tstheta"));
                MWNumericArray tssigma = new MWNumericArray(parameters.getDouble("tssigma"));

                System.out.println("Converted the data");

                Object[] out = twostep.two_step_CD_regu(3, _z, betathr, tstheta, tssigma);

                double[][] _b = (double[][]) ((MWNumericArray) out[0]).toDoubleArray();
                double[][] _w = (double[][]) ((MWNumericArray) out[1]).toDoubleArray();
                double[][] _y = (double[][]) ((MWNumericArray) out[2]).toDoubleArray();

                System.out.println("B = " + new TetradMatrix(_b));
                System.out.println("W = " + new TetradMatrix(_w));
                System.out.println("Y = " + new TetradMatrix(_y));

                final List<Node> variables = dataSet.getVariables();
                Graph graph = new EdgeListGraph(variables);

                for (int i = 0; i < variables.size(); i++) {
                    for (int j = 0; j < variables.size(); j++) {
                        if (_b[i][j] != 0) {
                            graph.addDirectedEdge(variables.get(j), variables.get(i));
                        }
                    }
                }

                return graph;
            } catch (MWException e) {
                throw new RuntimeException(e);
            } finally {
                if (twostep != null) {
                    twostep.dispose();
                }
            }
        } else {
            TwoStep twostep = new TwoStep(score);
            twostep.setKnowledge(knowledge);

            DataSet data = dataSet1;
            GeneralBootstrapTest search = new GeneralBootstrapTest(data, twostep, parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "Two Step using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add("tsbetathr");
        parameters.add("tstheta");
        parameters.add("tssigma");

        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");

        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}