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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.regression.RegressionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.*;

/**
 * <p>ResidualsWrapper class.</p>
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class ResidualsWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new time series dataset.
     *
     * @param data       - Previous data (from the parent node)
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ResidualsWrapper(DataWrapper data, DagWrapper dagWrapper, Parameters params) {
        DataModelList list = data.getDataModelList();
        DataModelList newList = new DataModelList();

        for (DataModel dataModel : list) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("The data must be a rectangular dataset");
            }
            Graph dag = dagWrapper.getGraph();
            dataSet = RegressionUtils.residuals(dataSet, dag);
            newList.add(dataSet);
        }

        this.setDataModel(newList);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data in which each column has been replaced by its regression residuals.", getDataModelList());

    }

    /**
     * <p>Constructor for ResidualsWrapper.</p>
     *
     * @param data         a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ResidualsWrapper(DataWrapper data, GraphWrapper graphWrapper, Parameters params) {
        DataModelList list = data.getDataModelList();
        DataModelList newList = new DataModelList();

        for (DataModel dataModel : list) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("The data must be a rectangular dataset");
            }
            Graph graph = graphWrapper.getGraph();
            dataSet = RegressionUtils.residuals(dataSet, graph);
            newList.add(dataSet);
        }

        this.setDataModel(newList);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data in which each column has been replaced by its regression residuals.", getDataModelList());
    }

    /**
     * <p>Constructor for ResidualsWrapper.</p>
     *
     * @param data    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ResidualsWrapper(DataWrapper data, SemGraphWrapper wrapper, Parameters params) {
        DataModelList list = data.getDataModelList();
        DataModelList newList = new DataModelList();

        for (DataModel dataModel : list) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("The data must be a rectangular dataset");
            }
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
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }
}



