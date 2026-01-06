package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.is.IsBDeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for ISBDeuScore (discrete). Pulls testing data from Params.TESTING_DATA and row from Params.INSTANCE_ROW.
 * Aligns by name and remaps category indices if the label orders differ.
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Instance-specific BDeu Score",
        command = "is-bdeu-score",
        dataType = {DataType.Discrete}
)
public final class IsBDeuScoreWrapper implements ScoreWrapper, HasKnowledge {

    /**
     * Represents a knowledge structure used within the IsBDeuScoreWrapper class. This variable stores an instance of
     * the Knowledge class and is utilized to manage and represent domain-specific constraints or prior information in
     * the context of scoring operations.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructor for the `IsBDeuScoreWrapper` class. This class serves as a wrapper for the Instance-Specific BDeu
     * (ISBDeu) scoring algorithm, which is designed for use with discrete data sets. The wrapper facilitates score
     * calculations, description retrieval, and parameter management specific to ISBDeu scoring.
     */
    public IsBDeuScoreWrapper() {

    }

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
                List<String> testLabs = iv.getCategories();

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

    /**
     * Calculates and returns a score for the given data model and associated parameters.
     *
     * @param dataModel  The data model to be scored. Must be an instance of a discrete {@code DataSet}.
     * @param parameters The parameters used for the score computation. Includes row index and optional
     *                   hyperparameters.
     * @return The calculated score instance as an {@code IsBDeuScore}.
     * @throws IllegalArgumentException if the data model is not a discrete {@code DataSet}, if the testing data is not
     *                                  discrete, or if the row index is out of range.
     */
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

        IsBDeuScore score = new IsBDeuScore(train, instanceOneRow);

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

    /**
     * Provides a description of the Instance-Specific BDeu score, a discrete Dirichlet–multinomial posterior predictive
     * score for the specific instance.
     *
     * @return A string describing the Instance-Specific BDeu score.
     */
    @Override
    public String getDescription() {
        return "Instance-Specific BDeu (discrete, Dirichlet–multinomial posterior predictive for the instance)";
    }

    /**
     * Returns the data type associated with this score wrapper. The data type indicates that the score is intended for
     * use with discrete data sets.
     *
     * @return The data type {@link DataType#Discrete}, representing compatibility with discrete data.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * Retrieves a list of parameter names required by the Instance-Specific BDeu (ISBDeu) scoring algorithm. These
     * parameters are used in the computation of the ISBDeu score for a given data model.
     *
     * @return A list of parameter names as strings, including alpha parameter, row index, and prior equivalent sample
     * size. Additional parameters may be provided by algorithm wrappers.
     */
    @Override
    public List<String> getParameters() {
        return List.of(
                Params.INSTANCE_SPECIFIC_ALPHA,
                Params.INSTANCE_ROW,
                Params.PRIOR_EQUIVALENT_SAMPLE_SIZE
                // Params.TESTING_DATA is injected by the algorithm wrapper
        );
    }

    /* ---------- Helpers ---------- */

    /**
     * Retrieves a variable from the model by its name.
     *
     * @param name The name of the variable to retrieve. This is a case-sensitive string representing the unique
     *             identifier of the variable within the model.
     * @return The {@code Node} object that represents the specified variable in the model. If no variable is found with
     * the given name, {@code null} is returned.
     */
    @Override
    public Node getVariable(String name) {
        return null;
    }

    /**
     * Retrieves a copy of the current knowledge associated with this score wrapper. The copy ensures that the original
     * knowledge remains unaltered by any external modifications.
     *
     * @return A copy of the current {@code Knowledge} object.
     */
    @Override
    public Knowledge getKnowledge() {
        return knowledge.copy();
    }

    /**
     * Sets the knowledge used by this score wrapper. The provided knowledge is copied to ensure that the original
     * object remains unaltered.
     *
     * @param knowledge The {@code Knowledge} object to be set. Represents constraints, rules, or prior knowledge used
     *                  in the scoring process.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge.copy();
    }
}