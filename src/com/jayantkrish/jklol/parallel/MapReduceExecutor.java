package com.jayantkrish.jklol.parallel;

import java.util.Collection;


/**
 * {@code MapReduceExecutor} is a simple parallel computing interface for
 * performing embarrassingly parallel tasks. These tasks are formatted as a map
 * (transformation) stage, followed by a reduce (accumulation) stage. Unlike
 * real mapreduce, there is no sort stage between the map and the reduce.
 * 
 * @author jayantk
 */
public interface MapReduceExecutor {

  public <A, B, C, D extends Mapper<A, B>, E extends Reducer<B, C>> C mapReduce(
      Collection<? extends A> items, D mapper, E reducer);
}
