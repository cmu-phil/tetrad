package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Basic unit tests for {@link DataSubsetEditor}.
 * <p>
 * These tests exercise:
 * <ul>
 *   <li>Variable selection via the "Selected" list.</li>
 *   <li>Row selection via the row-spec field.</li>
 *   <li>Use of {@link DataSubsetEditor#createSubset()} with the default sampling mode ({@code USE_AS_IS}).</li>
 * </ul>
 */
public class DataSubsetEditorTest {

    /**
     * Build a small 10x5 continuous dataset with variables X1..X5.
     * Each cell is value = row * 10 + col (0-based row/col indices),
     * so we can easily check correctness.
     */
    private DataSet makeTestDataSet() {
        int numRows = 10;
        int numCols = 5;

        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < numCols; j++) {
            vars.add(new ContinuousVariable("X" + (j + 1)));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(numRows, numCols), vars);

        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                double value = r * 10.0 + c;
                dataSet.setDouble(r, c, value);
            }
        }

        return dataSet;
    }

    /**
     * Helper to get a private field via reflection.
     */
    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object target, String fieldName, Class<T> type) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field '" + fieldName + "'", e);
        }
    }

    /**
     * Test that selecting a subset of variables and specifying a row range
     * produces the expected subset dataset.
     * <p>
     * Scenario:
     * <ul>
     *   <li>Original dataset: 10 rows, 5 variables {@code X1..X5}.</li>
     *   <li>Selected variables: {@code X2} and {@code X4}.</li>
     *   <li>Row spec: {@code "2-4"} (1-based), i.e. rows with indices 1, 2, 3 in 0-based.</li>
     * </ul>
     */
    @Test
    public void testCreateSubsetWithSelectedVarsAndRowRange() {
        DataSet source = makeTestDataSet();
        DataSubsetEditor editor = new DataSubsetEditor(source);

        // Put X2 and X4 into the Selected list using reflection.
        @SuppressWarnings("unchecked")
        DefaultListModel<Node> selectedModel =
                getPrivateField(editor, "selectedModel", DefaultListModel.class);

        Node x2 = source.getVariable("X2");
        Node x4 = source.getVariable("X4");

        assertNotNull("X2 should exist", x2);
        assertNotNull("X4 should exist", x4);

        selectedModel.addElement(x2);
        selectedModel.addElement(x4);

        // Set the row specification "2-4" (1-based indices).
        JTextField rowSpecField = getPrivateField(editor, "rowSpecField", JTextField.class);
        rowSpecField.setText("2-4");

        // Default sampling mode is USE_AS_IS, so we don't touch sampling controls.

        DataSet subset = editor.createSubset();
        assertNotNull("Subset should not be null", subset);

        // Expect 3 rows: 2,3,4 (1-based) => indices 1,2,3.
        assertEquals("Number of rows in subset", 3, subset.getNumRows());
        // Expect 2 variables: X2 and X4 (in that order).
        assertEquals("Number of columns in subset", 2, subset.getNumColumns());
        assertEquals("First variable name", "X2", subset.getVariable(0).getName());
        assertEquals("Second variable name", "X4", subset.getVariable(1).getName());

        // Check that values match the original dataset.
        // Original value is r*10 + c (0-based).
        for (int i = 0; i < subset.getNumRows(); i++) {
            int sourceRow = i + 1; // because we asked for rows 2-4 (1-based)
            double expectedX2 = sourceRow * 10.0 + 1; // column index 1 for X2
            double expectedX4 = sourceRow * 10.0 + 3; // column index 3 for X4

            double actual0 = subset.getDouble(i, 0);
            double actual1 = subset.getDouble(i, 1);

            assertEquals("Row " + i + ", col 0 (X2)", expectedX2, actual0, 0.0);
            assertEquals("Row " + i + ", col 1 (X4)", expectedX4, actual1, 0.0);
        }
    }

    /**
     * Test that leaving the row-spec blank yields all rows of the source,
     * when no variables are explicitly selected (so it defaults to all vars).
     */
    @Test
    public void testCreateSubsetWithBlankRowSpecUsesAllRows() {
        DataSet source = makeTestDataSet();
        DataSubsetEditor editor = new DataSubsetEditor(source);

        // Row spec field is blank by default; we don't touch it.
        // We also don't put anything into selectedModel, so the editor
        // should default to "all variables".

        DataSet subset = editor.createSubset();
        assertNotNull("Subset should not be null", subset);

        assertEquals("All rows should be present",
                source.getNumRows(), subset.getNumRows());
        assertEquals("All variables should be present",
                source.getNumColumns(), subset.getNumColumns());

        // Check the values match exactly.
        for (int r = 0; r < source.getNumRows(); r++) {
            for (int c = 0; c < source.getNumColumns(); c++) {
                double expected = source.getDouble(r, c);
                double actual = subset.getDouble(r, c);
                assertEquals("Value mismatch at (" + r + "," + c + ")", expected, actual, 0.0);
            }
        }
    }
}