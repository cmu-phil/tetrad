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




