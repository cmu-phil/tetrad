///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.util.LinkedList;
import java.util.Vector;

import static java.lang.Math.abs;

/**
 * Useful references: "Factor Analysis of Data Matrices" - Paul Horst (1965) This work has good specifications and
 * explanations of factor analysis algorithm and methods of communality estimation.
 * <p>
 * "Applied Factor Analysis" - R.J. Rummel (1970) This book is a good companion to the book listed above.  While it
 * doesn't specify any actual algorithm, it has a great introduction to the subject that gives the reader a good
 * appreciation of the philosophy and the mathematics behind factor analysis.
 *
 * @author Mike Freenor
 */
public class FactorAnalysis {
    private CovarianceMatrix covariance;

    // method-specific fields that get used
    private LinkedList<Double> dValues;
    private LinkedList<TetradMatrix> factorLoadingVectors;
    private double threshold = 0.001;
    private int numFactors = 2;
    private TetradMatrix residual;

    public FactorAnalysis(ICovarianceMatrix covarianceMatrix) {
        this.covariance = new CovarianceMatrix( covarianceMatrix);
    }

    public FactorAnalysis(DataSet dataSet) {
        this.covariance = new CovarianceMatrix(dataSet);
    }

    //================= COMMUNALITY ESTIMATES =================//

    /**
     * Successive method with residual matrix.
     * <p>
     * This algorithm makes use of a helper algorithm.  Together they solve for an unrotated
     * factor loading matrix.
     * <p>
     * This method calls upon its helper to find column vectors, with which it constructs its
     * factor loading matrix.  Upon receiving each successive column vector from its helper
     * method, it makes sure that we want to keep this vector instead of discarding it.  After
     * keeping a vector, a residual matrix is calculated, upon which solving for the next column
     * vector is directly dependent.
     * <p>
     * We stop looking for new vectors either when we've accounted for close to all of the variance in
     * the original correlation matrix, or when the "d scalar" for a new vector is less than 1 (the
     * d-scalar is the corresponding diagonal for the factor loading matrix -- thus, when it's less
     * than 1, the vector we've solved for barely accounts for any more variance).  This means we've
     * already "pulled out" all of the variance we can from the residual matrix, and we should stop
     * as further factors don't explain much more (and serve to complicate the model).
     * <p>
     * PSEUDO-CODE:
     * <p>
     * 0th Residual Matrix = Original Correlation Matrix
     * Ask helper for the 1st factor (first column vector in our factor loading vector)
     * Add 1st factor's d-scalar (for i'th factor, call its d-scalar the i'th d-scalar) to a list of d-scalars.
     * <p>
     * While the ratio of the sum of d-scalars to the trace of the original correlation matrix is less than .99
     * (in other words, while we haven't accounted for practically all of the variance):
     * <p>
     * i'th residual matrix = (i - 1)'th residual matrix SUBTRACT the major product moment of (i - 1)'th factor loading vector
     * Ask helper for i'th factor
     * If i'th factor's d-value is less than 1, throw it out and end loop.
     * Otherwise, add it to the factor loading matrix and continue loop.
     * <p>
     * END PSEUDO-CODE
     * <p>
     * At the end of the method, the list of column vectors is actually assembled into a TetradMatrix.
     */
    public TetradMatrix successiveResidual() {
        this.factorLoadingVectors = new LinkedList<>();
        this.dValues = new LinkedList<>();

        TetradMatrix residual = covariance.getMatrix().copy();
        TetradMatrix unitVector = new TetradMatrix(residual.rows(), 1);

        for (int i = 0; i < unitVector.rows(); i++) {
            unitVector.set(i, 0, 1);
        }

        for (int i = 0; i < getNumFactors(); i++) {
            boolean found = successiveResidualHelper(residual, unitVector);

            if (!found) break;

            TetradMatrix f = factorLoadingVectors.getLast();
            residual = residual.minus(f.times(f.transpose()));
        }

        factorLoadingVectors.removeFirst();

        TetradMatrix result = new TetradMatrix(residual.rows(), factorLoadingVectors.size());

        for (int i = 0; i < result.rows(); i++) {
            for (int j = 0; j < result.columns(); j++) {
                result.set(i, j, factorLoadingVectors.get(j).get(i, 0));
            }
        }

        this.residual = residual;

        return result;
    }

    public TetradMatrix successiveFactorVarimax(TetradMatrix factorLoadingMatrix) {
        if (factorLoadingMatrix.columns() == 1)
            return factorLoadingMatrix;

        Vector<TetradMatrix> residuals = new Vector<>();
        Vector<TetradMatrix> rotatedFactorVectors = new Vector<>();

        TetradMatrix normalizedFactorLoadings = normalizeRows(factorLoadingMatrix);
        residuals.add(normalizedFactorLoadings);

        TetradMatrix unitColumn = new TetradMatrix(factorLoadingMatrix.rows(), 1);

        for (int i = 0; i < factorLoadingMatrix.rows(); i++) {
            unitColumn.set(i, 0, 1);
        }

        TetradMatrix r = residuals.lastElement();

        TetradMatrix sumCols = r.transpose().times(unitColumn);
        TetradMatrix wVector = sumCols.scalarMult(1.0 / Math.sqrt(unitColumn.transpose().times(r).times(sumCols).get(0, 0)));
        TetradMatrix vVector = r.times(wVector);

        for (int k = 0; k < normalizedFactorLoadings.columns(); k++) {

            //time to find the minimum value in the v vector
            int lIndex = 0;
            double minValue = Double.POSITIVE_INFINITY;

            for (int i = 0; i < vVector.rows(); i++) {
                if (vVector.get(i, 0) < minValue) {
                    minValue = vVector.get(i, 0);
                    lIndex = i;
                }
            }

            Vector<TetradMatrix> hVectors = new Vector<>();
            Vector<TetradMatrix> bVectors = new Vector<>();
            double alpha1 = Double.NaN;

            r = residuals.lastElement();

            hVectors.add(new TetradMatrix(r.columns(), 1));
            TetradVector rowFromFactorLoading = r.getRow(lIndex);

            for (int j = 0; j < hVectors.lastElement().rows(); j++) {
                hVectors.lastElement().set(j, 0, rowFromFactorLoading.get(j));
            }

            for (int i = 0; i < 200; i++) {
                TetradMatrix bVector = r.times(hVectors.get(i));
                double averageSumSquaresBVector = unitColumn.transpose().times(matrixExp(bVector, 2))
                        .scalarMult(1.0 / (double) bVector.rows()).get(0, 0);

                TetradMatrix betaVector = matrixExp(bVector, 3).minus(bVector.scalarMult(averageSumSquaresBVector));
                TetradMatrix uVector = r.transpose().times(betaVector);

                double alpha2 = (Math.sqrt(uVector.transpose().times(uVector).get(0, 0)));
                bVectors.add(bVector);

                hVectors.add(uVector.scalarMult(1.0 / alpha2));

                if (!Double.isNaN(alpha1) && abs((alpha2 - alpha1)) < getThreshold()) {
                    break;
                }

                alpha1 = alpha2;
            }

            TetradMatrix b = bVectors.lastElement();

            rotatedFactorVectors.add(b);
            residuals.add(r.minus(b.times(hVectors.lastElement().transpose())));
        }

        TetradMatrix result = factorLoadingMatrix.like();

        if (!rotatedFactorVectors.isEmpty()) {
            for (int i = 0; i < rotatedFactorVectors.get(0).rows(); i++) {
                for (int j = 0; j < rotatedFactorVectors.size(); j++) {
                    result.set(i, j, rotatedFactorVectors.get(j).get(i, 0));
                }
            }
        }

        return result;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    // ------------------Private methods-------------------//

    /**
     * Helper method for the basic structure successive factor method above.
     * Takes a residual matrix and a approximation vector, and finds both
     * the factor loading vector and the "d scalar" which is used to determine
     * the amount of total variance accounted for so far.
     * <p>
     * The helper takes, to begin with, the unit vector as its approximation to the
     * factor column vector.  With each iteration, it approximates a bit closer --
     * the d-scalar for each successive step eventually converges to a value (provably).
     * <p>
     * Thus, the ratio between the last iteration's d-scalar and this iteration's d-scalar
     * should approach 1.  When this ratio gets sufficiently close to 1, the algorithm halts
     * and returns its getModel approximation.
     * <p>
     * Important to note: the residual matrix stays fixed for this entire algorithm.
     * <p>
     * PSEUDO-CODE:
     * <p>
     * Calculate the 0'th d-scalar, which is done with the following few calculations:
     * 0'th U Vector = residual matrix * approximation vector (this is just the unit vector for the 0'th)
     * 0'th L Scalar = transpose(approximation vector) * U Vector
     * 0'th d-scalar = square root(L Scalar)
     * 0'th approximation to factor loading (A Vector) = 0'th U Vector / 0'th d-scalar
     * <p>
     * <p>
     * While the ratio of the new d-scalar to the old is not sufficiently close to 1
     * (or if we haven't approximated 100 times yet, a failsafe):
     * <p>
     * i'th U Vector = residual matrix * (i - 1)'th factor loading
     * i'th L Scalar = transpose((i - 1)'th factor loading) * i'th U Vector
     * i'th D Scalar = square root(i'th L Scalar)
     * i'th factor loading = i'th U Vector / i'th D Scalar
     * <p>
     * Return the final i'th factor loading as our best approximation.
     */
    private boolean successiveResidualHelper(TetradMatrix residual, TetradMatrix approximationVector) {
        TetradMatrix l0 = approximationVector.transpose().times(residual).times(approximationVector);

        if (l0.get(0, 0) < 0) {
            return false;
        }

        double d = Math.sqrt(l0.get(0, 0));
        TetradMatrix f = residual.times(approximationVector).scalarMult(1.0 / d);

        for (int i = 0; i < 100; i++) {
            TetradMatrix ui = residual.times(f);
            TetradMatrix li = f.transpose().times(ui);
            double di = Math.sqrt(li.get(0, 0));

            if (abs((d - di)) <= getThreshold()) {
                break;
            }

            d = di;
            f = ui.scalarMult(1.0 / d);
        }

        this.dValues.add(d);
        this.factorLoadingVectors.add(f);
        return true;
    }


    //designed for normalizing a vector.
    //as usual, vectors are treated as matrices to simplify operations elsewhere
    private static TetradMatrix normalizeRows(TetradMatrix matrix) {
        Vector<TetradMatrix> normalizedRows = new Vector<>();
        for (int i = 0; i < matrix.rows(); i++) {
            TetradVector vector = matrix.getRow(i);
            TetradMatrix colVector = new TetradMatrix(matrix.columns(), 1);
            for (int j = 0; j < matrix.columns(); j++)
                colVector.set(j, 0, vector.get(j));

            normalizedRows.add(normalizeVector(colVector));
        }

        TetradMatrix result = new TetradMatrix(matrix.rows(), matrix.columns());
        for (int i = 0; i < matrix.rows(); i++) {
            TetradMatrix normalizedRow = normalizedRows.get(i);
            for (int j = 0; j < matrix.columns(); j++) {
                result.set(i, j, normalizedRow.get(j, 0));
            }
        }

        return result;
    }

    private static TetradMatrix normalizeVector(TetradMatrix vector) {
        double scalar = Math.sqrt(vector.transpose().times(vector).get(0, 0));
        return vector.scalarMult(1.0 / scalar);
    }

    private static TetradMatrix matrixExp(TetradMatrix matrix, double exponent) {
        TetradMatrix result = new TetradMatrix(matrix.rows(), matrix.columns());
        for (int i = 0; i < matrix.rows(); i++) {
            for (int j = 0; j < matrix.columns(); j++) {
                result.set(i, j, Math.pow(matrix.get(i, j), exponent));
            }
        }
        return result;
    }

    public double getThreshold() {
        return threshold;
    }

    public int getNumFactors() {
        return numFactors;
    }

    public void setNumFactors(int numFactors) {
        this.numFactors = numFactors;
    }

    public TetradMatrix getResidual() {
        return residual;
    }

    // factanal in R:

//    function (x, factors, data = NULL, covmat = NULL, n.obs = NA,
//              subset, na.action, start = NULL, scores = c("none", "regression",
//                      "Bartlett"), rotation = "varimax", control = NULL, ...)
//    {
//        sortLoadings <- function(Lambda) {
//        cn <- colnames(Lambda)
//        Phi <- attr(Lambda, "covariance")
//        ssq <- apply(Lambda, 2L, function(x) -sum(x^2))
//        Lambda <- Lambda[, order(ssq), drop = FALSE]
//        colnames(Lambda) <- cn
//        neg <- colSums(Lambda) < 0
//        Lambda[, neg] <- -Lambda[, neg]
//        if (!is.null(Phi)) {
//            unit <- ifelse(neg, -1, 1)
//            attr(Lambda, "covariance") <- unit %*% Phi[order(ssq),
//                    order(ssq)] %*% unit
//        }
//        Lambda
//    }
//        cl <- match.call()
//        na.act <- NULL
//        if (is.list(covmat)) {
//            if (any(is.na(match(c("cov", "n.obs"), names(covmat)))))
//                stop("'covmat' is not a valid covariance list")
//            cv <- covmat$cov
//            n.obs <- covmat$n.obs
//            have.x <- FALSE
//        }
//        else if (is.matrix(covmat)) {
//            cv <- covmat
//            have.x <- FALSE
//        }
//        else if (is.null(covmat)) {
//        if (missing(x))
//            stop("neither 'x' nor 'covmat' supplied")
//        have.x <- TRUE
//        if (inherits(x, "formula")) {
//            mt <- terms(x, data = data)
//            if (attr(mt, "response") > 0)
//                stop("response not allowed in formula")
//            attr(mt, "intercept") <- 0
//            mf <- match.call(expand.dots = FALSE)
//            names(mf)[names(mf) == "x"] <- "formula"
//            mf$factors <- mf$covmat <- mf$scores <- mf$start <- mf$rotation <- mf$control <- mf$... <- NULL
//            mf[[1L]] <- quote(stats::model.frame)
//            mf <- eval.parent(mf)
//            na.act <- attr(mf, "na.action")
//            if (.check_vars_numeric(mf))
//            stop("factor analysis applies only to numerical variables")
//            z <- model.matrix(mt, mf)
//        }
//        else {
//            z <- as.matrix(x)
//            if (!is.numeric(z))
//                stop("factor analysis applies only to numerical variables")
//            if (!missing(subset))
//                z <- z[subset, , drop = FALSE]
//        }
//        covmat <- cov.wt(z)
//        cv <- covmat$cov
//        n.obs <- covmat$n.obs
//    }
//    else stop("'covmat' is of unknown type")
//        scores <- match.arg(scores)
//        if (scores != "none" && !have.x)
//            stop("requested scores without an 'x' matrix")
//        p <- ncol(cv)
//        if (p < 3)
//            stop("factor analysis requires at least three variables")
//        dof <- 0.5 * ((p - factors)^2 - p - factors)
//        if (dof < 0)
//            stop(sprintf(ngettext(factors, "%d factor is too many for %d variables",
//                    "%d factors are too many for %d variables"), factors,
//                    p), domain = NA)
//        sds <- sqrt(diag(cv))
//        cv <- cv/(sds %o% sds)
//        cn <- list(nstart = 1, trace = FALSE, lower = 0.005)
//        cn[names(control)] <- control
//        more <- list(...)[c("nstart", "trace", "lower", "opt", "rotate")]
//        if (length(more))
//            cn[names(more)] <- more
//        if (is.null(start)) {
//        start <- (1 - 0.5 * factors/p)/diag(solve(cv))
//        if ((ns <- cn$nstart) > 1)
//            start <- cbind(start, matrix(runif(ns - 1), p, ns -
//                1, byrow = TRUE))
//    }
//        start <- as.matrix(start)
//        if (nrow(start) != p)
//            stop(sprintf(ngettext(p, "'start' must have %d row",
//                    "'start' must have %d rows"), p), domain = NA)
//        nc <- ncol(start)
//        if (nc < 1)
//            stop("no starting values supplied")
//        best <- Inf
//        for (i in 1L:nc) {
//        nfit <- factanal.fit.mle(cv, factors, start[, i], max(cn$lower,
//                0), cn$opt)
//        if (cn$trace)
//            cat("start", i, "value:", format(nfit$criteria[1L]),
//                    "uniqs:", format(as.vector(round(nfit$uniquenesses,
//                            4))), "\n")
//        if (nfit$converged && nfit$criteria[1L] < best) {
//            fit <- nfit
//            best <- fit$criteria[1L]
//        }
//    }
//        if (best == Inf)
//            stop(ngettext(nc, "unable to optimize from this starting value",
//                    "unable to optimize from these starting values"),
//                    domain = NA)
//        load <- fit$loadings
//        if (rotation != "none") {
//            rot <- do.call(rotation, c(list(load), cn$rotate))
//            load <- if (is.list(rot)) {
//                load <- rot$loadings
//                fit$rotmat <- if (inherits(rot, "GPArotation"))
//                    t(solve(rot$Th))
//                else rot$rotmat
//                            rot$loadings
//            }
//            else rot
//        }
//        fit$loadings <- sortLoadings(load)
//        class(fit$loadings) <- "loadings"
//        fit$na.action <- na.act
//        if (have.x && scores != "none") {
//            Lambda <- fit$loadings
//            zz <- scale(z, TRUE, TRUE)
//            switch(scores, regression = {
//                    sc <- zz %*% solve(cv, Lambda)
//            if (!is.null(Phi <- attr(Lambda, "covariance"))) sc <- sc %*%
//            Phi
//        }, Bartlett = {
//                    d <- 1/fit$uniquenesses
//                    tmp <- t(Lambda * d)
//                    sc <- t(solve(tmp %*% Lambda, tmp %*% t(zz)))
//        })
//            rownames(sc) <- rownames(z)
//            colnames(sc) <- colnames(Lambda)
//            if (!is.null(na.act))
//            sc <- napredict(na.act, sc)
//            fit$scores <- sc
//        }
//        if (!is.na(n.obs) && dof > 0) {
//            fit$STATISTIC <- (n.obs - 1 - (2 * p + 5)/6 - (2 * factors)/3) *
//                    fit$criteria["objective"]
//            fit$PVAL <- pchisq(fit$STATISTIC, dof, lower.tail = FALSE)
//        }
//        fit$n.obs <- n.obs
//        fit$call <- cl
//        fit
//    }
}



