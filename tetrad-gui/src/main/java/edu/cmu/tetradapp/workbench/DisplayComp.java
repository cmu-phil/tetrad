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

package edu.cmu.tetradapp.workbench;

/**
 * Created by IntelliJ IDEA. User: jdramsey Date: Apr 4, 2006 Time: 4:39:38 PM To change this template use File |
 * Settings | File Templates.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface DisplayComp {
    /**
     * <p>setName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    void setName(String name);

    /**
     * <p>contains.</p>
     *
     * @param x a int
     * @param y a int
     * @return a boolean
     */
    boolean contains(int x, int y);

    /**
     * <p>setSelected.</p>
     *
     * @param selected a boolean
     */
    void setSelected(boolean selected);
}




