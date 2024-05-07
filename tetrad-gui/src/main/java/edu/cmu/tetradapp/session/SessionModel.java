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

package edu.cmu.tetradapp.session;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface implemented by all session models in Tetrad. Note that every class C that implements SessionModel MUST be
 * accompanied by a unit test that ensures (1) that C can serialized out and loaded back in, passing a roundtrip test
 * (C.equals(save(load(C))), and (2) that sample models from every published version of Tetrad in which C has changed
 * will load correctly.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SessionModel extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getName.</p>
     *
     * @return the name of the session model.
     */
    String getName();

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
     */
    void setName(String name);


}





