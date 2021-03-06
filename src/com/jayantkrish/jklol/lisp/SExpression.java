package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

/**
 * A LISP S-expression.
 * 
 * @author jayantk
 */
public class SExpression {

  // Null unless this expression is a constant.
  private final String constantName;
  // Unique index in the symbol table containing the string
  // constantName.
  private final int constantNameIndex;

  // If the constant is a primitive type (such as an integer
  // or double), this variable contains its value.
  private final Object primitiveValue;

  // Null unless this expression is not a constant. 
  private final List<SExpression> subexpressions;

  private SExpression(String constantName, int constantNameIndex, Object primitiveValue,
      List<SExpression> subexpressions) {
    Preconditions.checkArgument(constantName == null ^ subexpressions == null);
    this.constantName = constantName;
    this.constantNameIndex = constantNameIndex;
    this.primitiveValue = primitiveValue;
    this.subexpressions = subexpressions;
  }

  public static SExpression constant(String constantName, int constantNameIndex,
      Object primitiveValue) {
    return new SExpression(constantName, constantNameIndex, primitiveValue, null);
  }

  public static SExpression nested(List<SExpression> subexpressions) {
    return new SExpression(null, -1, null, subexpressions);
  }

  public boolean isConstant() {
    return constantName != null;
  }
  
  public String getConstant() {
    return constantName;
  }

  public int getConstantIndex() {
    return constantNameIndex;
  }

  public Object getConstantPrimitiveValue() {
    return primitiveValue;
  }

  public List<SExpression> getSubexpressions() {
    return subexpressions;
  }
  
  @Override
  public String toString() {
    if (isConstant()) {
      return constantName;
    } else {
      return "(" + Joiner.on(" ").join(subexpressions) + ")";
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((constantName == null) ? 0 : constantName.hashCode());
    result = prime * result
        + ((subexpressions == null) ? 0 : subexpressions.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SExpression other = (SExpression) obj;
    if (constantName == null) {
      if (other.constantName != null)
        return false;
    } else if (!constantName.equals(other.constantName))
      return false;
    if (subexpressions == null) {
      if (other.subexpressions != null)
        return false;
    } else if (!subexpressions.equals(other.subexpressions))
      return false;
    return true;
  }
}
