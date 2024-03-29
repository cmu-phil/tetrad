package edu.cmu.tetradapp.editor;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.util.Set;

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
public class AlgcomparisonEditor {
    /**
     * The constructor for the AlgcomparisonEditor class. The constructor will create a new Comparison object and pass
     * in the PrintStream "localOut" to the constructor. The constructor will then use the Reflection API to populate
     * the lists of simulation methods, algorithms, and statistics, and pass these to the Comparison class through its
     * constructor. The constructor will then create a new JFrame and add the JLists, ParameterPanel, and JTextArea to
     * the JFrame. The constructor will then add an ActionListener to the "Run Comparison" button that will call the
     * runComparison() method in the Comparison class. The constructor will then set the JFrame to be visible.
     */
    public AlgcomparisonEditor() {
        // Create a new Comparison object and pass in the PrintStream "localOut" to the constructor
        // Use the Reflection API to populate the lists of simulation methods, algorithms, and statistics
        // Pass these lists



        // Create a new JFrame

        // Add the JLists, ParameterPanel, and JTextArea to the JFrame

        // Add an ActionListener to the "Run Comparison" button that will call the runComparison() method in the Comparison class

        // Set the JFrame to be visible


    }


    /**
     * Finds and returns a set of classes that implement a given interface within a specified package.
     *
     * @param packageName The name of the package to search in.
     * @param interfaceClazz The interface class to find implementations of.
     * @return A set of classes that implement the specified interface.
     */
    public static <T> Set<Class<? extends T>> findImplementations(String packageName, Class<T> interfaceClazz) {
        Reflections reflections = new Reflections(packageName, Scanners.SubTypes);
        return reflections.getSubTypesOf(interfaceClazz);
    }
}
