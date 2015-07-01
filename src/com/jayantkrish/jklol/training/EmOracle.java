package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Oracle for the steps of the {@link ExpectationMaximization} algorithm.
 * 
 * @author jayantk
 * @param <M> instantiated model type
 * @param <E> example type
 * @param <O> expectation type
 * @param <A> accumulator type for expectations
 */
public interface EmOracle<M, E, O, A> {

  /**
   * Instantiates any sort of intermediate data structure that may assist in
   * performing inference. For example, this could construct a
   * {@code FactorGraph} for {@code parameters}.
   * <p>
   * If no such initialization is necessary, simply return {@code parameters}.
   * 
   * @param parameters
   */
  public M instantiateModel(SufficientStatistics parameters);
  
  /**
   * Gets a data structure used to aggregate expectations of type O.
   *  
   * @return
   */
  public A getInitialExpectationAccumulator();
  
  /**
   * Accumulates {@code expectation} into {@code accumulator}.
   * 
   * @param expectation
   * @param aggregator
   */
  public void accumulateExpectation(O expectation, A accumulator);
  
  /**
   * Combines two accumulators into a single accumulator. May
   * mutate both accumulators.
   * 
   * @param accumulator1
   * @param accumulator2
   * @return
   */
  public A combineAccumulators(A accumulator1, A accumulator2);

  /**
   * E-step of the Expectation-Maximization algorithm.
   * 
   * @param model
   * @param currentParameters
   * @param example
   * @param log
   * @return
   */
  public O computeExpectations(M model, SufficientStatistics currentParameters,
      E example, LogFunction log);
  
  /**
   * M-step of the Expectation-Maximization algorithm. Re-estimates parameters
   * from the model expectations computed in the E-step.
   * <p>
   * This method may mutate and return {@code currentParameters} to avoid
   * unnecessary copying.
   * 
   * @param expectationAccumulator
   * @param currentParameters
   * @return
   */
  public SufficientStatistics maximizeParameters(A expectationAccumulator,
      SufficientStatistics currentParameters, LogFunction log);
}
