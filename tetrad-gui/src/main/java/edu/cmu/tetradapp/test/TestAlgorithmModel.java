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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GridSearchModel;

import java.util.List;

/**
 * Test the algorithm model.
 */
public class TestAlgorithmModel {

    /**
     * Main method.
     *
     * @param args the arguments.
     */
    public static void main(String[] args) {
        new TestAlgorithmModel().test1();
    }

    private void test1() {

        GridSearchModel gridSearchModel = new GridSearchModel(new Parameters());

        List<String> simulations = gridSearchModel.getSimulationName();
        List<String> algorithms = gridSearchModel.getAlgorithmsName();
        List<String> statistics = gridSearchModel.getStatisticsNames();

        System.out.println("Simulations: ");

        for (int i = 0; i < simulations.size(); i++) {
            String name = simulations.get(i);
            System.out.println((i + 1) + ". " + name);
        }

        System.out.println("Algorithms: ");

        for (int i = 0; i < algorithms.size(); i++) {
            String name = algorithms.get(i);
            System.out.println((i + 1) + ". " + name);
        }

        System.out.println("Statistics: ");

        for (int i = 0; i < statistics.size(); i++) {
            String name = statistics.get(i);
            System.out.println((i + 1) + ". " + name);
        }
    }
}

