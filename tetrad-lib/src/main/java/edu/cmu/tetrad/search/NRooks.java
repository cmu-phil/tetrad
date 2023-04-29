package edu.cmu.tetrad.search;

import java.util.*;

/**
 * <p>Returns row positions for placing rooks for an n x n matrix so the rooks
 * cannot attach each other, with a given boolean[][] specification of where rooks
 * are allowed to be placed. For this spec, spec[i][j] = true iff a rook can be
 * placed there.</p>
 * <p>Had some help from ChatGPT for this but it messed up one of the methods,
 * so taking some credit.</p>
 *
 * @author josephramsey
 * @author ChatGPT
 */
public class NRooks {

    /**
     * Solves the N-Rooks problem for the given board or allowable positions.
     * @param allowablePositions A matrix of allowable rook positions, should be
     *                       true iff the position is allowable.
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
            if (col >= p) {
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
