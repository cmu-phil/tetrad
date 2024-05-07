///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.Serial;

/**
 * <p>TimeSeriesWrapper class.</p>
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class TimeSeriesWrapper extends DataWrapper implements KnowledgeTransferable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a new time series dataset.
     *
     * @param data   - Previous data (from the parent node)
     * @param params - The parameters.
     */
    public TimeSeriesWrapper(DataWrapper data, Parameters params) {
        DataModelList dataSets = data.getDataModelList();
        DataModelList timeSeriesDataSets = new DataModelList();

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, params.getInt("numTimeLags", 1));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            this.knowledge = timeSeries.getKnowledge();
            timeSeriesDataSets.add(timeSeries);
        }

        this.setDataModel(timeSeriesDataSets);
        this.setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Expansion of parent data into lagged data.", getDataModelList());

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


