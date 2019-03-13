package edu.cmu.tetrad.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stores descriptions of the parameters for the simulation box. All parameters
 * that go into the interface need to be described here.
 *
 * @author jdramsey
 */
public class ParamDescriptionsCopy {

    private static final ParamDescriptionsCopy INSTANCE = new ParamDescriptionsCopy();

    private final Map<String, ParamDescription> map = new HashMap<>();

    private ParamDescriptionsCopy() {
        map.put("numMeasures", new ParamDescription("Number of measured variables (min = 1)", 10, 1, Integer.MAX_VALUE));
        map.get("numMeasures").setLongDescription("A measured variable is one for which values are recorded in the dataset being analyzed. " +
                "This parameter sets the number of measured variables to be randomly simulated; this will be the number " +
                "of columns in the dataset.");

        map.put("numLatents", new ParamDescription("Number of latent variables (min = 0)", 0, 0, Integer.MAX_VALUE));
        map.get("numLatents").setLongDescription("A latent (or ‘unmeasured’) variable is one for which values are not recorded in the dataset being analyzed. " +
                "These are variables that affect the measured variables but which are not included in the final dataset. " +
                "This situation comes up frequently when analyzing real data, so it is important to be able to generate " +
                "random datasets with this feature, in order to see how the algorithms react to the existence of latent " +
                "variables. Some algorithms, like FCI, FFCI, BPC, FOFC, FTFC, and such, are correct when some variables " +
                "not measured affect the data.");


        map.put("avgDegree", new ParamDescription("Average degree of graph (min = 1)", 2, 1, Integer.MAX_VALUE));
        map.get("avgDegree").setLongDescription("The average degree of a graph is equal to 2E / V, where E is the number of edges " +
                "in the graph and V the number of variables (vertices) in the graph, since each edge has two endpoints. " +
                "This allows one to have control over the density of randomly generated graphs. The default average " +
                "degree is 2, which corresponds to a graph in which there are the same number of edges as nodes. For " +
                "denser graphs, larger average degrees can be specified.");


        map.put("maxDegree", new ParamDescription("The maximum degree of the graph (min = -1)", 10, -1, Integer.MAX_VALUE));
        map.get("maxDegree").setLongDescription("It is possible for a random graph to have a single node with very high degree—i.e. " +
                "number of adjacent edges. This parameter places an upper bound on the maximum such degree. If no limit " +
                "is to be placed on the maximum degree, use the value -1.");

        map.put("maxIndegree", new ParamDescription("Maximum indegree of graph (min = 1)", 100, 1, Integer.MAX_VALUE));
        map.get("maxIndegree").setLongDescription("It is possible for a random graph to have a node in which there is " +
                "a very large “indegree”—that is, number of parents, or number of edges into that node. This parameter " +
                "places a bound on the maximum such indegree. If no limit is to be placed on the maximum indegree, " +
                "use the value -1.");


        map.put("maxOutdegree", new ParamDescription("Maximum outdegree of graph (min = 1)", 100, 1, Integer.MAX_VALUE));
        map.get("maxOutdegree").setLongDescription("It is possible for a random graph to have a node in which there " +
                "is a very large “outdegree”—that is, number of children, or number of edges out of that node. This " +
                "parameter places a bound on the maximum such outdegree. If no limit is to be placed on the max " +
                "outdegree, use the value -1.");

        map.put("connected", new ParamDescription("Yes if graph should be connected", false));
        map.get("connected").setLongDescription("It is possible to generate a random graph in which paths exists from " +
                "every node to every other. This places some constraints on how the graph may be generated, but it is " +
                "feasible in most cases. Setting this flag to “Yes” generates connected graphs.");

        map.put("sampleSize", new ParamDescription("Sample size (min = 1)", 1000, 1, Integer.MAX_VALUE));
        map.get("sampleSie").setLongDescription("One of the main features of a dataset is the number of records in " +
                "the data, or sample size, or N. When simulating random data, this parameter determines now many " +
                "records should be generated for the data. The minimum number of records is 1; the default is set to 1000.");


        map.put("numRuns", new ParamDescription("Number of runs (min = 1)", 1, 1, Integer.MAX_VALUE));
        map.get("numRuns").setLongDescription("An analysis(randomly pick graph, randomly simulate a dataset, run an " +
                "algorithm on it, look at the result) may be run over and over again, repeatedly, and results summarized. " +
                "This parameter indicates the number of repetitions that should be done for the analysis. The minimum is 1.");

        map.put("differentGraphs", new ParamDescription("Yes if a different graph should be used for each run", false));
        map.get("differentGraphs").setLongDescription("When doing an analysis where, repeatedly, a random graph is chosen, with " +
                "some further processing downstream, one may either keep using the same graph (with different simulated " +
                "random datasets based on that graph) or pick a new graph every time. This parameter determines that " +
                "behavior; if ‘Yes’ a new graph is chosen every time; if ‘No’, the same graph is always used.");

        map.put("alpha", new ParamDescription("Cutoff for p values (alpha) (min = 0.0)", 0.01, 0.0, 1.0));
        map.get("alpha").setLongDescription("Statistical tests often compare a test statistic to a distribution and " +
                "make a judgment that the null hypothesis has been rejected based on whether the area in the tails " +
                "for the distribution for that test statistic is greater than some cutoff alpha. For tests of independence, " +
                "for instance, a lower alpha level makes it easier to judge independence, and a higher alpha makes it " +
                "harder to judge independence. Thus, a lower alpha for a search generally results in a sparser graph.");

        map.put("penaltyDiscount", new ParamDescription("Penalty discount (min = 0.0)", 2.0, 0.0, Double.MAX_VALUE));
        map.get("penaltyDiscount").setLongDescription("A BIC score is of the form 2L – k ln N, where L is the likelihood," +
                " k the number of degrees of freedom, and N the sample size. Tests based on this statistic can often yield " +
                "too dense a graph; to compensate for this, we add a factor c, as follows: 2L – c k ln N, where usually " +
                "c >= 1. If c is chosen to be greater than 1, say 2, the output graph will be sparser. We call this c " +
                "the “penalty discount”; similar mechanisms have been proposed elsewhere.");

        map.put("standardize", new ParamDescription("Yes if the data should be standardized", false));
        map.get("standardize").setLongDescription("An operation one often wants to perform on a dataset is to standardize " +
                "each of its variables by subtracting the mean and dividing the standard deviation. This yields a " +
                "dataset in which each variable has mean zero and unit variance. If this parameter is set to ‘Yes’," +
                " this operation will be performed on the simulated dataset.");

        map.put("measurementVariance", new ParamDescription("Additive measurement noise variance (min = 0.0)", 0.0, 0, Double.MAX_VALUE));
        map.get("measurementVariance").setLongDescription("One difficult problem one encounters for analyzing real data " +
                "is measurement noise—that is, once the actual values V of a variable are determined, there is some " +
                "additional noise M added on top of that, so that the effect value is V + M, not V. We will assume here " +
                "what is often assumed, that this noise is additive and Gaussian, with some variance. If this parameter " +
                "value is set to zero, no measurement noise is added to the dataset per variable. Otherwise, if the " +
                "value is greater than zero, independent Gaussian noise will be added with mean zero and the given variance.");

        map.put("depth", new ParamDescription("Maximum size of conditioning set (unlimited = -1)", -1, -1, Integer.MAX_VALUE));
        map.get("depth").setLongDescription("This variable is usually called “depth” for algorithms such as PC in " +
                "which conditioning sets are considered of increasing size from zero up to some limit, called “depth”. " +
                "For example, if depth = 3, conditioning sets will be considered of sizes 0, 1, 2, and 3. In order to " +
                "express that no limit should be imposed, use the value -1.");

        map.put("meanLow", new ParamDescription("Low end of mean range (min = 0.0)", 0.5, 0.0, Double.MAX_VALUE));
        map.get("meanLow").setLongDescription("For a linear model, means of variables may be randomly shifted. " +
                "The default is for there to be no shift, but shifts from a minimum value to a maximum value may be " +
                "specified. The minimum must be less than or equal to the maximum.");

        map.put("meanHigh", new ParamDescription("High end of mean range (min = 0.0)", 1.5, 0.0, Double.MAX_VALUE));
        map.get("meanHigh").setLongDescription("For a linear model, means of variables may be randomly shifted. " +
                "The default is for there to be no shift, but shifts from a minimum value to a maximum value may " +
                "be specified. The minimum must be less than or equal to the maximum.");

        map.put("coefLow", new ParamDescription("Low end of coefficient range (min = 0.0)", 0.2, 0.0, Double.MAX_VALUE));
        map.get("coefLow").setLongDescription("When simulating data from linear models, one needs to specify the " +
                "distribution of the coefficient parameters. Here, we draw coefficients from U(-m2, -m1) U U(m1, m2);" +
                " m1 is what is being called the “low end of the coefficient range” and has a minimum value of 0.");

        map.put("coefHigh", new ParamDescription("High end of coefficient range (min = 0.0)", 0.7, 0.0, Double.MAX_VALUE));
        map.get("coefHigh").setLongDescription("When simulating data from linear models, one needs to specify the " +
                "distribution of the coefficient parameters. Here, we draw coefficients from U(-m2, -m1) U U(m1, m2);" +
                " m2 is what is being called the “high end of the coefficient range” and must be greater than m1.");

        map.put("covLow", new ParamDescription("Low end of covariance range (min = 0.0)", 0.5, 0.0, Double.MAX_VALUE));
        map.get("covLow").setLongDescription("When simulating data from linear models, one needs to specify the " +
                "distribution of the covariance parameters. Here, we draw coefficients from U(-c2, -c1) U U(c1, c2); " +
                "c1 is what is being called the “low end of the covariance range” and has a minimum value of 0. " +
                "The default value is 0.5.");

        map.put("covHigh", new ParamDescription("High end of covariance range (min = 0.0)", 1.5, 0.0, Double.MAX_VALUE));
        map.get("covHigh").setLongDescription("When simulating data from linear models, one needs to specify the " +
                "distribution of the covariance parameters. Here, we draw coefficients from U(-c2, -c1) U U(c1, c2); " +
                "c2 is what is being called the “high end of the covariance range” and must be greater  than c1. The " +
                "default value is 1.5.");

        map.put("varLow", new ParamDescription("Low end of variance range (min = 0.0)", 1.0, 0.0, Double.MAX_VALUE));
        map.get("varLow").setLongDescription("When simulating data from linear models, one needs to specify the " +
                "distribution of the variance parameters. Here, we draw coefficients from U(v1, v2); v1 is what is " +
                "being called the “low end of the variance range” and has a minimum 0. The default value is 1.0.");

        map.put("varHigh", new ParamDescription("High end of variance range (min = 0.0)", 3.0, 0.0, Double.MAX_VALUE));
        map.get("varHigh").setLongDescription("When simulating data from linear models, one needs to specify " +
                "the distribution of the variance parameters. Here, we draw coefficients from U(v1, v2); v2 is" +
                " what is being called the “high end of the variance range” and must be greater than v1. The " +
                "default value is 3.0.");

        map.put("dataType", new ParamDescription("Categorical or discrete", "categorical"));
        map.get("dataType").setLongDescription("For a mixed data type simulation, if this is set to “categorical” or " +
                "“discrete”, all variables are taken to be of that sort. Used internally to the program.");

        map.put("percentDiscrete", new ParamDescription("Percentage of discrete variables (0 - 100) for mixed data", 0.0, 0.0, 100.0));
        map.get("percentDiscrete").setLongDescription("For a mixed data type simulation, specifies the percentage of " +
                "variables that should be simulated (randomly) as discrete. The rest will be taken to be continuous. " +
                "The default is 0—i.e. no discrete variables.");

        map.put("numCategories", new ParamDescription("Number of categories for discrete variables (min = 2)", 4, 2, Integer.MAX_VALUE));
        map.get("numCategories").setLongDescription("The number of categories to be used for randomly generated discrete " +
                "variables. The default is 4; the minimum is 2.");

        map.put("minCategories", new ParamDescription("Minimum number of categories (min = 2)", 2, 2, Integer.MAX_VALUE));
        map.get("minCategories").setLongDescription("The minimum number of categories to be used for randomly generated " +
                "discrete variables. The default is 2.");

        map.put("maxCategories", new ParamDescription("Maximum number of categories (min = 2)", 2, 2, Integer.MAX_VALUE));
        map.get("maxCategories").setLongDescription("The maximum number of categories to be used for randomly generated " +
                "discrete variables. The default is 2. This needs to be greater or equal to than the minimum number " +
                "of categories.");

        map.put("samplePrior", new ParamDescription("Sample prior (min = 1.0)", 1.0, 1.0, Double.MAX_VALUE));
        map.get("samplePrior").setLongDescription("For the BDeu score, this sets the prior equivalent sample size. This " +
                "number is added to the sample size for each conditional probability table in the model and is divided " +
                "equally among the cells in the table.");

        map.put("structurePrior", new ParamDescription("Structure prior coefficient (min = 0.0)", 1.0, 0.0, Double.MAX_VALUE));
        map.get("structurePrior").setLongDescription("For Tetrad, we use as a structure prior the default number of " +
                "parents for any conditional probability table. Higher weight is accorded to tables with about that " +
                "number of parents. The prior structure weights are distributed according to a binomial distribution.");

        map.put("mgmParam1", new ParamDescription("MGM tuning parameter #1 (min = 0.0)", 0.1, 0.0, Double.MAX_VALUE));
        map.get("mgmParam1").setLongDescription("The MGM algorithm has three internal tuning parameters, of which this is one.");

        map.put("mgmParam2", new ParamDescription("MGM tuning parameter #2 (min = 0.0)", 0.1, 0.0, Double.MAX_VALUE));
        map.get("mgmParam2").setLongDescription("The MGM algorithm has three internal tuning parameters, of which this is one.");

        map.put("mgmParam3", new ParamDescription("MGM tuning parameter #3 (min = 0.0)", 0.1, 0.0, Double.MAX_VALUE));
        map.get("mgmParam3").setLongDescription("The MGM algorithm has three internal tuning parameters, of which this is one.");

        map.put("scaleFreeAlpha", new ParamDescription("For scale-free graphs, the parameter alpha (min = 0.0)", 0.05, 0.0, 1.0));
        map.get("scaleFreeAlpha").setLongDescription("We use the algorithm for generating scale free graphs described in B. " +
                "Bollobas,C. Borgs, J. Chayes, and O. Riordan, Directed scale-free graphs, Proceedings of the " +
                "fourteenth annual ACM-SIAM symposium on Discrete algorithms, 132--139, 2003. Please see this article " +
                "for a description of the parameters.");

        map.put("scaleFreeBeta", new ParamDescription("For scale-free graphs, the parameter beta (min = 0.0)", 0.9, 0.0, 1.0));
        map.get("scaleFreeBeta").setLongDescription("We use the algorithm for generating scale free graphs described in B. " +
                "Bollobas,C. Borgs, J. Chayes, and O. Riordan, Directed scale-free graphs, Proceedings of the " +
                "fourteenth annual ACM-SIAM symposium on Discrete algorithms, 132--139, 2003. Please see this article " +
                "for a description of the parameters.");

        map.put("scaleFreeDeltaIn", new ParamDescription("For scale-free graphs, the parameter delta_in (min = 0.0)", 3, 0.0, Double.MAX_VALUE));
        map.get("scaleFreeDeltaIn").setLongDescription("We use the algorithm for generating scale free graphs described in B. Bollobas,C. Borgs, " +
                "J. Chayes, and O. Riordan, Directed scale-free graphs, Proceedings of the fourteenth annual ACM-SIAM symposium on " +
                "Discrete algorithms, 132--139, 2003. Please see this article for a description of the parameters.");

        map.put("scaleFreeDeltaOut", new ParamDescription("For scale-free graphs, the parameter delta_out (min = 0.0)", 3, 0.0, Double.MAX_VALUE));
        map.get("scaleFreeDeltaOut").setLongDescription("We use the algorithm for generating scale free graphs described in B. " +
                "Bollobas,C. Borgs, J. Chayes, and O. Riordan, Directed scale-free graphs, Proceedings of the " +
                "fourteenth annual ACM-SIAM symposium on Discrete algorithms, 132--139, 2003. Please see this article " +
                "for a description of the parameters.");

        map.put("generalSemFunctionTemplateMeasured", new ParamDescription("General function template for measured variables", "TSUM(NEW(B)*$)"));
        map.get("generalSemFunctionTemplateMeasured").setLongDescription("This template specifies how equations for " +
                "measured variables are to be generated. For help in constructing such templates, see the Generalized SEM PM model.");

        map.put("generalSemFunctionTemplateLatent", new ParamDescription("General function template for latent variables", "TSUM(NEW(B)*$)"));
        map.get("generalSemFunctionTemplateLatent").setLongDescription("This template specifies how equations for latent " +
                "variables are to be generated. For help in constructing such templates, see the Generalized SEM PM model.");

        map.put("generalSemErrorTemplate", new ParamDescription("General function for error terms", "Beta(2, 5)"));
        map.get("generalSemErrorTemplate").setLongDescription("This template specifies how distributions for error terms " +
                "are to be generated. For help in constructing such templates, see the Generalized SEM PM model.");

        map.put("generalSemParameterTemplate", new ParamDescription("General function for parameters", "Split(-1.0, -0.5, 0.5, 1.0)"));
        map.get("generalSemParameterTemplate").setLongDescription("This template specifies how distributions for parameter " +
                "terms are to be generated. For help in constructing such templates, see the Generalized SEM PM model.");

        map.put("coefSymmetric", new ParamDescription("Yes if negative coefficient values should be considered", true));
        map.get("coefSymmetric").setLongDescription("Usually coefficient values for linear models are chosen from U(-b, -a) " +
                "U U(a, b) for some a, b; this is called the “symmetric” model (symmetric about zero). If only positive " +
                "values should be considered, this parameter should be set to false (“No” selected),");

        map.put("covSymmetric", new ParamDescription("Yes if negative covariance values should be considered", true));
        map.get("covSymmetric").setLongDescription("\n" +
                "Usually covariance values are chosen from U(-b, -a) U U(a, b) for some a, b; this is called the " +
                "“symmetric” model (symmetric about zero). If only positive values should be considered, this " +
                "parameter should be set to false (“No” selected)\n");


        map.put("randomSelectionSize", new ParamDescription("The number of datasets that should be taken in each random sample", 1));
        map.get("randomSelectionSize").setLongDescription("This parameter is for algorithms that take multiple datasets " +
                "as input, such as IMaGES. The idea is that maybe you have 100 dataset but want to take a random sample " +
                "of 5 such datasets. This parameter, in this example, is ‘5’. It is the number of dataset that should be " +
                "taken in each random sample of datasets.");



        map.put("maxit", new ParamDescription("MAXIT parameter (GLASSO) (min = 1)", 10000, 1, Integer.MAX_VALUE));
        map.get("maxit").setLongDescription("The R Fortan implementation of GLASSO (https://CRAN.R-project.org/package=glasso) " +
                "includes a number of parameters, of which this is one. This is the maximum number of iterations " +
                "of the optimization loop.");

        map.put("ia", new ParamDescription("IA parameter (GLASSO)", false));
        map.get("ia").setLongDescription("The R Fortan implementation of GLASSO (https://CRAN.R-project.org/package=glasso) " +
                "includes a number of parameters, of which this is one. This is the maximum number of " +
                "iterations of the optimization loop.");

        map.put("is", new ParamDescription("IS parameter (GLASSO)", false));
        map.get("is").setLongDescription("The R Fortan implementation of GLASSO (https://CRAN.R-project.org/package=glasso) " +
                "includes a number of parameters, of which this is one. This is the maximum number of " +
                "iterations of the optimization loop.");

        map.put("itr", new ParamDescription("ITR parameter (GLASSO)", false));
        map.get("itr").setLongDescription("The R Fortan implementation of GLASSO (https://CRAN.R-project.org/package=glasso) " +
                "includes a number of parameters, of which this is one. This is the maximum number of " +
                "iterations of the optimization loop.");

        map.put("ipen", new ParamDescription("IPEN parameter (GLASSO)", false));
        map.get("ipen").setLongDescription("The R Fortan implementation of GLASSO (https://CRAN.R-project.org/package=glasso) " +
                "includes a number of parameters, of which this is one. This is the maximum number of " +
                "iterations of the optimization loop.");

        map.put("thr", new ParamDescription("THR parameter (GLASSO) (min = 0.0)", 1e-4, 0.0, Double.MAX_VALUE));
        map.get("thr").setLongDescription("The R Fortan implementation of GLASSO (https://CRAN.R-project.org/package=glasso) " +
                "includes a number of parameters, of which this is one. This is the maximum number of " +
                "iterations of the optimization loop.");

        map.put("targetName", new ParamDescription("Target variable name", ""));
        map.get("targetName").setLongDescription("This parameter is for searches, such as Markov blanket searches, that " +
                "require a target variable. In the case of a Markov blanket search, one is searching the graph " +
                "over the Markov blanket of some target variable named V—this parameter specifies the name ‘V’.");

        map.put("verbose", new ParamDescription("Yes if verbose output should be printed or logged", true));
        map.get("verbose").setLongDescription("If this parameter is set to ‘Yes’, extra (“verbose”) output will be " +
                "printed if available giving some details about the step-by-step operation of the algorithm.");

        map.put("faithfulnessAssumed", new ParamDescription("Yes if (one edge) faithfulness should be assumed", true));
        map.get("faithfulnessAssumed").setLongDescription("This is a parameter for FGES (“Fast GES”). If this is set to " +
                "‘Yes’, it will be assumed that if X _||_ Y, by an independence test, then X _||_ Y | Z for nonempty Z. " +
                "If the model is faithful to the data, this will necessarily be the case. However, there are some " +
                "non-faithful examples one can propose where this is not the case. If one is worried about this kind " +
                "of unfaithfulness, one should set this parameter to ‘No’. If one is willing to tolerate this kind of " +
                "unfaithfulness, then setting this parameter to ‘Yes’ leads to significantly faster searches.");

        map.put("useWishart", new ParamDescription("Yes if the Wishart test shoud be used. No if the Delta test should be used", false));
        map.get("useWishart").setLongDescription("This is a parameter for the FOFC (Find One Factor Clusters) algorithm. " +
                "There are two tests implemented there for testing for tetrads being zero, Wishart and Delta. This " +
                "parameter picks which of these tests should be use: ‘Yes’ for Wishart and ‘No’ for Delta.");

        map.put("useGap", new ParamDescription("Yes if the GAP algorithms should be used. No if the SAG algorithm should be used", false));
        map.get("useGap").setLongDescription("This is a parameter for FOFC (Find One Factor Clusters). There are two " +
                "procedures implemented for growing pure clusters of variables. In principle they give the same answer, " +
                "but in practice they could give different answers. The first is GAP, “Grow and Pick”, where you specify " +
                "all the possible initial sets, grown them all to their maximum sizes, and pick a set of non-overlapping " +
                "such largest sets from these. The second is SAG, “Seed and Grow”, where you grow pure clusters one at a " +
                "time, excluding variables found in earlier clusters from showing up in later ones. This parameter " +
                "specifies which of these algorithms should be used, ‘Yes’ for GAP, ‘No’ for SAG.");

        // Multiple indicator random graphs
        map.put("numStructuralNodes", new ParamDescription("Number of structural nodes", 3));
        map.get("numStructuralNodes").setLongDescription("This is a parameter for generating random multiple indictor " +
                "models (MIMs). A structural node is one of the latent variables in the model; each structural node has " +
                "a number of child measured variables.");

        map.put("numStructuralEdges", new ParamDescription("Number of structural edges", 3));
        map.get("numStructuralEdges").setLongDescription("This is a parameter for generating random multiple indictor " +
                "models (MIMs). A structural edge is an edge connecting two structural nodes.");

        map.put("measurementModelDegree", new ParamDescription("Number of measurements per Latent", 5));
        map.get("measurementModelDegree").setLongDescription("Each structural node in the MIM will be created to have " +
                "this many measured children.");

        map.put("latentMeasuredImpureParents", new ParamDescription("Number of Latent --> Measured impure edges", 0));
        map.get("latentMeasuredImpureParents").setLongDescription("It is possible for structural nodes to have as " +
                "children measured variables that are children of other structural nodes. These edges in the graph will " +
                "be considered impure.");

        map.put("measuredMeasuredImpureParents", new ParamDescription("Number of Measured --> Measured impure edges", 0));
        map.get("measuredMeasuredImpureParents").setLongDescription("It is possible for measures from two different " +
                "structural nodes to have directed edges between them. These edges will be considered to be impure.");

        map.put("measuredMeasuredImpureAssociations", new ParamDescription("Number of Measured <-> Measured impure edges", 0));
        map.get("measuredMeasuredImpureAssociations").setLongDescription("It is possible for measures from two different " +
                "structural nodes to be confounded. These confounding (bidirected) edges will be considered to be impure.");


//        map.put("useRuleC", new ParamDescription("Yes if rule C for CCD should be used", false));
        map.put("applyR1", new ParamDescription("Yes if the orient away from arrow rule should be applied", true));
        map.get("applyR1").setLongDescription("The Orient Away from Arrow rule is usually applied for PC if there is a " +
                "structure X->Y—Z to yield X->Y->Z, to avoid the creation of a collider known not to exist. Set this " +
                "parameter to “No” if a chain of directed edges pointing in the same direction when only the first few " +
                "such orientations are justified based on the data.");

        map.put("probCycle", new ParamDescription("The probability of adding a cycle to the graph", 1.0, 0.0, 1.0));
        map.get("probCycle").setLongDescription("One way to add cycles to a graph is to pick a group of 3, 4, or 5 nodes " +
                "and create a cycle between those variables. A graph may be constructed in this way consisting entirely of " +
                "cycles, and this graph may then be used to test algorithms that should be able to handle cycles. This " +
                "parameter sets the probability that any particular such set of nodes will be used to form a cycle in the graph.");

        map.put("intervalBetweenShocks", new ParamDescription("Interval beween shocks (R. A. Fisher simulation model) (min = 1)", 10, 1, Integer.MAX_VALUE));
        map.get("intervalBetweenShocks").setLongDescription("This is a parameter for the linear Fisher option. The idea of " +
                "Fisher model (for the linear case) is to shock the system every so often and let it converge by applying " +
                "the rules of transformation (that is, the linear model) repeatedly until convergence. This sets the number " +
                "of step between shocks.");

        map.put("intervalBetweenRecordings", new ParamDescription("Interval between data recordings for the " +
                "linear Fisher model (min = 1)", 10, 1, Integer.MAX_VALUE));
        map.get("intervalBetweenRecordings").setLongDescription("");

        map.put("skipNumRecords", new ParamDescription("Number of records that should be skipped between recordings (min = 0)", 0, 0, Integer.MAX_VALUE));
        map.get("skipNumRecords").setLongDescription("This is a parameter for the linear Fisher option. The idea of Fisher " +
                "model (for the linear case) is to shock the system every so often and let it converge by applying the " +
                "rules of transformation (that is, the linear model) repeatedly until convergence. Data recordings are " +
                "made every so many steps. This is an additional parameter indicating how many data recordings are " +
                "skipped before actually inserting a record into the returned dataset. This is useful to test the " +
                "reaction of a method to missing time steps.");

        map.put("fisherEpsilon", new ParamDescription("Epsilon where |xi.t - xi.t-1| < epsilon, criterion for convergence", .001, Double.MIN_VALUE, Double.MAX_VALUE));
        map.get("fisherEpsilon").setLongDescription("This is a parameter for the linear Fisher option. The idea of " +
                "Fisher model (for the linear case) is to shock the system every so often and let it converge by " +
                "applying the rules of transformation (that is, the linear model) repeatedly until convergence. This " +
                "sets the criterion for convergence—the process continues until the differences from one time step to " +
                "the next fall below this epsilon.");

        map.put("useMaxPOrientationHeuristic", new ParamDescription(
                "Yes if the heuristic for orienting unshielded colliders for max P should be used",
                false));
        map.get("useMaxPOrientationHeuristic").setLongDescription("\n" +
                "The “Max P” method for orienting an unshielded triple X—Y—Z records p-values for X _||_ Z | S " +
                "for all S in adj(X) or adj(Z), finds the set S0 with the highest p-value, and orients X->Y<-Z " +
                "just in case Y is not in S0. Another way to do the orientation if X and Z are only weakly dependent, " +
                "is to simply see whether the p-value for X _||_ Z | Y is greater than the p-value for X _||_ Z. This " +
                "is the “heuristic” referred to her; the purpose is to speed up the search.\n");

        map.put("maxPOrientationMaxPathLength", new ParamDescription("Maximum path length for the unshielded collider heuristic for max P (min = 0)", 3, 0, Integer.MAX_VALUE));
        map.get("maxPOrientationMaxPathLength").setLongDescription("For the Max P “heuristic” to work, it must be the " +
                "case that X and Z are only weakly associated—that is, that paths between them are not too short. This " +
                "bounds the length of paths for this purpose.");

        map.put("orientTowardDConnections", new ParamDescription(
                "Yes if Richardson's step C (orient toward d-connection) should be used",
                true));
        map.get("orientTowardDConnections").setLongDescription("Please see the description of this algorithm in Thomas " +
                "Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and Cooper eds.");

        map.put("orientVisibleFeedbackLoops", new ParamDescription(
                "Yes if visible feedback loops should be oriented",
                true));
        map.get("orientVisibleFeedbackLoops").setLongDescription("Please see the description of this algorithm in " +
                "Thomas Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour " +
                "and Cooper eds.");

        map.put("doColliderOrientation", new ParamDescription(
                "Yes if unshielded collider orientation should be done",
                true));
        map.get("doColliderOrientation").setLongDescription("Please see the description of this algorithm in Thomas " +
                "Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and Cooper eds.");

        map.put("completeRuleSetUsed", new ParamDescription(
                "Yes if the complete FCI rule set should be used",
                false));
        map.get("completeRuleSetUsed").setLongDescription("For the FCI algorithm, to final orientation rules sets are " +
                "available, one due to P. Spirtes, guaranteeing arrow completeness, and a second due to J. Zhang, guaranteeing " +
                "additional tail completeness. If this parameter is set to “Yes,” the tail-complete rule set will be used.");

        map.put("maxDistinctValuesDiscrete", new ParamDescription("The maximum number of distinct values in a column for discrete variables (min = 0)", 0, 0, Integer.MAX_VALUE));
        map.get("maxDistinctValuesDiscrete").setLongDescription("Discrete variables will be simulated using any " +
                "number of categories from 2 up to this maximum. If set to 0 or 1, discrete variables will not be generated.");

        map.put("twoCycleAlpha", new ParamDescription("Alpha orienting 2-cycles (min = 0.0)", .05, 0.0, 1.0));
        map.get("twoCycleAlpha").setLongDescription("The alpha level of a T-test used to determine where 2-cycles exist " +
                "in the graph. A value of zero turns off 2-cycle detection. ");

        map.put("symmetricFirstStep", new ParamDescription("Yes if the first step step for FGES should do scoring for both X->Y and Y->X", false));
        map.get("symmetricFirstStep").setLongDescription("For discrete searches, and in some other situations, it may " +
                "make a difference for an edge X—Y whether you score X->Y or X<-Y, even though theoretically they should " +
                "have the same score. If this parameter is set to “Yes”, both scores will be calculated and the higher " +
                "score used. (Recall we are calculating BIC as 2L – c k ln N, where c is the penalty discount.)");

        map.put("discretize", new ParamDescription(
                "Yes if continuous variables should be discretized when child is discrete",
                true));
        map.get("discretize").setLongDescription("For the conditional Gaussian likelihood, when scoring X->D, where X " +
                "is continuous and D discrete, it is possible to write out the formula for that longhand, but a fast " +
                "way to do it (and in fact more accurate usually) is to simply discretize X for just those cases. If " +
                "this parameter is set to “Yes”, this discretization will be done.");

        map.put("determinismThreshold", new ParamDescription("Threshold for judging a regression of a variable onto its parents to be deternimistic (min = 0.0)", 0.1, 0.0, Double.POSITIVE_INFINITY));
        map.get("determinismThreshold").setLongDescription("When regressing a child variable onto a set of parent " +
                "variables, one way to test for determinism is to see whether the relevant matrix is singular. We " +
                "may instead ask how close to singular the matrix is; this gives a threshold for this. The default " +
                "value is 0.1.");


        map.put("cgExact", new ParamDescription(
                "Yes if the exact algorithm should be used for continuous parents and discrete children",
                false));
        map.get("cgExact").setLongDescription("For the conditional Gaussian likelihood, if the exact algorithm is " +
                "desired for discrete children and continuous parents, set this parameter to “Yes”.");

        map.put("numCategoriesToDiscretize", new ParamDescription(
                "The number of categories used to discretize continuous variables, if necessary (min = 2)", 3, 2, Integer.MAX_VALUE));
        map.get("numCategoriesToDiscretize").setLongDescription("If the exact algorithm is desired for discrete children " +
                "and continuous parents is not used, the conditional Gaussian likelihood needs to keep a copy of all " +
                "continuous variables on hand, discretized with a certain number of categories. This parameter gives " +
                "the number of categories to use for this second backup copy of the continuous variables.");


        map.put("maxPathLength", new ParamDescription("The maximum length for any discriminating path. -1 if unlimited (min = -1)", -1, -1, Integer.MAX_VALUE));
        map.get("maxPathLength").setLongDescription("See Spirtes, Glymour, and Scheines (2000), Causation, Prediction, " +
                "and Search for the definition of discrimination path. Finding discriminating paths can be expensive. " +
                "This sets the maximum length of such paths that the algorithm tries to find.");


        // Resampling
        map.put("numberResampling", new ParamDescription("The number of bootstraps/resampling iterations (min = 0)", 0, 0, Integer.MAX_VALUE));
        map.get("numberResampling").setLongDescription("For bootstrapping, the number of bootstrap iterations that should be done by " +
                "the algorithm, with results summarized.");

        map.put("percentResampleSize", new ParamDescription("The percentage of resample size (min = 0.1)", 100, 0.1, Double.MAX_VALUE));
        map.get("percentResampleSize").setLongDescription("Each bootstrap iteration uses a certain portion of the data drawn randomly " +
                "either with replacement or without replacement. This parameter specifies the percentage of records in " +
                "the bootstrap (as a percentage of the total original sample size of the data being bootstrapped), in " +
                "the range 1 to 100.");

        map.put("resamplingWithReplacement", new ParamDescription("Yes, if sampling with replacement (bootstrapping)", true));
        map.get("resamplingWithReplacement").setLongDescription("Resampling can be done with replacement or without replacement. If with " +
                "replacement, it is possible to have more than one copy of some of the records in the original " +
                "dataset being included in the bootstrap. This is what is usually meant by “bootstrap”. For this option, " +
                "select “Yes” here. It is also possible to prevent repetitions and do so-called “random subsampling”; " +
                "for this option, select “No” here.");

        map.put("resamplingEnsemble", new ParamDescription("Ensemble method: Preserved (0), Highest (1), Majority (2)", 1, 0, 2));
        map.get("resamplingEnsemble").setLongDescription("This parameter governs how summary graphs are generated based on graphs learned " +
                "from individual bootstrap samples. If “Preserved”, an edge is kept and its orientation is chosen based " +
                "on the highest probability. If “Highest”, an edge is kept the same way the preserved ensemble one does " +
                "except when [no edge]'s probability is the highest one, then the edge is ignored. If “Majority”, the edge " +
                "is kept only if its chosen orientations' probability is more than 0.5. ");

        map.put("addOriginalDataset", new ParamDescription("Yes, if adding an original dataset as another bootstrapping", false));
        map.get("addOriginalDataset").setLongDescription("It has been shown that adding in the algorithm result one would " +
                "get using the original data to those found by the bootstrap method can improve accuracy of summary graphs. Select “Yes” here to include an extra run using the original dataset.");

        // Probabilistic Test THESE NEED TO BE DONE STILL--I DON'T HAVE THE INFO.
        map.put("noRandomlyDeterminedIndependence", new ParamDescription("Yes, if use the cutoff threshold for the independence test.", false));
        map.put("cutoffIndTest", new ParamDescription("Independence cutoff threshold", 0.5, 0.0, 1.0));

        // RB-BSC
        map.put("thresholdNoRandomDataSearch", new ParamDescription("Yes, if use the cutoff threshold for the constraints independence test (stage 1).", false));
        map.put("cutoffDataSearch", new ParamDescription("Independence cutoff threshold", 0.5, 0.0, 1.0));
        map.put("thresholdNoRandomConstrainSearch", new ParamDescription("Yes, if use the cutoff threshold for the meta-constraints independence test (stage 2).", true));
        map.put("cutoffConstrainSearch", new ParamDescription("Constraint-independence cutoff threshold", 0.5, 0.0, 1.0));

        map.put("numRandomizedSearchModels", new ParamDescription("The number of search probabilistic model (min = 1)", 10, 1, Integer.MAX_VALUE));
        map.put("numBscBootstrapSamples", new ParamDescription("The number of bootstrappings drawing from posterior dist. (min = 1)", 50, 1, Integer.MAX_VALUE));
        map.put("lowerBound", new ParamDescription("Lower bound cutoff threshold", 0.3, 0.0, 1.0));
        map.put("upperBound", new ParamDescription("Upper bound cutoff threshold", 0.7, 0.0, 1.0));
        map.put("outputRBD", new ParamDescription("Output graph: Yes: dependent-constraint RB, No: independent-constraint RB.", true));

        //~Resampling

        map.put("fasRule", new ParamDescription(
                "Adjacency search: 1 = PC, 2 = PC-Stable, 3 =f Concurrent PC-Stable",
                1, 1, 3));
        map.get("fasRule").setLongDescription("For variants of PC, one may select either to use the usual PC adjacency search, " +
                "or the procedure from the PC-Stable algorithm (Diego and Maathuis), or the latter using a concurrent " +
                "algorithm (that is, one that runs in parallel on multiple processors).");

        map.put("colliderDiscoveryRule", new ParamDescription(
                "Collider discovery: 1 = Lookup from adjacency sepsets, 2 = Conservative (CPC), 3 = Max-P",
                1, 1, 3));
        map.get("colliderDiscoveryRule").setLongDescription("For variants of PC, one may choose from one of three different ways for " +
                "orienting colliders. One may look them up from sepsets, as in the original PC, or estimate them " +
                "conservatively, as from the Conservative PC algorithm, or by choosing the sepsets with the maximum " +
                "p-value.");

        map.put("conflictRule", new ParamDescription(
                "Collider conflicts: 1 = Overwrite, 2 = Orient bidirected, 3 = Prioritize existing colliders",
                1, 1, 3));
        map.get("conflictRule").setLongDescription("It is not possible to avoid collider orientation conflicts in PC entirely. " +
                "We offer three ways to deal with them. One may use the “overwrite” rule as introduced in the PCALG R package, " +
                "or one may mark all collider conflicts using bidirected edges, or one may prioritize existing colliders, " +
                "ignoring subsequent conflicting information.");

        map.put("randomizeColumns", new ParamDescription(
                "Yes if the order of the columns in each datasets should be randomized",
                false));
        map.get("randomizeColumns").setLongDescription("It is usually the case that for graphs that are faithful to the true model " +
                "the order of the columns in the dataset should not matter; you should always end up with the same " +
                "model. However, in the real world where unfaithfulness is an issue this may not be true. To test the " +
                "resilience of methods to random reordering of the columns in the data, set this parameter to “Yes”.");

        map.put("includePositiveCoefs", new ParamDescription(
                "Yes if positive coefficients should be included in the model",
                true));
        map.get("includePositiveCoefs").setLongDescription("e may include positive coefficients, negative coefficients, or both, " +
                "in the model. To include positive coefficients, set this parameter to “Yes”.");

        map.put("includeNegativeCoefs", new ParamDescription(
                "Yes if negative coefficients should be included in the model",
                true));
        map.get("includeNegativeCoefs").setLongDescription("One may include positive coefficients, negative coefficients, or both, in the " +
                "model. To include negative coefficients, set this parameter to “Yes”.");

        map.put("includePositiveSkewsForBeta", new ParamDescription(
                "Yes if positive skew values should be included in the model, if Beta errors are chosen",
                true));
        map.get("includePositiveSkewsForBeta").setLongDescription("Yes if positive skew values should be included in the model, if Beta errors are chosen.");

        map.put("includeNegativeSkewsForBeta", new ParamDescription(
                "Yes if negative skew values should be included in the model, if Beta errors are chosen",
                true));
        map.get("includeNegativeSkewsForBeta").setLongDescription("Yes if negative skew values should be included in the model, if Beta errors are chosen.");


        map.put("errorsNormal", new ParamDescription(
                "Yes if errors should be Normal; No if they should be Beta",
                true));
        map.get("errorsNormal").setLongDescription("A “quick and dirty” way to generate linear, non-Gaussian data is to " +
                "set this parameter to “No”; then the errors will be sampled from a Beta distribution.");

        map.put("betaLeftValue", new ParamDescription(
                "For Beta(x, y), the 'x'",
                1, 1, Double.POSITIVE_INFINITY));
        map.get("betaLeftValue").setLongDescription("If the errors are Beta(x, y), this is the “x”. (For Gaussian errors, " +
                "this is ignored.)");

        map.put("betaRightValue", new ParamDescription(
                "For Beta(x, y), the 'y'",
                5, 1, Double.POSITIVE_INFINITY));
        map.get("betaRightValue").setLongDescription("If the errors are Beta(x, y), this is the “y”. (For Gaussian errors, " +
                "this is ignored.)");

        map.put("extraEdgeThreshold", new ParamDescription(
                "Threshold for including extra edges",
                0.3, 0.0, Double.POSITIVE_INFINITY));
        map.get("extraEdgeThreshold").setLongDescription("For FASK, this includes an adjacency X—Y in the model if " +
                "|corr(X, Y | X > 0) – corr(X, Y | Y > 0)| > delta, where delta is this number. The default is 0.3. " +
                "Sanchez-Romero, Ramsey et al., (2018) Network Neuroscience.");

        map.get("maskThreshold").setLongDescription("For FASK, this includes an adjacency X—Y in the model if " +
                "|corr(X, Y | X > 0) – corr(X, Y | Y > 0)| > delta, where delta is this number. The default is " +
                "0.3. Sanchez-Romero, Ramsey et al., (2018) Network Neuroscience.");


        map.put("useFasAdjacencies", new ParamDescription(
                "Yes if adjacencies from the FAS search (correlation) should be used",
                true));
        map.get("useFasAdjacencies").setLongDescription("Determines whether adjacencies found by conditional correlation " +
                "should be included in the final model.");

        map.put("useSkewAdjacencies", new ParamDescription(
                "Yes if adjacencies based on skewness should be used",
                true));
        map.get("useSkewAdjacencies").setLongDescription("FASK can use adjacencies X—Y where |corr(X,Y|X>0) – corr(X,Y|Y>0)| " +
                "> threshold. This expression will be nonzero only if there is a path between X and Y; heuristically, if " +
                "the difference is greater than, say, 0.3, we infer an adjacency. To see adjacencies included for this " +
                "reason, set this parameter to “Yes”. Sanchez-Romero, Ramsey et al., (2018) Network Neuroscience.");

        map.put("useCorrDiffAdjacencies", new ParamDescription(
                "Yes if adjacencies from conditional correlation differences should be used",
                true));
        map.get("useCorrDiffAdjacencies").setLongDescription("FASK can use adjacencies X—Y where " +
                "|corr(X,Y|X>0) – corr(X,Y|Y>0)| > threshold. This expression will be nonzero only if there is a path " +
                "between X and Y; heuristically, if the difference is greater than, say, 0.3, we infer an adjacency. " +
                "To see adjacencies included for this reason, set this parameter to “Yes”. Sanchez-Romero, Ramsey et al.," +
                " (2018) Network Neuroscience.");

        map.put("faskDelta", new ParamDescription(
                "Threshold for judging negative coefficient edges as X->Y (range (-1, 0))",
                -0.2, -1.0, 1.0));
        map.get("faskDelta").setLongDescription("For FASK, a dataset dependent threshold that affects accuracy for " +
                "negatively skewed variables, in the range (-1, 0); the default value is -0.2. Sanchez-Romero, " +
                "Ramsey et al., (2018) Network Neuroscience.");

        map.put("faskDelta2", new ParamDescription(
                "Threshold for judging negative coefficient edges as X->Y",
                0.0, Double.NaN, Double.POSITIVE_INFINITY));
        map.get("faskDelta2").setLongDescription("For FASK, a dataset dependent threshold that affects accuracy for " +
                "negatively skewed variables, in the range (-1, 0); the default value is -0.2. " +
                "Sanchez-Romero, Ramsey et al., (2018) Network Neuroscience.");

        map.put("maxIterations", new ParamDescription(
                "The maximum number of iterations the algorithm should go through orienting edges",
                15, 0, Integer.MAX_VALUE));
        map.get("maxIterations").setLongDescription("In orienting, this algorith may go through a number of iterations, " +
                "conditioning on more and more variables until orientations are set. This sets that number.");

        map.put("numLags", new ParamDescription(
                "The number of lags in the time lag model",
                1, 1, Double.POSITIVE_INFINITY));
        map.get("numLags").setLongDescription("A time lag model may take variables from previous time steps into account. " +
                "This determines how many steps back these relevant variables might go.");

        map.put("saveLatentVars", new ParamDescription("Save latent variables.", false));
        map.get("saveLatentVars").setLongDescription("When saving datasets, even though latent variables in simulation " +
                "are supposed to be left out of the data, if one wishes to see what values those latent variables took " +
                "on, one may opt to save the latent variables out with the rest of the data.");

        map.put("probTwoCycle", new ParamDescription(
                "The probability of creating a 2-cycles in the graph (0 - 1)",
                0.0, 0.0, 1.0));
        map.get("probTwoCycle").setLongDescription("The types of bases that we’re using here (Gaussian, Epinechnikov) " +
                "have an infinite number of functions in them, but we’re only using a finite number of them, the most " +
                "significant ones. This parameter specifies how many of the most significant basis functions to use. " +
                "The default is 30.");

        map.put("numBasisFunctions", new ParamDescription(
                "Number of functions to use in (truncated) basis",
                30, 1, Integer.MAX_VALUE));
        map.get("numBasisFunctions").setLongDescription("The types of bases that we’re using here (Gaussian, Epinechnikov) " +
                "have an infinite number of functions in them, but we’re only using a finite number of them, the most " +
                "significant ones. This parameter specifies how many of the most significant basis functions to use. " +
                "The default is 30.");

        map.put("kciCutoff", new ParamDescription(
                "Cutoff",
                6, 1, Integer.MAX_VALUE));
        map.get("kciCutoff").setLongDescription("Cutoff for p-values.");

        map.put("kernelWidth", new ParamDescription(
                "Kernel width",
                1.0, Double.MIN_VALUE, Double.POSITIVE_INFINITY));
        map.get("kernelWidth").setLongDescription("A larger kernel width means that more information will be taken into " +
                "account but possibly less focused information.");

        map.put("kernelMultiplier", new ParamDescription(
                "Bowman and Azzalini (1997) default kernel bandwidhts should be multiplied by...",
                1.0, Double.MIN_VALUE, Double.POSITIVE_INFINITY));
        map.get("kernelMultiplier").setLongDescription("For the conditional correlation algorithm. Bowman, A. W., & " +
                "Azzalini, A. (1997, Applied smoothing techniques for data analysis: the kernel approach with S-Plus " +
                "illustrations (Vol. 18), OUP Oxford), give a formula for default optimal kernel widths. We allow these " +
                "defaults to be multiplied by some factor, which we call the “kernel multiplier”, to capture more or " +
                "less than this optimal signal. This multiplier must be a positive real number.");

        map.put("kernelType", new ParamDescription(
                "Kernel type (1 = Gaussian, 2 = Epinechnikov)",
                2, 1, 2));
        map.get("kernelType").setLongDescription("For CCI, this determine which kernel type will be used (1 = Gaussian, 2 = Epinechnikov).");

        map.put("basisType", new ParamDescription(
                "Basis type (1 = Polynomial, 2 = Cosine)",
                2, 1, 2));
        map.get("basisType").setLongDescription("For CCI, this determines which basis type will be used (1 = Polynomial, 2 = Cosine)");

        map.put("kciNumBootstraps", new ParamDescription(
                "Number of bootstraps for Theorems 4 and Proposition 5 for KCI",
                5000, 1, Integer.MAX_VALUE));
        map.get("kciNumBootstraps").setLongDescription("We have a number of parameters here for the Kernel " +
                "Conditional Independence Test (KCI). In order to understand the parameters, it is necessary to " +
                "read the paper on which this test is based, here: Zhang, K., Peters, J., Janzing, D., & Schölkopf, " +
                "B. (2012). Kernel-based conditional independence test and application in causal discovery. arXiv " +
                "preprint arXiv:1202.3775. This parameter is the number of bootstraps for Theorems 4 and Proposition " +
                "5. The default is 5000; it must be positive integer.");

        map.put("thresholdForNumEigenvalues", new ParamDescription(
                "Threshold to determine how many eigenvalues to use--the lower the more (0 to 1)",
                0.001, 0, Double.POSITIVE_INFINITY));
        map.get("thresholdForNumEigenvalues").setLongDescription("We have a number of parameters here for " +
                "the Kernel Conditional Independence Test (KCI). In order to understand the parameters, it is " +
                "necessary to read the paper on which this test is based, here: Zhang, K., Peters, J., Janzing, D., " +
                "& Schölkopf, B. (2012). Kernel-based conditional independence test and application in causal discovery. " +
                "arXiv preprint arXiv:1202.3775. This parameter is the threshold to determine how many eigenvalues to use--the lower the more (0 to 1). The default value is 0.001; it must be a positive real number.");

        map.put("rcitNumFeatures", new ParamDescription(
                "The number of random features to use",
                10, 1, Integer.MAX_VALUE));
        map.get("rcitNumFeatures").setLongDescription("");

        map.put("kciUseAppromation", new ParamDescription(
                "Use the approximate Gamma approximation algorithm", true));
        map.get("kciUseAppromation").setLongDescription("We have a number of parameters here for the " +
                "Kernel Conditional Independence Test (KCI). In order to understand the parameters, it is " +
                "necessary to read the paper on which this test is based, here: Zhang, K., Peters, J., Janzing, D., " +
                "& Schölkopf, B. (2012). Kernel-based conditional independence test and application in causal discovery. " +
                "arXiv preprint arXiv:1202.3775. If this parameter is set to ‘Yes’, the Gamma approximation algorithm " +
                "is used, as described in this paper; otherwise, the non-approximate procedure is used.");

        map.put("kciEpsilon", new ParamDescription(
                "Epsilon for Proposition 5, a small positive number", 0.001, 0, Double.POSITIVE_INFINITY));
        map.get("kciEpsilon").setLongDescription("We have a number of parameters here for the Kernel Conditional " +
                "Independence Test (KCI). In order to understand the parameters, it is necessary to read the paper " +
                "on which this test is based, here: Zhang, K., Peters, J., Janzing, D., & Schölkopf, B. (2012). " +
                "Kernel-based conditional independence test and application in causal discovery. arXiv preprint " +
                "arXiv:1202.3775. This parameter is the epsilon for Proposition 5, a small positive number. " +
                "The default value is 0.001; it must be a positive real number.");

//        map.put("rcitApproxType", new ParamDescription(
//                "Approximation Type: 1 = LPD4, 2 = Gamma, 3 = HBE, 4 = PERM",
//                4, 1, 4));
//        map.get("rcitApproxType").setLongDescription("");

        map.put("possibleDsepDone", new ParamDescription(
                "Yes if the possible dsep search should be done", true));
        map.get("possibleDsepDone").setLongDescription("This algorithm has a possible d-sep path search, which " +
                "can be time-consuming. See Spirtes, Glymour, and Scheines, Causation, Prediction and Search for " +
                "details.");

        map.put("selfLoopCoef", new ParamDescription(
                "The coefficient for the self-loop (default 0.0)", 0.0, 0.0, Double.POSITIVE_INFINITY));
        map.get("selfLoopCoef").setLongDescription("For simulation time series data, each variable depends on itself " +
                "one time-step back with a linear edge that has this coefficient.");

        // For Two-Step
//        map.put("tsbetathr", new ParamDescription(
//                "Threshold to determine whether a coefficient in the Beta matrix implies an edge int h graph",
//                0.01, 0, Double.POSITIVE_INFINITY));
//        map.get("tsbetathr").setLongDescription("");
//
//        map.put("tstheta", new ParamDescription(
//                "Alasso threshold",
//                1.0, 0, Double.POSITIVE_INFINITY));
//        map.get("tstheta").setLongDescription("");
//
//        map.put("tssigma", new ParamDescription(
//                "ICA threshold",
//                1.0, 0, Double.POSITIVE_INFINITY));
//        map.get("tssigma").setLongDescription("");

        map.put("kernelRegressionSampleSize", new ParamDescription(
                "Minimum sample size to use per conditioning for kernel regression",
                100, 1, Double.POSITIVE_INFINITY));
        map.get("kernelRegressionSampleSize").setLongDescription("Kernel regression for X _||_ Y | Z looks for " +
                "dependencies between X and Y for Z values near to some particular Z values of interest. One can " +
                "find the m nearest points to a given Z = z’ by expanding the search radius until you get that " +
                "many points. This parameter specifies the smallest such set of nearest points on which to allow " +
                "a judgment to be based.");

        map.put("kciAlpha", new ParamDescription("Cutoff for p values (alpha) (min = 0.0)", 0.05, 0.0, 1.0));
        map.get("kciAlpha").setLongDescription("Alpha level (0 to 1)");

        map.put("cciScoreAlpha", new ParamDescription("Cutoff for p values (alpha) (min = 0.0)", 0.01, 0.0, 1.0));
        map.get("cciScoreAlpha").setLongDescription("Alpha level (0 to 1)");

//        map.put("numDependenceSpotChecks", new ParamDescription(
//                "The number of specific <z1,...,zn> values for which to check X _||_ Y | Z = <z1,...,zn>",
//                0, 0,Integer.MAX_VALUE));
//        map.get("numDependenceSpotChecks").setLongDescription("");

        map.put("stableFAS", new ParamDescription(
                "Yes if the 'stable' FAS should be done", false));
        map.get("stableFAS").setLongDescription("In Colombo, D., & Maathuis, M. H. (2014, Order-independent " +
                "constraint-based causal structure learning, The Journal of Machine Learning Research, 15(1), " +
                "3741-3782), a modification of the adjacency search of PC was proposed that results in invariance " +
                "under order permutations of the variables in the data. If this parameter is set to ‘Yes’, this version " +
                "of the PC adjacency search is used.");

        map.put("concurrentFAS", new ParamDescription(
                "Yes if a concurrent FAS should be done", true));
        map.get("concurrentFAS").setLongDescription("Various versions of the PC adjacency search lend themselves to " +
                "concurrent processing—that is, doing different independence tests in parallel to speed up the processing. " +
                "If this parameter is set to ‘Yes’, and this option is available, it will be used.");

//        map.put("stableFASFDR", new ParamDescription(
//                "Yes if the 'stable' FAS should be done with the StableFDR adjustment", false));
//        map.get("stableFASFDR").setLongDescription("");
//
//        map.put("empirical", new ParamDescription(
//                "Yes if skew corrections should be done (\"empirical\")", true));
//        map.get("empirical").setLongDescription("");
//
//        map.put("faskbDelta", new ParamDescription(
//                "Threshold for judging negative coefficient edges as X->Y (range (-1, 0))",
//                -0.7, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
//        map.get("faskbDelta").setLongDescription("");

    }

    public static ParamDescriptionsCopy getInstance() {
        return INSTANCE;
    }

    public ParamDescription get(String name) {
        ParamDescription paramDesc = map.get(name);

        return (paramDesc == null)
                ? new ParamDescription(String.format("Please add a description to ParamDescriptions for %s.", name), 0)
                : paramDesc;
    }

    public void put(String name, ParamDescription paramDescription) {
        map.put(name, paramDescription);
    }

    public Set<String> getNames() {
        return map.keySet();
    }

}