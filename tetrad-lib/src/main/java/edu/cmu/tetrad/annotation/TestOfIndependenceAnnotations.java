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

package edu.cmu.tetrad.annotation;

import java.util.List;

/**
 * Sep 26, 2017 1:18:28 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class TestOfIndependenceAnnotations extends AbstractAnnotations<TestOfIndependence> {

    private static final TestOfIndependenceAnnotations INSTANCE = new TestOfIndependenceAnnotations();

    private TestOfIndependenceAnnotations() {
        super("edu.cmu.tetrad.algcomparison.independence", TestOfIndependence.class);
    }

    /**
     * <p>getInstance.</p>
     *
     * @return a {@link edu.cmu.tetrad.annotation.TestOfIndependenceAnnotations} object
     */
    public static TestOfIndependenceAnnotations getInstance() {
        return TestOfIndependenceAnnotations.INSTANCE;
    }

    /**
     * <p>filterOutExperimental.</p>
     *
     * @param list a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public List<AnnotatedClass<TestOfIndependence>> filterOutExperimental(List<AnnotatedClass<TestOfIndependence>> list) {
        return filterOutByAnnotation(list, Experimental.class);
    }

}

