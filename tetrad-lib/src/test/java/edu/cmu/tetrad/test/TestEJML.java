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

package edu.cmu.tetrad.test;

import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TestEJML {
    public static void main(String[] args) {
    }

    private static @NotNull TestEJML.EigResult getTopEigen(double[][] data, double threshold) {
        // Perform eigenvalue decomposition
        SimpleMatrix matrix = new SimpleMatrix(data);
        SimpleEVD<SimpleMatrix> evd = matrix.eig();
        List<Double> realEigen = new ArrayList<>();

        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            realEigen.add(evd.getEigenvalue(i).getReal());
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            if (evd.getEigenvalue(i).getReal() > threshold * evd.getEigenvalue(0).getReal()) {
                indices.add(i);
            }
        }

        // Make a new list of eigenvalues and eigenvectors based on the sorted indices
        List<Double> realEigen2 = new ArrayList<>();
        List<SimpleMatrix> eigenVectors2 = new ArrayList<>();
        for (Integer index : indices) {
            realEigen2.add(realEigen.get(index));
            eigenVectors2.add(evd.getEigenVector(index));
        }

        return new EigResult(realEigen2, eigenVectors2);
    }

    private record EigResult(List<Double> realEigen2, List<SimpleMatrix> eigenVectors2) {
    }


}



