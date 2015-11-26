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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.LogisticRegressionParams;

import javax.swing.*;
import java.awt.*;

/**
 * Edits parameters for Markov blanket search algorithms.
 *
 * @author Ricardo Silva (GES version)
 * @author Frank Wimberly adapted for PCX.
 * @author Frank Wimberly further adapted for Regression
 */
public final class LogisticRegressionParamsEditor extends JPanel implements ParameterEditor {


    private LogisticRegressionParams params;
    private Object[] parentModels;


    /**
     * Opens up an editor to let the user view the given RegressionRunner.
     */
    public LogisticRegressionParamsEditor() {

    }

    public void setParams(Params params) {
       this.params = (LogisticRegressionParams)params;
    }

    public void setParentModels(Object[] parentModels) {
        this.parentModels = parentModels;
    }

    public void setup() {
        LogisticRegressionParamsPanel panel =
                new LogisticRegressionParamsPanel(params, parentModels);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(panel, BorderLayout.CENTER);
    }

    public boolean mustBeShown() {
        return true;
    }
}





