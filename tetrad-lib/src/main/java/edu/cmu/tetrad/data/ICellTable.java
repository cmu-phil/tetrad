package edu.cmu.tetrad.data;

public interface ICellTable {
    /**
     * Returns the dimension of the specified variable in the cell table.
     *
     * @param varIndex the index of the variable.
     * @return the dimension of the variable.
     */
    int getDimension(int varIndex);

    /**
     * Calculates the marginal sum for the cell table based on the given coordinates.
     *
     * @param coords an array of coordinates where -1 indicates the variables over which marginal sums should be taken.
     * @return the marginal sum specified.
     */
    int calcMargin(int[] coords);

    /**
     * Calculates the marginal sum for the cell table based on the given coordinates and margin variables.
     *
     * @param coords the array of coordinates where -1 indicates the variables over which marginal sums should be taken.
     * @param marginVars the array of indices of the margin variables.
     * @return the marginal sum specified.
     */
    int calcMargin(int[] coords, int[] marginVars);

    /**
     * Returns the value of the cell specified by the given coordinates.
     *
     * @param coords the coordinates of the cell.
     * @return the value of the cell.
     */
    int getValue(int[] coords);
}
