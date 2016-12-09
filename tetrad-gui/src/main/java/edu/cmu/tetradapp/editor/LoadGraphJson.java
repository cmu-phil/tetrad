package edu.cmu.tetradapp.editor;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * 
 * Nov 30, 2016 5:54:33 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class LoadGraphJson extends AbstractAction {

    private static final long serialVersionUID = 2580641970762892159L;

    /**
     * The component whose image is to be saved.
     */
    private GraphEditable graphEditable;

    public LoadGraphJson(GraphEditable graphEditable, String title) {
	super(title);

        if (graphEditable == null) {
            throw new NullPointerException("Component must not be null.");
        }

        this.graphEditable = graphEditable;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = getJFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.showOpenDialog((Component) this.graphEditable);

        final File file = chooser.getSelectedFile();

        if (file == null) {
            System.out.println("File was null.");
            return;
        }

        Preferences.userRoot().put("fileSaveLocation", file.getParent());

        Graph graph = GraphUtils.loadGraphJson(file);
        GraphUtils.circleLayout(graph, 200, 200, 150);
        graphEditable.setGraph(graph);
    }

    private static JFileChooser getJFileChooser() {
        JFileChooser chooser = new JFileChooser();
        String sessionSaveLocation =
                Preferences.userRoot().get("fileSaveLocation", "");
        chooser.setCurrentDirectory(new File(sessionSaveLocation));
        chooser.resetChoosableFileFilters();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        return chooser;
    }

}
