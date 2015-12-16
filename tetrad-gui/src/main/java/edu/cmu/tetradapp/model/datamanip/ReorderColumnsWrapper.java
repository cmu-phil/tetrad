///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 */
public class ReorderColumnsWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;


    public ReorderColumnsWrapper(DataWrapper data) {
        if (data == null) {
            throw new NullPointerException("The givan data must not be null");
        }

        DataModelList dataModelList = data.getDataModelList();

        List<Node> variables = dataModelList.get(0).getVariables();

        DataModelList newData = new DataModelList();
        variables = new ArrayList<Node>(variables);
        Collections.shuffle(variables);

        if (dataModelList.get(0) instanceof DataSet) {
            List<DataSet> dataSets = new ArrayList<>();
            for (int i = 0; i < dataModelList.size(); i++) {
                dataSets.add((DataSet) dataModelList.get(i));
            }
            newData.addAll((DataUtils.reorderColumns(dataSets)));
        } else {

            for (DataModel dataModel : dataModelList) {
//            if (dataModel instanceof DataSet) {
//                DataSet _newData = reorderColumns((DataSet) dataModel);
//                newData.add(_newData);
//            }
//            else
                if (dataModel instanceof CovarianceMatrix) {
                    CovarianceMatrix cov = (CovarianceMatrix) dataModel;

                    List<String> vars = new ArrayList<String>();

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

    public static DataSet reorderColumns(DataSet dataModel) {
        DataSet dataSet = dataModel;

        List<Node> vars = new ArrayList<Node>();

        for (Node node : dataSet.getVariables()) {
            Node _node = dataSet.getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        Collections.shuffle(vars);
        return dataSet.subsetColumns(vars);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static ReorderColumnsWrapper serializableInstance() {
        return new ReorderColumnsWrapper(DataWrapper.serializableInstance());
    }


}



