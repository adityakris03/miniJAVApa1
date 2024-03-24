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

public class StatementList implements Iterable<Statement> {
    private final List<Statement> slist;

    public StatementList() {
        slist = new ArrayList<Statement>();
    }

    public void add(Statement s) {
        slist.add(s);
    }

    public Statement get(int i) {
        return slist.get(i);
    }

    public int size() {
        return slist.size();
    }

    public Iterator<Statement> iterator() {
        return slist.iterator();
    }
}
