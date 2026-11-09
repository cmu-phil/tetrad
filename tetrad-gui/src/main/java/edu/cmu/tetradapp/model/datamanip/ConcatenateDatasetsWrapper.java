///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MultidataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Concatenates the rows of the given tabular data sets, in order. Optionally (see the
 * parameter "concatAddSourceColumn"), a discrete source column is appended to the combined
 * data set recording, for each row, the name of the data set the row came from. This makes
 * the source data set available downstream as a grouping variable--for example, as the
 * grouping variable for the within-group serial dependence check in the data audit, when
 * the concatenated files are per-subject or per-session time series.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class ConcatenateDatasetsWrapper extends DataWrapper {

    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for ConcatenateDatasetsWrapper.</p>
     *
     * @param data   an array of {@link edu.cmu.tetradapp.model.DataWrapper} objects
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ConcatenateDatasetsWrapper(DataWrapper[] data, Parameters params) {
        construct(data, params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    private void construct(DataWrapper[] dataWrappers, Parameters params) {
        for (DataWrapper wrapper : dataWrappers) {
            if (wrapper == null) {
                throw new NullPointerException("The given data must not be null");
            }
        }

        List<DataModel> dataModels = new LinkedList<>();
        for (DataWrapper wrapper : dataWrappers) {
            wrapper.getDataModelList().forEach(dataModel -> {
                if (dataModel instanceof DataSet) {
                    dataModels.add(dataModel);
                } else {
                    throw new IllegalArgumentException("Sorry, I am only willing to concatenate tabular datasets.");
                }
            });
        }

        DataModel dataModel = MultidataUtils.combineDataset(dataModels);

        if (params.getBoolean("concatAddSourceColumn", false)) {
            String columnName = params.getString("concatSourceColumnName", "source");

            if (columnName == null || columnName.isBlank()) {
                columnName = "source";
            }

            dataModel = addSourceColumn((DataSet) dataModel, dataModels, columnName.trim());
        }

        dataModel.setName("Concatenated");
        this.setDataModel(dataModel);

        LogDataUtils.logDataModelList("Parent data in which constant columns have been removed.", getDataModelList());

    }

    /**
     * Returns a copy of the combined data set with a discrete source column appended, whose
     * value for each row is the name of the source data set the row came from. Source data
     * sets with null or blank names are named "data1", "data2", etc., by position, and
     * duplicate names are made unique by appending "_2", "_3", etc. If the requested column
     * name collides with an existing variable name, "_2", "_3", etc., is appended to it as
     * well. The returned data set is backed by a mixed data box, since it contains a discrete
     * column alongside whatever columns the sources had.
     */
    private static DataSet addSourceColumn(DataSet combined, List<DataModel> sources, String columnName) {
        int numRows = combined.getNumRows();
        int numCols = combined.getNumColumns();

        List<String> categories = new ArrayList<>();

        for (int i = 0; i < sources.size(); i++) {
            String name = sources.get(i).getName();

            if (name == null || name.isBlank()) {
                name = "data" + (i + 1);
            }

            name = name.trim();
            String candidate = name;
            int suffix = 2;

            while (categories.contains(candidate)) {
                candidate = name + "_" + suffix++;
            }

            categories.add(candidate);
        }

        List<String> existingNames = combined.getVariableNames();
        String finalName = columnName;
        int suffix = 2;

        while (existingNames.contains(finalName)) {
            finalName = columnName + "_" + suffix++;
        }

        List<Node> variables = new ArrayList<>(combined.getVariables());
        DiscreteVariable sourceVar = new DiscreteVariable(finalName, categories);
        variables.add(sourceVar);

        double[][] continuousData = new double[numCols + 1][];
        int[][] discreteData = new int[numCols + 1][];

        for (int j = 0; j < numCols; j++) {
            if (combined.getVariables().get(j) instanceof DiscreteVariable) {
                int[] column = new int[numRows];

                for (int i = 0; i < numRows; i++) {
                    column[i] = combined.getInt(i, j);
                }

                discreteData[j] = column;
            } else {
                double[] column = new double[numRows];

                for (int i = 0; i < numRows; i++) {
                    column[i] = combined.getDouble(i, j);
                }

                continuousData[j] = column;
            }
        }

        int[] sourceColumn = new int[numRows];
        int row = 0;

        for (int i = 0; i < sources.size(); i++) {
            int sourceRows = ((DataSet) sources.get(i)).getNumRows();

            for (int r = 0; r < sourceRows; r++) {
                sourceColumn[row++] = i;
            }
        }

        discreteData[numCols] = sourceColumn;

        return new BoxDataSet(new MixedDataBox(variables, numRows, continuousData, discreteData), variables);
    }
}
