== '''What is Tetrad CLI''' ==


Tetrad CLI (formerly CCD-Algorithm) is a Java application that provides a command-line interface (CLI) and application programming interface (API) for causal discovery algorithms produced by the Center for Causal Discovery.  The version in this distribution is @ARTIFACT_ID@-@VERSION@.  The application currently includes the algorithm(s):
* FGS (Fast Greedy Search) for continuous data -  is an optimization of the Greedy Equivalence Search algorithm	(GES,	Meek	1995;	Chickering	2003).  The optimizations are described in [http://arxiv.org/ftp/arxiv/papers/1507/1507.07749.pdf  ''Scaling up Greedy Causal Search for Continuous Variables'']

Causal discovery algorithms are a class of search algorithms that explore a space of graphical causal models, i.e., graphical models where directed edges imply causation, for a model (or models) that are a good fit for a dataset.  We suggest that newcomers to the field review [http://www.cs.cmu.edu/afs/cs.cmu.edu/project/learn-43/lib/photoz/.g/scottd/fullbook.pdf ''Causation, Prediction and Search''] by Spirtes, Glymour and Scheines for a primer on the subject.

Causal discovery algorithms allow a user to uncover the causal relationships between variables in a dataset.  These discovered causal relationships may be used further--understanding the underlying the processes of a system (e.g., the metabolic pathways of an organism), hypothesis generation (e.g., variables that best explain an outcome), guide experimentation (e.g., what gene knockout experiments should be performed) or prediction (e.g. parameterization of the causal graph using data and then using it as a classifier).

== '''Changes between versions''' ==
* CCD-Algorithm-4.4 - Initial release with FGS algorithm
* tetrad-5.3.0-20160215 - Improved handling of zero co-variance variables and constant values.
* tetrad-5.3.0-20160318 - Added additional validations and validation switches

== '''How can I use it?''' ==

Java 8 is the only prerequisite to run the software.  Note that by default Java will allocate the smaller of 1/4 system memory or 1GB to the Java virtual machine (JVM).  If you run out of memory (heap memory space) running your analyses you should increase the memory allocated to the JVM with the following switch '-XmxXXG' where XX is the number of gigabytes of ram you allow the JVM to utilize.  For example to allocate 8 gigabytes of ram you would add -Xmx8G immediately after the java command.

'''Run an example output using known data'''

Download the this file, [http://www.ccd.pitt.edu/wp-content/uploads/files/Retention.txt Retention.txt], which is a dataset containing information on college graduation and used in the publication "What Do College Ranking Data Tell Us About Student Retention?" by Drudzel and Glymour, 1994.

<pre>
java -jar @ARTIFACT_ID@-@VERSION@-jar-with-dependencies.jar --algorithm fgs --data Retention.txt  --maxIndegree -1 --output output --verbose
</pre>

The program will output the results of the FGS search procedure as a text file (in this example to output).   The beginning of the file contains the algorithm parameters used in the search.

Inspect the output which should show a graph with the following edges.
<pre>
Graph Edges:
1. fac_salary --- spending_per_stdt
2. spending_per_stdt --> rjct_rate
3. spending_per_stdt --- stdt_tchr_ratio
4. stdt_accept_rate --- fac_salary
5. stdt_clss_stndng --> rjct_rate
6. tst_scores --- fac_salary
7. tst_scores --- grad_rate
8. tst_scores --- spending_per_stdt
9. tst_scores --- stdt_clss_stndng
</pre>

In FGS, "Elapsed getEffectEdges = XXms" refers to the amount of time it took to evaluate all pairs of variables for correlation.  The file then details each step taken in the greedy search procedure i.e., insertion or deletion of edges based on a scoring function (i.e., BIC totalScore difference for each chosen search operation).

The end of the file contains the causal graph from the search procedure.  Here is a key to the edge types
<pre>
A---B There is causal relationship between variable A and B but we cannot determine the direction of the relationship
A-->B There is a causal relationship from variable A to B
</pre>

===Use as an API===

Here is an example of using the Tetrad library which is included in Tetrad-CLI as an API.  Javadocs for the API are here http://cmu-phil.github.io/tetrad/tetrad-lib-apidocs/

<pre>
package edu.cmu.tetrad.cli.search;

import edu.cmu.tetrad.cli.data.ContinuousDataReader;
import edu.cmu.tetrad.cli.data.TabularContinuousDataReader;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.TabularContinuousData;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Author : Jeremy Espino MD
 * Created  2/12/16 9:44 AM
 */
public class FgsApiExample {

    public static void main(String[] args) throws Exception {

        // set path to Retention data
        Path dataFile = Paths.get("tetrad-cli/test/data", "Retention.txt");

        Character delimiter = '\t';

        // perform data validation
        // note: assuming data has unique variable names and does not contain zero covariance pairs
        DataValidation dataValidation = new TabularContinuousData(dataFile, delimiter);
        if (!dataValidation.validate(System.err, true)) {
            System.exit(-128);
        }

        ContinuousDataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData();

        Fgs fgs = new Fgs(new CovarianceMatrixOnTheFly(dataSet));
        fgs.setOut(System.out);
        fgs.setDepth(-1);
        fgs.setIgnoreLinearDependent(false);
        fgs.setPenaltyDiscount(4.0);
        fgs.setNumPatternsToStore(0);  // always set to zero
        fgs.setFaithfulnessAssumed(true);
        fgs.setVerbose(true);

        Graph graph = fgs.search();
        System.out.println();
        System.out.println(graph.toString().trim());
        System.out.flush();

    }
}


</pre>

== '''Command line interface usage''' ==

Tetrad-cli has different switches for different algorithms.

=== Tetrad-cli usage for FGS for continuous data ===
<pre>
usage: java -jar tetrad-cli.jar --algorithm fgs --data <arg> [--delimiter
       <arg>] [--maxIndegree <arg>] [--exclude-variables <arg>] [--faithful]
       [--graphml] [--help] [--ignore-linear-dependence] [--knowledge
       <arg>] [--no-validation-output] [--out <arg>] [--output-prefix
       <arg>] [--penalty-discount <arg>] [--skip-non-zero-variance]
       [--skip-unique-var-name] [--thread <arg>] [--verbose]
    --data <arg>                 Data file.
    --delimiter <arg>            Data delimiter either comma, semicolon,
                                 space, colon, or tab. Default is tab.
    --maxIndegree <arg>                Search maxIndegree. Must be an integer >= -1
                                 (-1 means unlimited). Default is -1.
    --exclude-variables <arg>    A file containing variables to exclude.
    --faithful                   Assume faithfulness.
    --graphml                    Create graphML output.
    --help                       Show help.
    --ignore-linear-dependence   Ignore linear dependence.
    --knowledge <arg>            A file containing prior knowledge.
    --no-validation-output       No validation output files created.
    --out <arg>                  Output directory.
    --output-prefix <arg>        Prefix name of output files.
    --penalty-discount <arg>     Penalty discount. Default is 4.0
    --skip-non-zero-variance     Skip check for zero variance variables.
    --skip-unique-var-name       Skip check for unique variable names.
    --thread <arg>               Number of threads.
    --verbose                    Print additional information.

</pre>