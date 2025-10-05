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

package edu.cmu.tetradapp.session;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableExcluded;


/**
 * A sample class to be wrapped in a SessionNode as a model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Type1 implements SessionModel, TetradSerializableExcluded {
    private static final long serialVersionUID = 23L;

    /**
     * A sample constructor that takes Model 2 and Type3 as parent. The session node wrapping this should allow parent
     * session nodes to be added that wrap either Type2 or Type3 and when parents of both types are added it should
     * allow a model of type Type1 to be created. SessionNodes wrapping models of other types should not be addable as
     * parents.
     *
     * @param model1     a {@link Type2} object
     * @param model2     a {@link Type3} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Type1(Type2 model1, Type3 model2, Parameters parameters) {
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link Type1} object
     */
    public static Type1 serializableInstance() {
        return new Type1(Type2.serializableInstance(),
                Type3.serializableInstance(), new Parameters());
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        return (o instanceof Type1);
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






