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

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableExcluded;


/**
 * A sample class to be wrapped in a SessionNode as a model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Type10 implements SessionModel, TetradSerializableExcluded {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for Type10.</p>
     *
     * @param model1     a {@link Type6} object
     * @param model2     a {@link Type6} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Type10(Type6 model1, Type6 model2, Parameters parameters) {
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link Type10} object
     */
    public static Type10 serializableInstance() {
        return new Type10(Type6.serializableInstance(),
                Type6.serializableInstance(), new Parameters());
    }

    /**
     * <p>getName.</p>
     *
     * @return the name of the session model.
     */
    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the session model.
     */
    public void setName(String name) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}





