package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.List;

/**
 * Interface for a class that represents a scoring of a SEM model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Scorer {
    /**
     * <p>score.</p>
     *
     * @param dag a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a double
     */
    double score(Graph dag);

    /**
     * <p>getCovMatrix.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    ICovarianceMatrix getCovMatrix();

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String toString();

    /**
     * <p>getFml.</p>
     *
     * @return a double
     */
    double getFml();

    /**
     * <p>getBicScore.</p>
     *
     * @return a double
     */
    double getBicScore();

    /**
     * <p>getChiSquare.</p>
     *
     * @return a double
     */
    double getChiSquare();

    /**
     * <p>getPValue.</p>
     *
     * @return a double
     */
    double getPValue();

    /**
     * <p>getDataSet.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    DataSet getDataSet();

    /**
     * <p>getNumFreeParams.</p>
     *
     * @return a int
     */
    int getNumFreeParams();

    /**
     * <p>getDof.</p>
     *
     * @return a int
     */
    int getDof();

    /**
     * <p>getSampleSize.</p>
     *
     * @return a int
     */
    int getSampleSize();

    /**
     * <p>getMeasuredNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getMeasuredNodes();

    /**
     * <p>getSampleCovar.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getSampleCovar();

    /**
     * <p>getEdgeCoef.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getEdgeCoef();

    /**
     * <p>getErrorCovar.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getErrorCovar();

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getVariables();

    /**
     * <p>getEstSem.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    SemIm getEstSem();
}



