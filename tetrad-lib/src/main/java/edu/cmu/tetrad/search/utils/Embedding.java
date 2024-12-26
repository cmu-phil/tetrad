package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code Embedding} class provides utilities for transforming datasets into embedded representations through basis
 * expansions and one-hot encoding. This process is commonly used in preprocessing steps for machine learning or
 * statistical analysis, enabling enhanced variable representations.
 *
 * @author josephramsey
 * @author bandrews
 */
public class Embedding {

    /**
     * Computes the embedded data representation based on the provided dataset and parameters.
     *
     * @param dataSet          The original dataset to be embedded; must not be null.
     * @param truncationLimit  The maximum number of basis expansions for continuous variables; must be a positive
     *                         integer.
     * @param basisType        The type of basis function to use for continuous variable expansions.
     * @param basisScale       The scaling factor for data transformation. Set to 0 for standardization, positive for
     *                         scaling, and -1 to skip scaling.
     * @param usePseudoInverse A flag indicating whether pseudoinverse will be used. If not, one category is left out
     *                         per variable when encoding discrete variables as one-hot vectors.
     * @return An instance of {@code EmbeddedData}, containing the original dataset, the embedded dataset, and a mapping
     * from original variable indices to their respective transformed indices in the embedded dataset.
     * @throws IllegalArgumentException If the dataset is null, the truncation limit is less than 1, or the basis scale
     *                                  parameter is invalid.
     */
    public static @NotNull Embedding.EmbeddedData getEmbeddedData(DataSet dataSet, int truncationLimit,
                                                                  int basisType, double basisScale,
                                                                  boolean usePseudoInverse) {
        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must not be null.");
        }

        if (truncationLimit < 1) {
            throw new IllegalArgumentException("Truncation limit must be a positive integer.");
        }

        int n = dataSet.getNumRows();
        List<Node> variables = dataSet.getVariables();

        if (basisScale == 0.0) {
            dataSet = DataTransforms.standardizeData(dataSet);
        } else if (basisScale > 0.0) {
            dataSet = DataTransforms.scale(dataSet, basisScale);
        } else if (basisScale != -1) {
            throw new IllegalArgumentException("Basis scale must be a positive number, or 0 if the data should be " +
                                               "standardized, or -1 if the data should not be scaled.");
        }

        Map<Integer, List<Integer>> embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        // Index of embedded variables in new data set...
        int i = -1;

        for (int i_ = 0; i_ < variables.size(); i_++) {
            Node v = variables.get(i_);

            if (v instanceof DiscreteVariable) {
                Map<List<Integer>, Integer> keys = new HashMap<>();

                int numCategories = ((DiscreteVariable) v).getNumCategories();

                for (int c = 0; c < (usePseudoInverse ? numCategories : numCategories - 1); c++) {
                    List<Integer> key = new ArrayList<>();
                    i++;
                    key.add(c);
                    keys.put(key, i);

                    Node v_ = new ContinuousVariable(v.getName() + "." + ((DiscreteVariable) v).getCategory(c));
                    A.add(v_);
                    B.add(new double[n]);

                    for (int j = 0; j < n; j++) {
                        B.get(i)[j] = dataSet.getInt(j, i_) == c ? 1 : 0;
                    }
                }

                embedding.put(i_, new ArrayList<>(keys.values()));
            } else {
                List<Integer> indexList = new ArrayList<>();
                for (int p = 1; p <= truncationLimit; p++) {
                    i++;
                    Node vPower = basisScale == -1 ? new ContinuousVariable(v.getName()) :
                            new ContinuousVariable(v.getName() + ".P(" + p + ")");
                    A.add(vPower);
                    double[] functional = new double[n];
                    for (int j = 0; j < n; j++) {
                        functional[j] = StatUtils.basisFunctionValue(basisType, p, dataSet.getDouble(j, i_));
                    }
                    B.add(functional);
                    indexList.add(i);
                }
                embedding.put(i_, indexList);
            }
        }

        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        SimpleMatrix D = new SimpleMatrix(B_);
        BoxDataSet embeddedData = new BoxDataSet(new DoubleDataBox(D.toArray2()), A);
        return new EmbeddedData(dataSet.copy(), embeddedData, embedding);
    }

    /**
     * Represents the embedded data result, holding the original dataset, the transformed embedded dataset, and a
     * mapping between the indices of original variables and their corresponding transformed variables.
     * <p>
     * This record is a lightweight container for storing: - The original dataset (`originalData`) before
     * transformation. - The embedded dataset (`embeddedData`) after applying transformations such as basis expansions
     * and scaling. - A mapping (`embedding`) that associates each original variable with its expanded or encoded
     * indices in the embedded dataset.
     * <p>
     * This class is primarily used to encapsulate the result of a dataset transformation and provide easy access to
     * both the raw and embedded data representations, as well as the transformation metadata.
     */
    public record EmbeddedData(DataSet originalData, DataSet embeddedData, Map<Integer, List<Integer>> embedding) {
    }
}
