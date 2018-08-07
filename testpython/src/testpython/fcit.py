""" A fast conditional independence test.

This implementation uses the joblib library to parallelize test
statistic computation over all available cores. By default, num_perm=8
(instead of num_perm=10 in the non-parallel version) as 8 cores is a
common number on current architectures.

Reference:
Chalupka, Krzysztof and Perona, Pietro and Eberhardt, Frederick, 2017.
"""
import time

import joblib
import numpy as np
from scipy.stats import ttest_1samp
from sklearn.metrics import mean_squared_error as mse
from sklearn.model_selection import GridSearchCV
from sklearn.model_selection import ShuffleSplit
from sklearn.preprocessing import StandardScaler
from sklearn.tree import DecisionTreeRegressor


def interleave(x, z, seed=None):
    """ Interleave x and z dimension-wise.

    Args:
        x (n_samples, x_dim) array.
        z (n_samples, z_dim) array.

    Returns
        An array of shape (n_samples, x_dim + z_dim) in which
            the columns of x and z are interleaved at random.
    """
    state = np.random.get_state()
    np.random.seed(seed or int(time.time()))
    total_ids = np.random.permutation(x.shape[1]+z.shape[1])
    np.random.set_state(state)
    out = np.zeros([x.shape[0], x.shape[1] + z.shape[1]])
    out[:, total_ids[:x.shape[1]]] = x
    out[:, total_ids[x.shape[1]:]] = z
    return out

def cv_besttree(x, y, z, cv_grid, logdim, verbose, prop_test):
    """ Choose the best decision tree hyperparameters by
    cross-validation. The hyperparameter to optimize is min_samples_split
    (see sklearn's DecisionTreeRegressor).

    Args:
        x (n_samples, x_dim): Input data array.
        y (n_samples, y_dim): Output data array.
        z (n_samples, z_dim): Optional auxiliary input data.
        cv_grid (list of floats): List of hyperparameter values to try.
        logdim (bool): If True, set max_features to 'log2'.
        verbose (bool): If True, print out extra info.
        prop_test (float): Proportion of validation data to use.

    Returns:
        DecisionTreeRegressor with the best hyperparameter setting.
    """
    xz_dim = x.shape[1] + z.shape[1]
    max_features='log2' if (logdim and xz_dim > 10) else None
    if cv_grid is None:
        min_samples_split = 2
    elif len(cv_grid) == 1:
        min_samples_split = cv_grid[0]
    else:
        clf = DecisionTreeRegressor(max_features=max_features)
        splitter = ShuffleSplit(n_splits=3, test_size=prop_test)
        cv = GridSearchCV(estimator=clf, cv=splitter,
                          param_grid={'min_samples_split': cv_grid}, n_jobs=-1)
        cv.fit(interleave(x, z), y)
        min_samples_split = cv.best_params_['min_samples_split']
    if verbose:
        print('min_samples_split: {}.'.format(min_samples_split))
    clf = DecisionTreeRegressor(max_features=max_features,
                                min_samples_split=min_samples_split)
    return clf

def obtain_error(data_and_i):
    """
    A function used for multithreaded computation of the fcit test statistic.
    data['x']: First variable.
    data['y']: Second variable.
    data['z']: Conditioning variable.
    data['data_permutation']: Permuted indices of the data.
    data['perm_ids']: Permutation for the bootstrap.
    data['n_test']: Number of test points.
    data['clf']: Decision tree regressor.
    """
    data, i = data_and_i
    x = data['x']
    y = data['y']
    z = data['z']
    if data['reshuffle']:
        perm_ids = np.random.permutation(x.shape[0])
    else:
        perm_ids = np.arange(x.shape[0])
    data_permutation = data['data_permutation'][i]
    n_test = data['n_test']
    clf = data['clf']

    x_z = interleave(x[perm_ids], z, seed=i)

    clf.fit(x_z[data_permutation][n_test:], y[data_permutation][n_test:])
    return mse(y[data_permutation][:n_test],
               clf.predict(x_z[data_permutation][:n_test]))


def test(x, y, z=None, num_perm=8, prop_test=.1,
         discrete=(False, False), plot_return=False, verbose=False,
         logdim=False, cv_grid=[2, 8, 64, 512, 1e-2, .2, .4], **kwargs):
    """ Fast conditional independence test, based on decision-tree regression.

    See Chalupka, Perona, Eberhardt 2017 [arXiv link coming].

    Args:
        x (n_samples, x_dim): First variable.
        y (n_samples, y_dim): Second variable.
        z (n_samples, z_dim): Conditioning variable. If z==None (default),
            then performs an unconditional independence test.
        num_perm: Number of data permutations to estimate
            the p-value from marginal stats.
        prop_test (int): Proportion of data to evaluate test stat on.
        discrete (bool, bool): Whether x or y are discrete.
        plot_return (bool): If True, return statistics useful for plotting.
        verbose (bool): Print out progress messages (or not).
        logdim (bool): If True, set max_features='log2' in the decision tree.
        cv_grid (list): min_impurity_splits to cross-validate when training
            the decision tree regressor.

    Returns:
        p (float): The p-value for the null hypothesis
            that x is independent of y.
    """
    # Compute test set size.
    n_samples = x.shape[0]
    n_test = int(n_samples * prop_test)

    if z is None:
        z = np.empty([n_samples, 0])

    if discrete[0] and not discrete[1]:
        # If x xor y is discrete, use the continuous variable as input.
        x, y = y, x
    elif x.shape[1] < y.shape[1]:
        # Otherwise, predict the variable with fewer dimensions.
        x, y = y, x

    # Normalize y to make the decision tree stopping criterion meaningful.
    y = StandardScaler().fit_transform(y)

    # Set up storage for true data and permuted data MSEs.
    # d0_stats = np.zeros(num_perm)
    # d1_stats = np.zeros(num_perm)
    data_permutations = [
        np.random.permutation(n_samples) for i in range(num_perm)]

    # Compute mses for y = f(x, z), varying train-test splits.
    clf = cv_besttree(x, y, z, cv_grid, logdim, verbose, prop_test=prop_test)
    datadict = {
        'x': x,
        'y': y,
        'z': z,
        'data_permutation': data_permutations,
        'n_test': n_test,
        'reshuffle': False,
        'clf': clf,
    }
    d1_stats = np.array(joblib.Parallel(n_jobs=-1, max_nbytes=100e6)(
        joblib.delayed(obtain_error)((datadict, i)) for i in range(num_perm)))

    # Compute mses for y = f(x, reshuffle(z)), varying train-test splits.
    if z.shape[1] == 0:
        x_indep_y = x[np.random.permutation(n_samples)]
    else:
        x_indep_y = np.empty([x.shape[0], 0])
    # clf = cv_besttree(x_indep_y, y, z, cv_grid, logdim,
    #                   verbose, prop_test=prop_test)
    datadict['reshuffle'] = True
    datadict['x'] = x_indep_y
    d0_stats = np.array(joblib.Parallel(n_jobs=-1, max_nbytes=100e6)(
        joblib.delayed(obtain_error)((datadict, i)) for i in range(num_perm)))

    if verbose:
        np.set_printoptions(precision=3)
        print('D0 statistics: {}'.format(d0_stats))
        print('D1 statistics: {}\n'.format(d1_stats))

    # Compute the p-value (one-tailed t-test
    # that mean of mse ratios equals 1).
    t, p_value = ttest_1samp(d0_stats / d1_stats, 1)
    if t < 0:
        p_value = 1 - p_value / 2
    else:
        p_value = p_value / 2

    if plot_return:
        return (p_value, d0_stats, d1_stats)
    else:
        return p_value