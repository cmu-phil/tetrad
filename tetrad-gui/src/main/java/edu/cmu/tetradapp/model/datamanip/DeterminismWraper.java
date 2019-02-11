/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 *
 * Feb 11, 2019 4:19:04 PM
 *
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class DeterminismWraper extends DataWrapper {
    private static final long serialVersionUID = -5573234622763285581L;

    public DeterminismWraper(DataWrapper dataWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }

    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return 
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //=============================== Private Methods =========================//


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
    }
}
