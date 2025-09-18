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

    package edu.cmu.tetrad.search;

    import edu.cmu.tetrad.graph.Graph;
    import edu.cmu.tetrad.search.test.IndependenceTest;

    /**
     * Gives an interface for a search method that searches and returns a graph.
     *
     * @author josephramsey
     * @version $Id: $Id
     */
    public interface IGraphSearch {

        /**
         * Runs the search and returns a graph.
         *
         * @return The discovered graph.
         * @throws InterruptedException if any.
         */
        Graph search() throws InterruptedException;

        /**
         * Gets the test used by the search.
         *
         * @return The test used by the search.
         * @throws IllegalStateException if the search does not support getting the test.
         */
        default IndependenceTest getTest() {
            throw new IllegalStateException("This algorithm does not support getting the test.");
        }

        /**
         * Sets the test to be used by the search. The list of variables of the new proposed test must be equal to the list
         * of variables of the existing test.
         *
         * @param test The test to be used by the search.
         * @throws IllegalArgumentException if the list of variables of the new proposed test is not equal to the list of
         *                                  variables of the existing test.
         * @throws IllegalStateException    if the search does not support setting the test.
         */
        default void setTest(IndependenceTest test) {
            throw new IllegalStateException("This algorithm does not support setting the test.");
        }
    }




