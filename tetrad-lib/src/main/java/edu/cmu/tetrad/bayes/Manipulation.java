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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;

/**
 * Stores information for a variable source about evidence we have for each variable as
 * well as whether each variable has been manipulated.
 *
 * @author Joseph Ramsey
 */
public final class Manipulation implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private VariableSource variableSource;

    /**
     * An array indicating whether each variable in turn is manipulated.
     *
     * @serial Cannot be null.
     */
    private boolean[] manipulated;

    //===========================CONSTRUCTORS============================//

    /**
     * Constructs a container for evidence for the given Bayes IM.
     */
    public Manipulation(VariableSource variableSource) {
        if (variableSource == null) {
            throw new NullPointerException();
        }

        this.variableSource = variableSource;
        this.manipulated = new boolean[getNumNodes()];
    }

    /**
     * Copy constructor.
     */
    public Manipulation(Manipulation manipulation) {
        if (manipulation == null) {
            throw new NullPointerException();
        }

        if (manipulation.getVariableSource() == null) {
            throw new NullPointerException("Please reconstruct this part of the " +
                    "session; there was an error.");
        }

        this.variableSource = manipulation.getVariableSource();

        this.manipulated = new boolean[getNumNodes()];

        for (int i = 0; i < manipulated.length; i++) {
            this.manipulated[i] = manipulation.isManipulated(i);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Manipulation serializableInstance() {
        return new Manipulation(MlBayesIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    public void setManipulated(int nodeIndex, boolean manipulated) {
        this.manipulated[nodeIndex] = manipulated;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("\nManipulation:");
        buf.append("\n");

        for (int i = 0; i < getNumNodes(); i++) {
            buf.append(isManipulated(i) ? "(Man)" : "     ");
            buf.append("\t");
        }

        return buf.toString();
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof Manipulation)) {
            return false;
        }

        Manipulation evidence = (Manipulation) o;

        if (!(getVariableSource() == evidence.getVariableSource())) {
            return false;
        }

        for (int i = 0; i < manipulated.length; i++) {
            if (manipulated[i] != evidence.manipulated[i]) {
                return false;
            }
        }

        return true;
    }

    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + getVariableSource().hashCode();
        hashCode = 19 * hashCode + Arrays.hashCode(manipulated);
        return hashCode;
    }

    //===========================PRIVATE METHODS=========================//

    private int getNumNodes() {
        return getVariableSource().getVariables().size();
    }

    public boolean isManipulated(int nodeIndex) {
        return manipulated[nodeIndex];
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

//        if (variableSource == null) {
//            throw new NullPointerException("Variable source must not be null.");
//        }
    }

    private VariableSource getVariableSource() {
        return variableSource;
    }
}






