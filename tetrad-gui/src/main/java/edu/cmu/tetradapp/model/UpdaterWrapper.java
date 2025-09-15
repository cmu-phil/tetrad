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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.ManipulatingBayesUpdater;
import edu.cmu.tetrad.util.Parameters;

/**
 * Stuff the GUI needs to know. Wrapped so that the GUI can access the params.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface UpdaterWrapper {

    /**
     * Makes the params object accessible to the GUI.
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    Parameters getParams();

    /**
     * <p>getBayesUpdater.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.ManipulatingBayesUpdater} object
     */
    ManipulatingBayesUpdater getBayesUpdater();
}




