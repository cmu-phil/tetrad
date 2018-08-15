/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MultidataUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Aug 10, 2018 3:09:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class InterventionalDataFactory {

    private InterventionalDataFactory() {
    }

    public static DataModel createData(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions, boolean isDiscreteIntervVars) {
        if (dataModels == null || dataModels.isEmpty()) {
            return null;
        }

        DataModel dataModel = dataModels.get(0);
        DataBox dataBox = ((BoxDataSet) dataModel).getDataBox();

        if (dataBox instanceof DoubleDataBox) {
            return isDiscreteIntervVars
                    ? createContinuousDataWithDiscreteIntervention(dataModels, intervVars, interventions)
                    : createContinuousDataWithContinuousIntervention(dataModels, intervVars, interventions);
        } else if (dataBox instanceof VerticalIntDataBox) {
            return isDiscreteIntervVars
                    ? createDiscreteDataWithDiscreteIntervention(dataModels, intervVars, interventions)
                    : createDiscreteDataWithContinuousIntervention(dataModels, intervVars, interventions);
        } else if (dataBox instanceof MixedDataBox) {
            return isDiscreteIntervVars
                    ? createMixedDataWithDiscreteIntervention(dataModels, intervVars, interventions)
                    : createMixedDataWithContinuousIntervention(dataModels, intervVars, interventions);
        } else {
            throw new UnsupportedOperationException("This method only supports data with continuous, discrete, or mixed variables.");
        }
    }

    private static DataModel createMixedDataWithDiscreteIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);
        int numOfDataCols = MultidataUtils.getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<Node> variables = new ArrayList<>(numOfCols);
        MultidataUtils.combineVariables(dataModels, variables);
        addInterventionalVariables(intervVars, true, variables);

        double[][] continuousData = new double[numOfCols][];
        MultidataUtils.combineMixedContinuousData(dataModels, variables, continuousData, numOfRows, numOfDataCols);

        int[][] discreteData = new int[numOfCols][];
        MultidataUtils.combineMixedDiscreteData(dataModels, variables, discreteData, numOfRows, numOfDataCols);

        // add discrete intervention data
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = rowCounts[d];
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1);
                }
                row += r;
            }
            discreteData[col] = rowData;
        }

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createMixedDataWithContinuousIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);
        int numOfDataCols = MultidataUtils.getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<Node> variables = new ArrayList<>(numOfCols);
        MultidataUtils.combineVariables(dataModels, variables);
        addInterventionalVariables(intervVars, false, variables);

        double[][] continuousData = new double[numOfCols][];
        MultidataUtils.combineMixedContinuousData(dataModels, variables, continuousData, numOfRows, numOfDataCols);

        int[][] discreteData = new int[numOfCols][];
        MultidataUtils.combineMixedDiscreteData(dataModels, variables, discreteData, numOfRows, numOfDataCols);

        // add continuous intervention data
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            double[] rowData = new double[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = rowCounts[d];
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1.0);
                }
                row += r;
            }
            continuousData[col] = rowData;
        }

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createDiscreteDataWithDiscreteIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);
        int numOfDataCols = MultidataUtils.getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<Node> variables = new ArrayList<>(numOfCols);
        MultidataUtils.combineVariables(dataModels, variables);
        addInterventionalVariables(intervVars, true, variables);

        // add original discrete data
        int[][] discreteData = new int[numOfCols][];
        MultidataUtils.combineDiscreteDataToDiscreteVerticalData(dataModels, variables, discreteData, numOfRows, numOfDataCols);

        // add discrete intervention data
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = rowCounts[d];
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1);
                }
                row += r;
            }
            discreteData[col] = rowData;
        }

        return new BoxDataSet(new VerticalIntDataBox(discreteData), variables);
    }

    private static DataModel createDiscreteDataWithContinuousIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);
        int numOfDataCols = MultidataUtils.getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<Node> variables = new ArrayList<>(numOfCols);
        MultidataUtils.combineVariables(dataModels, variables);
        addInterventionalVariables(intervVars, false, variables);

        // add original discrete data
        int[][] discreteData = new int[numOfCols][];
        MultidataUtils.combineDiscreteDataToDiscreteVerticalData(dataModels, variables, discreteData, numOfRows, numOfDataCols);

        // add continuous intervention data
        double[][] continuousData = new double[numOfCols][];
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            double[] rowData = new double[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = rowCounts[d];
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1.0);
                }
                row += r;
            }
            continuousData[col] = rowData;
        }

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousDataWithDiscreteIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);
        int numOfDataCols = MultidataUtils.getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        // add original continuous data
        double[][] continuousData = new double[numOfCols][];
        MultidataUtils.combineContinuousDataToContinuousVerticalData(dataModels, continuousData, numOfRows, numOfDataCols);

        // add discrete intervention data
        int[][] discreteData = new int[numOfCols][];
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = rowCounts[d];
                while (r > 0) {
                    if (intervColumn[d]) {
                        rowData[row] = 1;
                    }
                    row++;
                    r--;
                }
            }
            discreteData[col] = rowData;
        }

        List<Node> variables = new ArrayList<>(numOfCols);
        MultidataUtils.combineVariables(dataModels, variables);
        addInterventionalVariables(intervVars, true, variables);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousDataWithContinuousIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] rowCounts = MultidataUtils.getRowCounts(dataModels);
        int numOfDataCols = MultidataUtils.getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(rowCounts).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        double[][] continuousData = new double[numOfRows][numOfCols];

        // add original continuous data
        MultidataUtils.combineContinuousData(dataModels, continuousData);

        // add continuous interventional data
        int dataCol = numOfDataCols;
        int row = 0;
        for (boolean[] intervColumn : interventions) {
            row = 0;
            for (int d = 0; d < intervColumn.length; d++) {
                int r = rowCounts[d];
                while (r > 0) {
                    if (intervColumn[d]) {
                        continuousData[row][dataCol] = 1;
                    }
                    row++;
                    r--;
                }
            }
            dataCol++;
        }

        List<Node> variables = new ArrayList<>(numOfCols);
        MultidataUtils.combineVariables(dataModels, variables);
        addInterventionalVariables(intervVars, false, variables);

        return new BoxDataSet(new DoubleDataBox(continuousData), variables);
    }

    private static void addInterventionalVariables(List<String> intervVars, boolean isDiscreteIntervVars, List<Node> variables) {
        intervVars.stream()
                .map(e -> isDiscreteIntervVars ? new DiscreteVariable(e, 2, true) : new ContinuousVariable(e, true))
                .collect(Collectors.toCollection(() -> variables));
    }

}
