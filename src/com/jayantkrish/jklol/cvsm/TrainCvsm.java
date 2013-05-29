package com.jayantkrish.jklol.cvsm;

import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmKlLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmSquareLoss;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainCvsm extends AbstractCli {

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;
  private OptionSpec<String> initialVectors;

  private OptionSpec<Void> fixInitializedVectors;  
  private OptionSpec<Void> regularizeDeltas;  
  private OptionSpec<Void> regularizeVectorDeltas;
  private OptionSpec<Void> initializeTensorsToIdentity;
  private OptionSpec<Void> squareLoss;

  public TrainCvsm() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE, CommonOptions.LBFGS);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    initialVectors = parser.accepts("initialVectors").withRequiredArg().ofType(String.class).required();

    fixInitializedVectors = parser.accepts("fixInitializedVectors");
    regularizeDeltas = parser.accepts("regularizeDeltas");
    regularizeVectorDeltas = parser.accepts("regularizeVectorDeltas");
    initializeTensorsToIdentity = parser.accepts("initializeTensorsToIdentity");
    squareLoss = parser.accepts("squareLoss");
  }

  @Override
  public void run(OptionSet options) {
    List<CvsmExample> examples = CvsmUtils.readTrainingData(options.valueOf(trainingFilename));

    Map<String, TensorSpec> vectors = Maps.newTreeMap();
    vectors = readVectors(options.valueOf(initialVectors));

    CvsmFamily family = buildCvsmModel(vectors, options.has(fixInitializedVectors));
    SufficientStatistics trainedParameters = estimateParameters(family, examples, vectors,
        options.has(squareLoss), options.has(initializeTensorsToIdentity), options.has(lbfgs), 
								options.valueOf(lbfgsMinibatchSize), options.valueOf(lbfgsMinibatchIterations), options.has(fixInitializedVectors), options.has(regularizeDeltas),
options.has(regularizeVectorDeltas));
    Cvsm trainedModel = family.getModelFromParameters(trainedParameters);

    IoUtils.serializeObjectToFile(trainedModel, options.valueOf(modelOutput));
  }

  private static Map<String, TensorSpec> readVectors(String filename) {
    Map<String, TensorSpec> vectors = Maps.newHashMap();
    for (String line : IoUtils.readLines(filename)) {
      String[] parts = line.split("\\s\\s*");
      String name = parts[0].trim();
      int[] sizes = ArrayUtils.parseInts(parts[1].split(","));
      int rank = Integer.parseInt(parts[2].trim());
      double[] values = parts.length > 3 ? ArrayUtils.parseDoubles(parts[3].split(",")) : null; 

      vectors.put(name, new TensorSpec(sizes, rank, values));
    }
    return vectors;
  }

  private SufficientStatistics estimateParameters(CvsmFamily family,
      List<CvsmExample> examples, Map<String, TensorSpec> initialParameterMap,
      boolean useSquareLoss, boolean initializeTensorsToIdentity, boolean useLbfgs, 
						  int lbfgsMinibatchSize, int lbfgsMinibatchIterations,
						  boolean fixInitializedVectors, boolean regularizeDeltas,
						  boolean regularizeVectorDeltas) {

    CvsmLoss loss = null;
    if (useSquareLoss) {
      loss = new CvsmSquareLoss();
    } else {
      loss = new CvsmKlLoss();
    }

    GradientOracle<Cvsm, CvsmExample> oracle = new CvsmLoglikelihoodOracle(family, loss);
    SufficientStatistics initialParameters = family.getNewSufficientStatistics();
    if (initializeTensorsToIdentity) {
	if (regularizeDeltas) {
	    family.setInitialTensorsToIdentity();
	} else {
	    family.initializeParametersToIdentity(initialParameters);
	}
    }

    if (!fixInitializedVectors) {
      CvsmSufficientStatistics cvsmStats = (CvsmSufficientStatistics) initialParameters;
      List<LrtFamily> families = family.getFamilies();
      List<String> names = cvsmStats.getNames().items();
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);

        if (initialParameterMap.containsKey(name)) {
          TensorSpec spec = initialParameterMap.get(name);
          SufficientStatistics curStats = cvsmStats.getSufficientStatistics(i);
          if (spec.hasValues()) {
            TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) curStats;
            Tensor tensor = tensorStats.get();
            Tensor increment = new DenseTensor(tensor.getDimensionNumbers(),
                tensor.getDimensionSizes(), initialParameterMap.get(name).getValues());

	    if (!regularizeVectorDeltas) {
		tensorStats.increment(tensorStats, -1.0);
		tensorStats.increment(increment, 1.0);
	    } else {
		families.get(i).setInitialTensor(increment);
	    }
          }
        }
      }
    }

    initialParameters.perturb(0.01);

    GradientOptimizer trainer = createGradientOptimizer(examples.size());
    return trainer.train(oracle, initialParameters, examples);
  }

  private static CvsmFamily buildCvsmModel(Map<String, TensorSpec> vectors,
      boolean fixInitializedValues) {
    Map<Integer, DiscreteVariable> varMap = Maps.newHashMap();

    IndexedList<String> tensorNames = IndexedList.create();
    List<LrtFamily> tensorDims = Lists.newArrayList();
    for (String vectorName : vectors.keySet()) {
      TensorSpec spec = vectors.get(vectorName);

      int[] sizes = spec.getSizes();
      VariableNumMap vars = VariableNumMap.emptyMap();
      for (int i = 0; i < sizes.length; i++) {
        if (!varMap.containsKey(sizes[i])) {
          varMap.put(sizes[i], DiscreteVariable.sequence("seq-" + sizes[i], sizes[i]));
        }
        DiscreteVariable dimType = varMap.get(sizes[i]);
        vars = vars.union(VariableNumMap.singleton(i, ("dim-" + i).intern(), dimType));
      }

      LrtFamily family = null;
      if (spec.getRank() == -1 || sizes.length == 1) {
        if (spec.hasValues() && fixInitializedValues) {
          family = new ConstantLrtFamily(vars, new TensorLowRankTensor(new DenseTensor(vars.getVariableNumsArray(), vars.getVariableSizes(), spec.getValues())));
        } else {
          family = new TensorLrtFamily(vars);
        }
      } else {
        family = new OpLrtFamily(vars, spec.getRank());
      }

      tensorNames.add(vectorName);
      tensorDims.add(family);
    }

    return new CvsmFamily(tensorNames, tensorDims);
  }

  public static void main(String[] args) {
    new TrainCvsm().run(args);
  } 

  private static class TensorSpec {
    private final int[] sizes;
    private final int rank;
    private final double[] values;

    public TensorSpec(int[] sizes, int rank, double[] values) {
      this.sizes = Preconditions.checkNotNull(sizes);
      this.rank = rank;
      this.values = values;
    }

    public int[] getSizes() {
      return sizes;
    }

    public int getRank() {
      return rank;
    }

    public double[] getValues() {
      return values;
    }

    public boolean hasValues() {
      return values != null;
    }
  }
}
