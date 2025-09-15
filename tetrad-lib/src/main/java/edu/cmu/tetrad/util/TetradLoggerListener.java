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

package edu.cmu.tetrad.util;

/**
 * A listener for tetrad's logger.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface TetradLoggerListener {


    /**
     * Invoked whenever a logger configuration is set on the <code>TetradLogger</code> and the logger is active (i.e.,
     * logging isn't turned off etc).
     *
     * @param evt a {@link edu.cmu.tetrad.util.TetradLoggerEvent} object
     */
    void configurationActivated(TetradLoggerEvent evt);


    /**
     * Invoked whenever a previously set logger config is resert or set to null and the logger is active (i.e., logging
     * isn't turned off etc).
     *
     * @param evt a {@link edu.cmu.tetrad.util.TetradLoggerEvent} object
     */
    void configurationDeactivated(TetradLoggerEvent evt);


}




