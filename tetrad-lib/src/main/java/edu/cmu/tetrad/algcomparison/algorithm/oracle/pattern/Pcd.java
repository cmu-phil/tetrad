package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class Pcd implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
//    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

//    public Pcd(IndependenceWrapper test) {
//        this.test = test;
//    }

//    public Pcd(IndependenceWrapper test, Algorithm initialGraph) {
////        this.test = test;
//        this.initialGraph = initialGraph;
//    }

    public Pcd() {
//        this.test = test;
//        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        IndTestScore test;

        if (dataSet instanceof ICovarianceMatrix) {
            SemBicScoreDeterministic score = new SemBicScoreDeterministic((ICovarianceMatrix) dataSet);
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            score.setDeterminismThreshold(parameters.getDouble("determinismThreshold"));
            test = new IndTestScore(score);
        } else if (dataSet instanceof DataSet) {
            SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataSet));
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            score.setDeterminismThreshold(parameters.getDouble("determinismThreshold"));
            test = new IndTestScore(score);
        } else {
            throw new IllegalArgumentException("Expecting a dataset or a covariance matrix.");
        }

        edu.cmu.tetrad.search.Pcd search = new edu.cmu.tetrad.search.Pcd(test);
        search.setDepth(parameters.getInt("depth"));
        search.setKnowledge(knowledge);
        search.setVerbose(parameters.getBoolean("verbose"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PC (\"Peter and Clark\") Deternimistic";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("depth");
        parameters.add("determinismThreshold");
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
}
