/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExprList implements Iterable<Expression> {
    private final List<Expression> elist;

    public ExprList() {
        elist = new ArrayList<Expression>();
    }

    public void add(Expression e) {
        elist.add(e);
    }

    public Expression get(int i) {
        return elist.get(i);
    }

    public int size() {
        return elist.size();
    }

    public Iterator<Expression> iterator() {
        return elist.iterator();
    }
}