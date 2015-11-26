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

package edu.cmu.tetrad.util;

/**
 * Specifies the protocol used in Tetrad for variable names. This protocol
 * should be used throughout Tetrad.
 *
 * @author Joseph Ramsey
 */
public class NamingProtocol {

    /**
     * @return true iff the given name is a legal variable name for Tetrad.
     *
     * @param name Ibid.
     * @return Ibid.
     */
    public static boolean isLegalName(String name) {
        return name.matches("[^0-9]?[^ \t]*");
    }

    /**
     * @return a description of the protocol being used in Tetrad that can be
     * displayed to the user when they enter illegal variable names.
     *
     * @return Ibid.
     */
    public static String getProtocolDescription() {
        return "Names must begin with non-numeric characters and may not contain " +
                "\nspaces or tabs.";
    }
}




