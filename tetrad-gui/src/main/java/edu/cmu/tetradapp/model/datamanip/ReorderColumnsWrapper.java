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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class ReorderColumnsWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for ReorderColumnsWrapper.</p>
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ReorderColumnsWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The givan data must not be null");
        }

        DataModelList dataModelList = data.getDataModelList();

        List<Node> variables = dataModelList.get(0).getVariables();

        DataModelList newData = new DataModelList();
        variables = new ArrayList<>(variables);
        RandomUtil.shuffle(variables);

        if (dataModelList.get(0) instanceof DataSet) {
            List<DataSet> dataSets = new ArrayList<>();
            for (DataModel dataModel : dataModelList) {
                dataSets.add((DataSet) dataModel);
            }
            newData.addAll((DataTransforms.shuffleColumns2(dataSets)));
        } else {

            for (DataModel dataModel : dataModelList) {
//            if (dataModel instanceof DataSet) {
//                DataSet _newData = shuffleColumns((DataSet) dataModel);
//                newData.add(_newData);
//            }
//            else
                if (dataModel instanceof CovarianceMatrix cov) {

                    List<String> vars = new ArrayList<>();

                    for (Node node : variables) {
                        vars.add(node.getName());
                    }

                    ICovarianceMatrix _newData = cov.getSubmatrix(vars);
                    newData.add(_newData);
                }
            }
        }

        this.setDataModel(newData);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data in which constant columns have been removed.", getDataModelList());

    }

    /**
     * <p>shuffleColumns.</p>
     *
     * @param dataModel a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet shuffleColumns(DataSet dataModel) {

        List<Node> vars = new ArrayList<>();

        for (Node node : dataModel.getVariables()) {
            Node _node = dataModel.getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        RandomUtil.shuffle(vars);
        return dataModel.subsetColumns(vars);
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


}




