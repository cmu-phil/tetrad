/*
 * Copyright (C) 2017 University of Pittsburgh.
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
package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import edu.pitt.dbmi.data.reader.covariance.CovarianceData;
import edu.pitt.dbmi.data.reader.metadata.ColumnMetadata;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.tabular.MixedTabularData;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularData;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Dec 15, 2018 11:10:30 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DataConvertUtils {

    private DataConvertUtils() {
    }

    public static DataModel toDataModel(Data data, Metadata metadata) {
        if (data instanceof ContinuousData) {
            return toContinuousDataModel((ContinuousData) data);
        } else if (data instanceof VerticalDiscreteTabularData) {
            return toVerticalDiscreteDataModel((VerticalDiscreteTabularData) data, metadata);
        } else if (data instanceof MixedTabularData) {
            return toMixedDataBox((MixedTabularData) data, metadata);
        } else if (data instanceof CovarianceData) {
            return toCovarianceMatrix((CovarianceData) data);
        } else {
            return null;
        }
    }

    public static DataModel toDataModel(Data data) {
        if (data instanceof ContinuousData) {
            return toContinuousDataModel((ContinuousData) data);
        } else if (data instanceof VerticalDiscreteTabularData) {
            return toVerticalDiscreteDataModel((VerticalDiscreteTabularData) data);
        } else if (data instanceof MixedTabularData) {
            return toMixedDataBox((MixedTabularData) data);
        } else if (data instanceof CovarianceData) {
            return toCovarianceMatrix((CovarianceData) data);
        } else {
            return null;
        }
    }

    public static DataModel toCovarianceMatrix(CovarianceData dataset) {
        List<Node> variables = toNodes(dataset.getVariables());
        TetradMatrix matrix = new TetradMatrix(dataset.getData());
        int sampleSize = dataset.getNumberOfCases();

        return new CovarianceMatrix(variables, matrix, sampleSize);
    }

    /**
     * Converting using metadata
     *
     * @param dataset
     * @param metadata
     * @return
     */
    public static DataModel toMixedDataBox(MixedTabularData dataset, Metadata metadata) {
        int numOfRows = dataset.getNumOfRows();
        DiscreteDataColumn[] columns = dataset.getDataColumns();
        double[][] continuousData = dataset.getContinuousData();
        int[][] discreteData = dataset.getDiscreteData();

        Node[] nodes = Arrays.stream(columns)
                .map(e -> e.getDataColumn().isDiscrete()
                ? new DiscreteVariable(e.getDataColumn().getName(), e.getCategories())
                : new ContinuousVariable(e.getDataColumn().getName()))
                .toArray(Node[]::new);

        metadata.getInterventionalColumns().forEach(e -> {
            ColumnMetadata valueColumn = e.getValueColumn();
            ColumnMetadata statusColumn = e.getStatusColumn();
            int valColNum = valueColumn.getColumnNumber() - 1;
            int statColNum = statusColumn.getColumnNumber() - 1;

            // Default NodeVariableType.DOMAIN for all variables
            // Overwrite NodeVariableType to NodeVariableType.INTERVENTION_VALUE or NodeVariableType.INTERVENTION_STATUS
            nodes[statColNum].setNodeVariableType(NodeVariableType.INTERVENTION_STATUS);
            nodes[valColNum].setNodeVariableType(NodeVariableType.INTERVENTION_VALUE);
        });

        List<Node> nodeList = Arrays.asList(nodes);
        return new BoxDataSet(new MixedDataBox(nodeList, numOfRows, continuousData, discreteData), nodeList);
    }

    public static DataModel toMixedDataBox(MixedTabularData dataset) {
        int numOfRows = dataset.getNumOfRows();
        DiscreteDataColumn[] columns = dataset.getDataColumns();
        double[][] continuousData = dataset.getContinuousData();
        int[][] discreteData = dataset.getDiscreteData();

        List<Node> nodes = Arrays.stream(columns)
                .map(e -> e.getDataColumn().isDiscrete()
                ? new DiscreteVariable(e.getDataColumn().getName(), e.getCategories())
                : new ContinuousVariable(e.getDataColumn().getName()))
                .collect(Collectors.toList());

        return new BoxDataSet(new MixedDataBox(nodes, numOfRows, continuousData, discreteData), nodes);
    }

    /**
     * Converting using metadata
     *
     * @param dataset
     * @param metatdata
     * @return
     */
    public static DataModel toVerticalDiscreteDataModel(VerticalDiscreteTabularData dataset, Metadata metatdata) {
        Node[] nodes = toNodes(dataset.getDataColumns()).stream()
                .toArray(Node[]::new);

        metatdata.getInterventionalColumns().forEach(e -> {
            ColumnMetadata valueColumn = e.getValueColumn();
            ColumnMetadata statusColumn = e.getStatusColumn();
            int valColNum = valueColumn.getColumnNumber() - 1;
            int statColNum = statusColumn.getColumnNumber() - 1;

            // Default NodeVariableType.DOMAIN for all variables
            // Overwrite NodeVariableType to NodeVariableType.INTERVENTION_VALUE or NodeVariableType.INTERVENTION_STATUS
            nodes[statColNum].setNodeVariableType(NodeVariableType.INTERVENTION_STATUS);
            nodes[valColNum].setNodeVariableType(NodeVariableType.INTERVENTION_VALUE);
        });

        DataBox dataBox = new VerticalIntDataBox(dataset.getData());
        List<Node> nodeList = Arrays.asList(nodes);

        return new BoxDataSet(dataBox, nodeList);
    }

    public static DataModel toVerticalDiscreteDataModel(VerticalDiscreteTabularData dataset) {
        DataBox dataBox = new VerticalIntDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getDataColumns());

        return new BoxDataSet(dataBox, variables);
    }

    public static DataModel toContinuousDataModel(ContinuousData dataset) {
        DataBox dataBox = new DoubleDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getDataColumns());

        return new BoxDataSet(dataBox, variables);
    }

    public static List<Node> toNodes(List<String> variables) {
        return variables.stream()
                .map(ContinuousVariable::new)
                .collect(Collectors.toList());
    }

    public static List<Node> toNodes(DiscreteDataColumn[] columns) {
        return Arrays.stream(columns)
                .map(e -> new DiscreteVariable(e.getDataColumn().getName(), e.getCategories()))
                .collect(Collectors.toList());
    }

    public static List<Node> toNodes(DataColumn[] columns) {
        return Arrays.stream(columns)
                .map(e -> new ContinuousVariable(e.getName()))
                .collect(Collectors.toList());
    }

}
