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

package edu.cmu.tetrad.calculator.expression;

import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.*;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.*;

/**
 * Manager for expressions, this includes all implementations of expression
 * descriptors for the calculator.
 *
 * @author Tyler Gibson
 */
public class ExpressionManager {


    /**
     * A mapping from tokens to their descriptors.
     */
    private Map<String, ExpressionDescriptor> tokenMap = new HashMap<>();


    /**
     * List of all the descriptors.
     */
    private List<ExpressionDescriptor> descriptors;

//    private static RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();


    /**
     * Singleton instance.
     */
    private final static ExpressionManager INSTANCE = new ExpressionManager();


    private ExpressionManager() {
        this.descriptors = new ArrayList<>(listDescriptors());
        for (ExpressionDescriptor exp : this.descriptors) {
            if (this.tokenMap.containsKey(exp.getToken())) {
                throw new IllegalStateException("Expression descriptors must have unique tokens, but " + exp.getToken()
                        + " is not unique.");
            }
            this.tokenMap.put(exp.getToken(), exp);
        }
    }

    //===================================== Public Methods ====================================//


    /**
     * @return an instanceo of the manager.
     */
    public static ExpressionManager getInstance() {
        return INSTANCE;
    }

    /**
     * @return a list of all the descriptions.
     */
    public List<ExpressionDescriptor> getDescriptors() {
        return Collections.unmodifiableList(this.descriptors);
    }

    /**
     * @return the descriptor to use for the given token.
     */
    public ExpressionDescriptor getDescriptorFromToken(String token) {
        return this.tokenMap.get(token);
    }

    //======================================= Private methods ===============================//


    /**
     * Builds all the descriptors.
     */
    private static List<ExpressionDescriptor> listDescriptors() {
        List<ExpressionDescriptor> descriptors = new LinkedList<>();

        // For templating. "NEW" has to come before "N".
        descriptors.add(new NewExpressionDescriptor());
        descriptors.add(new TSumExpressionDescriptor());
        descriptors.add(new TProductExpressionDescriptor());
        descriptors.add(new NewExpressionDescriptor2());
        descriptors.add(new TSumExpressionDescriptor2());
        descriptors.add(new TProductExpressionDescriptor2());

        // Mathematical operators.
        descriptors.add(new AdditionExpressionDescriptor());
        descriptors.add(new SubtractionExpressionDescriptor());
        descriptors.add(new MultiplicationExpressionDescriptor());
        descriptors.add(new DivisionExpressionDescriptor());
        descriptors.add(new PowExpressionDescriptor());
        descriptors.add(new PowExpressionDescriptor2());
        descriptors.add(new ExpExpressionDescriptor());
        descriptors.add(new SquareRootExpressionDescriptor());

        // cosh needs to come before cos in parsing. Same for others.
        descriptors.add(new CoshExpressionDescriptor());
        descriptors.add(new SinhExpressionDescriptor());
        descriptors.add(new TanhExpressionDescriptor());

        descriptors.add(new CosExpressionDescriptor());
        descriptors.add(new SinExpressionDescriptor());
        descriptors.add(new TanExpressionDescriptor());

        descriptors.add(new AcosExpressionDescriptor());
        descriptors.add(new AsinExpressionDescriptor());
        descriptors.add(new AtanExpressionDescriptor());

        descriptors.add(new LogisticExpressionDescriptor());
        descriptors.add(new NaturalLogExpressionDescriptor());
        descriptors.add(new Log10ExpressionDescriptor());
        descriptors.add(new RoundExpressionDescriptor());
        descriptors.add(new CeilExpressionDescriptor());
        descriptors.add(new FloorExpressionDescriptor());
        descriptors.add(new AbsoluteValueExpressionDescriptor());
        descriptors.add(new RandomExpressionDescriptor());
        descriptors.add(new MaxExpressionDescriptor());
        descriptors.add(new MinExpressionDescriptor());
        descriptors.add(new SignumExpressionDescriptor());

        descriptors.add(new AndExpressionDescriptor());
        descriptors.add(new OrExpressionDescriptor());
        descriptors.add(new XOrExpressionDescriptor());

        // <= must precede <.
        descriptors.add(new LessThanOrEqualExpressionDescriptor());
        descriptors.add(new LessThanExpressionDescriptor());
        descriptors.add(new EqualsExpressionDescriptor());

        // >= must precede >.
        descriptors.add(new GreaterThanOrEqualExpressionDescriptor());
        descriptors.add(new GreaterThanExpressionDescriptor());
        descriptors.add(new IfExpressionDescriptor());

        descriptors.add(new BetaExpressionDescriptor());
        descriptors.add(new CauchyExpressionDescriptor());
        descriptors.add(new ChiSquareExpressionDescriptor());
        descriptors.add(new ExponentialExpressionDescriptor());
//        descriptors.add(new ExponentialPowerExpressionDescriptor());
        descriptors.add(new FExpressionDescriptor());
        descriptors.add(new GammaExpressionDescriptor());
        descriptors.add(new GumbelExpressionDescriptor());
        descriptors.add(new LaplaceExpressionDescriptor());
        descriptors.add(new LevyExpressionDescriptor());
        descriptors.add(new LogNormalExpressionDescriptor());
        descriptors.add(new NakagamiExpressionDescriptor());
        descriptors.add(new NormalExpressionDescriptor());
        descriptors.add(new NExpressionDescriptor());
        descriptors.add(new ParetoExpressionDescriptor());
        descriptors.add(new PoissonExpressionDescriptor());
        descriptors.add(new SplitExpressionDescriptor());
        descriptors.add(new StudentTExpressionDescriptor());
        descriptors.add(new TriangularExpressionDescriptor());
        descriptors.add(new UniformExpressionDescriptor());
        descriptors.add(new UExpressionDescriptor());
        descriptors.add(new WeibullExpressionDescriptor());
        descriptors.add(new IndicatorExpressionDescriptor());
        descriptors.add(new TruncNormalExpressionDescriptor());
        descriptors.add(new DiscreteExpressionDescriptor());
        descriptors.add(new MixtureDescriptor());

        // AJ
        descriptors.add(new DiscErrorExpressionDescriptor());
        descriptors.add(new SwitchExpressionDescriptor());

//        Collections.sort(descriptor, new Comp());
        return descriptors;
    }

    //================================ Inner class ==============================//

    /**
     * Addition
     */
    private static class AdditionExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public AdditionExpressionDescriptor() {
            super("Addition", "+", Position.BOTH, true);
        }


        public Expression createExpression(final Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length > 0) {
                return new AbstractExpression("+", Position.BOTH, expressions) {
                    static final long serialVersionUID = 23L;

                    public double evaluate(Context context) {
                        double value = 0.0;
                        for (Expression exp : getExpressions()) {
                            value += exp.evaluate(context);
                        }
                        return value;
                    }
                };
            }

            throw new ExpressionInitializationException("Must have at least one argument.");
        }


    }


    /**
     * Addition
     */
    private static class SubtractionExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public SubtractionExpressionDescriptor() {
            super("Subtraction", "-", Position.INFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length == 1) {
                return new AbstractExpression("-", Position.INFIX, expressions) {
                    static final long serialVersionUID = 23L;

                    public double evaluate(Context context) {
                        return -getExpressions().get(0).evaluate(context);
                    }
                };
            } else if (expressions.length == 2) {
                return new AbstractExpression("-", Position.INFIX, expressions) {
                    static final long serialVersionUID = 23L;

                    public double evaluate(Context context) {
                        return getExpressions().get(0).evaluate(context) - getExpressions().get(1).evaluate(context);
                    }
                };
            }

            throw new ExpressionInitializationException("Must two arguments.");
        }
    }


    /**
     * Ceil
     */
    private static class CeilExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public CeilExpressionDescriptor() {
            super("Ceil", "ceil", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Ceil must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("ceil", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.ceil(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class SignumExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public SignumExpressionDescriptor() {
            super("Signum", "signum", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Signum must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("signum", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.signum(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    /**
     * Cosine
     */
    private static class CosExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public CosExpressionDescriptor() {
            super("Cosine", "cos", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Cos must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("cos", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.cos(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class CoshExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public CoshExpressionDescriptor() {
            super("Hyperbolic Cosine", "cosh", Position.PREFIX, false);
        }


        public Expression createExpression(final Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Cosh must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("cosh", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.cosh(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class AcosExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public AcosExpressionDescriptor() {
            super("Arc Cosine", "acos", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Acos must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("acos", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.acos(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }


    /**
     * Flooor.
     */
    private static class FloorExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public FloorExpressionDescriptor() {
            super("Floor", "floor", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Floor must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("floor", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.floor(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    /**
     * Flooor.
     */
    private static class AbsoluteValueExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public AbsoluteValueExpressionDescriptor() {
            super("Abs", "abs", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Floor must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("abs", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.abs(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class Log10ExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public Log10ExpressionDescriptor() {
            super("Log base 10", "log10", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Log10 must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("log10", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.log10(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }


    /**
     * Multiplication.
     */
    private static class MultiplicationExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public MultiplicationExpressionDescriptor() {
            super("Multiplication", "*", Position.BOTH, true);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 2) {
                throw new ExpressionInitializationException("Must have at least two arguments.");
            }
            return new AbstractExpression("*", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double value = 1.0;
                    for (Expression exp : getExpressions()) {
                        value = value * exp.evaluate(context);
                    }
                    return value;
                }
            };
        }

    }

    /**
     * Multiplication.
     */
    private static class DivisionExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public DivisionExpressionDescriptor() {
            super("Division", "/", Position.BOTH, true);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }
            return new AbstractExpression("/", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return getExpressions().get(0).evaluate(context)
                            / getExpressions().get(1).evaluate(context);
                }
            };
        }

    }

    /**
     * Natural log.
     */
    private static class NaturalLogExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public NaturalLogExpressionDescriptor() {
            super("Log base e", "ln", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("log must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("ln", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.log(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    /**
     * Random value.
     */
    private static class RandomExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public RandomExpressionDescriptor() {
            super("Random", "random", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 0) {
                throw new ExpressionInitializationException("Random must have no arguments.");
            }
            return new AbstractExpression("random", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.random();
                }
            };
        }
    }

    /**
     * Round expression.
     */
    private static class RoundExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public RoundExpressionDescriptor() {
            super("Round", "round", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Round must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("round", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.round(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }


    /**
     * Tangent expression.
     */
    private static class TanExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public TanExpressionDescriptor() {
            super("Tangent", "tan", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Tan must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("tan", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.tan(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class TanhExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public TanhExpressionDescriptor() {
            super("Hyperbolic tangent", "tanh", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Hyperbolic tangent must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("tanh", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.tanh(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class AtanExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public AtanExpressionDescriptor() {
            super("Arc Tangent", "atan", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Atan must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("atan", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.atan(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class LogisticExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public LogisticExpressionDescriptor() {
            super("Logistic", "logistic", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Logistic function must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("logistic", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double t = getExpressions().get(0).evaluate(context);
                    return 1.0 / (1.0 + Math.exp(-t));
                }
            };
        }
    }

    /**
     * Square Root.
     */
    private static class SquareRootExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public SquareRootExpressionDescriptor() {
            super("Square Root", "sqrt", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Square Root must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("sqrt", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.sqrt(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    /**
     * Sine expression.
     */
    private static class SinExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public SinExpressionDescriptor() {
            super("Sine", "sin", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Sine must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("sin", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.sin(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class SinhExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public SinhExpressionDescriptor() {
            super("Sinh", "sinh", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Sinh must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("sinh", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.sinh(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    private static class AsinExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public AsinExpressionDescriptor() {
            super("Arc Sine", "asin", Position.PREFIX, false);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Asin must have one and only one" +
                        " argument.");
            }
            return new AbstractExpression("asin", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Math.asin(getExpressions().get(0).evaluate(context));
                }
            };
        }
    }

    /**
     * Tyler didn't document this.
     *
     * @author Tyler Gibson
     */
    private static class PowExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public PowExpressionDescriptor() {
            super("Power", "pow", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Pow must have two arguments.");
            }

            return new AbstractExpression("pow", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    Expression exp1 = getExpressions().get(0);
                    Expression exp2 = getExpressions().get(1);

                    return Math.pow(exp1.evaluate(context), exp2.evaluate(context));
                }
            };
        }
    }

    private static class PowExpressionDescriptor2 extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public PowExpressionDescriptor2() {
            super("Power", "^", Position.INFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Pow must have two arguments.");
            }

            return new AbstractExpression("^", Position.INFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    Expression exp1 = getExpressions().get(0);
                    Expression exp2 = getExpressions().get(1);

                    return /*signum(exp1.evaluate(context)) **/ Math.pow(exp1.evaluate(context), exp2.evaluate(context));
                }
            };
        }
    }

    private static class ExpExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public ExpExpressionDescriptor() {
            super("Exponential", "exp", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Exp must have one argument.");
            }

            return new AbstractExpression("exp", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    Expression exp1 = getExpressions().get(0);
                    return Math.exp(exp1.evaluate(context));
                }
            };
        }
    }

    private static class MaxExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public MaxExpressionDescriptor() {
            super("Maximum", "max", Position.PREFIX, true);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 2) {
                throw new ExpressionInitializationException("max must have two or more arguments.");
            }
            return new AbstractExpression("max", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double max = getExpressions().get(0).evaluate(context);
                    for (int i = 1; i < getExpressions().size(); i++) {
                        double d = getExpressions().get(i).evaluate(context);
                        if (max < d) {
                            max = d;
                        }
                    }
                    return max;
                }
            };
        }
    }

    private static class MinExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public MinExpressionDescriptor() {
            super("Minimum", "min", Position.PREFIX, true);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 2) {
                throw new ExpressionInitializationException("min must have two or more arguments.");
            }
            return new AbstractExpression("min", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double min = getExpressions().get(0).evaluate(context);
                    for (int i = 1; i < getExpressions().size(); i++) {
                        double d = getExpressions().get(i).evaluate(context);
                        if (d < min) {
                            min = d;
                        }
                    }
                    return min;
                }
            };
        }
    }

    private static class ChiSquareExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public ChiSquareExpressionDescriptor() {
            super("Chi Square", "ChiSquare", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("ChiSquare must have one argument.");
            }

            return new AbstractExpression("ChiSquare", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    final ChiSquaredDistribution distribution = new ChiSquaredDistribution(randomGenerator, e1);
                    return distribution.sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    return new ChiSquaredDistribution(randomGenerator, e1);
                }
            };
        }
    }

    private static class GammaExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public GammaExpressionDescriptor() {
            super("Gamma", "Gamma", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Gamma must have two arguments.");
            }

            return new AbstractExpression("Gamma", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    final GammaDistribution distribution = new GammaDistribution(randomGenerator, e1, e2);
                    return distribution.sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new GammaDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class BetaExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public BetaExpressionDescriptor() {
            super("Beta", "Beta", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Beta must have two arguments.");
            }

            return new AbstractExpression("Beta", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new BetaDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new BetaDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class CauchyExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public CauchyExpressionDescriptor() {
            super("Cauchy", "Cauchy", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Normal must have two arguments.");
            }

            return new AbstractExpression("Cauchy", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new CauchyDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new CauchyDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }


    private static class FExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public FExpressionDescriptor() {
            super("FDist", "FDist", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Normal must have two arguments.");
            }

            return new AbstractExpression("FDist", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new FDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new FDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class GumbelExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public GumbelExpressionDescriptor() {
            super("Gumbel", "Gumbel", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Normal must have two arguments.");
            }

            return new AbstractExpression("Gumbel", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new GumbelDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new GumbelDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class LaplaceExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public LaplaceExpressionDescriptor() {
            super("Laplace", "Laplace", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Laplace must have two arguments.");
            }

            return new AbstractExpression("Laplace", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new LaplaceDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new LaplaceDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class LevyExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public LevyExpressionDescriptor() {
            super("Levy", "Levy", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Levy must have two arguments.");
            }

            return new AbstractExpression("Levy", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new LevyDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new LevyDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class NakagamiExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public NakagamiExpressionDescriptor() {
            super("Nakagami", "Nakagami", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Nakagami must have two arguments.");
            }

            return new AbstractExpression("Nakagami", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new NakagamiDistribution(randomGenerator, e1, e2, 1.0E-9D).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new NakagamiDistribution(randomGenerator, e1, e2, 1.0E-9D);
                }
            };
        }
    }

    private static class ParetoExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public ParetoExpressionDescriptor() {
            super("Pareto", "Pareto", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Pareto must have two arguments.");
            }

            return new AbstractExpression("Pareto", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new ParetoDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new ParetoDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class TriangularExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public TriangularExpressionDescriptor() {
            super("Triangular", "Triangular", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 3) {
                throw new ExpressionInitializationException("Triangular must have three arguments.");
            }

            return new AbstractExpression("Triangular", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    double e3 = getExpressions().get(2).evaluate(context);
                    return new TriangularDistribution(randomGenerator, e1, e2, e3).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    double e3 = getExpressions().get(2).evaluate(context);
                    return new TriangularDistribution(randomGenerator, e1, e2, e3);
                }
            };
        }
    }

    private static class UniformExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public UniformExpressionDescriptor() {
            super("Uniform", "Uniform", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Uniform must have two arguments.");
            }

            return new AbstractExpression("Uniform", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new UniformRealDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new UniformRealDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class UExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public UExpressionDescriptor() {
            super("Uniform", "U", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Uniform must have two arguments.");
            }

            return new AbstractExpression("Uniform", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new UniformRealDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new UniformRealDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class WeibullExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public WeibullExpressionDescriptor() {
            super("Weibull", "Weibull", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Weibull must have two arguments.");
            }

            return new AbstractExpression("Weibull", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new WeibullDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new WeibullDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class PoissonExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public PoissonExpressionDescriptor() {
            super("Poisson", "Poisson", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Poisson must have one argument.");
            }

            return new AbstractExpression("Poisson", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);
                    double e1 = exp1.evaluate(context);
                    return new PoissonDistribution(randomGenerator, e1, 1.0E-12D, 10000000).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    throw new IllegalArgumentException("Poisson does not have a p.d.f.");
                }

                public IntegerDistribution getIntegerDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);
                    double e1 = exp1.evaluate(context);
                    return new PoissonDistribution(randomGenerator, e1, 1.0E-12D, 10000000);
                }
            };
        }
    }

    private static class IndicatorExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public IndicatorExpressionDescriptor() {
            super("Indicator", "Indicator", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Exp must have one argument.");
            }

            return new AbstractExpression("Indicator", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    Expression exp1 = getExpressions().get(0);
                    double p = exp1.evaluate(context);

                    if (p < 0 || p > 1) throw new IllegalArgumentException("p must be in [0, 1]: " + p);

                    return RandomUtil.getInstance().nextDouble() < p ? 1 : 0;
                }
            };
        }
    }

//    private static class ExponentialPowerExpressionDescriptor extends AbstractExpressionDescriptor {
//        static final long serialVersionUID = 23L;
//
//        public ExponentialPowerExpressionDescriptor() {
//            super("ExponentialPower", "ExponentialPower", Position.PREFIX, false, false, true, "expr");
//        }
//
//        //=========================== Public Methods =========================//
//
//        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
//            if (expressions.length != 1) {
//                throw new ExpressionInitializationException("Exp must have one argument.");
//            }
//
//            return new AbstractExpression("ExponentialPower", Position.PREFIX, expressions) {
//                static final long serialVersionUID = 23L;
//
//                public double evaluate(Context context) {
//                    Expression exp1 = getExpressions().get(0);
//                    return RandomUtil.getInstance().nextExponentialPower(exp1.evaluate(context));
//                }
//            };
//        }
//    }

    private static class ExponentialExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public ExponentialExpressionDescriptor() {
            super("ExponentialDist", "ExponentialDist", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Exp must have one argument.");
            }

            return new AbstractExpression("ExponentialDist", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);

                    double e1 = exp1.evaluate(context);

                    final ExponentialDistribution distribution = new ExponentialDistribution(randomGenerator, e1);
                    return distribution.sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);

                    double e1 = exp1.evaluate(context);

                    final ExponentialDistribution distribution = new ExponentialDistribution(randomGenerator, e1);
                    return distribution;
                }
            };


        }
    }

    // // "exp(Normal(0, 1))"
    private static class LogNormalExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public LogNormalExpressionDescriptor() {
            super("Log Normal", "LogNormal", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Exp must have one argument.");
            }

            return new AbstractExpression("LogNormal", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);
                    Expression exp2 = getExpressions().get(1);

                    double e1 = exp1.evaluate(context);
                    double e2 = exp2.evaluate(context);

//                    if (e2 <= 0) {
//                        throw new IllegalArgumentException();
//                    }

                    final LogNormalDistribution distribution = new LogNormalDistribution(randomGenerator, e1, e2);
                    return distribution.sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);
                    Expression exp2 = getExpressions().get(1);

                    double e1 = exp1.evaluate(context);
                    double e2 = exp2.evaluate(context);

                    if (e1 <= 0 || e2 <= 0) {
                        return null;
                    }

                    return new LogNormalDistribution(randomGenerator, e1, e2);
                }

            };


        }
    }

    private static class NormalExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public NormalExpressionDescriptor() {
            super("Normal", "Normal", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Normal must have two arguments.");
            }

            return new AbstractExpression("Normal", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new NormalDistribution(randomGenerator, e1, e2).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new NormalDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class TruncNormalExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public TruncNormalExpressionDescriptor() {
            super("TruncNormal", "TruncNormal", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 4) {
                throw new ExpressionInitializationException("TruncNormal must have four arguments.");
            }

            return new AbstractExpression("TruncNormal", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    Expression exp1 = getExpressions().get(0);
                    Expression exp2 = getExpressions().get(1);
                    Expression exp3 = getExpressions().get(2);
                    Expression exp4 = getExpressions().get(3);
                    double mean = exp1.evaluate(context);
                    double sd = exp2.evaluate(context);
                    double low = exp3.evaluate(context);
                    double high = exp4.evaluate(context);

                    if (sd < 0) {
                        return Double.NaN;
                    }

                    if (low >= high) {
                        return Double.NaN;
                    }

                    return RandomUtil.getInstance().nextTruncatedNormal(mean, sd, low, high);
                }
            };
        }
    }

    private static class NExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public NExpressionDescriptor() {
            super("Normal", "N", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Normal must have two arguments.");
            }

            return new AbstractExpression("N", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    final NormalDistribution distribution = new NormalDistribution(randomGenerator, e1, e2);
                    return distribution.sample();
//                    faster
//                    return RandomUtil.getInstance().nextNormal(e1, e2);
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    double e1 = getExpressions().get(0).evaluate(context);
                    double e2 = getExpressions().get(1).evaluate(context);
                    return new NormalDistribution(randomGenerator, e1, e2);
                }
            };
        }
    }

    private static class DiscreteExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public DiscreteExpressionDescriptor() {
            super("Discrete", "Discrete", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 1) {
                throw new ExpressionInitializationException("Discrete distribution must have at least one argument.");
            }

            return new AbstractExpression("Discrete", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double[] p = new double[getExpressions().size()];

                    for (int i = 0; i < getExpressions().size(); i++) {
                        Expression exp = getExpressions().get(i);
                        p[i] = exp.evaluate(context);
                    }

                    p = convert(p);

                    double r = RandomUtil.getInstance().nextDouble();

                    for (int i = 0; i < p.length; i++) {
                        if (r < p[i]) return i;
                    }

                    throw new IllegalArgumentException();
                }

                private double[] convert(double... p) {
                    for (double _p : p) {
                        if (_p < 0) throw new IllegalArgumentException("All arguments must be >= 0: " + _p);
                    }

                    double sum = 0.0;

                    for (double _p : p) {
                        sum += _p;
                    }

                    for (int i = 0; i < p.length; i++) {
                        p[i] = p[i] /= sum;
                    }

                    for (int i = 1; i < p.length; i++) {
                        p[i] = p[i - 1] + p[i];
                    }

                    return p;
                }
            };
        }
    }

    // "0.3 * Normal(-2, 0.5) + 0.7 * Normal(2, 0.5)
    private static class MixtureDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public MixtureDescriptor() {
            super("Mixture", "Mixture", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (!(expressions.length > 0 && expressions.length % 2 == 0)) {
                throw new ExpressionInitializationException("Mixture must have an even expr of arguments, 2 or more.");
            }

            return new AbstractExpression("Mixture", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> exp = getExpressions();

                    int numMixed = exp.size() / 2;
                    double[] a = new double[numMixed];
                    double totalA = 0;

                    for (int i = 0; i < numMixed; i++) {
                        a[i] = exp.get(2 * i).evaluate(context);
                        if (a[i] <= 0) throw new IllegalArgumentException("Coefficients must be > 0: " + a[i]);
                        totalA += a[i];
                    }

                    if (Math.abs(totalA - 1.0) > 1e-2) {
                        throw new IllegalArgumentException("Coefficients must sum to 1.0: " + totalA);
                    }

                    for (int i = 0; i < numMixed; i++) {
                        a[i] /= totalA;
                    }

                    double r = RandomUtil.getInstance().nextDouble();
                    double sum = 0.0;

                    for (int i = 0; i < numMixed; i++) {
                        sum += a[i];

                        if (r < sum) {
                            return exp.get(2 * i + 1).evaluate(context);
                        }
                    }

                    throw new IllegalStateException("Random expr did not choose one of the options: " + r);
                }
            };
        }
    }

    private static class StudentTExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public StudentTExpressionDescriptor() {
            super("StudentT", "StudentT", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("StudentT must have one argument.");
            }

            return new AbstractExpression("StudentT", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);
                    double e1 = exp1.evaluate(context);
                    return new TDistribution(randomGenerator, e1).sample();
                }

                public RealDistribution getRealDistribution(Context context) {
                    RandomGenerator randomGenerator = RandomUtil.getInstance().getRandomGenerator();
                    Expression exp1 = getExpressions().get(0);
                    double e1 = exp1.evaluate(context);
                    return new TDistribution(randomGenerator, e1);
                }
            };
        }
    }

//    private static class HyperbolicExpressionDescriptor extends AbstractExpressionDescriptor {
//        static final long serialVersionUID = 23L;
//
//        public HyperbolicExpressionDescriptor() {
//            super("Hyperbolic", "Hyperbolic", Position.PREFIX, false, false, true, "expr");
//        }
//
//        //=========================== Public Methods =========================//
//
//        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
//            if (expressions.length != 2) {
//                throw new ExpressionInitializationException("Hyperbolic must have two arguments.");
//            }
//
//            return new AbstractExpression("Hyperbolic", Position.PREFIX, expressions) {
//                static final long serialVersionUID = 23L;
//
//                public double evaluate(Context context) {
//                    Expression exp1 = getExpressions().get(0);
//                    Expression exp2 = getExpressions().get(1);
//                    return RandomUtil.getInstance().nextHyperbolic(exp1.evaluate(context), exp2.evaluate(context));
//                }
//            };
//        }
//    }


    /**
     * Draws from the U(a1, b2) U U(a3, a4)...
     */
    private static class SplitExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public SplitExpressionDescriptor() {
            super("Split", "Split", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length % 2 != 0) {
                throw new ExpressionInitializationException("Split must have an even number of arguments.");
            }

            return new AbstractExpression("Split", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    if (getExpressions().size() < 2) {
                        throw new IllegalArgumentException("Split must have at least two arguments, Split(a, b).");
                    }

                    if (getExpressions().size() % 2 != 0) {
                        throw new IllegalArgumentException("Must have an even number of arguments for Split.");
                    }

                    double[] endpoints = new double[getExpressions().size()];

                    for (int i = 0; i < getExpressions().size(); i++) {
                        endpoints[i] = getExpressions().get(i).evaluate(context);
                    }

                    double[] lengths = new double[endpoints.length / 2];
                    double totalLength = 0;

                    for (int i = 0; i < endpoints.length / 2; i++) {
                        if (endpoints[2 * i] >= endpoints[2 * i + 1]) {
                            throw new IllegalArgumentException("For Split, a must be less than b for each pair.");
                        }

                        lengths[i] = endpoints[2 * i + 1] - endpoints[2 * i];
                        totalLength += lengths[i];
                    }

                    double r = RandomUtil.getInstance().nextDouble() * totalLength;

                    for (int i = 0; i < endpoints.length / 2; i++) {
                        if (r < lengths[i]) {
                            return endpoints[2 * i] + r;
                        }

                        r -= lengths[i];
                    }

                    return Double.NaN;
                }
            };
        }
    }

    //==========================================BOOLEAN FUNCTIONS===========================================//

    /**
     * For boolean "and". Will return true if all sub-expressions evaluate to a non-zero value and
     * false otherwise.
     */
    private static class AndExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;


        public AndExpressionDescriptor() {
            super("And", "AND", Position.PREFIX, true);
        }


        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 2) {
                throw new ExpressionInitializationException("Must have at least two arguments.");
            }
            return new AbstractExpression("AND", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    boolean allOnes = true;

                    for (Expression exp : getExpressions()) {
                        if (exp.evaluate(context) != 1.0) {
                            allOnes = false;
                        }
                    }

                    return allOnes ? 1.0 : 0.0;
                }
            };
        }
    }


    /**
     * For boolean "Or". Will return 1.0 if at least one of the sub-expressions is non-zero.
     */
    private static class OrExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public OrExpressionDescriptor() {
            super("Or", "OR", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 2) {
                throw new ExpressionInitializationException("Must have at least two arguments.");
            }
            return new AbstractExpression("OR", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    for (Expression exp : getExpressions()) {
                        if (exp.evaluate(context) == 1.0) {
                            return 1.0;
                        }
                    }
                    return 0.0;
                }
            };
        }
    }

    /**
     * For boolean "Or". Will return 1.0 if at least one of the sub-expressions is non-zero.
     */
    private static class XOrExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public XOrExpressionDescriptor() {
            super("Exclusive or", "XOR", Position.PREFIX, false);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }
            return new AbstractExpression("XOR", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double first = getExpressions().get(0).evaluate(context);
                    double second = getExpressions().get(1).evaluate(context);
                    first = first == 1.0 ? 1.0 : 0.0;
                    second = second == 1.0 ? 1.0 : 0.0;

                    return first + second == 1.0 ? 1.0 : 0.0;
                }
            };
        }
    }

    private static class LessThanExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public LessThanExpressionDescriptor() {
            super("Less Than", "<", Position.BOTH, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }

            return new AbstractExpression("<", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);
                    double b = expressions.get(1).evaluate(context);
                    return a < b ? 1.0 : 0.0;
                }
            };
        }
    }

    private static class LessThanOrEqualExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public LessThanOrEqualExpressionDescriptor() {
            super("Less Than Or Equals", "<=", Position.BOTH, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }

            return new AbstractExpression("<=", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);
                    double b = expressions.get(1).evaluate(context);
                    return a <= b ? 1.0 : 0.0;
                }
            };
        }
    }

    private static class EqualsExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public EqualsExpressionDescriptor() {
            super("Equals", "=", Position.BOTH, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }

            return new AbstractExpression("=", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);
                    double b = expressions.get(1).evaluate(context);
                    return a == b ? 1.0 : 0.0;
                }
            };
        }
    }

    private static class GreaterThanExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public GreaterThanExpressionDescriptor() {
            super("Greater Than", ">", Position.BOTH, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }

            return new AbstractExpression("<", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);
                    double b = expressions.get(1).evaluate(context);
                    return a > b ? 1.0 : 0.0;
                }
            };
        }
    }

    private static class GreaterThanOrEqualExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public GreaterThanOrEqualExpressionDescriptor() {
            super("Greater Than Or Equals", ">=", Position.BOTH, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 2) {
                throw new ExpressionInitializationException("Must have two arguments.");
            }

            return new AbstractExpression("<", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);
                    double b = expressions.get(1).evaluate(context);
                    return a >= b ? 1.0 : 0.0;
                }
            };
        }
    }

    private static class IfExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public IfExpressionDescriptor() {
            super("If", "IF", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 3) {
                throw new ExpressionInitializationException("Must have three arguments.");
            }

            return new AbstractExpression("IF", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);
                    double b = expressions.get(1).evaluate(context);
                    double c = expressions.get(2).evaluate(context);
                    return a == 1.0 ? b : c;
                }
            };
        }
    }

    private static class NewExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public NewExpressionDescriptor() {
            super("New Parameter", "NEW", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Must have one argument, a parameter.");
            }

            if (!(expressions[0] instanceof VariableExpression)) {
                throw new ExpressionInitializationException("Expecting a parameter name as argument.");
            }

            return new AbstractExpression("NEW", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Double.NaN;
                }
            };
        }
    }

    private static class NewExpressionDescriptor2 extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public NewExpressionDescriptor2() {
            super("New Parameter", "new", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Must have one argument, a parameter.");
            }

            if (!(expressions[0] instanceof VariableExpression)) {
                throw new ExpressionInitializationException("Expecting a parameter name as argument.");
            }

            return new AbstractExpression("new", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Double.NaN;
                }
            };
        }
    }

    private static class TSumExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public TSumExpressionDescriptor() {
            super("Template Sum", "TSUM", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Must have one argument, a parameter.");
            }

            return new AbstractExpression("TSUM", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Double.NaN;
                }
            };
        }
    }

    private static class TSumExpressionDescriptor2 extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public TSumExpressionDescriptor2() {
            super("Template Sum", "tsum", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Must have one argument, a parameter.");
            }

            return new AbstractExpression("tsum", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Double.NaN;
                }
            };
        }
    }

    private static class TProductExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public TProductExpressionDescriptor() {
            super("Template Product", "TPROD", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Must have one argument, a parameter.");
            }

            return new AbstractExpression("TPROD", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Double.NaN;
                }
            };
        }
    }

    private static class TProductExpressionDescriptor2 extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public TProductExpressionDescriptor2() {
            super("Template Product", "tprod", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length != 1) {
                throw new ExpressionInitializationException("Must have one argument, a parameter.");
            }

            return new AbstractExpression("tprod", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    return Double.NaN;
                }
            };
        }
    }

    //Added by AJ Sedgewick
    //First term should be a random draw that will be used to select a category index based on the (un-normalized)
    //weights given in the rest of the terms
    private static class DiscErrorExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public DiscErrorExpressionDescriptor() {
            super("DiscError", "DiscError", Position.PREFIX, false);
        }

        //=========================== Public Methods =========================//

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 2) {
                throw new ExpressionInitializationException("Discrete error distribution must have at least two arguments.");
            }

            return new AbstractExpression("DiscError", Position.PREFIX, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    double[] p = new double[getExpressions().size() - 1];
                    Expression errExp = getExpressions().get(0);
                    double err = errExp.evaluate(context);
                    String expPrint = "";
                    for (int i = 0; i < getExpressions().size() - 1; i++) {
                        Expression exp = getExpressions().get(i + 1);
                        p[i] = exp.evaluate(context);
                        expPrint += exp + "\n";
                    }

                    double[] p2 = convert(p);

                    //double r = RandomUtil.getInstance().nextDouble();

                    for (int i = 0; i < p2.length; i++) {
                        if (err < p2[i]) return i;
                    }

                    throw new IllegalArgumentException("exps: " + expPrint + " err: " + err + " p: " + Arrays.toString(p)
                            + " p2: " + Arrays.toString(p2));
                }

                private double[] convert(double... p) {
                    /*for (double _p : p) {
                        if (_p < 0) throw new IllegalArgumentException("All arguments must be >= 0: " + _p);
                    }*/

                    double sum = 0.0;
                    double[] pout = new double[p.length];

                    for (int i = 0; i < p.length; i++) {
                        pout[i] = Math.exp(p[i]);
                        sum += pout[i];
                    }

                    for (int i = 0; i < p.length; i++) {
                        pout[i] = pout[i] /= sum;
                    }

                    for (int i = 1; i < p.length; i++) {
                        pout[i] = pout[i - 1] + pout[i];
                    }

                    return pout;
                }
            };
        }
    }

    //the first term is an index (non-negative integer) that tells the expression which of the rest of the terms to
    //return
    private static class SwitchExpressionDescriptor extends AbstractExpressionDescriptor {
        static final long serialVersionUID = 23L;

        public SwitchExpressionDescriptor() {
            super("Switch", "Switch", Position.PREFIX, true);
        }

        public Expression createExpression(Expression... expressions) throws ExpressionInitializationException {
            if (expressions.length < 3) {
                //should use IF for three args...
                throw new ExpressionInitializationException("Must have at least four arguments.");
            }

            return new AbstractExpression("Switch", Position.BOTH, expressions) {
                static final long serialVersionUID = 23L;

                public double evaluate(Context context) {
                    List<Expression> expressions = getExpressions();
                    double a = expressions.get(0).evaluate(context);

                    if (a % 1 != 0 || a < 0) {
                        throw new IllegalArgumentException("First term index must be non-negative integer");
                    } else if (a >= expressions.size() - 1) {
                        throw new IllegalArgumentException("First term index out of bounds");
                    }
                    return expressions.get((int) a + 1).evaluate(context);
                }
            };
        }
    }
}


