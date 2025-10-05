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
 * Specifies the protocol used in Tetrad for variable names. This protocol should be used throughout Tetrad.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NamingProtocol {

    /**
     * <p>Constructor for NamingProtocol.</p>
     */
    private NamingProtocol() {
    }

    /**
     * <p>isLegalName.</p>
     *
     * @param name Ibid.
     * @return Ibid.
     */
    public static boolean isLegalName(String name) {
        return name.matches("[^0-9]?[^ \t]*");
    }

    /**
     * <p>getProtocolDescription.</p>
     *
     * @return Ibid.
     */
    public static String getProtocolDescription() {
        return "Names must begin with non-numeric characters and may not contain " +
               "\nspaces or tabs.";
    }
}





