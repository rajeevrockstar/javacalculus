/**
 *
 */
package javacalculus.evaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javacalculus.core.CALC;
import javacalculus.core.CalcParser;
import javacalculus.evaluator.extend.CalcFunctionEvaluator;
import javacalculus.exception.CalcWrongParametersException;
import javacalculus.struct.*;

/**
 * This function evaluator applies the Integral operator to a function with
 * respect to a given variable.
 *
 * @author Duyun Chen <A
 * HREF="mailto:duchen@seas.upenn.edu">[duchen@seas.upenn.edu]</A>, Seth Shannin
 * <A HREF="mailto:sshannin@seas.upenn.edu">[sshannin@seas.upenn.edu]</A>
 *
 * @author Seva Luchianov
 */
public class CalcINT implements CalcFunctionEvaluator {
    
    public boolean intByPartsThreadsAreAlive = true;

    public CalcINT() {
    }

    @Override
    public CalcObject evaluate(CalcFunction function) {
        if (function.size() == 2) {	//case INT(function, variable)
            if (function.get(1) instanceof CalcSymbol) {	//evaluate, adding an arbitrary constant for good practice
                //return CALC.ADD.createFunction(integrate(function.get(0), (CalcSymbol) function.get(1)), new CalcSymbol("C"));
                return CALC.ADD.createFunction(integrate(function.get(0), (CalcSymbol) function.get(1)));
            } else {
                throw new CalcWrongParametersException("INT -> 2nd parameter syntax");
            }
        } else {
            throw new CalcWrongParametersException("INT -> wrong number of parameters");
        }
    }

    public CalcObject integrate(CalcObject object, CalcSymbol var) {
        CalcObject obj = object;
        if (obj instanceof CalcFunction) { //input f(x..xn)
            obj = CALC.SYM_EVAL(obj); //evaluate the function before attempting integration
        }
        if (obj.isNumber() || (obj instanceof CalcSymbol && !((CalcSymbol) obj).equals(var))) {	//	INT(c,x) = c*x
            return CALC.MULTIPLY.createFunction(obj, var);
        }
        if (obj.equals(var)) { //	INT(x, x) = x^2/2
            return CALC.MULTIPLY.createFunction(CALC.POWER.createFunction(var, CALC.TWO), CALC.HALF);
        }
        if (obj.getHeader().equals(CALC.ADD) && ((CalcFunction) obj).size() > 1) { //	INT(y1+y2+...,x) = INT(y1,x) + INT(y2,x) + ...
            CalcFunction function = (CalcFunction) obj;
            CalcFunction functionB = new CalcFunction(CALC.ADD, function, 1, function.size());
            return CALC.ADD.createFunction(integrate(function.get(0), var), integrate(functionB, var));
        }
        if (obj.getHeader().equals(CALC.MULTIPLY)) {	//INT(c*f(x),x) = c*INT(f(x),x)
            CalcFunction function = new CalcFunction(CALC.MULTIPLY);
            function.addAll((CalcFunction) obj);
            //function = (CalcFunction) CALC.SYM_EVAL(function);
            CalcObject firstObj = function.get(0);
            if (firstObj.isNumber()) {
                return CALC.MULTIPLY.createFunction(function.get(0),
                        integrate(new CalcFunction(CALC.MULTIPLY, function, 1, function.size()), var));
            } else { //	INT(f(x)*g(x),x) = ?? (u-sub)


                //TEST
                //udvPairs[0][1] = CALC.SYM_EVAL(function);
                //udvPairs[0][2] = CALC.ONE;
                //udvPairs[432][1] = CALC.ONE;
                //udvPairs[432][2] = CALC.SYM_EVAL(function);
                //TEST
                /*
                 * could be integration by parts
                 * INT(u*dv) = u*v-INT(v*du)
                 * 
                 * 2 options
                 * LAITE
                 * or
                 * pick the most complicated thing that can be integrated for dv (parse all possibilities?)
                 *   start here first, will assume only 2 functions being multiplied. 6/20/13
                 * 
                 */

                //f(g(x)) = f'(g(x))*g'(x)
                //Lets try to handle g'(x)*f(g(x))
                int maxDepth = 0;
                int index = 0;
                int tempDepth;
                ArrayList<CalcObject> funcObjects = giveList(CALC.MULTIPLY, function);
                for (int i = 0; i < funcObjects.size(); i++) {
                    tempDepth = ((CalcInteger) CALC.SYM_EVAL(CALC.DEPTH.createFunction(funcObjects.get(i)))).intValue();
                    if (tempDepth > maxDepth) {
                        maxDepth = tempDepth;
                        index = i;
                    }
                }
                firstObj = funcObjects.remove(index);
                CalcObject secondObj = CALC.ONE;
                CalcObject coef = CALC.ONE;
                for (CalcObject temp : funcObjects) {
                    secondObj = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(secondObj, temp));
                }
                //System.err.println(firstObj);
                //System.err.println(secondObj);
                CalcObject inOfFunc = null;
                CalcObject diffSecond;
                CalcObject checkU = null;
                CalcObject toBeInt = null;
                if (firstObj.getHeader().equals(CALC.POWER)) {
                    if (((CalcFunction) firstObj).get(1).isNumber()) {//f(x)^k*f'(x)
                        inOfFunc = ((CalcFunction) firstObj).get(0);
                        //System.out.println("inOfFunc: " + inOfFunc);
                        diffSecond = CALC.SYM_EVAL(CALC.DIFF.createFunction(inOfFunc, var));
                        if (diffSecond.getHeader().equals(CALC.MULTIPLY)) {
                            if (((CalcFunction) diffSecond).get(0).isNumber()) {
                                coef = ((CalcFunction) diffSecond).get(0);//numbers always in front?
                                ((CalcFunction) diffSecond).remove(0);
                            }
                        }
                        CalcObject power = ((CalcFunction) firstObj).get(1);
                        //System.out.println("diffSecond: " + diffSecond);
                        //diffSecond = CALC.SYM_EVAL(CALC.EXPAND.createFunction(diffSecond));
                        //checkU = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(CALC.EXPAND.createFunction(secondObj), CALC.POWER.createFunction(diffSecond, CALC.NEG_ONE)));
                        if (superEquals(secondObj, diffSecond)) {
                            checkU = CALC.ONE;
                        }
                        //System.out.println("checkU: " + checkU);
                        toBeInt = CALC.POWER.createFunction(var, power);
                        //System.out.println("toBeInt: " + toBeInt);
                    } else if (((CalcFunction) firstObj).get(0).isNumber()) {//k^f(x)*f'(x)...
                        inOfFunc = ((CalcFunction) firstObj).get(1);
                        //System.out.println("inOfFunc: " + inOfFunc);
                        diffSecond = CALC.SYM_EVAL(CALC.DIFF.createFunction(inOfFunc, var));
                        if (diffSecond.getHeader().equals(CALC.MULTIPLY)) {
                            if (((CalcFunction) diffSecond).get(0).isNumber()) {
                                coef = ((CalcFunction) diffSecond).get(0);//numbers always in front?
                                ((CalcFunction) diffSecond).remove(0);
                            }
                        }
                        CalcObject base = ((CalcFunction) firstObj).get(0);
                        //System.out.println("diffSecond: " + diffSecond);
                        //diffSecond = CALC.SYM_EVAL(CALC.EXPAND.createFunction(diffSecond));
                        //checkU = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(CALC.EXPAND.createFunction(secondObj), CALC.POWER.createFunction(diffSecond, CALC.NEG_ONE)));
                        if (superEquals(secondObj, diffSecond)) {
                            checkU = CALC.ONE;
                        }
                        //System.out.println("checkU: " + checkU);
                        toBeInt = CALC.POWER.createFunction(base, var);
                        //System.out.println("toBeInt: " + toBeInt);
                    }
                } else if (firstObj.getHeader().equals(CALC.SIN)) {//f'(x)*sin(f(x))
                    inOfFunc = ((CalcFunction) firstObj).get(0);
                    //System.out.println("inOfFunc: " + inOfFunc);
                    diffSecond = CALC.SYM_EVAL(CALC.DIFF.createFunction(inOfFunc, var));
                    if (diffSecond.getHeader().equals(CALC.MULTIPLY)) {
                        if (((CalcFunction) diffSecond).get(0).isNumber()) {
                            coef = ((CalcFunction) diffSecond).get(0);//numbers always in front?
                            ((CalcFunction) diffSecond).remove(0);
                        }
                    }
                    //System.out.println("diffSecond: " + diffSecond);
                    //diffSecond = CALC.SYM_EVAL(CALC.EXPAND.createFunction(diffSecond));
                    //checkU = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(CALC.EXPAND.createFunction(secondObj), CALC.POWER.createFunction(diffSecond, CALC.NEG_ONE)));
                    if (superEquals(secondObj, diffSecond)) {
                        checkU = CALC.ONE;
                    }
                    //System.out.println("checkU: " + checkU);
                    toBeInt = CALC.SIN.createFunction(var);
                    //System.out.println("toBeInt: " + toBeInt);
                } else if (firstObj.getHeader().equals(CALC.COS)) {//f'(x)*cos(f(x))
                    inOfFunc = ((CalcFunction) firstObj).get(0);
                    //System.out.println("inOfFunc: " + inOfFunc);
                    diffSecond = CALC.SYM_EVAL(CALC.DIFF.createFunction(inOfFunc, var));
                    if (diffSecond.getHeader().equals(CALC.MULTIPLY)) {
                        if (((CalcFunction) diffSecond).get(0).isNumber()) {
                            coef = ((CalcFunction) diffSecond).get(0);//numbers always in front?
                            ((CalcFunction) diffSecond).remove(0);
                        }
                    }
                    //System.out.println("diffSecond: " + diffSecond);
                    //diffSecond = CALC.SYM_EVAL(CALC.EXPAND.createFunction(diffSecond));
                    //checkU = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(CALC.EXPAND.createFunction(secondObj), CALC.POWER.createFunction(diffSecond, CALC.NEG_ONE)));
                    if (superEquals(secondObj, diffSecond)) {
                        checkU = CALC.ONE;
                    }
                    //System.out.println("checkU: " + checkU);
                    toBeInt = CALC.COS.createFunction(var);
                    //System.out.println("toBeInt: " + toBeInt);
                }
                if (checkU != null && checkU.equals(CALC.ONE)) {
                    //System.out.println("U is one: " + checkU);
                    CalcObject result = integrate(toBeInt, var);
                    String resultString = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(CALC.POWER.createFunction(coef, CALC.NEG_ONE), result)).toString().replaceAll(var.toString(), "(" + inOfFunc.toString() + ")");
                    CalcParser parser = new CalcParser();
                    try {
                        return CALC.SYM_EVAL(parser.parse(resultString));
                    } catch (Exception e) {
                        System.err.println("error parsing new function");
                        e.printStackTrace(System.err);
                        return obj;
                        //return CALC.INT.createFunction(obj, var);
                    }
                }
                //System.out.println("U did not work out: " + checkU);
                //System.out.println("THIS IS THE OBJECT: " + obj);
                //System.out.println("THIS IS SECOND OBJ: " + secondObj);
                //return obj;
                //return CALC.INT.createFunction(obj, var);

                //we have gotten to here, u-sub has produced no result, must now try integration by parts
                //we assume there are only 2 sub functions in multiply and that the most "complex" functions are those with the most nested operations

                //perhaps this should be implemented along side with u sub

                //pull out all the parts necessary to make g'(x) for f'(g(x)), use it as dv, and leave the rest behind for u...

                /*
                 * who care which function is more difficult? this is a computer, simultaniously integrate every possibility until one of them completes.
                 * This avoids the infinite recursion problem.
                 * 6-21-13
                 */

                //START INTEGRATION BY PARTS
                //System.out.println("Lets do integration by parts");
                //secondObj = function.get(1);
                //System.out.println("FirstObj: " + firstObj);
                //udvPairs[0][1] = CALC.SYM_EVAL(function);
                //udvPairs[0][2] = CALC.ONE;
                //udvPairs[432][1] = CALC.ONE;
                //udvPairs[432][2] = CALC.SYM_EVAL(function);
                //ArrayList<CalcObject> funcObjects = giveList(CALC.MULTIPLY, function);
                funcObjects = giveList(CALC.MULTIPLY, function);
                //System.out.println(funcObjects);
                ArrayList<CalcObject[]> udvPairs = new ArrayList<>();
                CalcObject[] temp = new CalcObject[2];
                CalcObject notOne = CALC.ONE;
                for (int i = 0; i < funcObjects.size(); i++) {
                    notOne = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(notOne, funcObjects.get(i)));
                }
                temp[0] = CALC.ONE;
                temp[1] = notOne;
                udvPairs.add(temp);
                temp = new CalcObject[2];
                temp[1] = CALC.ONE;
                temp[0] = notOne;
                udvPairs.add(temp);
                for (int i = 0; i < funcObjects.size() - 1; i++) {
                    //System.out.println("i=" + i);
                    //System.out.println(function.size());
                    for (int j = 0; j < funcObjects.size() - i; j++) {
                        //System.out.println("j=" + j);
                        for (int skip = 0; skip < funcObjects.size() - i - j; skip++) {
                            //System.out.println("skip=" + skip);
                            CalcObject u = CALC.ONE;
                            CalcObject dv = CALC.ONE;
                            u = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(u, funcObjects.get(j)));
                            for (int start = j + skip + 1; start <= j + i + skip; start++) {
                                u = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(u, funcObjects.get(start)));
                            }
                            for (int end = 0; end < j; end++) {
                                dv = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(dv, funcObjects.get(end)));
                            }
                            for (int end = j + 1; end < j + skip + 1; end++) {
                                dv = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(dv, funcObjects.get(end)));
                            }
                            for (int end = j + i + 1 + skip; end < funcObjects.size(); end++) {
                                dv = CALC.SYM_EVAL(CALC.MULTIPLY.createFunction(dv, funcObjects.get(end)));
                            }
                            //}
                            //System.out.println("Pair " + pairCounter + "; u: " + u.toString() + " dv: " + dv.toString());
                            temp = new CalcObject[2];
                            temp[0] = u;
                            temp[1] = dv;
                            boolean addIt = true;
                            for (int x = 0; x < udvPairs.size(); x++) {
                                if (udvPairs.get(x)[0].equals(u) && udvPairs.get(x)[1].equals(dv)) {
                                    addIt = false;
                                    x = udvPairs.size();
                                }
                            }
                            if (addIt) {
                                udvPairs.add(temp);
                            }
                            //pairCounter++;
                        }
                    }
                }
                ArrayList<IntegrationThread> intByPartsThreads = new ArrayList<>();
                for (CalcObject[] pair : udvPairs) {
                    IntegrationThread tempT = new IntegrationThread(pair, var, this);
                    intByPartsThreads.add(tempT);
                }
                for (IntegrationThread intThread : intByPartsThreads) {
                    intThread.start();
                }
                CalcObject answer = null;
                while (answer == null) {
                    for (IntegrationThread intThread : intByPartsThreads) {
                        if (intThread.answer != null) {
                            System.out.println(intThread.answer);
                            answer = intThread.answer;
                            this.intByPartsThreadsAreAlive = false;
                            return answer;
                            //intByPartsThreads = null;
                        }
                    }
                }
                //return obj; //should never have to return obj at end, if statements above handle it
            }
        }
        if (obj.getHeader().equals(CALC.POWER)) { //this part is probably trickiest (form f(x)^g(x)). A lot of integrals here does not evaluate into elementary functions
            CalcFunction function = (CalcFunction) obj;
            CalcObject firstObj = function.get(0);
            CalcObject secondObj = function.get(1);
            if (firstObj instanceof CalcSymbol) {
                if (secondObj.isNumber() || secondObj instanceof CalcSymbol && !(secondObj.equals(var))) { //	INT(x^n,x) = x^(n+1)/(n+1)
                    if (!secondObj.equals(CALC.NEG_ONE)) {//handle 1/x
                        CalcObject temp = CALC.MULTIPLY.createFunction(
                                CALC.POWER.createFunction(firstObj, CALC.ADD.createFunction(secondObj, CALC.ONE)),
                                CALC.POWER.createFunction(CALC.ADD.createFunction(secondObj, CALC.ONE), CALC.NEG_ONE));
                        //System.out.println("WE ARE IN THE 1/x BRANCH");
                        //System.out.println(temp);
                        return temp;
                    } else {
                        return CALC.LN.createFunction(CALC.ABS.createFunction(firstObj));
                    }
                }
            } else if (firstObj.isNumber()) {	// INT(c^x,x) = c^x/ln(c)
                return CALC.MULTIPLY.createFunction(obj, CALC.POWER.createFunction(CALC.LN.createFunction(firstObj), CALC.NEG_ONE));
            }
        }
        if (obj.getHeader().equals(CALC.LN)) {	//	INT(LN(x),x) = x*LN(x) - x
            return CALC.ADD.createFunction(
                    CALC.MULTIPLY.createFunction(var, obj),
                    CALC.MULTIPLY.createFunction(var, CALC.NEG_ONE));
        }
        if (obj.getHeader().equals(CALC.SIN)) {	//	INT(SIN(x),x) = -COS(x)
            CalcFunction function = (CalcFunction) obj;
            CalcObject firstObj = function.get(0);
            if (firstObj.equals(var)) {
                return CALC.MULTIPLY.createFunction(CALC.NEG_ONE, CALC.COS.createFunction(firstObj));
            }
        }
        if (obj.getHeader().equals(CALC.COS)) {	//	INT(COS(x),x) = SIN(x)
            CalcFunction function = (CalcFunction) obj;
            CalcObject firstObj = function.get(0);
            if (firstObj.equals(var)) {
                return CALC.SIN.createFunction(firstObj);
            }
        }
        if (obj.getHeader().equals(CALC.TAN)) {	//	INT(TAN(x),x) = -LN(|COS(x)|)
            CalcFunction function = (CalcFunction) obj;
            CalcObject firstObj = function.get(0);
            if (firstObj.equals(var)) {
                return CALC.MULTIPLY.createFunction(CALC.NEG_ONE,
                        CALC.LN.createFunction(CALC.ABS.createFunction(CALC.COS.createFunction(var))));
            }
        }
        if (obj.getHeader().equals(CALC.ABS)) {	//	INT(|x|,x) = x*|x|/2
            CalcFunction function = (CalcFunction) obj;
            CalcObject firstObj = function.get(0);
            if (firstObj.equals(var)) {
                return CALC.MULTIPLY.createFunction(var, CALC.HALF,
                        CALC.ABS.createFunction(var));
            }
        }
        return obj;
        //return CALC.INT.createFunction(obj, var); //don't know how to integrate (yet). Return original expression.
    }

    public boolean superEquals(CalcObject first, CalcObject second) {
        CalcObject firstObj = CALC.SYM_EVAL(CALC.EXPAND.createFunction(first));
        CalcObject secondObj = CALC.SYM_EVAL(CALC.EXPAND.createFunction(second));
        return firstObj.equals(secondObj);
    }

    public ArrayList<CalcObject> giveList(CalcSymbol operator, CalcObject func) {
        ArrayList<CalcObject> list = new ArrayList<>();
        //System.out.println(func);
        if (func instanceof CalcFunction && func.getHeader().equals(operator)) {
            ArrayList<CalcObject> funcParts = ((CalcFunction) func).getAll();
            for (int i = 0; i < funcParts.size(); i++) {
                CalcObject firstObj = funcParts.get(i);
                //if (firstObj instanceof CalcFunction && ((CalcFunction) firstObj).getHeader().equals(operator)) {
                list.addAll(giveList(operator, firstObj));
                //}
            }
            //System.out.println("LIST in loop" + list);
        } else {
            list.add(func);
            //System.out.println("LIST" + list);
        }
        return list;
    }

    //insert private method integrate(function, var, u-sub)
    //use for recursion to simplify other u-sub cases
}
