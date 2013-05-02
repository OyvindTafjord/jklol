package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;

/**
 * Returns full rank tensors defined over a set of variables.
 * 
 * @author jayantk
 */
public class TensorLrtFamily implements LrtFamily {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap vars;
  // If true, the tensor returned by this family is a constant,
  // and is not modified by training.
  private final boolean isConstant;
  
  public TensorLrtFamily(VariableNumMap vars, boolean isConstant) {
    this.vars = Preconditions.checkNotNull(vars);
    this.isConstant = isConstant;
  }
  
  @Override
  public int[] getDimensionNumbers() {
    return vars.getVariableNumsArray();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    DenseTensorBuilder builder = new DenseTensorBuilder(vars.getVariableNumsArray(), 
        vars.getVariableSizes());
    return TensorSufficientStatistics.createDense(vars, builder);
  }

  @Override
  public LowRankTensor getModelFromParameters(SufficientStatistics parameters) {
    return new TensorLowRankTensor(((TensorSufficientStatistics) parameters).get());
  }
  
  @Override
  public void increment(SufficientStatistics gradient, LowRankTensor value, 
      LowRankTensor increment, double multiplier) {
    if (!isConstant) {
      ((TensorSufficientStatistics) gradient).increment(increment.getTensor(), multiplier);
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return parameters.getDescription();
  }
}
