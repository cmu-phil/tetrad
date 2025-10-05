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

package edu.cmu.tetrad.search.utils;

import java.util.ArrayList;
import java.util.Stack;

/**
 * <p>Returns row positions for placing rooks for an n x n matrix so the rooks
 * cannot attach each other, with a given boolean[][] specification of where rooks are allowed to be placed. For this
 * spec, spec[i][j] = true iff a rook can be placed there.</p>
 * <p>Had some help from ChatGPT for this.</p>
 *
 * @author josephramsey
 * @author ChatGPT
 * @version $Id: $Id
 */
public class NRooks {

    /**
     * Prevent instantiation.
     */
    private NRooks() {
    }

    /**
     * Solves the N-Rooks problem for the given board or allowable positions.
     *
     * @param allowablePositions A matrix of allowable rook positions, should be true iff the position is allowable.
     * @return A list of row indices for where to place the rooks for each solution.
     */
    public static ArrayList<int[]> nRooks(boolean[][] allowablePositions) {
        for (boolean[] positions : allowablePositions) {
            if (positions.length != allowablePositions[0].length) {
                throw new IllegalArgumentException("Expecting a square matrix.");
            }
        }

        int p = allowablePositions.length;
        boolean[][] board = new boolean[p][p];
        ArrayList<int[]> solutions = new ArrayList<>();

        Stack<Integer> rows = new Stack<>();
        Stack<Integer> cols = new Stack<>();
        int row = 0;
        int col = 0;

        while (row < p) {
            if (col == p) {
                if (rows.isEmpty()) {
                    break;
                }
                row = rows.pop();
                col = cols.pop() + 1;
                board[row][col - 1] = false;
                continue;
            }
            if (allowablePositions[row][col] && isValid(board, row, col)) {
                board[row][col] = true;
                rows.push(row);
                cols.push(col);
                row++;
                col = 0;
                if (row == p) {
                    int[] solution = new int[p];
                    for (int i = 0; i < p; i++) {
                        for (int j = 0; j < p; j++) {
                            if (board[i][j]) {
                                solution[i] = j;
                                break;
                            }
                        }
                    }
                    solutions.add(solution);
                    row = p - 1;
                    col = p;
                }
            } else {
                col++;
            }
        }

        return solutions;
    }

    private static boolean isValid(boolean[][] board, int row, int col) {
        for (int i = 0; i < row; i++) {
            if (board[i][col]) {
                return false;
            }
        }

        return true;
    }
}

