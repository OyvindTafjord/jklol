package com.jayantkrish.jklol.cvsm.tree;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.CvsmGradient;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmLogisticTree extends AbstractCvsmTree {
  
  private final CvsmTree subtree;
  
  public CvsmLogisticTree(CvsmTree subtree) {
    super(new TensorLowRankTensor(subtree.getValue().getTensor().elementwiseProduct(-1.0)
        .elementwiseExp().elementwiseAddition(1.0).elementwiseInverse()));
    this.subtree = Preconditions.checkNotNull(subtree);
  }

  @Override
  public List<CvsmTree> getSubtrees() {
    return Arrays.asList(subtree);
  }

  @Override
  public CvsmTree replaceSubtrees(List<CvsmTree> subtrees) {
    Preconditions.checkArgument(subtrees.size() == 1);
    return new CvsmLogisticTree(subtrees.get(0));
  }

  @Override
  public void backpropagateGradient(LowRankTensor treeGradient, CvsmGradient gradient) {
    Tensor value = getValue().getTensor();
    Tensor nodeGradient = value.elementwiseProduct(value.elementwiseProduct(-1.0).elementwiseAddition(1.0));
    Tensor subtreeGradient = nodeGradient.elementwiseProduct(treeGradient.getTensor());
    
    subtree.backpropagateGradient(new TensorLowRankTensor(subtreeGradient), gradient);
  }

  @Override
  public double getLoss() {
    return 0;
  }
}
