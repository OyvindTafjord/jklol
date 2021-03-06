package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Represents a set of deterministic OR constraints over a set of variables.
 * 
 * @author jayantk
 */
public class OrConstraintFactor extends AbstractFactor {

  private static final long serialVersionUID = 4493291411547813806L;
  
  private final VariableNumMap inputVars;
  private final List<Factor> inputVarFactors;

  private final VariableNumMap orVars;
  private final Map<String, Object> orValues;

  /**
   * Creates an {@code OrConstraintFactor}, where {@code inputVarFactors} are
   * the initial distributions over {@code inputVars}.
   * 
   * @param inputVars
   * @param orVars
   * @param orValues
   * @param inputVarFactors
   */
  public OrConstraintFactor(VariableNumMap inputVars,
      VariableNumMap orVars, Map<String, Object> orValues,
      List<Factor> inputVarFactors) {
    super(inputVars.union(orVars));
    Preconditions.checkArgument(orVars.size() == orValues.size());
    Preconditions.checkArgument(orVars.getBooleanVariables().size() == orVars.size());
    Preconditions.checkArgument(inputVarFactors.size() == inputVars.size());

    this.inputVars = inputVars;
    this.orVars = orVars;
    this.orValues = orValues;
    this.inputVarFactors = inputVarFactors;
  }

  /**
   * Creates an {@code OrConstraintFactor}, with the identity distribution over
   * {@code inputVars}.
   * 
   * @param inputVars
   * @param orVars
   * @param orValues
   */
  public static OrConstraintFactor createWithoutDistributions(VariableNumMap inputVars,
      VariableNumMap orVars, Map<String, Object> orValues) {
    List<Factor> inputFactors = Lists.newArrayList();
    for (int varNum : inputVars.getVariableNumsArray()) {
      inputFactors.add(TableFactor.logUnity(inputVars.intersection(varNum)));
    }
    
    return new OrConstraintFactor(inputVars, orVars, orValues, inputFactors);
  }
    
  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    boolean haveAllInbound = true;
    for (Map.Entry<SeparatorSet, Factor> inboundMessage : inboundMessages.entrySet()) {
      if (inboundMessage.getValue() == null) {
        haveAllInbound = false;
      }
    }
    if (haveAllInbound) {
      return inboundMessages.keySet();
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    return Math.exp(getUnnormalizedLogProbability(assignment));
  }
  
  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNumsArray()));

    Set<Object> requiredValues = Sets.newHashSet();
    Set<Object> impossibleValues = Sets.newHashSet();
    getRequiredAndImpossibleValues(assignment, requiredValues, impossibleValues);

    return (new SetCoverFactor(inputVars, requiredValues, 
        Predicates.not(Predicates.in(impossibleValues)), inputVarFactors))
        .getUnnormalizedLogProbability(assignment);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    if (!getVars().containsAny(assignment.getVariableNumsArray())) {
      return this;
    }
    Preconditions.checkArgument(assignment.containsAll(orVars.getVariableNumsArray()));
    
    Set<Object> requiredValues = Sets.newHashSet();
    Set<Object> impossibleValues = Sets.newHashSet();
    getRequiredAndImpossibleValues(assignment, requiredValues, impossibleValues);
  
    Assignment inputAssignment = assignment.intersection(inputVars.getVariableNumsArray());
    return new SetCoverFactor(inputVars, requiredValues, 
        Predicates.not(Predicates.in(impossibleValues)), inputVarFactors).conditional(inputAssignment);
  }
  
  
  /**
   * Populates {@code requiredValues} and {@code impossibleValues} with the
   * objects that are deemed required/impossible by {@code assignment}.
   * 
   * @param assignment
   * @param requiredValues
   * @param impossibleValues
   */
  private void getRequiredAndImpossibleValues(Assignment assignment,
      Set<Object> requiredValues, Set<Object> impossibleValues) {
    Assignment truthValues = assignment.intersection(orVars.getVariableNumsArray());
    for (String variableName : orVars.getVariableNamesArray()) {
      int variableNum = orVars.getVariableByName(variableName);

      // Each orVar acts as a deterministic indicator function. If true,
      // then its corresponding value must be found in the inputVar. Otherwise,
      // the corresponding value cannot be found in the inputVar.
      Boolean truthVal = (Boolean) truthValues.getValue(variableNum);
      if (truthVal) {
        requiredValues.add(orValues.get(variableName));
      } else {
        impossibleValues.add(orValues.get(variableName));
      }
    }
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    return getMarginal(varNumsToEliminate);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    return getMarginal(varNumsToEliminate);
  }

  /**
   * Computes both marginals and max-marginals (which happen to be the same) for
   * this factor. This method can only compute marginals over individual
   * variables in {@code this.inputVars}. Note that when the or-constraints are
   * not conditioned on, the distributions on {@code inputVars} are independent.
   * 
   * @param varNumsToEliminate
   * @return
   */
  private Factor getMarginal(Collection<Integer> varNumsToEliminate) {
    // We can only compute marginals on individual variables in inputVars
    VariableNumMap uneliminatedVars = getVars().removeAll(varNumsToEliminate);
    Preconditions.checkArgument(uneliminatedVars.size() == 1);
    Preconditions.checkArgument(uneliminatedVars.containsAny(inputVars));

    int variableIndex = uneliminatedVars.getVariableNumsArray()[0];
    int listIndex = inputVars.getVariableNums().indexOf(variableIndex);

    Preconditions.checkArgument(inputVarFactors.get(listIndex) != null);
    // Return the factor describing the marginal.
    return inputVarFactors.get(listIndex);
  }

  @Override
  public Factor product(Factor other) {
    return product(Arrays.asList(other));
  }
  
  @Override
  public Factor product(List<Factor> others) {
    List<Factor> newInputVarFactors = Lists.newArrayList(inputVarFactors);
    for (Factor other : others) {
      Preconditions.checkArgument(other.getVars().size() == 1);
      Preconditions.checkArgument(other.getVars().containsAny(inputVars));

      int otherVarIndex = other.getVars().getVariableNums().get(0);
      int listIndex = inputVars.getVariableNums().indexOf(otherVarIndex);

      newInputVarFactors.set(listIndex, newInputVarFactors.get(listIndex).product(other));
    }
    return new OrConstraintFactor(inputVars, orVars, orValues, newInputVarFactors);
  }

  @Override
  public Factor outerProduct(Factor other) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public double size() {
    // In a sense, this factor is a very large clique.
    return Double.POSITIVE_INFINITY;
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
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
    Preconditions.checkArgument(numAssignments == 1, "Only 1 best assignment is supported.");
    Set<Assignment> bestAssignments = Sets.newHashSet();
    for (Factor factor : inputVarFactors) {
      bestAssignments.addAll(factor.getMostLikelyAssignments(numAssignments));
    }
    Assignment inputAssignment = Assignment.unionAll(bestAssignments);
    
    Set<Object> values = Sets.newHashSet(inputAssignment.getValues());
    List<Boolean> outputOrValues = Lists.newArrayList();
    for (String variableName : orVars.getVariableNames()) {
      outputOrValues.add(values.contains(orValues.get(variableName)));
    }
    Assignment orAssignment = orVars.outcomeToAssignment(outputOrValues);
    return Arrays.asList(orAssignment.union(inputAssignment));
  }
  
  @Override
  public String toString() {
    return "OrConstraintFactor(inputs=" + inputVars.toString() + ",constraints=" + orValues + ")";
  }
}
