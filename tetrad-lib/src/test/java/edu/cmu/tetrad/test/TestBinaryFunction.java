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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.search.BinaryFunction;
import edu.cmu.tetrad.search.BinaryFunctionUtils;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

public class TestBinaryFunction extends TestCase {

    public TestBinaryFunction(String name) {
        super(name);
    }

    public void rtest1() {
        BinaryFunctionUtils utils = new BinaryFunctionUtils(4);

        System.out.println("# validated = ");
        long numValidated = utils.count();
        System.out.println("TOTAL " + numValidated + " OUT OF " + utils.getNumFunctions());
    }

    public void rtest2() {
        BinaryFunctionUtils utils = new BinaryFunctionUtils(4);
        List<BinaryFunction> functions = utils.findNontransitiveTriple();
    }

    public void rtest3() {
        BinaryFunctionUtils utils = new BinaryFunctionUtils(3);
        long num = utils.count2();

        System.out.println("... " + num);
    }

    public void rtest4() {
        BinaryFunctionUtils utils = new BinaryFunctionUtils(4);

        utils.checkTriple(4,
                new boolean[]{true, false, true, false, true, true, false, false,
                        false, false, true, true, false, true, true, true},
                new boolean[]{true, false, true, false, false, false, true, true,
                        true, true, false, false, false, true, false, true},
                new boolean[]{true, true, false, false, false, true, false, true,
                        true, false, true, false, false, false, true, true}
        );
    }

    public void test() {
        // Keep the unit test runner happy.
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestBinaryFunction.class);
    }
}



