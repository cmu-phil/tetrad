package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * The MissingnessIndicatorAdder class provides functionality to add missingness indicators to a dataset. Missingness
 * indicators are binary variables indicating the presence (1.0) or absence (0.0) of observed values for each variable
 * in the original dataset. These indicators are added as additional columns in the resulting dataset when a variable
 * contains missing values.
 *
 * @author josephramsey
 */
public class MissingnessIndicatorAdder {

    /**
     * Utility class for handling operations related to adding missingness indicators to datasets.
     * <p>
     * This class cannot be instantiated and is designed to group all related static methods for handling missingness
     * indicator functionality. The constructor throws an {@link IllegalStateException} to enforce the utility class
     * pattern.
     */
    private MissingnessIndicatorAdder() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Adds missingness indicators to a dataset.
     *
     * @param dataSet The original dataset.
     * @return A new dataset with missingness indicators added as additional columns.
     */
    public static DataSet addMissingnessIndicators(DataSet dataSet) {
        if (dataSet == null) {
            throw new IllegalArgumentException("Dataset must not be null.");
        }

        List<Node> originalVariables = dataSet.getVariables();
        List<Node> newVariables = new ArrayList<>(originalVariables);
        List<double[]> newDataColumns = new ArrayList<>();

        // Iterate through each variable in the dataset
        for (int i = 0; i < originalVariables.size(); i++) {
            Node variable = originalVariables.get(i);

            // Check for missing values in the column
            boolean hasMissing = false;
            double[] columnData = new double[dataSet.getNumRows()];
            for (int j = 0; j < dataSet.getNumRows(); j++) {
                if (Double.isNaN(dataSet.getDouble(j, i))) {
                    hasMissing = true;
                    columnData[j] = 0.0; // Missing
                } else {
                    columnData[j] = 1.0; // Observed
                }
            }

            // If missing values are found, add a missingness indicator
            if (hasMissing) {
                Node indicatorVariable = new ContinuousVariable(variable.getName() + "_missing");
                newVariables.add(indicatorVariable);
                newDataColumns.add(columnData);
            }
        }

        // Combine original and new data into a new dataset
        int newColumnCount = originalVariables.size() + newDataColumns.size();
        double[][] expandedData = new double[dataSet.getNumRows()][newColumnCount];

        // Copy original data
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < originalVariables.size(); j++) {
                expandedData[i][j] = dataSet.getDouble(i, j);
            }
        }

        // Add new columns
        for (int col = 0; col < newDataColumns.size(); col++) {
            double[] newColumn = newDataColumns.get(col);
            for (int row = 0; row < newColumn.length; row++) {
                expandedData[row][originalVariables.size() + col] = newColumn[row];
            }
        }

        // Create the new dataset
        return new BoxDataSet(new DoubleDataBox(expandedData), newVariables);
    }

    /**
     * The main method demonstrating the process of adding missingness indicators to a dataset. This example uses a mock
     * dataset, defines variables, and invokes the method to expand the dataset with missingness indicators.
     *
     * @param args Command-line arguments provided during the execution of the program.
     */
    public static void main(String[] args) {
        // Mock dataset
        double[][] mockData = {
                {1.0, Double.NaN, 3.0},
                {2.0, 4.0, Double.NaN},
                {Double.NaN, 5.0, 6.0}
        };
        List<Node> variables = List.of(
                new ContinuousVariable("Var1"),
                new ContinuousVariable("Var2"),
                new ContinuousVariable("Var3")
        );

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(mockData), variables);

        // Add missingness indicators
        DataSet expandedDataSet = addMissingnessIndicators(dataSet);

        // Print the result
        System.out.println("Expanded Dataset:");
        System.out.println(expandedDataSet);
    }
}

