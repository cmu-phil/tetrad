package edu.cmu.tetrad.search;

// I am translating this from Kun's matlab code.

import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.util.ArrayList;
import java.util.List;

////===============================NOT NEARLY FINISHED=======================//

public class AlassoRegression {

    public AlassoRegression(TetradMatrix x, TetradMatrix y, double varNoise, double lambda) {

        x = x.transpose();
        y = y.transpose();

//        var_noise_back = var_noise;

        double varNoiseBack = varNoise;

//
//        Trad1 = 0.2; Trad2 = 1 - Trad1;

        double trad1 = 0.2;
        double trad2 = 1 - trad1;
        int N = x.columns();
        int T = x.rows();

//[N,T] = size(x);

        double tol = 1e-2;

        double betaMin = 1e-12;
        double betaMin2 = 1e-2;

        List<Double> sumAdjustBeta = new ArrayList<>();
        List<Double> pl = new ArrayList<>();

//        tol = 1E-2; % 1E-10; %%% temp: Aug 5, 17:27
//        beta_min = 1E-12;
//        beta_min2 = 1E-2;
//        sum_adjust_beta = [];
//        pl = [];

        TetradMatrix betaHat = (x.transpose()).times((x.times(x.transpose())).inverse()).times(y);

//
//        beta_hat = y*x' * inv(x*x');

        if (varNoise == 0) {
            StatUtils.variance(y.minus(betaHat.times(x)).toArray()[0]); // maybe transpose.
        }


//        if var_noise == 0
//        var_noise = var(y - beta_hat*x);
//        end


        TetradMatrix xNew = betaHat.diag().diag().times(x);

//        x_new = diag(beta_hat) * x;

        double error = 1;
//        Error = 1;

        TetradMatrix betaNeoO = ones(N, 1);

//        beta_new_o = ones(N,1);
//% beta_new_o = 1E-5 * ones(N,1);
//
//% store for curve plotting
//        sum_adjust_beta = [sum_adjust_beta sum(abs(beta_new_o))];
//        pl = [pl (y-beta_new_o'*x_new)*(y-beta_new_o'*x_new)'/2/var_noise + lambda * sum(abs(beta_new_o))];
//
//        while Error > tol
//        Sigma = diag(1./abs(beta_new_o));
//    %     Sigma = diag(1./beta_new_o); % this is wrong?
//    %     beta_new_n = inv(x_new*x_new' + var_noise*lambda * Sigma) * (x_new*y');
//    % % with gradient trad-off!
//                beta_new_n = inv(x_new*x_new' + var_noise*lambda * Sigma) * (x_new*y') * Trad1 + beta_new_o * Trad2 ;
//        beta_new_n = sign(beta_new_n) .* max(abs(beta_new_n),beta_min);
//        Error = norm(beta_new_n - beta_new_o);
//        beta_new_o = beta_new_n;
//        sum_adjust_beta = [sum_adjust_beta sum(abs(beta_new_n))];
//        pl = [pl (y-beta_new_n'*x_new)*(y-beta_new_n'*x_new)'/2/var_noise + lambda * sum(abs(beta_new_n))];
//        end


    }

    private TetradMatrix ones(int r, int c) {
        TetradMatrix ones = new TetradMatrix(r, c);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                ones.set(i, j, 1);
            }
        }

        return ones;
    }

}
