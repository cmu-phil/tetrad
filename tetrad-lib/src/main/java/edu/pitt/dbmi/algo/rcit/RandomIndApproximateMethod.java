package edu.pitt.dbmi.algo.rcit;
/**
 * Approximation Method for approximating the null distribution
 * */
public enum RandomIndApproximateMethod {
	lpd4,  // the Lindsay-Pilla-Basak method (default)
	gamma, // the Satterthwaite-Welch method
	hbe,   // the Hall-Buckley-Eagleson method
//	chi2,  // a normalized chi-squared statistic
	perm   // permutation testing (warning: this one is slow but recommended for small samples generally <500 )
}
