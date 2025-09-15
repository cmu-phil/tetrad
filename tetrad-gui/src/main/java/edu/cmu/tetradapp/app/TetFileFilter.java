package edu.cmu.tetradapp.app;

import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * Filters out all but .tet file when loading and saving.
 *
 * @author josephramsey
 */
final class TetFileFilter extends FileFilter {

    /**
     * {@inheritDoc}
     * <p>
     * Accepts a file if its name ends with ".tet".
     */
    public boolean accept(File file) {
        return file.isDirectory() || file.getName().endsWith(".tet");
    }

    /**
     * <p>getDescription.</p>
     *
     * @return the description of this file filter that will be displayed in a JFileChooser.
     */
    public String getDescription() {
        return "Tetrad serialized session workbench (.tet)";
    }
}





