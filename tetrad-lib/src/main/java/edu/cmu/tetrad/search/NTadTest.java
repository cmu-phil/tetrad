package edu.cmu.tetrad.search;

import java.util.List;

public interface NTadTest {
    double tetrad(int[][] tet);

    double tetrads(int[][]... tets);

    double tetrads(List<int[][]> tets);
}
