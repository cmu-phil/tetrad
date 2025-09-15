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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.TimeSeriesData;

import javax.swing.*;
import java.awt.*;

/**
 * Displays a DataSet object as a JTable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TimeSeriesDataDisplay extends JPanel
        implements DataModelContainer {

    /**
     * The JTable that displays the DataSet.
     */
    private final TimeSeriesDataDisplayJTable timeSerieaDataDisplayJTable;

    /**
     * Constructor. Takes a DataSet as a model.
     *
     * @param model a {@link edu.cmu.tetrad.data.TimeSeriesData} object
     */
    public TimeSeriesDataDisplay(TimeSeriesData model) {
        this.timeSerieaDataDisplayJTable = new TimeSeriesDataDisplayJTable(model);
        setLayout(new BorderLayout());
        add(new JScrollPane(this.timeSerieaDataDisplayJTable), BorderLayout.CENTER);
    }

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        return this.timeSerieaDataDisplayJTable.getDataModel();
    }
}





