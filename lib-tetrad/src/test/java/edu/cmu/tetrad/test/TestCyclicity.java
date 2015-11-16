/////////////////////////////////////////////////////////////////////////////////
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

import jdepend.framework.JDepend;
import jdepend.framework.JavaPackage;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Checks for package cycles.
 */
public class TestCyclicity extends TestCase {
    private JDepend jdepend;

    public TestCyclicity(String name) {
        super(name);
    }

    public void setUp() {
        jdepend = new JDepend();

        try {
            jdepend.addDirectory(new File("target/classes/edu/cmu/tetrad").getAbsolutePath());
//            jdepend.addDirectory(new File("../../../tetrad/target/classes/edu/cmu/tetradapp").getAbsolutePath());
        }
        catch (IOException e) {
            fail(e.getMessage());
        }
    }

    public void tearDown() {
        jdepend = null;
    }

    public void testBlank() {

    }

    /**
     * Tests that a package dependency cycle does not exist for any of the
     * analyzed packages.
     *
     * NOTE: THIS NEEDS TO BE TURNED OFF UNTIL THE OLD CALCULATORWRAPPER CAN BE
     * REMOVED FROM THE CODE (edu.cmu.tetrad.model.calculator.CalculatorWrapper).
     * UNTIL THEN IT HAS TO BE RUN MANUALLY BY DELETING THE OLD CALCULATORWRAPPER
     * RUNNING IT AND THEN RESTORING THE OLD CALCULATORWRAPPER. -Joe 2009/6/8
     */
    public void testAllPackagesCycle() {
        Collection packages = jdepend.analyze();

        for (Object aPackage : packages) {
            JavaPackage p = (JavaPackage) aPackage;

            if (p.containsCycle()) {
                System.out.println("\n***Package: " + p.getName() + ".");
                System.out.println();
                System.out.println(
                        "This package participates in a package cycle. In the following " +
                                "\nlist, for each i, some class in package i depends on some " +
                                "\nclass in package i + 1. Please find the cycle and remove it.");

                List l = new LinkedList();
                p.collectCycle(l);
                System.out.println();

                for (int j = 0; j < l.size(); j++) {
                    JavaPackage pack = (JavaPackage) l.get(j);
                    System.out.println((j + 1) + ".\t" + pack.getName());
                }

                System.out.println();
            }
        }

        if (jdepend.containsCycles()) {
            fail("Package cycle(s) found!");
        }
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(TestCyclicity.class);
    }
}




