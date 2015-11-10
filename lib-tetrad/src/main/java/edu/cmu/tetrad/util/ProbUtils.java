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

package edu.cmu.tetrad.util;

import cern.jet.stat.Probability;

/**
 * <p>Implements a number of important functions from probability and
 * statistics.</p> <p>We don't know who wrote this class; we didn't.  All we
 * added was the documentation. -Joe Ramsey, jdramsey@andrew.cmu.edu</p>
 *
 * @author Unknown, but thanks
 */
@SuppressWarnings({"ConstantConditions", "WeakerAccess", "UnusedDeclaration"})
public class ProbUtils {

    private static double r[] = {1.253314137315500251207883, 1.193182964731915311846094,
            1.137490921203604514832235, 1.085827027468003637553896,
            1.037824575853726812300365, .9931557904881572182738326,
            .9515271920712067152786701, .9126755670832121676776603,
            .8763644564536923467278531, .8423810914521299997866721,
            .8105337152790304152036357, .7806492378708633711733062,
            .7525711790634080514554734, .7261578617139919103863031,
            .7012808218544300582109727, .6778234075911775329302582,
            .6556795424187984715438712, .6347526319769262711052108,
            .6149545961509297292775566, .5962050108690213064751457,
            .5784303460476310766336125, .5615632879362914156483528,
            .5455421356582169186076368, .5303102630712526699958747,
            .5158156382179633550265125, .5020103936204170322841234,
            .488850441527573754354743, .4762951289605100272316751,
            .4643069280394421644372609, .452851157630626619621641,
            .4418957328326000054087525, .4314109392400032535140663,
            .4213692292880544732249343, .4117450382989767017176231,
            .4025146181296716932830159, .3936558865630571131481576,
            .3851482907984342415801495, .3769726835829618014159496,
            .3691112106902638389489919, .3615472085963400644161054,
            .354265111329793337375764, .3472503655851968568924767,
            .3404893532870850071346728, .3339693208791823593693459,
            .3276783146905523474475952, .3216051217986084063997594,
            .3157392158694103491188545, .3100707075093597582907416,
            .304590298710103019232964, .2992892410108769288187367,
            .2941592970402895988586268, .2891927051332122944730683,
            .2843821467484925828542051, .2797207164400090390048569,
            .2752018941576061687414437, .2708195196759090041951434,
            .2665677689682234829510997, .2624411323600359552832388,
            .2584343943120382172559107, .2545426146965892806142785,
            .2507611114439652148255265, .2470854444460805703298742,
            .2435114006154562183725053, .2400349800063908654015814,
            .2366523829135604830915503, .233359997870698598664295,
            .2301543904788006381072361, .2270322929993801119871592,
            .2239905946538290863869083, .2210263325749769736284363,
            .2181366833614710122297952, .2153189551897363262218578,
            .2125705804420320545972856, .2098891088125368083169621,
            .2072722008565007589748572, .2047176219503302188107064,
            .2022232366330545215419131, .1997870033019862546952077,
            .1974069692375194490844627, .1950812659339918105426953,
            .1928081047153155616100713, .1905857726157402721374016,
            .1884126285076001815699527, .1862870994592909823499507,
            .1842076773079703381780956, .1821729154326492082990465,
            .1801814257143915475980612, .1782318756713315633990563,
            .1763229857571025870546387, .17445352681211268567823,
            .1726223176578507652332691, .170828222825113676267847,
            .1690701504076941880139726, .1673470500336553419899068,
            .1656579109468773975553577, .1640017601920640363366404,
            .1623776608968673374563811, .1607847106452193635505097,
            .1592220399363673265836744, .1576888107244717415286631,
            .1561842150339760769159709, .154707473646271358338463,
            .1532578348534790212960446, .1518345732754411325827937,
            .1504369887362691412736569, .1490644051970330214282687,
            .1477161697413932577413847, .1463916516111827416630501,
            .1450902412891308370578393, .1438113496261050352444228,
            .1425544070104022432905889, .1413188625767789529834851,
            .1401041834530502056148988, .1389098540422202650857274,
            .1377353753382303588480118, .1365802642735279803607799,
            .1354440530967635155710012, .1343262887790271962841785,
            .1332265324471292504019244, .132144358842515398166367,
            .1310793558044917945593207, .1300311237765102200812464,
            .128999275334337470011875, .1279834347349965694864678,
            .1269832374854368818978314, .1259983299299428153278645,
            .1250283688553503087964205, .1240730211131909094124509,
            .1231319632579322598468361, .1222048812005296276989127,
            .1212914698765461287919247, .1203914329281397110711456,
            .1195044823992530794107805, .1186303384433775019338515,
            .1177687290432979327966364, .1169193897422535131538919,
            .1160820633859823480155247, .1152564998751443189754465,
            .1144424559276431412284366, .1136396948503935650514457,
            .112847986320103044088645, .1120671061726592771214108,
            .1112968362007358601364944, .110536963959247939376068,
            .1097872825783083063597883, .1090475905833518904388312,
            .1083176917221130451719685, .1075973947981563778083775,
            .1068865135106744244241717, .1061848663002832119442509,
            .105492276200556096942253, .1048085706950511306815753,
            .1041335815795982142447371, .1034671448296236160489718,
            .1028091004723000198182652, .1021592924633203069380762,
            .1015175685681027854865852, .1008837802472445895049806,
            .1002577825460485147279854, .09963943398795665776104485,
            .09902859647173190962510087, .09842513517223564521296569,
            .09782891844465686959119527, .09723981773205465097029204,
            .09665770747608198672456013, .09608246503076461364872451,
            .09551397057921561379174917, .09495210705316911518666266,
            .09439676005522442883814663, .09384781778369499237091277,
            .09330517095996170483952101, .09276871275823452392594484,
            .09223833873763036123879576, .09171394677647927541850185,
            .09119543700877473684364846, .09068271176268733191388065,
            .09017567550106469857171747, .08967423476384374680079322,
            .08917829811230432667975505, .08868777607509647008527077,
            .08820258109597615778665339, .08772262748318725850402136,
            .0872478313604298571655505, .08677811061935764238292468,
            .08631338487354936400705558, .0858535754139016061424034,
            .08539860516539225449532534, .08494839864516607442904103,
            .08450288192189570024131742, .08406198257637363654606902,
            .08362562966329130783291586, .08319375367416516011749277,
            .0827662865013691388259681, .08234316140323582329192271,
            .08192431297018950814835363, .08150967709187601159489754,
            .08109919092525534990138904, .08069279286362471847049943,
            .0802904225065404650858022, .07989202063060893323638823,
            .07949752916111719512006792, .07910689114447578744239717,
            .0787200507214466106865758, .07833695310113015625477634,
            .07795754453568718779269906, .07758177229577092502292493,
            .07720958464664666235002043, .07684093082497660209183881,
            .07647576101624849508185813, .07611402633282746114038384,
            .07575567879261111001491475, .07540067129826880125610838,
            .07504895761704657047089306, .07470049236111991075842844,
            .07435523096847723310605399, .07401312968431743926073765,
            .07367414554294562620094298, .07333823635015150386570458,
            .0730053606660556482535154, .07267547778840923133747565,
            .07234854773633336836416045, .07202453123448470287828978,
            .07170338969763431106948403, .07138508521564745055865869,
            .07106958053885214609220417, .07075683906378472602241689,
            .07044682481930169454732137, .07013950245304622696078506,
            .06983483721825943539080115, .06953279496092599670031216,
            .06923334210724437899739519, .06893644565141218490800048,
            .06864207314371744366250278, .06835019267892698635104373,
            .06806077288496332988399061, .06777378291186177570382577,
            .06748919242099969956446575, .06720697157459026913544459,
            .06692709102543307719554012, .06664952190691442013000059,
            .06637423582325018469784634};

    /**
     * Normal cumulative distribution function (the value which results by
     * integrating the normal distribution function from negative infinity up to
     * y).
     *
     * @param y the upper limit of integration.
     * @return the area accumulated in the integration.
     */
    @SuppressWarnings({"SuspiciousNameCombination"})
    public static double normalCdf(double y) {

        double f, h;
        int j;
        double dcphi, x, z, f1, f2, f3, f4, f5;

        x = y;

        if (Math.abs(x) > 15.) {
            dcphi = 0.;
        }
        else {
            j = (int) Math.floor(Math.abs(x) * 16. + .5);
            z = j * .0625;
            h = Math.abs(x) - z;
            f = r[j];
            f1 = f * z - 1;
            f2 = f + z * f1;
            f3 = f1 * 2. + z * f2;
            f4 = f2 * 3 + z * f3;
            f5 = f3 * 4 + z * f4;
            dcphi = f + h * (f1 * 120. +
                    h * (f2 * 60. + h * (f3 * 20. + h * (f4 * 5. + h * f5)))) /
                    120.;
            dcphi = dcphi * .3989422804014326779 * Math.exp(x * -.5 * x);
        }

        if (x < 0.) {
            return dcphi;
        }
        else {
            return (1.0 - dcphi);
        }
    }

    /**
     * Virtual Machine Epsilon.
     */
    private static double vm_epsilon = 1.0;

    /**
     * Method declaration
     *
     * @return result.
     */
    private static double macheps() {

        if (vm_epsilon >= 1.0) {
            while (1.0 + vm_epsilon / 2.0 != 1.0) {
                vm_epsilon /= 2.0;
            }
        }

        return vm_epsilon;
    }

    private static final double[] cof = {76.18009172947146, -86.50532032941677,
            24.01409824083091, -1.231739572450155, 0.1208650973866179e-2,
            -0.5395239384953e-5};

    /**
     * This is a more literal (that is, exact) copy of the log gamma method from
     * Numerical Recipes than the following one.  It was created by cutting and
     * pasting from the PDF version of the book and then converting C syntax to
     * Java. </p> The static double array above goes with this. </p> Converted
     * to Java by Frank Wimberly
     *
     * @param xx
     * @return the value ln[?(xx)] for xx > 0
     */
    public static double lngamma(double xx) {
        //Returns the value ln[?(xx)] for xx > 0.

        if (xx <= 0) return Double.NaN;

        //Internal arithmetic will be done in double precision, a nicety that you can omit if ?ve-?gure
        //accuracy is good enough.
        double x, y, tmp, ser;

        int j;
        y = x = xx;
        tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        ser = 1.000000000190015;
        for (j = 0; j <= 5; j++) {
            ser += cof[j] / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    /*
    private static final double COF1 = 76.18009173, COF2 = -86.50532033,
    COF3 = 24.01409822, COF4 = -1.231739516,
    COF5 = 0.120858003e-2, COF6 = -0.536382e-5;
    */

    /**
     * Log gamma function from Numerical Recipes.  Calculates the log gamma function
     * of xx.
     *
     * This is a less accurate version of the above and it has therefore been commented out.
     * @param xx value to calculate the log gamma for.
     * @return the log gamma of xx.
     */
    /*
    public static double lngamma(double xx) {

        double x, tmp, ser;

        if (xx < 1.0) {
            return (lngamma(1.0 + xx) - Math.log(xx));
        }
        else {
            x = xx - 1.0;
            tmp = x + 5.5;
            tmp -= (x + 0.5) * Math.log(tmp);
            ser = 1.0 + COF1 / (x + 1.0) + COF2 / (x + 2.0)
                    + COF3 / (x + 3.0) + COF4 / (x + 4.0) + COF5 / (x + 5.0)
                    + COF6 / (x + 6.0);

            return (-tmp + Math.log(2.50662827465 * ser));
        }
    }
    */

    /**
     * Calculates the log beta function of p and q.
     */
    public static double logbeta(double p, double q) {
        return (lngamma(p) + lngamma(q) - lngamma(p + q));
    }

    /**
     * Incomplete Beta function. </p> Translated from FORTRAN july 1977 edition.
     * w. fullerton, c3, los alamos scientific lab. based on bosten and
     * battiste, remark on algorithm 179, comm. acm, v 17, p 153, (1974).
     *
     * @param x   upper limit of integration.  x must be in (0,1) inclusive.
     * @param pin first beta distribution parameter.  p must be gt 0.0.
     * @param qin second beta distribution parameter.  q must be gt 0.0.
     * @return result.
     */
    @SuppressWarnings({"SuspiciousNameCombination", "WeakerAccess"})
    public static double betaCdf(double x, double pin, double qin) {

        //
        // Translated from FORTRAN
        // july 1977 edition.  w. fullerton, c3, los alamos scientific lab.
        // based on bosten and battiste, remark on algorithm 179, comm. acm,
        // v 17, p 153, (1974).
        //
        // input arguments --
        // x      upper limit of integration.  x must be in (0,1) inclusive.
        // p      first beta distribution parameter.  p must be gt 0.0.
        // q      second beta distribution parameter.  q must be gt 0.0.
        // betai  the incomplete beta function ratio is the probability that a
        // random variable from a beta distribution having parameters
        // p and q will be less than or equal to x.
        //
        double c, finsum, p, ps, q, term, xb, xi, y, dbetai, p1;
        int i, n, ib;
        double eps, alneps, sml, alnsml;

        if (x <= 0.0) {
            return 0.0;

            // I'm not sure these tolerances are optimal.
        }

        eps = macheps();
        alneps = Math.log(eps);
        sml = eps;
        alnsml = alneps;
        y = x;
        p = pin;
        q = qin;

        if ((q > p) || (x >= 0.8)) {
            if (x >= 0.2) {
                y = 1.0 - y;
                p = qin;
                q = pin;
            }
        }

        if ((p + q) * y / (p + 1.0) < eps) {
            dbetai = 0.0;
            xb = p * Math.log(Math.max(y, sml)) - Math.log(p) - logbeta(p, q);

            if ((xb > alnsml) && (y != 0.0)) {
                dbetai = Math.exp(xb);
            }

            if ((y != x) || (p != pin)) {
                dbetai = 1.0 - dbetai;
            }
        }
        else {

            //
            // evaluate the infinite sum first.  term will equal
            // y**/pbeta(ps,p) * (1.-ps)-sub-i * y**i / fac(i) .
            //
            ps = q - Math.floor(q);

            if (ps == 0.0) {
                ps = 1.0;
            }

            xb = p * Math.log(y) - logbeta(ps, p) - Math.log(p);
            dbetai = 0.0;

            if (xb >= alnsml) {
                dbetai = Math.exp(xb);
                term = dbetai * p;

                if (ps != 1.0) {
                    n = (int) Math.max(alneps / Math.log(y), 4.0);

                    for (i = 1; i <= n; i++) {
                        xi = i;
                        term = term * (xi - ps) * y / xi;
                        dbetai = dbetai + term / (p + xi);
                    }
                }
            }

            //
            // now evaluate the finite sum, maybe.
            //
            if (q > 1.0) {
                xb = p * Math.log(y) + q * Math.log(1.0 - y) - logbeta(p, q) -
                        Math.log(q);
                ib = (int) Math.max(xb / alnsml, 0.0);
                term = Math.exp(xb - ((double) ib) * alnsml);
                c = 1.0 / (1.0 - y);
                p1 = q * c / (p + q - 1.0);
                finsum = 0.0;
                n = (int) q;

                if (q == (double) n) {
                    n--;
                }

                for (i = 1; i <= n; i++) {
                    if ((p1 <= 1.0) && (term / eps <= finsum)) {
                        break;
                    }

                    xi = i;
                    term = (q - xi + 1.0) * c * term / (p + q - xi);

                    if (term > 1.0) {
                        ib = ib - 1;
                    }

                    if (term > 1.0) {
                        term = term * sml;
                    }

                    if (ib == 0) {
                        finsum += term;
                    }
                }

                dbetai += finsum;
            }

            if ((y != x) || (p != pin)) {
                dbetai = 1.0 - dbetai;
            }

            dbetai = Math.max(Math.min(dbetai, 1.0), 0.0);
        }

        return dbetai;
    }

    /**
     * Binomial cumulative distribution function.
     */
    public static double binomialCdf(int k, int n, double p) {

        double da, db, dp;
        //        int ia, ib;

        if (k < 0) {
            dp = 0.0;
        }
        else if (k >= n) {
            dp = 1.0;
        }
        else if (p == 0.0) {
            dp = (k < 0) ? 0.0 : 1.0;
        }
        else if (p == 1.0) {
            dp = (k < n) ? 0.0 : 1.0;
        }
        else {
            da = (double) k + 1.0;
            db = (double) (n - k);
            dp = 1.0 - betaCdf(p, da, db);
        }

        return dp;
    }

    /**
     * Cauchy CDF
     */
    public static double cauchyCdf(double x) {
        return (Math.atan(x) + Math.PI / 2) / Math.PI;
    }

    /**
     * F CDF.
     */
    public static double fCdf(double x, double df1, double df2) {
        return (1.0 - betaCdf(df2 / (df2 + df1 * x), 0.5 * df2, 0.5 * df1));
    }

    // From Numerical Recipes, with normal approximation from Appl. Stat. 239
    private static final double EPSILON = 1.0e-14, LARGE_A = 10000.0;
    private static final int ITMAX = 1000;

    /**
     * Compute gamma cdf by a normal approximation
     */
    private static double gnorm(double a, double x) {

        double /*p, */ sx;

        if ((x <= 0.0) || (a <= 0.0)) {
            return 0.0;
        }
        else {
            sx = Math.sqrt(a) * 3.0 *
                    (Math.pow(x / a, 1.0 / 3.0) + 1.0 / (a * 9.0) - 1.0);

            return normalCdf(sx);
        }
    }

    private static double gser(double a, double x, double gln) {

        double p, sum, del, ap;
        int n;
        boolean done = false;

        if ((x <= 0.0) || (a <= 0.0)) {
            p = 0.0;
        }
        else {
            ap = a;
            del = 1.0 / a;
            sum = del;

            for (n = 1; (!done) && (n < ITMAX); n++) {
                ap += 1.0;
                del *= x / ap;
                sum += del;

                if (Math.abs(del) < EPSILON) {
                    done = true;
                }
            }

            p = sum * Math.exp(-x + a * Math.log(x) - gln);
        }

        return p;
    }

    /**
     * compute gamma cdf by its series representation
     */
    private static double gcf(double a, double x, double gln) {

        double gold = 0.0, g, fac = 1.0, b1 = 1.0;
        double b0 = 0.0, anf, ana, an, a1, a0 = 1.0;
        double p;
        boolean done = false;

        a1 = x;
        p = 0.0;

        for (an = 1.0; (!done) && (an <= ITMAX); an += 1.0) {
            ana = an - a;
            a0 = (a1 + a0 * ana) * fac;
            b0 = (b1 + b0 * ana) * fac;
            anf = an * fac;
            a1 = x * a0 + anf * a1;
            b1 = x * b0 + anf * b1;

            if (a1 != 0.0) {
                fac = 1.0 / a1;
                g = b1 * fac;

                if (Math.abs((g - gold) / g) < EPSILON) {
                    p = Math.exp(-x + a * Math.log(x) - gln) * g;
                    done = true;
                }

                gold = g;
            }
        }

        return p;
    }

    /**
     * compute complementary gamma cdf by its continued fraction expansion
     */
    @SuppressWarnings({"WeakerAccess"})
    public static double gammaCdf(double a, double x) {

        double gln /*, p*/;

        if ((x <= 0.0) || (a <= 0.0)) {
            return Double.NaN;
        }
        else if (a > LARGE_A) {
            return gnorm(a, x);
        }
        else {
            gln = lngamma(a);

            if (x < (a + 1.0)) {
                return gser(a, x, gln);
            }
            else {
                return (1.0 - gcf(a, x, gln));
            }
        }
    }

    public static double chisqCdf(double x, double df) {
        return Probability.chiSquare(df, x);
//
//        return gammaCdf(0.5 * df, 0.5 * x);
    }

    @SuppressWarnings({"SuspiciousNameCombination"})
    public static double poissonCdf(int k, double y) {

        double dp, dx;

        if (k < 0) {
            dp = 0.0;
        }
        else if (y == 0.0) {
            dp = (k < 0) ? 0.0 : 1.0;
        }
        else {
            dx = k + 1.0;
            dp = 1.0 - gammaCdf(dx, y);
        }

        return (dp);
    }


    private static final double TWOVRPI = 0.636619772367581343, HALF_PI =
            1.5707963268, TOL = .000001;

    /**
     * CACM Algorithm 395, by G. W. Hill
     */
    public static double tCdf(double x, double df) {

        double t, y, b, a, z, j, n, cdf;

        n = df;
        z = 1.0;
        t = x * x;
        y = t / n;
        b = 1.0 + y;

        if ((n > Math.floor(n)) || ((n >= 20.0) && (t < n)) || (n > 20.0)) {
            if ((n < 2.0) && (n != 1.0)) {

                /* beta integral aproximation for small df */
                double da = 0.5, db = 0.5 * n, dx, dp;
                //                int ia = 0, ib = (int) Math.floor(db);

                dx = db / (db + da * t);
                dp = betaCdf(dx, db, da);
                cdf = (x >= 0) ? 1.0 - .5 * dp : .5 * dp;
            }
            else {

                /* asymptotic series for large or non-integer df */
                if (y > TOL) {
                    y = Math.log(b);
                }

                a = n - 0.5;
                b = 48.0 * a * a;
                y = a * y;
                y = (((((-0.4 * y - 3.3) * y - 24.0) * y - 85.5) /
                        (0.8 * y * y + 100.0 + b) + y + 3.0) / b + 1.0) *
                        Math.sqrt(y);
                y = -1.0 * y;
                cdf = normalCdf(y);

                if (x > 0.0) {
                    cdf = 1.0 - cdf;
                }
            }
        }
        else {

            /* nested summation of cosine series */
            if ((n < 20.0) && (t < 4.0)) {
                a = Math.sqrt(y);
                y = a;

                if (n == 1.0) {
                    a = 0.0;
                }
            }
            else {
                a = Math.sqrt(b);
                y = a * n;

                for (j = 2; Math.abs(a - z) > TOL; j += 2.0) {
                    z = a;
                    y = (y * (j - 1)) / (b * j);
                    a = a + y / (n + j);
                }

                n += 2.0;
                z = 0.0;
                y = 0.0;
                a = -a;
            }

            for (n = n - 2.0; n > 1.0; n -= 2.0) {
                a = ((n - 1.0) / (b * n)) * a + y;
            }

            a = (Math.abs(n) < TOL) ?
                    a / Math.sqrt(b) : TWOVRPI * (Math.atan(y) + a / b);
            cdf = z - a;

            if (x > 0.0) {
                cdf = 1.0 - 0.5 * cdf;
            }
            else {
                cdf = 0.5 * cdf;
            }
        }

        return cdf;
    }


    private static final double sae = -30.0, zero = 0.0, one = 1.0, two =
            2.0, three = 3.0, four = 4.0, five = 5.0, six = 6.0;

    //
    // xinbta.f -- translated by f2c and modified
    //
    // algorithm as 109 appl. statist. (1977), vol.26, no.1
    // (replacing algorithm as 64  appl. statist. (1973), vol.22, no.3)
    //
    // Remark AS R83 has been incorporated in this version.
    //
    // Computes inverse of the incomplete beta function
    // ratio for given positive values of the arguments
    // p and q, alpha between zero and one.
    // log of complete beta function, beta, is assumed to be known.
    //
    // Auxiliary function required: binc
    //
    // SAE below is the most negative decimal exponent which does not
    // cause an underflow; a value of -308 or thereabouts will often be
    //

    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public static double betaQuantile(double alpha, double p, double q) {

        double beta;

        // System generated locals
        double ret_val, d_1, d_2;

        // Local variables
        boolean indx;
        double prev, a, g, h, r, s, t, w, y, yprev, pp, qq;
        double sq, tx, adj, acu;
        int iex;
        double fpu, xin;

        beta = lngamma(p) + lngamma(q) - lngamma(p + q);

        // Define accuracy and initialise.
        fpu = sae * 10.;
        ret_val = alpha;

        if ((p <= zero) || (q <= zero)) {
            return ret_val;
        }

        if ((alpha == zero) || (alpha == one)) {
            return ret_val;

            // change tail if necessary
        }

        if (alpha <= .5) {
            a = alpha;
            pp = p;
            qq = q;
            indx = false;
        }
        else {
            a = one - alpha;
            pp = q;
            qq = p;
            indx = true;
        }

        // calculate the initial approximation
        r = Math.sqrt(-Math.log(a * a));
        y = r - (r * .27061 + 2.30753) / (one + (r * .04481 + .99229) * r);

        if ((pp > one) && (qq > one)) {
            r = (y * y - three) / six;
            s = one / (pp + pp - one);
            t = one / (qq + qq - one);
            h = two / (s + t);
            d_1 = y * Math.sqrt(h + r) / h;
            d_2 = (t - s) * (r + five / six - two / (three * h));
            w = d_1 - d_2;
            ret_val = pp / (pp + qq * Math.exp(w + w));
        }
        else {
            r = qq + qq;
            t = one / (qq * 9.);

            // Computing 3rd power
            d_1 = one - t + y * Math.sqrt(t);
            t = r * (d_1 * d_1 * d_1);

            if (t <= zero) {
                ret_val =
                        one - Math.exp((Math.log((one - a) * qq) + beta) / qq);
            }
            else {
                t = (four * pp + r - two) / t;

                if (t <= one) {
                    ret_val = Math.exp((Math.log(a * pp) + beta) / pp);
                }
                else {
                    ret_val = one - two / (t + one);
                }
            }
        }

        //
        // solve for x by a modified newton-raphson method, using the function binc
        //
        r = one - pp;
        t = one - qq;
        yprev = zero;
        sq = one;
        prev = one;

        if (ret_val < 1e-4) {
            ret_val = 1e-4;
        }

        if (ret_val > .9999) {
            ret_val = .9999;

            // Computing MAX, two 2nd powers
        }

        d_1 = -5.0 / (pp * pp) - 1.0 / (a * a) - 13.0;
        iex = (sae > d_1) ? (int) sae : (int) d_1;
        acu = Math.pow(10.0, (double) iex);

        do {
            y = betaCdf(ret_val, pp, qq);
            xin = ret_val;
            y = (y - a) * Math.exp(
                    beta + r * Math.log(xin) + t * Math.log(one - xin));

            if (y * yprev <= zero) {
                prev = (sq > fpu) ? sq : fpu;
            }

            g = one;

            do {
                adj = g * y;
                sq = adj * adj;

                if (sq < prev) {
                    tx = ret_val - adj;

                    if ((tx >= zero) && (tx <= one)) {
                        if ((prev <= acu) || (y * y <= acu)) {
                            if (indx) {
                                ret_val = one - ret_val;
                            }

                            return ret_val;
                        }

                        if ((tx != zero) && (tx != one)) {
                            break;
                        }
                    }
                }

                g /= three;
            } while (true);

            if (tx == ret_val) {
                if (indx) {
                    ret_val = one - ret_val;
                }

                return ret_val;
            }

            ret_val = tx;
            yprev = y;
        } while (true);
    }

    @SuppressWarnings({"UnusedAssignment"})
    public static int binomialQuantile(double x, int n, double p) {

        int k, k1, k2, del/*, ia*/;
        double m, s, p1, p2, pk;

        if (p == 0.0) {
            return 0;
        }

        if (p == n) {
            return n;
        }

        m = n * p;
        s = Math.sqrt(n * p * (1 - p));
        del = Math.max(1, (int) (0.2 * s));
        k = (int) (m + s * normalQuantile(x));
        k1 = k;
        k2 = k;

        do {
            k1 = k1 - del;
            k1 = Math.max(0, k1);
            p1 = binomialCdf(k1, n, p);
        } while ((k1 > 0) && (p1 > x));

        if ((k1 == 0) && (p1 >= x)) {
            return (k1);
        }

        do {
            k2 = k2 + del;
            k2 = Math.min(n, k2);
            p2 = binomialCdf(k2, n, p);
        } while ((k2 < n) && (p2 < x));

        if ((k2 == n) && (p2 <= x)) {
            return (k2);
        }

        while (k2 - k1 > 1) {
            k = (k1 + k2) / 2;
            pk = binomialCdf(k, n, p);

            if (pk < x) {
                k1 = k;
                p1 = pk;
            }
            else {
                k2 = k;
                p2 = pk;
            }
        }

        return (k2);
    }

    public static double cauchyQuantile(double x) {
        return Math.tan(Math.PI * (x - 0.5));
    }

    private static final double aa = .6931471806;
    private static final double c1 = .01;
    private static final double c2 = .222222;
    private static final double c3 = .32;
    private static final double c4 = .4;
    private static final double c5 = 1.24;
    private static final double c6 = 2.2;
    private static final double c7 = 4.67;
    private static final double c8 = 6.66;
    private static final double c9 = 6.73;
    private static final double e = 5e-7;
    private static final double c10 = 13.32;
    private static final double c11 = 60.0;
    private static final double c12 = 70.0;
    private static final double c13 = 84.0;
    private static final double c14 = 105.0;
    private static final double c15 = 120.0;
    private static final double c16 = 127.0;
    private static final double c17 = 140.0;
    private static final double c18 = 1175.0;
    private static final double c19 = 210.0;
    private static final double c20 = 252.0;
    private static final double c21 = 2264.0;
    private static final double c22 = 294.0;
    private static final double c23 = 346.0;
    private static final double c24 = 420.0;
    private static final double c25 = 462.0;
    private static final double c26 = 606.0;
    private static final double c27 = 672.0;
    private static final double c28 = 707.0;
    private static final double c29 = 735.0;
    private static final double c30 = 889.0;
    private static final double c31 = 932.0;
    private static final double c32 = 966.0;
    private static final double c33 = 1141.0;
    private static final double c34 = 1182.0;
    private static final double c35 = 1278.0;
    private static final double c36 = 1740.0;
    private static final double c37 = 2520.0;
    private static final double c38 = 5040.0;
    private static final double half = .5;
    //    private static final double pmin = 0.0;
    //    private static final double pmax = 1.0;

    //
    // ppchi2.f -- translated by f2c and modified
    //
    // Algorithm AS 91   Appl. Statist. (1975) Vol.24, P.35
    // To evaluate the percentage points of the chi-squared
    // probability distribution function.
    //
    // p must lie in the range 0.000002 to 0.999998,
    // (but I am using it for 0 < p < 1 - seems to work)
    // v must be positive,
    // g must be supplied and should be equal to ln(gamma(v/2.0))
    //
    // Auxiliary routines required: ppnd = AS 111 (or AS 241) and gammad.
    //

    @SuppressWarnings({"UnusedAssignment"})
    public static double chisqQuantile(double p, double v) {

        // System generated locals
        double ret_val, d_1, d_2;

        // Local variables
        double a, b, c, g, q, t, x, p1, p2, s1, s2, s3, s4, s5, s6, ch;
        double xx;
        //        int if1;

        g = lngamma(v * 0.5);

        // test arguments and initialise
        ret_val = -one;
        xx = half * v;
        c = xx - one;

        if (v < -c5 * Math.log(p)) {

            // starting approximation for small chi-squared
            ch = Math.pow(p * xx * Math.exp(g + xx * aa), one / xx);

            if (ch < e) {
                ret_val = ch;

                return ret_val;
            }
        }
        else if (v > c3) {

            // call to algorithm AS 111 - note that p has been tested above.
            // AS 241 could be used as an alternative.
            x = normalQuantile(p);

            // starting approximation using Wilson and Hilferty estimate
            p1 = c2 / v;

            // Computing 3rd power
            d_1 = x * Math.sqrt(p1) + one - p1;
            ch = v * (d_1 * d_1 * d_1);

            // starting approximation for p tending to 1
            if (ch > c6 * v + six) {
                ch = -two * (Math.log(one - p) - c * Math.log(half * ch) + g);
            }
        }
        else {

            // starting approximation for v less than or equal to 0.32
            ch = c4;
            a = Math.log(one - p);

            do {
                q = ch;
                p1 = one + ch * (c7 + ch);
                p2 = ch * (c9 + ch * (c8 + ch));
                d_1 = -half + (c7 + two * ch) / p1;
                d_2 = (c9 + ch * (c10 + three * ch)) / p2;
                t = d_1 - d_2;
                ch -= (one - Math.exp(a + g + half * ch + c * aa) * p2 / p1) /
                        t;
            } while (Math.abs(q / ch - one) > c1);
        }

        do {

            // call to gammad and calculation of seven term Taylor series
            q = ch;
            p1 = half * ch;
            p2 = p - gammaCdf(xx, p1);
            t = p2 * Math.exp(xx * aa + g + p1 - c * Math.log(ch));
            b = t / ch;
            a = half * t - b * c;
            s1 = (c19 +
                    a * (c17 + a * (c14 + a * (c13 + a * (c12 + c11 * a))))) /
                    c24;
            s2 = (c24 + a * (c29 + a * (c32 + a * (c33 + c35 * a)))) / c37;
            s3 = (c19 + a * (c25 + a * (c28 + c31 * a))) / c37;
            s4 = (c20 + a * (c27 + c34 * a) + c * (c22 + a * (c30 + c36 * a))) /
                    c38;
            s5 = (c13 + c21 * a + c * (c18 + c26 * a)) / c37;
            s6 = (c15 + c * (c23 + c16 * c)) / c38;
            d_1 = (s3 - b * (s4 - b * (s5 - b * s6)));
            d_1 = (s1 - b * (s2 - b * d_1));
            ch += t * (one + half * t * s1 - b * c * d_1);
        } while (Math.abs(q / ch - one) > e);

        ret_val = ch;

        return ret_val;
    }

    public static double fQuantile(double p, double df1, double df2) {

        double dx;

        if (p == 0.0) {
            return 0.0;
        }
        else {
            dx = betaCdf(1.0 - p, 0.5 * df2, 0.5 * df1);

            return (df2 * (1.0 / dx - 1.0) / df1);
        }
    }

    public static double gammaQuantile(double a, double p) {
        return (0.5 * chisqQuantile(p, 2.0 * a));
    }

    private static final double split = 0.42e0;
    private static final double a0 = 2.50662823884;
    private static final double a1 = -18.61500062529;
    private static final double a2 = 41.39119773534;
    private static final double a3 = -25.44106049637;
    private static final double b1 = -8.47351093090;
    private static final double b2 = 23.08336743743;
    private static final double b3 = -21.06224101826;
    private static final double b4 = 3.13082909833;
    private static final double cc0 = -2.78718931138, cc1 =
            -2.29796479134, cc2 = 4.85014127135, cc3 = 2.32121276850;
    private static final double d1 = 3.54388924762;
    private static final double d2 = 1.63706781897;

    //
    // Algorithm as 111 Applied statistics (1977), vol 26 no 1 page 121
    // Produces normal deviate corresponding to lower tail area of p
    // the hash sums are the sums of the moduli of the coefficients
    // they nave no inherent meanings but are incuded for use in
    // checking transcriptions.  Functions abs,alog and sqrt are used.
    //

    public static double normalQuantile(double p) {

        double q, r, ppn;

        q = p - half;

        if (Math.abs(q) <= split) {
            r = q * q;
            ppn = q * (((a3 * r + a2) * r + a1) * r + a0) /
                    ((((b4 * r + b3) * r + b2) * r + b1) * r + one);
        }
        else {
            r = p;

            if (q > zero) {
                r = one - p;
            }

            r = Math.sqrt(-Math.log(r));
            ppn = (((cc3 * r + cc2) * r + cc1) * r + cc0) /
                    ((d2 * r + d1) * r + one);

            if (q < zero) {
                ppn = -ppn;
            }
        }

        return (ppn);
    }

    @SuppressWarnings({"UnusedAssignment"})
    public static int poissonQuantile(double x, double l) {

        int k, k1, k2, del /*, ia*/;
        double m, s, p1, p2, pk;

        if (x == 0.0) {
            return 0;
        }

        if (l == 0.0) {
            return 0;
        }

        m = l;
        s = Math.sqrt(l);
        del = Math.max(1, (int) (0.2 * s));
        k = (int) (m + s * normalQuantile(x));
        k1 = k;
        k2 = k;

        do {
            k1 = k1 - del;
            k1 = Math.max(0, k1);
            p1 = poissonCdf(k1, l);
        } while ((k1 > 0) && (p1 > x));

        if ((k1 == 0) && (p1 >= x)) {
            return (k1);
        }

        do {
            k2 = k2 + del;
            p2 = poissonCdf(k2, l);
        } while (p2 < x);

        while (k2 - k1 > 1) {
            k = (k1 + k2) / 2;
            pk = poissonCdf(k, l);

            if (pk < x) {
                k1 = k;
                p1 = pk;
            }
            else {
                k2 = k;
                p2 = pk;
            }
        }

        return (k2);
    }

    /**
     * CACM Algorithm 396, by G. W. Hill
     */
    public static double tQuantile(double pp, double n) {

        double sq, p, a, b, c, d, x, y;

        // convert to double upper tailed probability
        p = (pp < 0.5) ? 2.0 * pp : 2.0 * (1.0 - pp);

        if (n <= 3.0) {
            if (n == 1) {
                sq = Math.tan(HALF_PI * (1.0 - p));
            }
            else if (n == 2.0) {
                sq = Math.sqrt(2.0 / (p * (2.0 - p)) - 2.0);
            }
            else {
                sq = betaQuantile(p, 0.5 * n, 0.5);

                if (sq != 0.0) {
                    sq = Math.sqrt(n / sq - n);
                }
            }
        }
        else {
            a = 1.0 / (n - 0.5);
            b = 48.0 / (a * a);
            c = ((20700.0 * a / b - 98.0) * a - 16) * a + 96.36;
            d = ((94.5 / (b + c) - 3.0) / b + 1.0) * Math.sqrt(a * HALF_PI) * n;
            x = d * p;
            y = Math.pow(x, 2.0 / n);

            if (y > 0.05 + a) {

                // asymptotic inverse expansion about normal
                x = normalQuantile(0.5 * p);
                y = x * x;

                if (n < 5) {
                    c = c + 0.3 * (n - 4.5) * (x + 0.6);
                }

                c = (((0.05 * d * x - 5.0) * x - 7.0) * x - 2.0) * x + b + c;
                y = (((((0.4 * y + 6.3) * y + 36.0) * y + 94.5) / c - y - 3.0) /
                        b + 1.0) * x;
                y = a * y * y;
                y = (y > .002) ? Math.exp(y) - 1.0 : 0.5 * y * y + y;
            }
            else {
                y = ((1.0 / (((n + 6.0) / (n * y) - 0.089 * d - 0.822) *
                        (n + 2.0) * 3.0) + 0.5 / (n + 4.0)) * y - 1.0) *
                        (n + 1.0) / (n + 2.0) + 1.0 / y;
            }

            sq = Math.sqrt(n * y);
        }

        // decode based on tail
        if (pp < 0.5) {
            sq = -sq;
        }

        return sq;
    }

    public static double betaPdf(double x, double a, double b) {

        if ((x <= 0.0) || (x >= 1.0)) {
            return 0.0;
        }
        else {
            return (Math.exp(Math.log(x) * (a - 1) + Math.log(1 - x) * (b - 1) -
                    logbeta(a, b)));
        }
    }

    public static double binomialPmf(int k, int n, double p) {

        if (p == 0.0) {
            return ((k == 0) ? 1.0 : 0.0);
        }
        else if (p == 1.0) {
            return ((k == n) ? 1.0 : 0.0);
        }
        else if ((k < 0) || (k > n)) {
            return 0.0;
        }
        else {
            return (Math.exp(lngamma(n + 1.0) - lngamma(k + 1.0) -
                    lngamma(n - k + 1.0) + k * Math.log(p) +
                    (n - k) * Math.log(1.0 - p)));
        }
    }

    public static double cauchyPdf(double x) {
        return tPdf(x, 1.0);
    }

    public static double chisqPdf(double x, double v) {
        return (0.5 * gammaPdf(0.5 * x, 0.5 * v));
    }

    public static double fPdf(double x, double a, double b) {

        if (x <= 0.0) {
            return 0.0;
        }
        else {
            return (Math.exp(0.5 * a * Math.log(a) + 0.5 * b * Math.log(b) +
                    (0.5 * a - 1.0) * Math.log(x) - logbeta(0.5 * a, 0.5 * b) -
                    0.5 * (a + b) * Math.log(b + a * x)));
        }
    }

    public static double gammaPdf(double x, double a) {

        if (x <= 0.0) {
            return 0.0;
        }
        else {
            return Math.exp(Math.log(x) * (a - 1) - x - lngamma(a));
        }
    }

    public static double normalPdf(double x) {
        return (Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI));
    }

    public static double poissonPmf(int k, double lambda) {

        if (lambda == 0.0) {
            return ((k == 0) ? 1.0 : 0.0);
        }
        else if (k < 0) {
            return 0.0;
        }
        else {
            return (Math.exp(k * Math.log(lambda) - lambda - lngamma(k + 1.0)));
        }
    }

    @SuppressWarnings({"SameParameterValue"})
    public static double tPdf(double x, double a) {

        return ((1.0 / Math.sqrt(a * Math.PI)) * Math.exp(lngamma(
                0.5 * (a + 1)) - lngamma(0.5 * a) -
                0.5 * (a + 1) * Math.log(1.0 + x * x / a)));
    }

    private static final long MASK = 4294967295L;
    private static long seedi = 123456789L, seedj = 362436069L;

    public static void uniformSeeds(long a, long b) {
        seedi = a & MASK;
        seedj = b & MASK;
    }

    public static double uniformRand() {

        seedi = (seedi * 69069 + 23606797) & MASK;
        seedj ^= (seedj << 13) & MASK;
        seedj ^= (seedj >> 17) & MASK;
        seedj ^= (seedj << 5) & MASK;

        return ((double) ((seedi + seedj) & MASK) * Math.pow(2.0, -32.0));
    }

    public static int bernoulliRand(double p) {

        return (uniformRand() <= p) ? 1 : 0;
    }

    /**
     * Poisson random generator from Numerical Recipes
     */
    public static int poissonRand(double xm) {

        double sqrt2xm, logxm, expxm, g;
        double t, y;
        int k;

        if (xm < 12.0) {
            expxm = Math.exp(-xm);
            k = -1;
            t = 1.0;

            do {
                k++;

                t *= uniformRand();
            } while (t > expxm);
        }
        else {
            sqrt2xm = Math.sqrt(2.0 * xm);
            logxm = Math.log(xm);
            g = xm * logxm - lngamma(xm + 1.0);

            do {
                do {
                    y = Math.tan(Math.PI * uniformRand());
                    k = (int) Math.floor(sqrt2xm * y + xm);
                } while (k < 0);

                t = 0.9 * (1.0 + y * y) *
                        Math.exp(k * logxm - lngamma((double) k + 1.0) - g);
            } while (uniformRand() > t);
        }

        return (k);
    }

    /**
     * Binomial random generator from Numerical Recipes
     */
    public static int binomialRand(int n, double pp) {

        int j, k;
        double am, em, g, p, sq, t, y;
        double pc, plog, pclog, en;

        p = (pp <= 0.5) ? pp : 1.0 - pp;
        am = n * p;

        if (p == 0.0) {
            k = 0;
        }
        else if (p == 1.0) {
            k = n;
        }
        else if (n < 50) {
            k = 0;

            for (j = 0; j < n; j++) {
                if (uniformRand() < p) {
                    k++;
                }
            }
        }
        else if (am < 1.0) {
            g = Math.exp(-am);
            t = 1.0;
            k = -1;

            do {
                k++;

                t *= uniformRand();
            } while (t > g);

            if (k > n) {
                k = n;
            }
        }
        else {
            en = n;
            g = lngamma(en + 1.0);
            pc = 1.0 - p;
            plog = Math.log(p);
            pclog = Math.log(pc);
            sq = Math.sqrt(2.0 * am * pc);

            do {
                do {
                    y = Math.tan(Math.PI * uniformRand());
                    em = sq * y + am;
                } while ((em < 0.0) || (em >= en + 1.0));

                em = Math.floor(em);
                t = 1.2 * sq * (1.0 + y * y) * Math.exp(g - lngamma(em + 1.0) -
                        lngamma(en - em + 1.0) + em * plog + (en - em) * pclog);
            } while (uniformRand() > t);

            k = (int) em;
        }

        if (p != pp) {
            k = n - k;
        }

        return (k);
    }

    /**
     * Normal random generator
     */
    public static double normalRand() {

        double c, x, y, u, u1, v;

        c = Math.sqrt(2.0 / Math.exp(1.0));

        /* ratio of uniforms with linear pretest */
        do {
            u = uniformRand();
            u1 = uniformRand();
            v = c * (2 * u1 - 1);
            x = v / u;
            y = x * x / 4.0;
        } while ((y > (1 - u)) && (y > -Math.log(u)));

        return (x);
    }

    /**
     * Cauchy random generator.
     */
    public static double cauchyRand() {

        double u1, u2, v1, v2;

        /* ratio of uniforms on half disk */
        do {
            u1 = uniformRand();
            u2 = uniformRand();
            v1 = 2.0 * u1 - 1.0;
            v2 = u2;
        } while (v1 * v1 + v2 * v2 > 1.0);

        return (v1 / v2);
    }

    /**
     * Gamma random generator.
     */
    public static double gammaRand(double a) {

        double e, x, u0, u1, u2, v, w, c, c1, c2, c3, c4, c5;
        boolean done;

        e = Math.exp(1.0);

        if (a < 1.0) {

            /* Ahrens and Dieter algorithm */
            done = false;
            c = (a + e) / e;

            do {
                u0 = uniformRand();
                u1 = uniformRand();
                v = c * u0;

                if (v <= 1.0) {
                    x = Math.exp(Math.log(v) / a);

                    if (u1 <= Math.exp(-x)) {
                        done = true;
                    }
                }
                else {
                    x = -Math.log((c - v) / a);

                    if ((x > 0.0) && (u1 < Math.exp((a - 1.0) * Math.log(x)))) {
                        done = true;
                    }
                }
            } while (!done);
        }
        else if (a == 1.0) {
            x = -Math.log(uniformRand());
        }
        else {

            /* Cheng and Feast algorithm */
            c1 = a - 1.0;
            c2 = (a - 1.0 / (6.0 * a)) / c1;
            c3 = 2.0 / c1;
            c4 = 2.0 / (a - 1.0) + 2.0;
            c5 = 1.0 / Math.sqrt(a);

            do {
                do {
                    u1 = uniformRand();
                    u2 = uniformRand();

                    if (a > 2.5) {
                        u1 = u2 + c5 * (1.0 - 1.86 * u1);
                    }
                } while ((u1 <= 0.0) || (u1 >= 1.0));

                w = c2 * u2 / u1;
            } while ((c3 * u1 + w + 1.0 / w) > c4 &&
                    (c3 * Math.log(u1) - Math.log(w) + w) > 1.0);

            x = c1 * w;
        }

        return (x);
    }

    /**
     * Chi square random generator.
     */
    public static double chisqRand(double df) {
        return (2.0 * gammaRand(df / 2.0));
    }

    /**
     * T distribution random generator.
     */
    public static double tRand(double df) {
        return (normalRand() / Math.sqrt(chisqRand(df) / df));
    }

    /**
     * Beta distribution random generator.
     */
    public static double betaRand(double a, double b) {

        double x, y;

        x = gammaRand(a);
        y = gammaRand(b);

        return (x / (x + y));
    }

    /**
     * F distribution random generator.
     */
    public static double fRand(double ndf, double ddf) {
        return ((ddf * chisqRand(ndf)) / (ndf * chisqRand(ddf)));
    }

    /**
     * Bivariate normal CDF. Adapted from statlib, file general/gaut.c, by Ajay
     * Shah. Adaptation for Java found in http://www.geocities.com/optionpage/utility.html
     * --November 1st 2003, Ricardo Silva
     *
     * @param ah upper bound 1
     * @param ak upper bound 1
     * @param r  correlation
     * @return Prob(x1 <= ah, x2 <= ak)
     */

    public static double biNormalCdf(double ah, double ak, double r) {
        return biNormalCdf2(-ah, -ak, r);
    }

    @SuppressWarnings({"UnusedAssignment"})
    private static double biNormalCdf2(double ah, double ak, double r) {
        double a2, ap, b, cn, conex, ex, g2, gh, gk, gw =
                0, h2, h4, rr, s1, s2, sgn, sn, sp, sqr, t, temp, w2, wh =
                0, wk = 0;
        int is = 0;
        int myflag = 1;
        final double con = (2.0 * Math.PI / 2.0) * 0.0000000001;// 10.0e-10;
        temp = -ah;
        gh = ProbUtils.normalCdf(temp);
        gh = gh / 2.0;
        temp = -ak;
        gk = ProbUtils.normalCdf(temp);
        gk = gk / 2.0;

        b = 0;


        if (r == 0) {
            b = 4 * gh * gk;
        }
        else {
            rr = 1 - r * r;
            assert rr >= 0;
            if (rr != 0) {
                sqr = Math.sqrt(rr);
                if (ah == 0) {
                    if (ak == 0) {
                        b = Math.atan(r / sqr) / (2 * Math.PI) + .25;
                        if (b < 0) {
                            b = 0;
                        }
                        if (b > 1) {
                            b = 1;
                        }
                        return (b);
                    }
                    b = b + gk;
                }
                else {
                    b = gh;
                    if (ah * ak != 0) {
                        if (ah * ak < 0) {
                            b = b - .5;
                        }
                        b = b + gk;
                    }
                    wh = -ah;
                    wk = (ak / ah - r) / sqr;
                    gw = 2 * gh;
                    is = -1;
                    myflag = -1;
                }
                do {
                    if (myflag == -1) {
                        myflag = 1;
                    }
                    else {
                        wh = -ak;
                        wk = (ah / ak - r) / sqr;
                        gw = 2 * gk;
                        is = 1;
                    }
                    sgn = -1;
                    t = 0;
                    if (wk != 0) {
                        if (Math.abs(wk) != 1) {
                            if (Math.abs(wk) > 1) {
                                sgn = -sgn;
                                wh = wh * wk;
                                g2 = ProbUtils.normalCdf(wh);
                                wk = 1 / wk;
                                if (wk < 0) {
                                    b = b + .5;
                                }
                                b = b - (gw + g2) / 2 + gw * g2;
                            }
                            h2 = wh * wh;
                            a2 = wk * wk;
                            h4 = h2 * .5;
                            ex = 0;
                            if (h4 < 150.0) {
                                ex = Math.exp(-h4);
                            }
                            w2 = h4 * ex;
                            ap = 1;
                            s2 = ap - ex;
                            sp = ap;
                            s1 = 0;
                            sn = s1;
                            conex = Math.abs(con / wk);
                            do {
                                cn = ap * s2 / (sn + sp);
                                s1 = s1 + cn;
                                if (Math.abs(cn) <= conex) {
                                    break;
                                }
                                sn = sp;
                                sp = sp + 1;
                                s2 = s2 - w2;
                                w2 = w2 * h4 / sp;
                                ap = -ap * a2;
                            } while (true);
                            t = (Math.atan(wk) - wk * s1) / (2 * Math.PI);
                        }
                        else {
                            t = wk * gw * (1 - gw) / 2;
                        }

                        b = b + sgn * t;
                    }
                    if (is >= 0) {
                        break;
                    }
                } while (ak != 0);
            }
            else if (r >= 0) {
                if (ah >= ak) {
                    b = 2 * gh;
                }
                else {
                    b = 2 * gk;
                }
            }
            else if (ah + ak < 0) {
                b = 2 * (gh + gk) - 1;
            }
        }
        if (b < 0) {
            b = 0;
        }
        if (b > 1) {
            b = 1;
        }
        return (b);
    }


    /**
     * Compute the probability over a rectangular region with correlation matrix
     * c. Algorithm extracted from </p> Alan Genz: Numerical Computation of
     * Multivariate Normal Probabilities; revised version published in J. Comp.
     * Graph Stat. 1 (1992), pp. 141-149. http://www.math.wsu.edu/faculty/genz/homepage
     * </p> Warning: this method has the side effect of changing the order of
     * the elements in the arrays given as input. </p> --November 3st 2003,
     * Ricardo Silva
     *
     * @param a lower bounds (use Double.NEGATIVE_INFINITY if necessary)
     * @param b upper bounds (use Double.POSITIVE_INFINITY if necessary)
     */
    public static double multinormalProb(double a[], double b[],
            double cov[][]) {
        assert a.length == b.length;
        for (int i = 0; i < a.length; i++) {
            assert a[i] <= b[i];
        }
        /*for (int i = 0; i < a.length; i++)
            System.out.println("a = " + a[i] + ", b = " + b[i]);
        for (int i = 0; i < cov.length; i++) {
             for (int j = 0; j < cov.length; j++)
                 System.out.print(cov[i][j] + "\t");
            System.out.println();
        }
        System.out.println();*/
        orderIntegral(a, b, cov);
        /*for (int i = 0; i < a.length; i++)
            System.out.println("a = " + a[i] + ", b = " + b[i]);
        for (int i = 0; i < cov.length; i++) {
             for (int j = 0; j < cov.length; j++)
                 System.out.print(cov[i][j] + "\t");
            System.out.println();
        }
        System.exit(0);*/
        double c[][] = MatrixUtils.cholesky(cov);
        double d[] = new double[a.length], e[] = new double[a.length], f[] =
                new double[a.length];
        double w[] = new double[a.length - 1], y[] = new double[a.length - 1];
        double intSum = 0., varSum = 0., error, auxSum;
        int n = 0;
        RandomUtil r = RandomUtil.getInstance();

        if (a[0] == Double.NEGATIVE_INFINITY) {
            d[0] = 0.;
        }
        else {
            d[0] = ProbUtils.normalCdf(a[0] / c[0][0]);
        }
        if (b[0] == Double.POSITIVE_INFINITY) {
            e[0] = 1.;
        }
        else {
            e[0] = ProbUtils.normalCdf(b[0] / c[0][0]);
        }
        f[0] = e[0] - d[0];

        do {
            for (int i = 0; i < w.length; i++) {
                w[i] = r.nextDouble();
            }
            for (int i = 1; i < a.length; i++) {
                double quant = d[i - 1] + w[i - 1] * (e[i - 1] - d[i - 1]);
                if (quant == 1.) {
                    y[i - 1] = Double.MAX_VALUE;
                }
                else if (quant == 0.) {
                    y[i - 1] = -Double.MAX_VALUE;
                }
                else {
                    y[i - 1] = ProbUtils.normalQuantile(
                            d[i - 1] + w[i - 1] * (e[i - 1] - d[i - 1]));
                }
                auxSum = 0.;
                for (int j = 0; j < i; j++) {
                    auxSum += c[i][j] * y[j];
                }
                if (a[i] == Double.NEGATIVE_INFINITY) {
                    d[i] = 0.;
                }
                else {
                    d[i] = ProbUtils.normalCdf((a[i] - auxSum) / c[i][i]);
                }
                if (b[i] == Double.POSITIVE_INFINITY) {
                    e[i] = 1.;
                }
                else {
                    e[i] = ProbUtils.normalCdf((b[i] - auxSum) / c[i][i]);
                }
                f[i] = (e[i] - d[i]) * f[i - 1];
            }
            intSum += f[f.length - 1];
            varSum += f[f.length - 1] * f[f.length - 1];
            n++;
            //error = 2.5 * Math.sqrt((varSum / n - (intSum / n) * (intSum / n)) / n);
            error = 2;
        } while ((error > 0.0001 && n < 5000) || n < 50);
        return intSum / n;
    }

    private static void orderIntegral(double a[], double b[], double cov[][]) {
        double temp1, temp2, temp3, temp4;
        int idx1, idx2;
        for (int i = 0; i < a.length - 1; i++) {
            int smallest = i;
            for (int j = i + 1; j < a.length; j++) {
                if (Double.isInfinite(a[j]) && Double.isInfinite(a[smallest])) {
                    if (b[j] < b[smallest]) {
                        smallest = j;
                    }
                }
                else
                if (Double.isInfinite(b[j]) && Double.isInfinite(b[smallest])) {
                    if (a[j] > a[smallest]) {
                        smallest = j;
                    }
                }
                else
                if (Double.isInfinite(a[smallest]) && Double.isInfinite(b[j])) {
                    if (b[smallest] <= 0. && a[j] >= 0. && a[j] > -b[smallest])
                    {
                        smallest = j;
                    }
                    else if (b[smallest] >= 0. && a[j] >= 0.) {
                        smallest = j;
                    }
                    else
                    if (b[smallest] >= 0. && a[j] <= 0. && -a[j] < b[smallest])
                    {
                        smallest = j;
                    }
                }
                else
                if (Double.isInfinite(b[smallest]) && Double.isInfinite(a[j])) {
                    if (a[smallest] <= 0. && b[j] >= 0. && b[j] < -a[smallest])
                    {
                        smallest = j;
                    }
                    else if (a[smallest] <= 0. && b[j] <= 0.) {
                        smallest = j;
                    }
                    else
                    if (a[smallest] >= 0. && b[j] <= 0. && -b[j] > a[smallest])
                    {
                        smallest = j;
                    }
                }
                else if ((Double.isInfinite(a[smallest]) ||
                        Double.isInfinite(b[smallest])) &&
                        !Double.isInfinite(a[j]) && !Double.isInfinite(b[j])) {
                    smallest = j;
                }
                else if (!Double.isInfinite(a[smallest]) &&
                        !Double.isInfinite(b[smallest]) &&
                        !Double.isInfinite(a[j]) && !Double.isInfinite(b[j]) &&
                        Math.abs(b[j] - a[j]) <
                                Math.abs(b[smallest] - a[smallest])) {
                    smallest = j;
                }
            }
            temp1 = a[i];
            temp2 = b[i];
            a[i] = a[smallest];
            b[i] = b[smallest];
            a[smallest] = temp1;
            b[smallest] = temp2;
            if (smallest > i) {
                idx1 = smallest;
                idx2 = i;
            }
            else if (smallest < i) {
                idx1 = i;
                idx2 = smallest;
            }
            else {
                continue;
            }
            temp1 = cov[idx1][idx2];
            temp3 = cov[idx1][idx1];
            temp4 = cov[idx2][idx2];
            for (int j = 0; j < cov.length; j++) {
                temp2 = cov[idx1][j];
                cov[j][idx1] = cov[idx1][j] = cov[idx2][j];
                cov[j][idx2] = cov[idx2][j] = temp2;
            }
            cov[idx2][idx1] = cov[idx1][idx2] = temp1;
            cov[idx1][idx1] = temp4;
            cov[idx2][idx2] = temp3;
        }
    }

}





