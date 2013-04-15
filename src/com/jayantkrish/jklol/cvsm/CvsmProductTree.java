package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmProductTree extends AbstractCvsmTree {

  private final CvsmTree bigTree;
  private final CvsmTree smallTree;
  
  public CvsmProductTree(CvsmTree bigTree, CvsmTree smallTree) {
    super(bigTree.getValue().elementwiseProduct(smallTree.getValue()));

    this.bigTree = bigTree;
    this.smallTree = smallTree;
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmFamily family, SufficientStatistics gradient) {
    LowRankTensor bigTreeValue = bigTree.getValue();
    LowRankTensor smallTreeValue = smallTree.getValue();
    Preconditions.checkArgument(Arrays.equals(treeGradient.getDimensionNumbers(), bigTreeValue.getDimensionNumbers()));

    SortedSet<Integer> dimsToEliminate = Sets.newTreeSet(Ints.asList(bigTreeValue.getDimensionNumbers()));
    dimsToEliminate.removeAll(Ints.asList(smallTreeValue.getDimensionNumbers()));
    Tensor smallTreeGradient = treeGradient.elementwiseProduct(bigTreeValue)
        .sumOutDimensions(dimsToEliminate);
    smallTree.backpropagateGradient(smallTreeGradient, family, gradient);

    Tensor ones = DenseTensor.constant(bigTreeValue.getDimensionNumbers(),
        bigTreeValue.getDimensionSizes(), 1.0);
    Tensor bigTreeGradient = ones.elementwiseProduct(smallTreeValue)
        .elementwiseProduct(treeGradient);
    bigTree.backpropagateGradient(bigTreeGradient, family, gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
