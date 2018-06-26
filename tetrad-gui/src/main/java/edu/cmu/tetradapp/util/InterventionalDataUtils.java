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
package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *
 * Jun 20, 2018 3:57:23 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class InterventionalDataUtils {

    private InterventionalDataUtils() {
    }

    public static DataModel createInterventionalDataset(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions, boolean asDiscrete) {
        Optional<DataModel> opt = dataModels.stream().findFirst();
        if (opt.isPresent()) {
            DataModel dataModel = opt.get();
            if (dataModel.isContinuous()) {
                if (asDiscrete) {
                    return createFromContinuousDataWithDiscreteIntervention(dataModels, intervVars, interventions);
                } else {
                    return createContinuousWithContinuousIntervention(dataModels, intervVars, interventions);
                }
            } else if (dataModel.isDiscrete()) {
                if (asDiscrete) {
                    return createDiscreteWithDiscreteIntervention(dataModels, intervVars, interventions);
                } else {
                    return createDiscreteWithContinuousIntervention(dataModels, intervVars, interventions);
                }
            } else {
            }
        }

        return null;
    }

    private static DataModel createDiscreteWithDiscreteIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels);

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<VerticalIntDataBox> list = dataModels.stream()
                .filter(e -> e.isDiscrete())
                .map(e -> (VerticalIntDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());
        VerticalIntDataBox[] models = list.toArray(new VerticalIntDataBox[list.size()]);

        // merge the existing data over
        int[][] discreteData = new int[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            int[] rowData = new int[numOfRows];
            int index = 0;
            for (VerticalIntDataBox model : models) {
                int[][] data = model.getVariableVectors();
                int[] values = data[col];
                System.arraycopy(values, 0, rowData, index, values.length);
                index += values.length;
            }
            discreteData[col] = rowData;
        }

        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = numOfDataRows[d];
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

        List<Node> variables = createVariables(dataModels, intervVars, true);

        return new BoxDataSet(new VerticalIntDataBox(discreteData), variables);
    }

    private static DataModel createDiscreteWithContinuousIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels);

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<VerticalIntDataBox> list = dataModels.stream()
                .filter(e -> e.isDiscrete())
                .map(e -> (VerticalIntDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());
        VerticalIntDataBox[] models = list.toArray(new VerticalIntDataBox[list.size()]);

        // merge the existing data over
        int[][] discreteData = new int[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            int[] rowData = new int[numOfRows];
            int index = 0;
            for (VerticalIntDataBox model : models) {
                int[][] data = model.getVariableVectors();
                int[] values = data[col];
                System.arraycopy(values, 0, rowData, index, values.length);
                index += values.length;
            }
            discreteData[col] = rowData;
        }

        double[][] continuousData = new double[numOfCols][];
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            double[] rowData = new double[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = numOfDataRows[d];
                while (r > 0) {
                    if (intervColumn[d]) {
                        rowData[row] = 1.0;
                    }
                    row++;
                    r--;
                }
            }
            continuousData[col] = rowData;
        }

        List<Node> variables = createVariables(dataModels, intervVars, false);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousWithContinuousIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels);

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        int row = 0;
        double[][] contData = new double[numOfRows][numOfCols];
        for (DataModel dataModel : dataModels) {
            if (dataModel.isContinuous() && dataModel instanceof BoxDataSet) {
                BoxDataSet boxDataSet = (BoxDataSet) dataModel;
                DataBox dataBox = boxDataSet.getDataBox();
                if (dataBox instanceof DoubleDataBox) {
                    double[][] data = ((DoubleDataBox) dataBox).getData();
                    for (double[] rowData : data) {
                        System.arraycopy(rowData, 0, contData[row++], 0, rowData.length);
                    }
                }
            }
        }

        int dataCol = numOfDataCols;
        for (boolean[] intervColumn : interventions) {
            row = 0;
            for (int d = 0; d < intervColumn.length; d++) {
                int r = numOfDataRows[d];
                while (r > 0) {
                    if (intervColumn[d]) {
                        contData[row][dataCol] = 1;
                    }
                    row++;
                    r--;
                }
            }
            dataCol++;
        }

        List<Node> variables = createVariables(dataModels, intervVars, false);

        return new BoxDataSet(new DoubleDataBox(contData), variables);
    }

    private static DataModel createFromContinuousDataWithDiscreteIntervention(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels);

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<DoubleDataBox> list = dataModels.stream()
                .filter(e -> e.isContinuous())
                .map(e -> (DoubleDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());
        DoubleDataBox[] models = list.toArray(new DoubleDataBox[list.size()]);

        // combine and transpose original data
        double[][] continuousData = new double[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            double[] rowData = new double[numOfRows];
            int row = 0;
            for (DoubleDataBox dataBox : models) {
                double[][] data = dataBox.getData();
                for (double[] rData : data) {
                    rowData[row++] = rData[col];
                }
            }
            continuousData[col] = rowData;
        }

        // combine inventional data
        int[][] discreteData = new int[numOfCols][];
        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = numOfDataRows[d];
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

        List<Node> variables = createVariables(dataModels, intervVars, true);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static int getNumberOfColumns(List<DataModel> dataModels) {
        int numOfCols = 0;

        Optional<DataModel> opt = dataModels.stream().findFirst();
        if (opt.isPresent()) {
            DataModel dataModel = opt.get();
            if (dataModel instanceof BoxDataSet) {
                numOfCols += ((BoxDataSet) dataModel).getDataBox().numCols();
            }
        }

        return numOfCols;
    }

    private static int[] getNumberOfRows(List<DataModel> dataModels) {
        int[] counts = new int[dataModels.size()];

        int index = 0;
        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof BoxDataSet) {
                counts[index++] = ((BoxDataSet) dataModel).getNumRows();
            }
        }

        return counts;
    }

    private static List<Node> createVariables(List<DataModel> dataModels, List<String> intervVars, boolean isDiscrete) {
        Optional<DataModel> opt = dataModels.stream().findFirst();
        if (opt.isPresent()) {
            // copy original variables
            List<Node> list = opt.get().getVariables()
                    .stream().collect(Collectors.toList());

            // add new variables
            intervVars.stream()
                    .map(e -> isDiscrete ? new DiscreteVariable(e, 2) : new ContinuousVariable(e))
                    .collect(Collectors.toCollection(() -> list));

            return list;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

}
