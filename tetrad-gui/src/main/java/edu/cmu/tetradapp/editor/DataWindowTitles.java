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

/**
 * Builds titles for tool windows opened from a data editor, naming the data set the tool was
 * run on. The Tools menu actions (data audit, nonlinearity checks, descriptive statistics, plot
 * matrix, Q-Q plot, independence facts, calibration calculator) all act on the data set
 * selected in the editor at the moment they are invoked, and each opens a snapshot window; when
 * the editor holds several data sets - the subjects of a panel simulation, or several runs - a
 * bare "Data Audit" title does not say which one. This appends the data set's name when it has
 * one.
 *
 * @author josephramsey
 */
final class DataWindowTitles {

    private DataWindowTitles() {
    }

    /**
     * Returns the base title followed by " - " and the data model's name, or the base title alone
     * if the model is null or unnamed.
     *
     * @param base  the tool's title, e.g. "Data Audit".
     * @param model the data model the tool was run on.
     * @return the window title.
     */
    static String of(String base, DataModel model) {
        if (model == null) return base;
        String name = model.getName();
        if (name == null || name.isBlank()) return base;
        return base + " - " + name.trim();
    }
}
