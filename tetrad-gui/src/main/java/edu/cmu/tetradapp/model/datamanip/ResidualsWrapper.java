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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.regression.RegressionUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.SemGraphWrapper;

/**
 * @author Tyler
 */
public class ResidualsWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    /**
     * Constructs a new time series dataset.
     *
     * @param data   - Previous data (from the parent node)
     */
    public ResidualsWrapper(DataWrapper data, DagWrapper dagWrapper) {
        DataModelList list = data.getDataModelList();
        DataModelList newList = new DataModelList();

        for (DataModel dataModel : list) {
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("The data must be a rectangular dataset");
            }
            DataSet dataSet = (DataSet) dataModel;
            Dag dag = dagWrapper.getDag();
            dataSet = RegressionUtils.residuals(dataSet, dag);
            newList.add(dataSet);
        }

        this.setDataModel(newList);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data in which each column has been replaced by its regression residuals.", getDataModelList());

    }

    public ResidualsWrapper(DataWrapper data, GraphWrapper graphWrapper) {
        DataModelList list = data.getDataModelList();
        DataModelList newList = new DataModelList();

        for (DataModel dataModel : list) {
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("The data must be a rectangular dataset");
            }
            DataSet dataSet = (DataSet) dataModel;
            Graph graph = graphWrapper.getGraph();
            dataSet = RegressionUtils.residuals(dataSet, graph);
            newList.add(dataSet);
        }

        this.setDataModel(newList);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data in which each column has been replaced by its regression residuals.", getDataModelList());
    }

    public ResidualsWrapper(DataWrapper data, SemGraphWrapper wrapper) {
        DataModelList list = data.getDataModelList();
        DataModelList newList = new DataModelList();

        for (DataModel dataModel : list) {
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("The data must be a rectangular dataset");
            }
            DataSet dataSet = (DataSet) dataModel;
            Graph graph = wrapper.getGraph();
            dataSet = RegressionUtils.residuals(dataSet, graph);
            newList.add(dataSet);
        }

        this.setDataModel(newList);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data in which each column has been replaced by its regression residuals.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new ResidualsWrapper(DataWrapper.serializableInstance(),
                DagWrapper.serializableInstance());
    }
}


