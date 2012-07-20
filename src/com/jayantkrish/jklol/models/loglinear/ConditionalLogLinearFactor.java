package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.LinearClassifierFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.Variables;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ConditionalLogLinearFactorProto;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto.Type;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.tensor.TensorFactory;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A log-linear factor where one of the variables is always conditioned on. The
 * conditioned-on variable is a feature vector, which determines the conditional
 * distribution over the other variable.
 * 
 * @author jayantk
 */
public class ConditionalLogLinearFactor extends AbstractParametricFactor {

  private final VariableNumMap inputVar;
  private final VariableNumMap outputVars;
  private final VariableNumMap conditionalVars;
  
  // Names of the features. Also defines the expected dimensionality 
  // of the input feature vector.
  private final DiscreteVariable featureDictionary;

  // Size parameters for the sufficient statistics tensor. 
  private final int[] dimensionNums;
  private final int[] dimensionSizes;
  private final VariableNumMap sufficientStatisticVars;
  
  // Constructs the type of tensor to use.
  private final TensorFactory tensorFactory;

  /**
   * Create a factor which represents a conditional distribution over outputVars
   * given inputVar. {@code featureVectorDimensionality} is the dimension of the
   * feature vector which will be assigned to {@code inputVar}.
   * 
   * @param inputVar
   * @param outputVars
   * @param conditionalVars
   * @param featureDictionary
   * @param tensorFactory
   */
  public ConditionalLogLinearFactor(VariableNumMap inputVar, VariableNumMap outputVars, 
      VariableNumMap conditionalVars, DiscreteVariable featureDictionary, TensorFactory tensorFactory) {
    super(inputVar.union(outputVars));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(outputVars.getDiscreteVariables().size() == outputVars.size());
    this.inputVar = inputVar;
    this.outputVars = outputVars;
    this.conditionalVars = conditionalVars;
    this.featureDictionary = featureDictionary;
    
    this.dimensionNums = Ints.toArray(inputVar.union(outputVars).getVariableNums());
    Preconditions.checkArgument(dimensionNums[0] == inputVar.getOnlyVariableNum());
        
    this.dimensionSizes = new int[outputVars.size() + 1];
    dimensionSizes[0] = featureDictionary.numValues();
    int[] outputSizes = outputVars.getVariableSizes();
    for (int i = 0; i < outputSizes.length; i++) {
      dimensionSizes[i + 1] = outputSizes[i];
    }
    
    VariableNumMap featureVar = VariableNumMap.singleton(inputVar.getOnlyVariableNum(), 
        inputVar.getOnlyVariableName(), featureDictionary);
    this.sufficientStatisticVars = featureVar.union(outputVars);
    this.tensorFactory = Preconditions.checkNotNull(tensorFactory);
  }

  @Override
  public Factor getFactorFromParameters(SufficientStatistics parameters) {
    return new LinearClassifierFactor(inputVar, outputVars, conditionalVars, 
        getWeightTensorFromStatistics(parameters).build());
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters) { 
    TensorBuilder weightTensor = getWeightTensorFromStatistics(parameters);
    VariableNumMap featureVariable = VariableNumMap.singleton(inputVar.getOnlyVariableNum(), 
        inputVar.getVariableNames().get(0) + "_features", featureDictionary);
    TableFactor parameterFactor = new TableFactor(featureVariable.union(outputVars), 
        weightTensor.build());
    return parameterFactor.getParameterDescription();
  }
  
  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {    
    return new TensorSufficientStatistics(sufficientStatisticVars, 
        tensorFactory.getBuilder(dimensionNums, dimensionSizes));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, 
      Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    
    TensorBuilder weightTensor = getWeightTensorFromStatistics(statistics);
    Tensor inputValueFeatures = (Tensor) assignment.getValue(inputVar.getOnlyVariableNum());
    Iterator<KeyValue> keyValueIter = inputValueFeatures.keyValueIterator();
    
    int[] outputKeys = outputVars.assignmentToIntArray(assignment.intersection(outputVars));
    int[] weightKey = new int[outputKeys.length + 1];
    for (int i = 0; i < outputKeys.length; i++) {
      weightKey[i + 1] = outputKeys[i];
    }

    while (keyValueIter.hasNext()) {
      KeyValue featureKeyValue = keyValueIter.next();
      weightKey[0] = featureKeyValue.getKey()[0];
      weightTensor.incrementEntry(count * featureKeyValue.getValue(), weightKey);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, Factor marginal, 
      Assignment conditionalAssignment, double count, double partitionFunction) {
    Preconditions.checkArgument(conditionalAssignment.containsAll(inputVar.getVariableNums()));

    // Construct a factor representing the unnormalized probability distribution over all
    // of the output variables.
    VariableNumMap conditionedVars = outputVars.intersection(conditionalAssignment.getVariableNums());
    DiscreteFactor outputMarginal = marginal.coerceToDiscrete();
    if (conditionedVars.size() > 0) {
      Assignment conditionedAssignment = conditionalAssignment.intersection(conditionedVars);
      DiscreteFactor conditionedFactor = TableFactor.pointDistribution(conditionedVars, conditionedAssignment);
      outputMarginal = conditionedFactor.outerProduct(marginal);
    }

    Tensor inputTensor = ((Tensor) conditionalAssignment.getValue(inputVar.getOnlyVariableNum()))
        .relabelDimensions(Ints.toArray(inputVar.getVariableNums()));
    
    TensorBuilder weightTensor = getWeightTensorFromStatistics(statistics);
    Tensor foo = inputTensor.outerProduct(outputMarginal.getWeights());
    weightTensor.incrementWithMultiplier(foo, count / partitionFunction);
  }
  
  @Override
  public ParametricFactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    ParametricFactorProto.Builder builder = ParametricFactorProto.newBuilder();
    builder.setType(Type.CONDITIONAL_LOG_LINEAR);
    builder.setVariables(getVars().toProto(variableTypeIndex));
    
    ConditionalLogLinearFactorProto.Builder subBuilder = builder.getConditionalLogLinearFactorBuilder();
    subBuilder.setInputVariable(inputVar.toProto(variableTypeIndex));
    subBuilder.setOutputVariable(outputVars.toProto(variableTypeIndex));
    subBuilder.setFeatureDictionary(featureDictionary.toProto());
    // TODO: fix this.
    subBuilder.setDenseTensorFactory(true);
    
    return builder.build();
  }
  
  public static ConditionalLogLinearFactor fromProto(ParametricFactorProto proto, 
      IndexedList<Variable> variableTypeIndex) {
    Preconditions.checkArgument(proto.getType().equals(Type.CONDITIONAL_LOG_LINEAR));
    Preconditions.checkArgument(proto.hasConditionalLogLinearFactor());
    
    ConditionalLogLinearFactorProto subProto = proto.getConditionalLogLinearFactor();
    VariableNumMap inputVar = VariableNumMap.fromProto(subProto.getInputVariable(), variableTypeIndex);
    VariableNumMap outputVar = VariableNumMap.fromProto(subProto.getOutputVariable(), variableTypeIndex);
    VariableNumMap conditionalVars = VariableNumMap.fromProto(subProto.getConditionalVariable(), variableTypeIndex);
    DiscreteVariable featureDictionary = (DiscreteVariable) Variables.fromProto(subProto.getFeatureDictionary());
    TensorFactory factory = subProto.getDenseTensorFactory() ? DenseTensorBuilder.getFactory() 
        : SparseTensorBuilder.getFactory();
    return new ConditionalLogLinearFactor(inputVar, outputVar, conditionalVars, featureDictionary, factory);
  }
  
  private TensorBuilder getWeightTensorFromStatistics(SufficientStatistics stats) {
    TensorBuilder weightTensor = ((TensorSufficientStatistics) stats).get();
    Preconditions.checkArgument(Arrays.equals(dimensionNums, weightTensor.getDimensionNumbers()),
        "Wrong parameter dimensions. Expected %s, got %s", Arrays.toString(dimensionNums), 
        Arrays.toString(weightTensor.getDimensionNumbers()));
    Preconditions.checkArgument(Arrays.equals(dimensionSizes, weightTensor.getDimensionSizes()));
    return weightTensor;
  }
}
