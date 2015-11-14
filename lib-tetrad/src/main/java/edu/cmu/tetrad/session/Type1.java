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

package edu.cmu.tetrad.session;

import edu.cmu.tetrad.util.TetradSerializableExcluded;


/**
 * A sample class to be wrapped in a SessionNode as a model.
 */
public class Type1 implements SessionModel, TetradSerializableExcluded {
    static final long serialVersionUID = 23L;

    /**
     * A sample constructor that takes Model 2 and Type3 as parent. The session
     * node wrapping this should allow parent session nodes to be added that
     * wrap either Type2 or Type3 and when parents of both types are added it
     * should allow a model of type Type1 to be created. SessionNodes wrapping
     * models of other types should not be addable as parents.
     */
    public Type1(Type2 model1, Type3 model2) {
    }

    public static Type1 serializableInstance() {
        return new Type1(Type2.serializableInstance(),
                Type3.serializableInstance());
    }

    public boolean equals(Object o) {
        return (o instanceof Type1);
    }

    /**
     * Sets the name of the session model.
     */
    public void setName(String name) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * @return the name of the session model.
     */
    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}





