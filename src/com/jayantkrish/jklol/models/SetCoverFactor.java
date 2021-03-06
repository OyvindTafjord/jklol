package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class SetCoverFactor extends AbstractFactor {

  private static final long serialVersionUID = -684267665747322900L;
  
  private final ImmutableSet<Object> requiredValues;
  private final Predicate<Object> possibleValues;
  
  private final List<Factor> inputVarFactors;
  private final List<Factor> cachedMaxMarginals;
  
  public SetCoverFactor(VariableNumMap inputVars, Set<Object> requiredValues, 
      Predicate<Object> possibleValues, List<Factor> inputVarFactors) { 
    super(inputVars);
    Preconditions.checkArgument(inputVarFactors.size() == inputVars.size());
    this.requiredValues = ImmutableSet.copyOf(requiredValues);
    this.possibleValues = possibleValues;
    
    this.inputVarFactors = Lists.newArrayList(inputVarFactors);
    
    // Precompute max-marginals. Only necessary if we've received all child messages.
    boolean allNonNull = true;
    for (Factor factor : inputVarFactors) {
      allNonNull &= factor != null;
    }
    
    if (allNonNull) {
      this.cachedMaxMarginals = cacheMaxMarginals();
    } else {
      this.cachedMaxMarginals = null;
    }
  }
  
  private List<Factor> cacheMaxMarginals() {
    System.out.println("Computing max marginals.");
    List<Factor> maxMarginals = Lists.newArrayListWithCapacity(inputVarFactors.size());
    for (int i = 0; i < inputVarFactors.size(); i++) {
      maxMarginals.add(null);
    }
    for (Object requiredValue : requiredValues) {
      // Fulfill each requirement by greedily selecting the factor with the best probability of each required value.
      double bestLogProbability = Double.NEGATIVE_INFINITY;
      int bestFactorIndex = -1;
      for (int i = 0; i < this.inputVarFactors.size(); i++) {
        Factor factor = inputVarFactors.get(i);
        double logProb = factor.getUnnormalizedLogProbability(requiredValue);
        if (logProb >= bestLogProbability && maxMarginals.get(i) == null) {
          bestFactorIndex = i;
          bestLogProbability = logProb;
        }
      }
      Preconditions.checkState(bestFactorIndex != -1);
      VariableNumMap factorVariables = inputVarFactors.get(bestFactorIndex).getVars();
      
      Factor maxMarginal = TableFactor.logPointDistribution(factorVariables, 
          factorVariables.outcomeArrayToAssignment(requiredValue)).product(Math.exp(bestLogProbability)); 
      maxMarginals.set(bestFactorIndex, maxMarginal);
    }

    // For all unconstrained factors, the max-marginal is simply the factor with any illegal values
    // removed.
    for (int i = 0; i < inputVarFactors.size(); i++) {
      if (maxMarginals.get(i) == null) {
        DiscreteFactor currentFactor = inputVarFactors.get(i).coerceToDiscrete(); 
        TableFactorBuilder builder = new TableFactorBuilder(currentFactor.getVars(),
            SparseTensorBuilder.getFactory());
        builder.incrementWeight(currentFactor);
        Iterator<Outcome> iter = currentFactor.outcomeIterator();
        while (iter.hasNext()) {
          Assignment a = iter.next().getAssignment();
          if (!possibleValues.apply(a.getOnlyValue())) {
            builder.setWeight(a, 0.0);
          }
        }

        maxMarginals.set(i, builder.build());
      }
    }

    return maxMarginals;
  }
  
  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    return Math.exp(getUnnormalizedLogProbability(assignment));
  }
  
  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    double logProbability = 0.0;
    List<Object> inputValues = assignment.intersection(getVars()).getValues();
    Set<Object> unfoundValues = Sets.newHashSet(requiredValues);
    
    for (int i = 0; i < inputValues.size(); i++) {
      Object inputValue = inputValues.get(i);
      if (!possibleValues.apply(inputValue)) {

        // If any value is in the set of impossible values, then this assignment
        // has zero probability.
        return Double.NEGATIVE_INFINITY;
      }
      unfoundValues.remove(inputValue);
      if (inputVarFactors.get(i) != null) {
        logProbability += inputVarFactors.get(i).getUnnormalizedLogProbability(assignment);
      }
    }

    if (unfoundValues.size() > 0) {
      // Not all of the required values were found in the inputVar.
      return Double.NEGATIVE_INFINITY;
    } else {
      return logProbability;
    }
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    for (Map.Entry<SeparatorSet, Factor> sepSet : inboundMessages.entrySet()) {
      if (sepSet.getValue() == null) {
        return Collections.emptySet();
      }
    }
    return inboundMessages.keySet();
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    VariableNumMap remainingVar = getVars().removeAll(varNumsToEliminate);
    Preconditions.checkArgument(remainingVar.size() == 1);
    
    for (int i = 0; i < cachedMaxMarginals.size(); i++) {
      if (cachedMaxMarginals.get(i).getVars().containsAll(remainingVar)) {
        return cachedMaxMarginals.get(i);
      }
    }
    // We should always find a factor containing the variable, and therefore
    // never reach this point.
    throw new IllegalStateException("Could not find a cached max marginal for: " + remainingVar);
  }
  
  @Override
  public Factor product(Factor other) {
    return product(Arrays.asList(other));
  }
  
  @Override
  public Factor product(List<Factor> others) {
    if (others.size() == 0) {
      return this;
    }

    List<Factor> newInputVarFactors = Lists.<Factor>newArrayList(inputVarFactors);
    for (Factor other : others) {
      Preconditions.checkArgument(other.getVars().size() == 1);
      Preconditions.checkArgument(other.getVars().containsAny(getVars()));

      int otherVarIndex = other.getVars().getVariableNums().get(0);
      int listIndex = getVars().getVariableNums().indexOf(otherVarIndex);

      if (newInputVarFactors.get(listIndex) == null) {
        newInputVarFactors.set(listIndex, other);
      } else {
        newInputVarFactors.set(listIndex, newInputVarFactors.get(listIndex).product(other));
      }
    }
    return new SetCoverFactor(getVars(), requiredValues, 
        possibleValues, newInputVarFactors);
  }

  @Override
  public Factor outerProduct(Factor other) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public double size() {
    return Double.POSITIVE_INFINITY;
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Factor conditional(Assignment assignment) {
    if (!getVars().containsAny(assignment.getVariableNums())) {
      return this;
    }
    VariableNumMap myVars = getVars();    
    Set<Object> newRequiredValues = Sets.newHashSet(requiredValues);
    for (Integer varNum : assignment.getVariableNums()) {
      if (myVars.contains(varNum)) {
        Object value = assignment.getValue(varNum);
        newRequiredValues.remove(value);
        
        if (!possibleValues.apply(value)) {
          // Can't possibly satisfy the constraints anymore.
          return TableFactor.zero(myVars.removeAll(assignment.getVariableNums()));
        }
      }
    }
    
    List<Factor> newFactors = Lists.newArrayListWithCapacity(inputVarFactors.size()); 
    for (Factor inputVarFactor : inputVarFactors) {
      if (!inputVarFactor.getVars().containsAny(assignment.getVariableNums())) {
        newFactors.add(inputVarFactor);
      }
    }
    
    return new SetCoverFactor(myVars.removeAll(assignment.getVariableNums()), 
        newRequiredValues, possibleValues, newFactors);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    Preconditions.checkArgument(numAssignments == 1);
    List<Assignment> outputAssignments = Lists.newArrayListWithCapacity(cachedMaxMarginals.size());
    for (Factor cached : cachedMaxMarginals) {
      outputAssignments.addAll(cached.getMostLikelyAssignments(numAssignments));
    }
        
    if (outputAssignments.size() == cachedMaxMarginals.size()) {
      return Arrays.asList(Assignment.unionAll(outputAssignments));
    } else {
      return Collections.emptyList();
    }
  }
}
