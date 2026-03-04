package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;

import java.text.ParseException;

/**
 * The {@code ExpressionSampler} class provides functionality for parsing, storing, and
 * evaluating mathematical expressions. It uses a parsed {@code Expression} representation
 * of the mathematical formula and a {@code Context} for resolving variable values during
 * evaluation.
 *
 * This class is useful in scenarios where mathematical expressions need to be dynamically
 * evaluated, with the ability to specify variable values through an external context.
 */
public class ExpressionSampler implements Sampler {
    /**
     * Represents a mathematical expression that has been parsed and encapsulates its structure
     * and logic for evaluation. This field acts as the primary component for defining and
     * performing calculations within the {@code ExpressionSampler} class. It is initialized
     * during the construction of an {@code ExpressionSampler} instance and further utilized
     * in context-based evaluations and expression retrieval.
     *
     * This field is immutable, ensuring that the underlying expression remains consistent
     * after initialization.
     */
    private final Expression expression;

    /**
     * Represents the evaluation context associated with this {@code ExpressionSampler}.
     * The {@code Context} is utilized to resolve variable values during expression
     * evaluation. By default, an empty context is used, meaning no variables
     * are defined for evaluation unless explicitly set using {@link #setContext(Context)}.
     */
    private Context context;

    /**
     * Constructs a new {@code ExpressionSampler} with the specified mathematical expression.
     * The given expression is parsed and stored internally for evaluation.
     *
     * @param expression the mathematical expression to parse and store. Must be a valid
     *                   expression string that can be parsed by {@code ExpressionParser}.
     * @throws ParseException if the provided expression cannot be parsed into a valid
     *                        mathematical expression.
     */
    public ExpressionSampler(String expression) throws ParseException {
        if (expression == null) {
            throw new NullPointerException("Expression cannot be null.");
        }
        this.expression = new ExpressionParser().parseExpression(expression);
        this.context = new Context() {
            @Override
            public Double getValue(String var) {
                throw new IllegalArgumentException("All values in the expression must be defined.");
            }
        };
    }

    /**
     * Retrieves the stored mathematical expression associated with this instance.
     * The returned {@code Expression} represents the parsed representation of
     * the mathematical expression provided during construction.
     *
     * @return the {@code Expression} object that encapsulates the stored mathematical expression.
     */
    public Expression getExpression() {
        return expression;
    }

    /**
     * Evaluates the stored mathematical expression using an empty context and returns the result.
     *
     * @return the result of evaluating the mathematical expression as a double
     */
    public double sample() {
        return expression.evaluate(context);
    }

    /**
     * Retrieves the current {@code Context} instance associated with this {@code ExpressionSampler}.
     * The {@code Context} is used during expression evaluation to resolve the values of variables
     * within the mathematical expression.
     *
     * @return the currently assigned {@code Context} instance for this {@code ExpressionSampler}. This
     * is by default set to an empty context.
     */
    public Context getContext() {
        return context;
    }

    /**
     * Updates the current {@code Context} instance associated with this {@code ExpressionSampler}.
     * The {@code Context} is used during expression evaluation to resolve the values of variables
     * within the mathematical expression.
     *
     * @param context the {@code Context} instance to be assigned. This context will be used to look up
     *                variable values during expression evaluation. If set to {@code null}, the evaluation
     *                will proceed without variable resolution.
     */
    public void setContext(Context context) {
        if (context == null) {
            throw new NullPointerException("Context cannot be null.");
        }
        this.context = context;
    }
}