package edu.cmu.tetradapp.editor;

import edu.cmu.tetradapp.model.AlgcomparisonModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Displays an editor that lets the user interact with the Comparison class in the edu.cmu.tetrad.algcomparison package.
 * The user can select from a list of simulation methods, a list of algorithms, a list of statistics, and a list of
 * parameters. The list of parameters will be populated based on the simulation method, algorithm, and statistics
 * selected. The user can then run the comparison and view the results in a JTextArea, where the string displayed is
 * written to using the PrintStream "localOut" in the Comparison class. The variables 'localOut' needs to be passed in
 * as a parameter to the constructor of the Comparison class.
 * <p>
 * The list of simulation methods, algorithms, statistics, and parameters are populated by the methods in the Comparison
 * class. The lists of simulation methods, algorithms, statistics, and parameters are obtained using the Reflection API.
 * Simulations are classes that implement the edu.cmu.tetrad.algcomparison.simulation.Simulation interface, algorithms
 * are classes that implement the edu.cmu.tetrad.algcomparison.algorithm.Algorithm interface, and statistics are classes
 * that implement the edu.cmu.tetrad.algcomparison.statistic.Statistic interface. The simulation statistics are obtained
 * by a method as yet unknown. The algorithm statistics displayed may be obtained by calling the getStatistics() method
 * in the Algorithm interface. The parameters are obtained by calling the getParameters() method in the Algorithm
 * interface.
 * <p>
 * The simulations, algorithms, and statistics are each presented in a JList in a JScrollPane. The user can select one
 * simulation method, one or more algorithms, and one or more statistics. The parameters are presented in a parameter
 * panel that will be coded in the ParameterPanel class. The user can select the parameters for each algorithm and will
 * dynamically change based on the simulation method and algorithms selected. The user can then run the comparison by
 * clicking the "Run Comparison" button. The results will be displayed in a JTextArea in a JScrollPane.
 *
 * @author josephramsey 2024-3-29
 */
public class AlgcomparisonEditor extends JPanel {

    /**
     * The AlgcomparisonModel class represents a model used in an algorithm comparison application. It contains methods
     * and properties related to the comparison of algorithms.
     */
    AlgcomparisonModel model;

    /**
     * The constructor for the AlgcomparisonEditor class. The constructor will create a new Comparison object and pass
     * in the PrintStream "localOut" to the constructor. The constructor will then use the Reflection API to populate
     * the lists of simulation methods, algorithms, and statistics, and pass these to the Comparison class through its
     * constructor. The constructor will then create a new JFrame and add the JLists, ParameterPanel, and JTextArea to
     * the JFrame. The constructor will then add an ActionListener to the "Run Comparison" button that will call the
     * runComparison() method in the Comparison class. The constructor will then set the JFrame to be visible.
     */
    public AlgcomparisonEditor(AlgcomparisonModel model) {
        this.model = model;

        JTabbedPane tabbedPane = new JTabbedPane();

        // Create a new Comparison object and pass in the PrintStream "localOut" to the constructor
        // Use the Reflection API to populate the lists of simulation methods, algorithms, and statistics
        // Pass these lists


        tabbedPane.addTab("Selection", getSelectionBox(model));
        tabbedPane.addTab("Parameters", new JPanel());
        tabbedPane.addTab("Results", new JPanel());
        tabbedPane.addTab("Help", new JPanel());
        tabbedPane.setPreferredSize(new Dimension(800, 400));

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        // Create a new JFrame

        // Add the JLists, ParameterPanel, and JTextArea to the JFrame

        // Add an ActionListener to the "Run Comparison" button that will call the runComparison() method in the Comparison class

        // Set the JFrame to be visible


    }

    @NotNull
    private static Box getSelectionBox(AlgcomparisonModel model) {
        java.util.List<String> simulations = model.getSimulationName();
        java.util.List<String> algorithms = model.getAlgorithmsName();
        java.util.List<String> statistics = model.getStatisticsNames();

        JList<String> simulationList = new JList<>(simulations.toArray(new String[0]));
        JList<String> algorithmList = new JList<>(algorithms.toArray(new String[0]));
        JList<String> statisticList = new JList<>(statistics.toArray(new String[0]));

        simulationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        algorithmList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        statisticList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        simulationList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) { // This condition checks that the user has finished changing the selection.
                    JList source = (JList) e.getSource();
                    String selected = (String) source.getSelectedValue();

                    model.setSelectedSimulation(selected);

                    // Update the algorithm list based on the selected simulation
                    java.util.List<String> algorithms = model.getAlgorithmsName();
                    algorithmList.setListData(algorithms.toArray(new String[0]));
                }
            }
        });

        Box horiz1 = Box.createHorizontalBox();

        Box vert1 = Box.createVerticalBox();
        vert1.add(new JLabel("Simulation:"));
        vert1.add(new JScrollPane(simulationList));

        Box vert2 = Box.createVerticalBox();
        vert2.add(new JLabel("Algorithms:"));
        vert2.add(new JScrollPane(algorithmList));

        Box vert3 = Box.createVerticalBox();
        vert3.add(new JLabel("Statistics:"));
        vert3.add(new JScrollPane(statisticList));

        JLabel label = new JLabel("Instructions: Select a simulation, one or more algorithms, and one or more statistics.");
        Box horiz2 = Box.createHorizontalBox();
        horiz2.add(label);
        horiz2.add(Box.createHorizontalGlue());

        Box vert4 = Box.createVerticalBox();
        vert4.add(horiz2);

        horiz1.add(vert1);
        horiz1.add(vert2);
        horiz1.add(vert3);

        vert4.add(horiz1);

        return vert4;
    }


    public static class BufferedListeningByteArrayOutputStream extends ByteArrayOutputStream {
        private final StringBuilder buffer = new StringBuilder();

        public static void main(String[] args) {
            BufferedListeningByteArrayOutputStream baos = new BufferedListeningByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);

            // Example usage
            ps.println("Hello, world!");
            ps.printf("Pi is approximately %.2f%n", Math.PI);

            ps.close();
        }

        @Override
        public void write(int b) {
            super.write(b);
            // Convert single byte to character and add to buffer
            char c = (char) b;
            buffer.append(c);
            if (c == '\n') {
                processBuffer();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            super.write(b, off, len);
            // Convert bytes to string and add to buffer
            String s = new String(b, off, len, StandardCharsets.UTF_8);
            buffer.append(s);
            // Process buffer if newline character is found
            if (s.contains("\n")) {
                processBuffer();
            }
        }

        private void processBuffer() {
            // Process the buffered data (print it in this case)
            System.out.print("Buffered data: " + buffer.toString());
            buffer.setLength(0); // Clear the buffer for next data
        }
    }

}
