package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FastGraphVertex;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.NotifiedList;

import java.util.*;

public class BasicBlock implements FastGraphVertex, Collection<Stmt> {

    /**
     * Specifies that this block should not be merged in later passes.
     */
    public static final int FLAG_NO_MERGE = 0x1;
    public final ControlFlowGraph cfg;
    private final NotifiedList<Stmt> statements;
    /**
     * Two blocks A, B, must have A.id == B.id IFF A == B
     * Very important!
     */
    private int id;
    private int flags = 0;

    // for debugging purposes. the number of times the label was changed
    private int relabelCount = 0;

    public BasicBlock(ControlFlowGraph cfg) {
        this.cfg = cfg;
        this.id = cfg.makeBlockId();
        statements = new NotifiedList<>(
                (s) -> s.setBlock(this),
                (s) -> {
                    if (s.getBlock() == this)
                        s.setBlock(null);
                }
        );
    }

    public boolean isFlagSet(int flag) {
        return (flags & flag) == flag;
    }

    public void setFlag(int flag, boolean b) {
        if (b) {
            flags |= flag;
        } else {
            flags ^= flag;
        }
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public ControlFlowGraph getGraph() {
        return cfg;
    }

    public void transfer(BasicBlock dst) {
        Iterator<Stmt> it = statements.iterator();

        while (it.hasNext()) {
            Stmt s = it.next();
            it.remove();
            dst.add(s);
            assert (s.getBlock() == dst);
        }
    }

    /**
     * Transfers statements up to index `to`, exclusively, to block `dst`.
     */
    public void transferUpto(BasicBlock dst, int to) {
        // FIXME: faster
        for (int i = to - 1; i >= 0; i--) {
            Stmt s = remove(0);
            dst.add(s);
            assert (s.getBlock() == dst);
        }
    }

    @Override
    public String getDisplayName() {
        return blockNameById(id);
    }

    private String blockNameById(int id) {
        return "L" + id;
    }

    /**
     * If you call me you better know what you are doing.
     * If you use me in any collections, they must be entirely rebuilt from scratch
     * ESPECIALLY indexed or hash-based ones.
     * This includes collections of edges too.
     *
     * @param i newId
     */
    public void setId(int i) {
        relabelCount++;
        id = i;
    }

    @Override
    public int getNumericId() {
        return id;
    }

    @Override
    public String toString() {
        return this.getDisplayName();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        BasicBlock bb = (BasicBlock) o;

        assert this.getGraph() == bb.getGraph();

        if (id == bb.id) {
            assert (relabelCount == bb.relabelCount);
            assert (this == bb);
        }
        return id == bb.id;
    }

    public void checkConsistency() {
        for (Stmt stmt : statements)
            if (stmt.getBlock() != this)
                throw new IllegalStateException("Orphaned child " + stmt);

        var revEdges = this.cfg.getReverseEdges(this);

        // Make sure that all phis have valid arguments for every incoming edge
        // and that every argument has an incoming edge
        for (Stmt stmt : this) {
            if (!(stmt instanceof CopyPhiStmt))
                break;

            var phiExpr = ((CopyPhiStmt) stmt).getExpression();

            for (FlowEdge<BasicBlock> revEdge : revEdges) {
                if (phiExpr.getArgument(revEdge.src()) == null)
                    throw new IllegalStateException("Missing phi parameter");
            }

            for (BasicBlock incoming : phiExpr.getArguments().keySet()) {
                if (this.cfg.getEdges(incoming).stream().noneMatch(edge -> edge.dst().equals(this)))
                    throw new IllegalStateException("Too many phi parameters");
            }
        }

        for (Stmt stmt : this) {
            if (stmt != this.getTerminator() && stmt.isTerminating())
                throw new IllegalStateException("Two terminating statements!");
            if (stmt instanceof CopyVarStmt copyVar) {
                if (!this.getGraph().getLocals().defs.get(copyVar.getVariable().getLocal()).equals(copyVar)) {
                    throw new IllegalStateException("Double definition of " + copyVar.getVariable());
                }
            }
        }

        var terminator = this.getTerminator();

        if (terminator instanceof BranchStmt) {
            BasicBlock[] nextBlocks = ((BranchStmt) terminator).getNextBasicBlocks();

            Set<FlowEdge<BasicBlock>> edges = cfg.getEdges(this);

            if (edges.size() != new HashSet<>(Arrays.asList(nextBlocks)).size())
                throw new IllegalStateException("Edge block missmatch");

            for (BasicBlock nextBasicBlock : nextBlocks) {
                if (!edges.contains(new BasicFlowEdge(this, nextBasicBlock))) {
                    throw new IllegalStateException("Edge block missmatch");
                }
            }
        }
    }

    // List functions
    @Override
    public boolean add(Stmt stmt) {
        return statements.add(stmt);
    }

    public void add(int index, Stmt stmt) {
        statements.add(index, stmt);
    }

    @Override
    public boolean remove(Object o) {
        return statements.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return statements.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Stmt> c) {
        return statements.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends Stmt> c) {
        return statements.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return statements.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return statements.retainAll(c);
    }

    public Stmt remove(int index) {
        return statements.remove(index);
    }

    @Override
    public boolean contains(Object o) {
        return statements.contains(o);
    }

    @Override
    public boolean isEmpty() {
        return statements.isEmpty();
    }

    public int indexOf(Object o) {
        return statements.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return statements.lastIndexOf(o);
    }

    public Stmt get(int index) {
        return statements.get(index);
    }

    public Stmt set(int index, Stmt stmt) {
        return statements.set(index, stmt);
    }

    @Override
    public int size() {
        return statements.size();
    }

    @Override
    public void clear() {
        statements.clear();
    }

    @Override
    public Iterator<Stmt> iterator() {
        return statements.iterator();
    }

    public ListIterator<Stmt> listIterator() {
        return statements.listIterator();
    }

    public ListIterator<Stmt> listIterator(int index) {
        return statements.listIterator(index);
    }

    @Override
    public Object[] toArray() {
        return statements.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return statements.toArray(a);
    }
    // End list functions

    /**
     * Returns defined variables
     */
    public Set<Local> getDefinedVariables() {
        Set<Local> locals = new HashSet<>();

        for (Stmt stmt : this) {
            if (stmt instanceof AbstractCopyStmt) {
                locals.add(((AbstractCopyStmt) stmt).getVariable().getLocal());
            }
        }

        return locals;
    }

    /**
     * Returns defined variables
     */
    public List<Local> enumerateDefinedVariables() {
        List<Local> locals = new ArrayList<>();

        for (Stmt stmt : this) {
            if (stmt instanceof AbstractCopyStmt) {
                locals.add(((AbstractCopyStmt) stmt).getVariable().getLocal());
            }
        }

        return locals;
    }

    /**
     * Returns accessed variables
     */
    public HashSet<Local> getAccessedLocals() {
        HashSet<Local> locals = new HashSet<>();

        this.getAccessedLocals(locals);

        return locals;
    }

    public void getAccessedLocals(HashSet<Local> locals) {
        for (Stmt stmt : this) {
            for (Expr expr : stmt.enumerateOnlyChildren()) {
                if (expr instanceof VarExpr) {
                    locals.add(((VarExpr) expr).getLocal());
                }
            }
        }
    }

    /**
     * Returns defined and accessed variables
     */
    public HashSet<Local> getReferencedLocals() {
        HashSet<Local> locals = new HashSet<>();

        locals.addAll(getDefinedVariables());
        locals.addAll(getAccessedLocals());

        return locals;
    }

    /**
     * Does this basic block end with a terminating instruction?
     */
    public boolean isTerminated() {
        return !this.isEmpty() && getTerminator().isTerminating();
    }

    public NotifiedList<Stmt> getStatements() {
        return statements;
    }

    public Stmt getTerminator() {
        return this.get(this.size() - 1);
    }
}
