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
 * Jun 27, 2018 2:57:58 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class InterventionalDataFactory {

    private InterventionalDataFactory() {
    }

    public static DataModel createData(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions, boolean isDiscreteIntervention) {
        Optional<DataModel> opt = dataModels.stream().findFirst();
        if (opt.isPresent()) {
            DataModel dataModel = opt.get();
            if (dataModel.isContinuous()) {
                if (isDiscreteIntervention) {
                    return createContinuousDataWithDiscreteInterventions(dataModels, intervVars, interventions);
                } else {
                    return (dataModels.size() == 1)
                            ? createContinuousDataWithContinuousInterventions(dataModels.get(0), intervVars, interventions)
                            : createContinuousDataWithContinuousInterventions(dataModels, intervVars, interventions);
                }
            } else if (dataModel.isContinuous()) {
                if (isDiscreteIntervention) {
                    return (dataModels.size() == 1)
                            ? createDiscreteDataWithContinuousInterventions(dataModels.get(0), intervVars, interventions)
                            : createDiscreteDataWithContinuousInterventions(dataModels, intervVars, interventions);
                } else {
                }
            } else {
            }
        }

        return null;
    }

    private static DataModel createDiscreteDataWithContinuousInterventions(DataModel dataModel, List<String> intervVars, boolean[][] interventions) {
        int numOfDataCols = getNumberOfColumns(dataModel);

        int numOfRows = getNumberOfRows(dataModel);
        int numOfCols = numOfDataCols + intervVars.size();

        VerticalIntDataBox dataBox = (VerticalIntDataBox) ((BoxDataSet) dataModel).getDataBox();
        int[][] data = dataBox.getVariableVectors();

        // merge the existing data over
        int[][] discreteData = new int[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            int[] rowData = new int[numOfRows];
            int[] values = data[col];
            System.arraycopy(values, 0, rowData, 0, values.length);
            discreteData[col] = rowData;
        }

        int col = numOfDataCols;
        double[][] continuousData = new double[numOfCols][];
        for (boolean[] intervColumn : interventions) {
            double[] rowData = new double[numOfRows];
            if (intervColumn[0]) {
                Arrays.fill(rowData, 1.0);
            }
            continuousData[col] = rowData;
        }

        List<Node> variables = copyVariables(dataModel);
        addInterventionalVariables(variables, intervVars, false);

        return null;
    }

    private static DataModel createDiscreteDataWithContinuousInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

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

        List<Node> variables = copyVariables(dataModels.get(0));
        addInterventionalVariables(variables, intervVars, false);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousDataWithDiscreteInterventions(DataModel dataModel, List<String> intervVars, boolean[][] interventions) {
        int numOfDataCols = getNumberOfColumns(dataModel);

        int numOfRows = getNumberOfRows(dataModel);
        int numOfCols = numOfDataCols + intervVars.size();

        DoubleDataBox dataBox = (DoubleDataBox) ((BoxDataSet) dataModel).getDataBox();
        double[][] data = dataBox.getData();

        // copy and transpose original data
        double[][] continuousData = new double[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            double[] rowData = new double[numOfRows];
            for (int row = 0; row < numOfRows; row++) {
                rowData[row] = data[row][col];
            }
            continuousData[col] = rowData;
        }

        // combine inventional data
        int col = numOfDataCols;
        int[][] discreteData = new int[numOfCols][];
        for (boolean[] intervColumn : interventions) {
            int[] rowData = new int[numOfRows];
            if (intervColumn[0]) {
                Arrays.fill(rowData, 1);
            }
            discreteData[col] = rowData;
        }

        List<Node> variables = copyVariables(dataModel);
        addInterventionalVariables(variables, intervVars, true);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousDataWithDiscreteInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

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

        List<Node> variables = copyVariables(dataModels.get(0));
        addInterventionalVariables(variables, intervVars, true);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousDataWithContinuousInterventions(DataModel dataModel, List<String> intervVars, boolean[][] interventions) {
        int numOfRows = getNumberOfRows(dataModel);
        int numOfDataCols = getNumberOfColumns(dataModel);

        int numOfCols = numOfDataCols + intervVars.size();

        DoubleDataBox dataBox = (DoubleDataBox) ((BoxDataSet) dataModel).getDataBox();
        double[][] data = dataBox.getData();

        // copy original data over
        double[][] continuousData = new double[numOfRows][numOfCols];
        for (int row = 0; row < numOfRows; row++) {
            System.arraycopy(data[row], 0, continuousData[row], 0, numOfDataCols);
        }

        int dataCol = numOfDataCols;
        for (boolean[] intervColumn : interventions) {
            if (intervColumn[0]) {
                for (int row = 0; row < numOfRows; row++) {
                    continuousData[row][dataCol] = 1.0;
                }
            }

            dataCol++;
        }

        List<Node> variables = copyVariables(dataModel);
        addInterventionalVariables(variables, intervVars, false);

        return new BoxDataSet(new DoubleDataBox(continuousData), variables);
    }

    private static DataModel createContinuousDataWithContinuousInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        int row = 0;
        double[][] continuousData = new double[numOfRows][numOfCols];
        for (DataModel dataModel : dataModels) {
            if (dataModel.isContinuous() && dataModel instanceof BoxDataSet) {
                BoxDataSet boxDataSet = (BoxDataSet) dataModel;
                DataBox dataBox = boxDataSet.getDataBox();
                if (dataBox instanceof DoubleDataBox) {
                    double[][] data = ((DoubleDataBox) dataBox).getData();
                    for (double[] rowData : data) {
                        System.arraycopy(rowData, 0, continuousData[row++], 0, rowData.length);
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
                        continuousData[row][dataCol] = 1;
                    }
                    row++;
                    r--;
                }
            }
            dataCol++;
        }

        List<Node> variables = copyVariables(dataModels.get(0));
        addInterventionalVariables(variables, intervVars, false);

        return new BoxDataSet(new DoubleDataBox(continuousData), variables);
    }

    private static List<Node> copyVariables(DataModel dataModel) {
        return (dataModel == null)
                ? Collections.EMPTY_LIST
                : dataModel.getVariables().stream().collect(Collectors.toList());
    }

    private static void addInterventionalVariables(List<Node> variables, List<String> intervVars, boolean isDiscrete) {
        intervVars.stream()
                .map(e -> isDiscrete ? new DiscreteVariable(e, 2) : new ContinuousVariable(e))
                .collect(Collectors.toCollection(() -> variables));
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

    private static int getNumberOfRows(DataModel dataModel) {
        return (dataModel instanceof BoxDataSet)
                ? ((BoxDataSet) dataModel).getNumRows()
                : 0;
    }

    private static int getNumberOfColumns(DataModel dataModel) {
        return (dataModel instanceof BoxDataSet)
                ? ((BoxDataSet) dataModel).getDataBox().numCols()
                : 0;
    }

}
