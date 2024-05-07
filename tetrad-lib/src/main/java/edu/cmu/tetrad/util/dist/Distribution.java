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

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface for a statistical distribution from which random values can be drawn. Methods are provided for
 * setting/getting parameters in the interface. A single random number generator is used throughout Tetrad to ensure
 * randomness.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@SuppressWarnings("UnusedDeclaration")
public interface Distribution extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Returns the number of parameters in the distribution.
     *
     * @return the number of parameters.
     */
    int getNumParameters();

    /**
     * Returns the name of the distribution.
     *
     * @return the name.
     */
    String getName();

    /**
     * Sets the index'th parameter to the given value.
     *
     * @param index Ibid. Must be &gt;= 0 and &lt; # parameters.
     * @param value Ibid.
     */
    void setParameter(int index, double value);

    /**
     * Returns the index'th parameter.
     *
     * @param index Ibid. Muist be &lt;= 0 and &lt; # parameters.
     * @return The Ibid.
     */
    double getParameter(int index);

    /**
     * The name of the index'th parameter, for display purposes.
     *
     * @param index Ibid. Must be &gt;= 0 and &lt; # parameters.
     * @return Ibid.
     */
    String getParameterName(int index);

    /**
     * Returns the next random number from the distribution.
     *
     * @return Ibid.
     */
    double nextRandom();
}





