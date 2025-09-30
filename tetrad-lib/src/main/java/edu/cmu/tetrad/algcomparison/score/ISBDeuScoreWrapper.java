package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ISBDeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.*;

/**
 * Wrapper for ISBDeuScore (discrete). Pulls testing data from Params.TESTING_DATA and row
 * from Params.INSTANCE_ROW. Aligns by name and remaps category indices if the label orders differ.
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Instance-specific BDeu Score",
        command = "is-bdeu-score",
        dataType = {DataType.Discrete}
)
public final class ISBDeuScoreWrapper implements ScoreWrapper, HasKnowledge {

    private Knowledge knowledge = new Knowledge();

    @Override
    public Score getScore(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet train) || !train.isDiscrete()) {
            throw new IllegalArgumentException("Requires a discrete DataSet.");
        }

        DataSet testing = knowledge.getTestingData();
        if (testing == null) testing = train;
        if (!testing.isDiscrete()) {
            throw new IllegalArgumentException("Testing data must be discrete.");
        }

        int row = parameters.getInt(Params.INSTANCE_ROW, 0);
        if (row < 0 || row >= testing.getNumRows()) {
            throw new IllegalArgumentException("Instance row out of range: " + row);
        }

        // Align columns by name and remap discrete categories if label order differs
        DataSet alignedTesting = alignByNameAndCategories(train, testing);

        // One-row instance dataset
        DataSet instanceOneRow = alignedTesting.subsetRows(new int[]{row});

        ISBDeuScore score = new ISBDeuScore(train, instanceOneRow);

        // isAlpha
        double isAlpha = parameters.getDouble(Params.INSTANCE_SPECIFIC_ALPHA, 1.0);
        score.setIsAlpha(isAlpha);

        // optional: pass ESS if present (matches your BDeu knobs)
        if (parameters.getParametersNames().contains(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE)) {
            score.setPriorEquivalentSampleSize(
                    parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));
        }

        return score;
    }

    @Override
    public String getDescription() {
        return "Instance-Specific BDeu (discrete, Dirichlet–multinomial posterior predictive for the instance)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        return List.of(
                Params.INSTANCE_SPECIFIC_ALPHA,
                Params.INSTANCE_ROW,
                Params.PRIOR_EQUIVALENT_SAMPLE_SIZE
                // Params.TESTING_DATA is injected by the algorithm wrapper
        );
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }

    /* ---------- Helpers ---------- */

    private static DataSet alignByNameAndCategories(DataSet ref, DataSet other) {
        List<Node> refVars = ref.getVariables();
        int p = refVars.size();
        int[] cols = new int[p];

        for (int j = 0; j < p; j++) {
            String name = refVars.get(j).getName();
            Node ov = other.getVariable(name);
            if (ov == null) {
                throw new IllegalArgumentException("Testing dataset missing variable: " + name);
            }
            cols[j] = other.getColumn(ov);
        }

        DataSet projected = other.subsetColumns(cols);

        // unify Node identity and remap discrete category indices if label order differs
        for (int j = 0; j < p; j++) {
            Node refVar = refVars.get(j);
            projected.setVariable(j, refVar);

            if (refVar instanceof DiscreteVariable tv) {
                Node ov = projected.getVariable(j);
                if (!(ov instanceof DiscreteVariable iv)) {
                    throw new IllegalArgumentException("Variable " + tv.getName() + " must be discrete in testing data.");
                }

                List<String> trainLabs = tv.getCategories();
                List<String> testLabs  = iv.getCategories();

                if (!trainLabs.equals(testLabs)) {
                    // Must be the same set; if so, build index remap (testing->train)
                    if (!new HashSet<>(trainLabs).equals(new HashSet<>(testLabs))) {
                        throw new IllegalArgumentException("Category labels differ for "
                                                           + tv.getName() + " (train=" + trainLabs + ", test=" + testLabs + ").");
                    }
                    int K = testLabs.size();
                    Map<String, Integer> trainIndex = new HashMap<>();
                    for (int k = 0; k < K; k++) trainIndex.put(trainLabs.get(k), k);
                    int[] remap = new int[K];
                    for (int k = 0; k < K; k++) remap[k] = trainIndex.get(testLabs.get(k));

                    // apply remap to the integer table (respect missing sentinel -99)
                    final int R = projected.getNumRows();
                    for (int r = 0; r < R; r++) {
                        int v = projected.getInt(r, j);
                        if (v == -99) continue;
                        if (v < 0 || v >= K) {
                            throw new IllegalArgumentException("Out-of-range category at row " + r
                                                               + ", var " + tv.getName());
                        }
                        projected.setInt(r, j, remap[v]);
                    }
                }
            }
        }

        return projected;
    }

    @Override
    public Knowledge getKnowledge() {
        return knowledge.copy();
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge.copy();
    }
}