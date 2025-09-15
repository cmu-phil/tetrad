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

import java.util.EventObject;

/**
 * An event associated with the <code>TetradLoggerListener</code>.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class TetradLoggerEvent extends EventObject {

    /**
     * The config associated with the event, may be null.
     */
    private final TetradLoggerConfig config;


    /**
     * Constructs the event given the source and the <code>TetradLoggerConfig</code> associated with the event if there
     * is one
     *
     * @param source - The source
     * @param config - The config, may be null.
     */
    public TetradLoggerEvent(Object source, TetradLoggerConfig config) {
        super(source);
        this.config = config;
    }


    /**
     * <p>getTetradLoggerConfig.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     */
    public TetradLoggerConfig getTetradLoggerConfig() {
        return this.config;
    }


}




