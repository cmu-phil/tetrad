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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Matrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * Tests data loaders against sample files.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestHistogram extends TestCase {
    /**
     * <p>Constructor for TestHistogram.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public TestHistogram(String name) {
        super(name);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to
     * <p>
     * the test runner.
     *
     * @return a {@link junit.framework.Test} object
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestHistogram.class);
    }

    /**
     * <p>test1.</p>
     */
    public void test1() {
        Matrix dataMatrix = new Matrix(10, 2);

        dataMatrix.set(0, 0, 0);
        dataMatrix.set(1, 0, 0);
        dataMatrix.set(2, 0, 0);
        dataMatrix.set(3, 0, 0);
        dataMatrix.set(4, 0, 0);
        dataMatrix.set(5, 0, 1);
        dataMatrix.set(6, 0, 1);
        dataMatrix.set(7, 0, 1);
        dataMatrix.set(8, 0, 1);
        dataMatrix.set(9, 0, 1);

        dataMatrix.set(0, 1, 0);
        dataMatrix.set(1, 1, 1);
        dataMatrix.set(2, 1, 1);
        dataMatrix.set(3, 1, 1);
        dataMatrix.set(4, 1, 1);
        dataMatrix.set(5, 1, 0);
        dataMatrix.set(6, 1, 0);
        dataMatrix.set(7, 1, 0);
        dataMatrix.set(8, 1, 0);
        dataMatrix.set(9, 1, 1);
    }
}



