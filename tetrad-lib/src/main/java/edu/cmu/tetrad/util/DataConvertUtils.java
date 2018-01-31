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
import edu.pitt.dbmi.data.ContinuousTabularDataset;
import edu.pitt.dbmi.data.CovarianceDataset;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.MixedTabularDataset;
import edu.pitt.dbmi.data.VerticalDiscreteTabularDataset;
import edu.pitt.dbmi.data.reader.tabular.DiscreteVarInfo;
import edu.pitt.dbmi.data.reader.tabular.MixedVarInfo;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Jun 20, 2017 11:10:30 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
    public class DataConvertUtils {

    private DataConvertUtils() {
    }

    public static DataModel toDataModel(Dataset dataset) {
        if (dataset instanceof ContinuousTabularDataset) {
            return toContinuousDataModel((ContinuousTabularDataset) dataset);
        } else if (dataset instanceof VerticalDiscreteTabularDataset) {
            return toVerticalDiscreteDataModel((VerticalDiscreteTabularDataset) dataset);
        } else if (dataset instanceof MixedTabularDataset) {
            return toMixedDataBox((MixedTabularDataset) dataset);
        } else if (dataset instanceof CovarianceDataset) {
            return toCovarianceMatrix((CovarianceDataset) dataset);
        } else {
            return null;
        }
    }

    public static DataModel toMixedDataBox(MixedTabularDataset mixedTabularDataset) {
        int numOfRows = mixedTabularDataset.getNumOfRows();
        MixedVarInfo[] mixedVarInfos = mixedTabularDataset.getMixedVarInfos();
        double[][] continuousData = mixedTabularDataset.getContinuousData();
        int[][] discreteData = mixedTabularDataset.getDiscreteData();

        List<Node> nodes = new LinkedList<>();
        for (MixedVarInfo mixedVarInfo : mixedVarInfos) {
            if (mixedVarInfo.isContinuous()) {
                nodes.add(new ContinuousVariable(mixedVarInfo.getName()));
            } else {
                nodes.add(new DiscreteVariable(mixedVarInfo.getName(), mixedVarInfo.getCategories()));
            }
        }

        return new BoxDataSet(new MixedDataBox(nodes, numOfRows, continuousData, discreteData), nodes);
    }

    public static DataModel toCovarianceMatrix(CovarianceDataset dataset) {
        List<Node> variables = toNodes(dataset.getVariables());
        TetradMatrix matrix = new TetradMatrix(dataset.getData());
        int sampleSize = dataset.getNumberOfCases();

        return new CovarianceMatrix(variables, matrix, sampleSize);
    }

    public static DataModel toVerticalDiscreteDataModel(VerticalDiscreteTabularDataset dataset) {
        DataBox dataBox = new VerticalIntDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getVariableInfos());

        return new BoxDataSet(dataBox, variables);
    }

    public static DataModel toContinuousDataModel(ContinuousTabularDataset dataset) {
        DataBox dataBox = new DoubleDataBox(dataset.getData());
        List<Node> variables = toNodes(dataset.getVariables());

        return new BoxDataSet(dataBox, variables);
    }

    public static List<Node> toNodes(DiscreteVarInfo[] varInfos) {
        List<Node> nodes = new LinkedList<>();

        for (DiscreteVarInfo varInfo : varInfos) {
            nodes.add(new DiscreteVariable(varInfo.getName(), varInfo.getCategories()));
        }

        return nodes;
    }

    public static List<Node> toNodes(List<String> variables) {
        List<Node> nodes = new LinkedList<>();

        variables.forEach(variable -> {
            nodes.add(new ContinuousVariable(variable));
        });

        return nodes;
    }

}
