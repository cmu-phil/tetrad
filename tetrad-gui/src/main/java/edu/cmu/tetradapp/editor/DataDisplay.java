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
import edu.cmu.tetrad.data.DataSet;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Displays a DataSet object as a JTable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DataDisplay extends JPanel implements DataModelContainer,
        PropertyChangeListener {

    /**
     * The JTable that displays the data.
     */
    private final TabularDataJTable tabularDataJTable;

    /**
     * The property change support.
     */
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Constructor. Takes a DataSet as a model.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataDisplay(DataSet dataSet) {
        this.tabularDataJTable = new TabularDataJTable(dataSet);
        this.tabularDataJTable.addPropertyChangeListener(this);
        setLayout(new BorderLayout());
        add(new JScrollPane(getDataDisplayJTable()), BorderLayout.CENTER);
    }

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        return getDataDisplayJTable().getDataModel();
    }

    /**
     * <p>getDataDisplayJTable.</p>
     *
     * @return a {@link edu.cmu.tetradapp.editor.TabularDataJTable} object
     */
    public TabularDataJTable getDataDisplayJTable() {
        return this.tabularDataJTable;
    }

    /**
     * {@inheritDoc}
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        // For some reason openjdk leaves this null at this point first call. Not sure why. 4/23/2009 jdr
        if (this.pcs == null) {
            this.pcs = new PropertyChangeSupport(this);
        }

        this.pcs.addPropertyChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent evt) {
//        System.out.println("DataDisplay: " + evt.getPropertyName());
        this.pcs.firePropertyChange(evt);
    }
}


