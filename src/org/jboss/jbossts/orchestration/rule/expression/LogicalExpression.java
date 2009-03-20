/*
* JBoss, Home of Professional Open Source
* Copyright 2008, Red Hat Middleware LLC, and individual contributors
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*
* @authors Andrew Dinn
*/
package org.jboss.jbossts.orchestration.rule.expression;

import org.jboss.jbossts.orchestration.rule.type.Type;
import org.jboss.jbossts.orchestration.rule.exception.TypeException;
import org.jboss.jbossts.orchestration.rule.exception.ExecuteException;
import org.jboss.jbossts.orchestration.rule.exception.CompileException;
import org.jboss.jbossts.orchestration.rule.Rule;
import org.jboss.jbossts.orchestration.rule.compiler.StackHeights;
import org.jboss.jbossts.orchestration.rule.helper.HelperAdapter;
import org.jboss.jbossts.orchestration.rule.grammar.ParseNode;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

/**
 * A binary logical operator expression
 */
public class LogicalExpression extends BooleanExpression
{
    public LogicalExpression(Rule rule, int oper, ParseNode token, Expression left, Expression right)
    {
        super(rule, oper, token, left, right);
    }

    public Type typeCheck(Type expected) throws TypeException {
        Type type1 = getOperand(0).typeCheck(Type.Z);
        Type type2 = getOperand(1).typeCheck(Type.Z);
        type = Type.Z;
        if (Type.dereference(expected).isDefined() && !expected.isAssignableFrom(type)) {
            throw new TypeException("LogicalExpression.typeCheck : invalid expected result type " + expected.getName() + getPos());
        }

        return type;
    }

    public Object interpret(HelperAdapter helper) throws ExecuteException {
        Boolean value = (Boolean)getOperand(0).interpret(helper);

        if (oper == AND) {
            return (value && (Boolean)getOperand(1).interpret(helper));
        } else { // oper == OR
            return (value || (Boolean)getOperand(1).interpret(helper));
        }
    }

    public void compile(MethodVisitor mv, StackHeights currentStackHeights, StackHeights maxStackHeights) throws CompileException
    {
        Expression oper0 = getOperand(0);
        Expression oper1 = getOperand(1);

        int currentStack = currentStackHeights.stackCount;

        // compile the first expression and make sure it is a boolean -- adds 1 to stack height
        oper0.compile(mv, currentStackHeights, maxStackHeights);
        if (oper0.getType() == Type.BOOLEAN) {
            compileBooleanConversion(Type.BOOLEAN, type.Z, mv, currentStackHeights, maxStackHeights);
        }
        // plant a test and branch
        Label nextLabel = new Label();
        Label endLabel = new Label();
        if (oper == AND) {
            // only try next if we got true here
            mv.visitJumpInsn(Opcodes.IFNE, nextLabel);
            // ok, the first branch was false so stack a false for the result and skip to the end
            mv.visitLdcInsn(false);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        } else {
            // only try next if we got false here
            mv.visitJumpInsn(Opcodes.IFEQ, nextLabel);
            // ok, the first branch was true so stack a true for the result and skip to the end
            mv.visitLdcInsn(true);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        // the else branch -- adds 1 to stack height
        mv.visitLabel(nextLabel);
        oper1.compile(mv, currentStackHeights, maxStackHeights);
        if (oper0.getType() == Type.BOOLEAN) {
            compileBooleanConversion(Type.BOOLEAN, type.Z, mv, currentStackHeights, maxStackHeights);
        }
        // the final result is the result of the second oper which is on the stack already
        // This is the end, my beau-tiful friend
        mv.visitLabel(endLabel);
        // check stack height
        if (currentStackHeights.stackCount != currentStack + 1) {
            throw new CompileException("LogicalExpression.compile : invalid stack height " + currentStackHeights.stackCount + " expecting " + currentStack + 1);
        }
        // no need to check max stack height as recursive calls will have added at least 1 
    }
}