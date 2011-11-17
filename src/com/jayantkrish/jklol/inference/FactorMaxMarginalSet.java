package com.jayantkrish.jklol.inference;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Max marginals computed from a list of max marginal {@code Factor}s.
 *
 * @author jayant
 */
public class FactorMaxMarginalSet implements MaxMarginalSet {
  
  private final FactorGraph factorGraph;
  private final Assignment conditionedValues;

  public FactorMaxMarginalSet(FactorGraph factorGraph, Assignment conditionedValues) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);
    this.conditionedValues = Preconditions.checkNotNull(conditionedValues);
  }

  @Override
  public MaxMarginalSet addConditionalVariables(Assignment values) {
    return new FactorMaxMarginalSet(factorGraph, conditionedValues.union(values));
  }
  
  @Override
  public int beamSize() {
    return 1;
  }

  @Override
  public Assignment getNthBestAssignment(int n) {
    // At the moment, only the best assignment is supported.
    Preconditions.checkArgument(n == 0);
    return getBestAssignmentGiven(factorGraph, 0, Sets.<Integer>newHashSet(), Assignment.EMPTY)
        .union(conditionedValues);
  }
  
  
  /**
   * Performs a depth-first search of {@code factorGraph}, starting at
   * {@code factorNum}, to find an assignment with maximal probability. If
   * multiple maximal probability assignments exist, this method returns an
   * arbitrary one.
   * 
   * @param factorGraph factor graph which is searched.
   * @param factorNum current factor to visit.
   * @param visitedFactors list of factors already visited by the depth-first search.
   * @param a the maximal probability assignment for the already visited factors. 
   * @return
   */
  private static Assignment getBestAssignmentGiven(FactorGraph factorGraph, int factorNum, 
      Set<Integer> visitedFactors, Assignment a) {
    Factor curFactor = factorGraph.getFactor(factorNum);
    Assignment best = curFactor.conditional(a).getMostLikelyAssignments(1).get(0);
    
    visitedFactors.add(factorNum);

    for (int adjacentFactorNum : factorGraph.getAdjacentFactors(factorNum)) {
      if (!visitedFactors.contains(adjacentFactorNum)) {
        Assignment bestChild = getBestAssignmentGiven(factorGraph, adjacentFactorNum, 
            visitedFactors, best).removeAll(best.getVariableNums());
        best = best.union(bestChild);
      }
    }
    return best;
  }
}
