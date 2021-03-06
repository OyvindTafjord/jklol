package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeContext;
import com.jayantkrish.jklol.training.NullLogFunction;

public class UnificationLexiconInductionStrategy implements LexiconInductionStrategy {
  
  private final CcgInference inferenceAlg;
  private final TypeContext typeContext;

  public UnificationLexiconInductionStrategy(CcgInference inferenceAlg,
      TypeContext typeContext) {
    this.inferenceAlg = Preconditions.checkNotNull(inferenceAlg);
    this.typeContext = Preconditions.checkNotNull(typeContext);
  }

  public void proposeLexiconEntries(CcgExample example, CcgParser parser,
      Set<LexiconEntry> entriesToAdd, Set<LexiconEntry> entriesToRemove) {
    if (parser == null) {
      Preconditions.checkArgument(example.hasLogicalForm());
      HeadedSyntacticCategory sentenceCat = makeSyntacticCategory(example.getLogicalForm());
      entriesToAdd.add(createLexiconEntry(example.getSentence().getWords(),
          sentenceCat, example.getLogicalForm()));
      System.out.println(sentenceCat + " " + example.getLogicalForm());
    } else {
      CcgParse bestParse = inferenceAlg.getBestConditionalParse(parser, example.getSentence(),
          null, new NullLogFunction(), null, null, example.getLogicalForm());

      if (bestParse != null) {
        System.out.println(bestParse + " " + bestParse.getLogicalForm());
        proposeAllSplits(bestParse, entriesToAdd, entriesToRemove);
      } 
    }
  }

  private void proposeAllSplits(CcgParse parse, Set<LexiconEntry> addAccumulator,
      Set<LexiconEntry> removeAccumulator) {
    if (parse.isTerminal()) {
      addAccumulator.addAll(proposeSplit(parse.getLexiconTriggerWords(), parse.getLogicalForm()));

      if (parse.getWords().size() > 0) {
        removeAccumulator.add(new LexiconEntry(parse.getWords(), parse.getLexiconEntry()));
      }
    } else {
      proposeAllSplits(parse.getLeft(), addAccumulator, removeAccumulator);
      proposeAllSplits(parse.getRight(), addAccumulator, removeAccumulator);
    }
  }

  /**
   * Proposes lexicon entries that can be combined to produce
   * {@code parse}, which is a terminal parse tree.
   * 
   * @param parse
   * @return
   */
  public Set<LexiconEntry> proposeSplit(List<String> words, Expression rootLf) {
    Set<LexiconEntry> lexiconEntries = Sets.newHashSet();
    for (int splitIndex = 1; splitIndex < words.size(); splitIndex++) {
      List<String> leftWords = words.subList(0, splitIndex);
      List<String> rightWords = words.subList(splitIndex, words.size());

      for (int i = 0; i < 2; i++) {
        Direction direction = (i == 0) ? Direction.LEFT : Direction.RIGHT;
        List<String> argWords = (i == 0) ? leftWords : rightWords;
        List<String> funcWords = (i == 0) ? rightWords : leftWords;

        List<HeadedSyntacticCategory> funcCats = Lists.newArrayList();
        List<HeadedSyntacticCategory> argCats = Lists.newArrayList();
        List<Expression> funcAccumulator = Lists.newArrayList();
        List<Expression> argAccumulator = Lists.newArrayList();
        generateExpressions(rootLf, direction, argAccumulator, funcAccumulator, argCats, funcCats);

        for (int j = 0; j < argAccumulator.size(); j++) {
          Expression funcLf = funcAccumulator.get(j);
          Expression argLf = argAccumulator.get(j);
          lexiconEntries.add(createLexiconEntry(funcWords, funcCats.get(j), funcLf));
          lexiconEntries.add(createLexiconEntry(argWords, argCats.get(j), argLf));
        }
      }
    }
    return lexiconEntries;
  }

  private void generateExpressions(Expression expression, Direction direction, List<Expression> argAccumulator,
      List<Expression> funcAccumulator, List<HeadedSyntacticCategory> argCatAccumulator,
      List<HeadedSyntacticCategory> funcCatAccumulator) {
    Expression body = expression;
    List<ConstantExpression> arguments = Lists.newArrayList();
    List<Type> argumentTypes = Lists.newArrayList();
    if (expression instanceof LambdaExpression) {
      LambdaExpression lambdaExpression = (LambdaExpression) expression;
      arguments = lambdaExpression.getArguments();
      argumentTypes = lambdaExpression.getArgumentTypes();
      body = lambdaExpression.getBody();
    }

    ConstantExpression newArg = new ConstantExpression("arg-" + arguments.size());
    // Add the logical form where one of the splits 
    // just applies the other split.
    List<ConstantExpression> newArgs = Lists.newArrayList(newArg);
    List<Type> newArgTypes = Lists.newArrayList(expression.getType(typeContext));
    newArgs.addAll(arguments);
    newArgTypes.addAll(argumentTypes);
    argAccumulator.add(expression);
    HeadedSyntacticCategory expressionCat = makeSyntacticCategory(expression);
    argCatAccumulator.add(expressionCat);
    if (newArgs.size() > 1) {
      funcAccumulator.add(new LambdaExpression(newArgs, newArgTypes, new ApplicationExpression(newArgs)));
      funcCatAccumulator.add(expressionCat.addArgument(expressionCat, direction, 0));
    } else {
      funcAccumulator.add(new LambdaExpression(newArgs, newArgTypes, newArgs.get(0)));
      funcCatAccumulator.add(expressionCat.addArgument(expressionCat, direction, 0));
    }

    // Try splitting up the current function application. 
    if (body instanceof ApplicationExpression) {
      ApplicationExpression applicationBody = (ApplicationExpression) body;
      List<Expression> subexpressions = applicationBody.getSubexpressions();

      for (int i = 0; i < subexpressions.size(); i++) {
        if (!arguments.contains(subexpressions.get(i))) {
          Type type = subexpressions.get(i).getType(typeContext);
          if (type != null) {
            argAccumulator.add(subexpressions.get(i));
            HeadedSyntacticCategory argCat = makeSyntacticCategory(subexpressions.get(i));
            argCatAccumulator.add(argCat);
            List<Expression> newBodyList = Lists.newArrayList(subexpressions);
            newBodyList.set(i, newArg);
            Expression newBody = new ApplicationExpression(newBodyList);

            if (arguments.size() > 0) {
              newArgs = Lists.newArrayList(newArg);
              newArgs.addAll(arguments);
              newArgTypes = Lists.newArrayList(type);
              newArgTypes.addAll(argumentTypes);

              funcAccumulator.add(new LambdaExpression(newArgs, newArgTypes, newBody));
            } else {
              funcAccumulator.add(new LambdaExpression(Arrays.asList(newArg),
                  Arrays.asList(type), newBody));
            }
            HeadedSyntacticCategory funcCat = expressionCat.addArgument(argCat, direction, 0);
            funcCatAccumulator.add(funcCat);
          }
        }
      }
    }
  }

  private LexiconEntry createLexiconEntry(List<String> words, HeadedSyntacticCategory cat,
      Expression logicalForm) {
    return new LexiconEntry(words, createCcgCategory(cat, logicalForm));
  }

  private CcgCategory createCcgCategory(HeadedSyntacticCategory cat, Expression logicalForm) {
    List<Set<String>> assignment = Lists.newArrayList();
    for (int i = 0; i < cat.getUniqueVariables().length; i++) {
      assignment.add(Sets.<String>newHashSet());
    }

    return new CcgCategory(cat, logicalForm, Collections.<String>emptyList(),
        Collections.<Integer>emptyList(), Collections.<Integer>emptyList(), assignment);
  }

  private HeadedSyntacticCategory makeSyntacticCategory(Expression lf) {
    Type type = lf.getType(typeContext);
    Preconditions.checkState(type != null, "Cannot type expression: " + lf);

    String syntacticCatString = "N:" + type.toString() + "{0}";
    return HeadedSyntacticCategory.parseFrom(syntacticCatString);
  }
}
