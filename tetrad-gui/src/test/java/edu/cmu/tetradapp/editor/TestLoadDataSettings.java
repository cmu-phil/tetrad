/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.editor;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests that the load settings panel can be set from a record of settings, and gives the same record back.
 *
 * @author Claude
 */
public class TestLoadDataSettings {

    private static File dataFile() throws Exception {
        File file = File.createTempFile("study", ".csv");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "id,a,b\n1,2.0,x\n2,3.5,y\n3,1.5,x\n");
        return file;
    }

    private static LoadDataSettings panel(File file) throws Exception {
        LoadDataSettings settings = new LoadDataSettings(List.of(file));
        settings.basicSettings();
        settings.advancedSettings();
        return settings;
    }

    /**
     * Settings that all differ from the panel's usual ones are applied, and described back the same.
     */
    @Test
    public void testTabularSettingsAreAppliedAndDescribedBack() throws Exception {
        File file = dataFile();
        LoadDataSettings panel = panel(file);

        // What the panel opens with for this file: the delimiter is inferred from the file.
        Map<String, String> usual = panel.describeSettings(file);
        assertEquals("Comma", usual.get("Value delimiter"));
        assertEquals("Mixed, max discrete categories 0", usual.get("Data type"));
        assertEquals("None", usual.get("Case column to exclude/ignore"));
        assertEquals("//", usual.get("Comment marker"));
        assertEquals("\"", usual.get("Quote character"));
        assertEquals("*", usual.get("Missing value marker"));

        Map<String, String> recorded = new LinkedHashMap<>();
        recorded.put("File", file.getName());
        recorded.put("First row variable names", "Yes");
        recorded.put("Data file type", "Tabular data");
        recorded.put("Data type", "Mixed, max discrete categories 4");
        recorded.put("Value delimiter", "Semicolon");
        recorded.put("Case column to exclude/ignore", "Column labeled id");
        recorded.put("Comment marker", "%");
        recorded.put("Quote character", "None");
        recorded.put("Missing value marker", "NA");

        List<String> applied = panel.applySettings(recorded);
        assertEquals(8, applied.size());
        assertEquals(recorded, panel.describeSettings(file));
        assertEquals(List.copyOf(recorded.keySet()), List.copyOf(panel.describeSettings(file).keySet()));

        // And back again to a second, different record.
        recorded.put("First row variable names", "No");
        recorded.put("Data type", "Discrete");
        recorded.put("Value delimiter", "Whitespace");
        recorded.put("Case column to exclude/ignore", "First column");
        recorded.put("Comment marker", "#");
        recorded.put("Quote character", "'");
        recorded.put("Missing value marker", "?");
        panel.applySettings(recorded);
        assertEquals(recorded, panel.describeSettings(file));
    }

    /**
     * With no first row of names there is no labeled column to exclude, and the panel disables that choice. A
     * record that asks for both is applied as far as the panel allows.
     */
    @Test
    public void testThePanelsOwnRulesHold() throws Exception {
        File file = dataFile();
        LoadDataSettings panel = panel(file);

        Map<String, String> recorded = new LinkedHashMap<>();
        recorded.put("First row variable names", "No");
        recorded.put("Case column to exclude/ignore", "Column labeled id");
        List<String> applied = panel.applySettings(recorded);

        assertEquals(List.of("First row variable names"), applied);
        assertEquals("No", panel.describeSettings(file).get("First row variable names"));
        assertEquals("None", panel.describeSettings(file).get("Case column to exclude/ignore"));
    }

    @Test
    public void testCovarianceSettings() throws Exception {
        File file = dataFile();
        LoadDataSettings panel = panel(file);

        Map<String, String> recorded = new LinkedHashMap<>();
        recorded.put("File", file.getName());
        recorded.put("Data file type", "Covariance data (lower triangle)");
        recorded.put("Value delimiter", "Tab");
        recorded.put("Comment marker", "//");
        recorded.put("Quote character", "\"");
        panel.applySettings(recorded);

        // A covariance file has no data type, first row, case column or missing value marker to record.
        assertEquals(recorded, panel.describeSettings(file));
    }

    /**
     * Values the panel does not offer, and no record at all, leave the panel as it was.
     */
    @Test
    public void testWhatIsNotUnderstoodChangesNothing() throws Exception {
        File file = dataFile();
        LoadDataSettings panel = panel(file);
        Map<String, String> usual = panel.describeSettings(file);

        assertTrue(panel.applySettings(null).isEmpty());

        Map<String, String> odd = new LinkedHashMap<>();
        odd.put("Data file type", "Hologram");
        odd.put("Data type", "Mixed, max discrete categories many");
        odd.put("Value delimiter", "Ampersand");
        odd.put("Case column to exclude/ignore", "Last column");
        odd.put("Quote character", "`");
        odd.put("No such setting", "x");

        assertTrue(panel.applySettings(odd).isEmpty());
        assertEquals(usual, panel.describeSettings(file));
    }
}
