package com.jayantkrish.jklol.ccg.gi;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class GroundedParserLoglikelihoodOracle implements 
  GradientOracle<GroundedParser, GroundedParseExample> {
  
  private final ParametricGroundedParser family;
  private final int beamSize;

  public GroundedParserLoglikelihoodOracle(ParametricGroundedParser family,
      int beamSize) {
    this.family = Preconditions.checkNotNull(family);
    this.beamSize = beamSize;
  }
  
  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public GroundedParser instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, GroundedParser model,
      GroundedParseExample example, LogFunction log) {
    AnnotatedSentence sentence = example.getSentence();
    Object diagram = example.getDiagram();

    // Get a distribution over unconditional executions.
    log.startTimer("update_gradient/input_marginal");
    List<GroundedCcgParse> unconditionalParses = model.beamSearch(sentence,
        diagram, beamSize, null, null, log);
    
    if (unconditionalParses.size() == 0) {
      System.out.println("unconditional search failure");
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");
    
    // Get a distribution on executions conditioned on the label of the example.
    log.startTimer("update_gradient/output_marginal");
    Predicate<State> evalFilter = example.getEvalFilter();
    ChartCost chartFilter = example.getChartFilter();
    List<GroundedCcgParse> conditionalParsesInit = model.beamSearch(sentence,
        diagram, beamSize, chartFilter, evalFilter, log);
    
    List<GroundedCcgParse> conditionalParses = Lists.newArrayList();
    for (GroundedCcgParse parse : conditionalParsesInit) {
      System.out.println(parse.getDenotation() + " " + parse.getLogicalForm() + " " + parse.getSyntacticParse());
      if (example.isCorrectDenotation(parse.getDenotation(), parse.getDiagram())) {
        conditionalParses.add(parse);
      }
    }
    
    if (conditionalParses.size() == 0) {
      System.out.println("conditional search failure");
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/output_marginal");

    log.startTimer("update_gradient/increment_gradient");
    double unconditionalPartitionFunction = getPartitionFunction(unconditionalParses);
    for (GroundedCcgParse parse : unconditionalParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, diagram, parse,
          -1.0 * parse.getSubtreeProbability() / unconditionalPartitionFunction);
    }

    double conditionalPartitionFunction = getPartitionFunction(conditionalParses);
    for (GroundedCcgParse parse : conditionalParses) {
      family.incrementSufficientStatistics(gradient, currentParameters, sentence, diagram, parse,
          parse.getSubtreeProbability() / conditionalPartitionFunction);
    }
    log.stopTimer("update_gradient/increment_gradient");

    // Note that the returned loglikelihood is an approximation because
    // inference is approximate.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }
  
  public double getPartitionFunction(Collection<GroundedCcgParse> parses) {
    double partitionFunction = 0.0;
    for (GroundedCcgParse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }
}
