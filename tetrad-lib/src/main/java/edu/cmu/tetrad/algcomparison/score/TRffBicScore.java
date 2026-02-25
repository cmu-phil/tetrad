package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code TRffBicScore} class is a scoring mechanism designed to evaluate the fit and complexity
 * of a data model. This score uses the Student-T Fourier Feature Bayes Information Criterion (BIC),
 * which is particularly suitable for mixed-data settings. The class provides parameterized control over
 * regularization, feature tuning, and penalty adjustments, allowing flexibility during model evaluation.
 * <p>
 * The scoring mechanism is implemented within the Tetrad framework and adheres to the {@code ScoreWrapper} interface.
 * It is annotated with {@code Score}, specifying the name, command, and applicable data type.
 */
@edu.cmu.tetrad.annotation.Score(name = "TRFF BIC Score", command = "trff-bic-score", dataType = {DataType.Mixed})
@General
@Mixed
//@Experimental
public class TRffBicScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents the data model associated with the current instance of the scoring mechanism.
     * This variable holds the dataset used for calculations and operations performed within the
     * {@code TRffBicScore} class. It is assigned when the scoring method is invoked and
     * contains information about the structure and content of the dataset to be evaluated.
     * <p>
     * The {@code DataModel} interface, implemented by the assigned instance, provides methods
     * for operations such as determining if the dataset is continuous, discrete, or mixed,
     * accessing variables by name, and creating copies for further processing.
     */
    private transient DataModel dataSet;

    /**
     * Constructor for the TRffBicScore class. This class serves as a
     * Student-T Fourier Feature Bayes Information Criterion (BIC)
     * scoring mechanism typically applied in mixed-data settings.
     * <p>
     * The score is used in conjunction with algorithms that evaluate the fit
     * and complexity of a model based on penalized likelihood principles.
     * <p>
     * This constructor initializes a new instance of the TRffBicScore
     * without requiring parameters.
     */
    public TRffBicScore() {
    }

    /**
     * Calculates and returns a score for the given dataset based on the specified parameters.
     *
     * @param dataSet    The dataset for which the score is to be calculated. It must be an instance of DataSet.
     * @param parameters The parameters used to configure the scoring process, including tuning knobs
     *                   such as ridge regularization, number of random Fourier features, and penalty discount.
     * @return An instance of {@link edu.cmu.tetrad.search.score.TRffBicScore} configured with the provided dataset and parameters.
     * @throws IllegalArgumentException If the provided dataset is not an instance of DataSet.
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        final edu.cmu.tetrad.search.score.TRffBicScore score;
        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.TRffBicScore((DataSet) dataSet);
        } else {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        // Exposed knobs (stable + meaningful):
        score.setRidge(parameters.getDouble(Params.TRFF_RIDGE));
        score.setRffFeatures(parameters.getInt(Params.NUM_FF_FEATURES));

        // Optional: expose nu if you truly want users to tune heavy-tail robustness.
        // If not, delete this line and just leave nu at the score's internal default.
        score.setNu(parameters.getDouble(Params.TRFF_NU));

        // Hidden knobs: set to sane internal defaults (do NOT expose in UI).
        // Keep these deterministic so runs are reproducible.
        score.setIrlsIters(8);

        // Prefer internal sigma selection (median heuristic etc.) rather than a UI parameter.
        // If the score currently REQUIRES sigma to be set, pick a safe sentinel and have the score auto-compute.
        // score.setRffSigma(Double.NaN); // recommended pattern if your score treats NaN as "auto"

        // Likewise: scale should be initialization / fallback only.
        // score.setScale(1.0);

        // If you want a penalty discount at all, make it a fixed policy, not a tuning knob.
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT_DEFAULT_1));

        return score;
    }

    /**
     * Provides a textual description of the current scoring mechanism.
     *
     * @return a string representing the description of the T-RFF BIC Score
     */
    @Override
    public String getDescription() {
        return "TRFF BIC Score";
    }

    /**
     * Retrieves the data type associated with this scoring mechanism.
     *
     * @return the data type of the score, which corresponds to {@code DataType.Mixed}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves a list of parameter names associated with the
     * Student-T Random Fourier Feature Bayes Information Criterion (BIC)
     * scoring mechanism. These parameters can be used to configure
     * the behavior of the scoring process.
     *
     * @return a list of parameter names as strings. Each parameter represents a configurable aspect
     * of the scoring mechanism, such as ridge regularization, number of Fourier features, penalty
     * discount, and other optional settings.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.TRFF_RIDGE);
        parameters.add(Params.NUM_FF_FEATURES);
        parameters.add(Params.PENALTY_DISCOUNT_DEFAULT_1);
        parameters.add(Params.TRFF_NU);

        return parameters;
    }

    /**
     * Retrieves a variable from the dataset based on the given name.
     *
     * @param name the name of the variable to retrieve. It must correspond to a variable
     *             contained in the dataset.
     * @return the {@code Node} representing the variable associated with the specified name.
     * Returns null if the dataset does not contain a variable with the given name.
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.dataSet = null;             // <-- force rebind after load
    }
}