package edu.cmu.tetrad.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllformedLocaleException;
import java.util.List;

/**
 * <p>Returns row positions for placing rooks for an n x n matrix with a given
 * boolean[][] specification of where rooks are allowed to be placed. For
 * this spec, spec[i][j] = false iff a rook can be placed there.</p>
 * <p>Had some help from ChatGPT for this but it messed up one of the methods,
 * so taking some credit.</p>
 *
 * @author josephramsey
 * @author ChatGPT
 */
public class NRooks {

    /**
     * Solves the N-Rooks problem for the given allowable board.
     * @param allowablePositions A matrix of allowable rook positions, should be
     *                       true iff the position is allowable.
     * @return A list of row indices for where to place the rooks for each solution.
     */
    public static ArrayList<int[]> nRooks(boolean[][] allowablePositions) {
        for (boolean[] positions : allowablePositions) {
            if (positions.length != allowablePositions[0].length) {
                throw new IllformedLocaleException("Expecting a square matrix.");
            }
        }

        int p = allowablePositions.length;
        boolean[][] _allowable = new boolean[p][p];
        ArrayList<int[]> solutions = new ArrayList<>();
        dfs(_allowable, allowablePositions, 0, solutions);
        return solutions;
    }

    /**
     * Prints the discovered N Rooks solutions.
     * @param solutions The solutions.
     */
    public static void printSolutions(List<int[]> solutions) {
        System.out.println("Number of solutions: " + solutions.size());
        for (int i = 0; i < solutions.size(); i++) {
            int[] solution = solutions.get(i);
            System.out.println((i + 1) + ". " + Arrays.toString(solution));
        }
    }

    private static void dfs(boolean[][] board, boolean[][] allowablePositions, int row, ArrayList<int[]> solutions) {
        if (row == board.length) {

            // Base case: all rooks have been placed
            int[] solution = new int[board.length];
            for (int i = 0; i < board.length; i++) {
                for (int j = 0; j < board.length; j++) {
                    if (board[i][j]) {
                        solution[i] = j;
                        break;
                    }
                }
            }
            solutions.add(solution);
            return;
        }

        for (int col = 0; col < board.length; col++) {
            if (isValid(board, row, col)) {
                if (!allowablePositions[row][col]) continue;
                board[row][col] = true;
                dfs(board, allowablePositions, row+1, solutions);
                board[row][col] = false;
            }
        }
    }

    private static boolean isValid(boolean[][] board, int row, int col) {

        // check if the current position is valid
        for (int i = 0; i < row; i++) {
            if (board[i][col]) {
                for (boolean[] booleans : board) {
                    if (booleans[col]) return false;
                }
            }
        }

        return true;
    }
}
