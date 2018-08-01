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
import java.util.LinkedList;
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
                    return createContinuousDataWithContinuousInterventions(dataModels, intervVars, interventions);
                }
            } else if (dataModel.isDiscrete()) {
                if (isDiscreteIntervention) {
                    return createDiscreteDataWithDiscreteInterventions(dataModels, intervVars, interventions);
                } else {
                    return createDiscreteDataWithContinuousInterventions(dataModels, intervVars, interventions);
                }
            } else {
                if (isDiscreteIntervention) {
                    return createMixedDataWithDiscreteInterventions(dataModels, intervVars, interventions);
                } else {
                    return createMixedDataWithContinuousInterventions(dataModels, intervVars, interventions);
                }
            }
        }

        return null;
    }

    private static DataModel createMixedDataWithDiscreteInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<MixedDataBox> models = dataModels.stream()
                .filter(e -> e.isMixed())
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

        // merge the existing discrete data over
        int[][] discreteData = new int[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            int[] rowData = new int[numOfRows];
            int index = 0;
            for (MixedDataBox model : models) {
                int[][] data = model.getDiscreteData();
                int[] values = data[col];
                if (values == null) {
                    rowData = null;
                    break;
                } else {
                    System.arraycopy(values, 0, rowData, index, values.length);
                    index += values.length;
                }
            }
            discreteData[col] = rowData;
        }

        // merge the existing continuous data over
        double[][] continuousData = new double[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            double[] rowData = new double[numOfRows];
            int index = 0;
            for (MixedDataBox model : models) {
                double[][] data = model.getContinuousData();
                double[] values = data[col];
                if (values == null) {
                    rowData = null;
                    break;
                } else {
                    System.arraycopy(values, 0, rowData, index, values.length);
                    index += values.length;
                }
            }
            continuousData[col] = rowData;
        }

        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            int[] rowData = new int[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = numOfDataRows[d];
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1);
                }
                row += r;
            }
            discreteData[col] = rowData;
        }

        List<Node> variables = createMixedVariables(dataModels, intervVars, true);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createMixedDataWithContinuousInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<MixedDataBox> models = dataModels.stream()
                .filter(e -> e.isMixed())
                .map(e -> (MixedDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

        // merge the existing discrete data over
        int[][] discreteData = new int[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            int[] rowData = new int[numOfRows];
            int index = 0;
            for (MixedDataBox model : models) {
                int[][] data = model.getDiscreteData();
                int[] values = data[col];
                if (values == null) {
                    rowData = null;
                    break;
                } else {
                    System.arraycopy(values, 0, rowData, index, values.length);
                    index += values.length;
                }
            }
            discreteData[col] = rowData;
        }

        // merge the existing continuous data over
        double[][] continuousData = new double[numOfCols][];
        for (int col = 0; col < numOfDataCols; col++) {
            double[] rowData = new double[numOfRows];
            int index = 0;
            for (MixedDataBox model : models) {
                double[][] data = model.getContinuousData();
                double[] values = data[col];
                if (values == null) {
                    rowData = null;
                    break;
                } else {
                    System.arraycopy(values, 0, rowData, index, values.length);
                    index += values.length;
                }
            }
            continuousData[col] = rowData;
        }

        int intervCol = 0;
        for (int col = numOfDataCols; col < numOfCols; col++) {
            double[] rowData = new double[numOfRows];
            int row = 0;
            boolean[] intervColumn = interventions[intervCol++];
            for (int d = 0; d < intervColumn.length; d++) {
                int r = numOfDataRows[d];
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1.0);
                }
                row += r;
            }
            continuousData[col] = rowData;
        }

        List<Node> variables = createMixedVariables(dataModels, intervVars, false);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createDiscreteDataWithDiscreteInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<VerticalIntDataBox> models = dataModels.stream()
                .filter(e -> e.isDiscrete())
                .map(e -> (VerticalIntDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

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
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1);
                }
                row += r;
            }
            discreteData[col] = rowData;
        }

        List<Node> variables = createMixedVariables(dataModels, intervVars, true);

        return new BoxDataSet(new VerticalIntDataBox(discreteData), variables);
    }

    private static DataModel createDiscreteDataWithContinuousInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<VerticalIntDataBox> models = dataModels.stream()
                .filter(e -> e.isDiscrete())
                .map(e -> (VerticalIntDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

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
                if (intervColumn[d]) {
                    Arrays.fill(rowData, row, row + r, 1.0);
                }
                row += r;
            }
            continuousData[col] = rowData;
        }

        List<Node> variables = createMixedVariables(dataModels, intervVars, false);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
    }

    private static DataModel createContinuousDataWithDiscreteInterventions(List<DataModel> dataModels, List<String> intervVars, boolean[][] interventions) {
        int[] numOfDataRows = getNumberOfRows(dataModels);
        int numOfDataCols = getNumberOfColumns(dataModels.get(0));

        int numOfRows = Arrays.stream(numOfDataRows).sum();
        int numOfCols = numOfDataCols + intervVars.size();

        List<DoubleDataBox> models = dataModels.stream()
                .filter(e -> e.isContinuous())
                .map(e -> (DoubleDataBox) ((BoxDataSet) e).getDataBox())
                .collect(Collectors.toList());

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

        List<Node> variables = createVariables(dataModels.get(0), intervVars, true);

        return new BoxDataSet(new MixedDataBox(variables, numOfRows, continuousData, discreteData), variables);
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

        List<Node> variables = createVariables(dataModels.get(0), intervVars, false);

        return new BoxDataSet(new DoubleDataBox(continuousData), variables);
    }

    private static List<Node> createMixedVariables(List<DataModel> dataModels, List<String> intervVars, boolean isDiscrete) {
        int size = dataModels.get(0).getVariables().size();
        Node[] vars = new Node[size + intervVars.size()];
        dataModels.forEach(e -> {
            int index = 0;
            for (Node node : e.getVariables()) {
                Node n1 = vars[index];
                if (n1 == null) {
                    vars[index] = node;
                } else {
                    if (node instanceof DiscreteVariable && n1 instanceof DiscreteVariable) {
                        if (((DiscreteVariable) n1).getNumCategories() < ((DiscreteVariable) node).getNumCategories()) {
                            vars[index] = node;
                        }
                    }
                }
                index++;
            }
        });

        int index = size;
        for (String var : intervVars) {
            vars[index++] = isDiscrete ? new DiscreteVariable(var, 2) : new ContinuousVariable(var);
        }

        return Arrays.asList(vars);
    }

    private static List<Node> createVariables(DataModel dataModel, List<String> intervVars, boolean isDiscrete) {
        List<Node> variables = new LinkedList<>();

        // copy original variables
        dataModel.getVariables().stream()
                .collect(Collectors.toCollection(() -> variables));

        // create interventional variables, use the default NodeType.MEASURED and set interventional flag to true
        // so they can be identified later in knowledge graph and search resulting graph - Zhou
        intervVars.stream()
                .map(e -> isDiscrete ? new DiscreteVariable(e, 2, true) : new ContinuousVariable(e, true))
                .collect(Collectors.toCollection(() -> variables));

        return variables;
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

    private static int getNumberOfColumns(DataModel dataModel) {
        return (dataModel instanceof BoxDataSet)
                ? ((BoxDataSet) dataModel).getDataBox().numCols()
                : 0;
    }

}
