package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.SufficientStatisticsCalculator.BatchStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Estimates the parameters of a {@link ParametricFactorGraph} using expectation
 * maximization.
 * 
 * @author jayantk
 */
public class EMTrainer {

  private final int numIterations;
  private final SufficientStatisticsCalculator statisticsCalculator;

  private final LogFunction log;

  /**
   * Creates an {@code EMTrainer} that
   * 
   * @param numIterations
   * @param statisticsCalculator
   * @param log
   */
  public EMTrainer(int numIterations, SufficientStatisticsCalculator statisticsCalculator, LogFunction log) {
    this.numIterations = numIterations;
    this.statisticsCalculator = statisticsCalculator;
    if (log != null) {
      this.log = log;
    } else {
      this.log = new NullLogFunction();
    }
  }

  public SufficientStatistics train(ParametricFactorGraph bn,
      SufficientStatistics initialParameters, Iterable<Assignment> trainingData) {
    List<Assignment> trainingDataList = Lists.newArrayList(trainingData);

    SufficientStatistics oldStatistics = null;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      // E-step: compute the expected values of the hidden variables given the
      // current set of parameters.
      FactorGraph factorGraph = bn.getFactorGraphFromParameters(initialParameters);
      BatchStatistics batchStatistics = statisticsCalculator
          .computeSufficientStatistics(factorGraph, bn, trainingDataList, log);
      SufficientStatistics statistics = batchStatistics.getStatistics();
      log.logStatistic(i, "average loglikelihood",
          Double.toString(batchStatistics.getLoglikelihood() / batchStatistics.getNumExamples()));

      // M-step: maximize the parameters.
      // Due to a poor design decision I made in an experiment using this class,
      // initialParameters must always contain the current parameters of the
      // model.
      if (oldStatistics != null) {
        initialParameters.increment(oldStatistics, -1.0);
      }
      initialParameters.increment(statistics, 1.0);
      oldStatistics = statistics;

      log.notifyIterationEnd(i);
    }
    return initialParameters;
  }
}