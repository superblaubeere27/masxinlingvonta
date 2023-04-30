package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import com.google.common.collect.Streams;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.PhiExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.CFGUtils;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.ChainIterator;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ControlFlowGraph extends FlowGraph<BasicBlock, FlowEdge<BasicBlock>> implements Opcode {

    private final LocalsPool locals;
    private final CompilerMethod compilerMethod;
    private final ImmType[] argumentTypes;
    private final ImmType returnType;

    // used for assigning unique id's to basicblocks. ugly hack
    // fyi, we start at one arbitrarily.
    private int blockCounter = 1;

    public ControlFlowGraph(LocalsPool locals, CompilerMethod compilerMethod, ImmType[] argumentTypes, ImmType returnType) {
        this.locals = locals;
        this.compilerMethod = compilerMethod;
        this.argumentTypes = argumentTypes;
        this.returnType = returnType;
    }

    /**
     * Copy constructor
     */
    public ControlFlowGraph(ControlFlowGraph cfg) {
        super(cfg);

        this.locals = cfg.locals;
        this.compilerMethod = cfg.compilerMethod;
        this.argumentTypes = cfg.argumentTypes;
        this.returnType = cfg.returnType;
    }

    /**
     * Refactors basic blocks; replaces occurrences of the keys of forwardedBlocks with their corresponding value
     */
    public void refactorBasicBlocks(HashMap<BasicBlock, BasicBlock> forwardedBlocks) {
        var entryMapping = forwardedBlocks.get(this.getEntry());

        if (entryMapping != null) {
            this.entries.clear();
            this.entries.add(entryMapping);
        }

        for (BasicBlock block : this.vertices()) {
            if (forwardedBlocks.containsKey(block))
                continue;

            for (Stmt stmt : block) {
                if (!(stmt instanceof BranchStmt)) {
                    continue;
                }

                var prevLen = this.size();

                BranchStmt branchStmt = (BranchStmt) stmt;

                branchStmt.refactor(forwardedBlocks);

                if (prevLen != this.size()) {
                    throw new IllegalStateException();
                }
            }
        }

        forwardedBlocks.keySet().forEach(this::exciseBlock);
    }

    public int makeBlockId() {
        return blockCounter++;
    }

    public Stream<CodeUnit> allExprStream() {
        return vertices().stream().flatMap(Collection::stream).map(Stmt::enumerateWithSelf).flatMap(Streams::stream);
    }

    public void exciseBlock(BasicBlock block) {
        // We need to remember outgoing edges here as removing branches from a basic block will remove the edge with it.
        var outgoingEdges = new ArrayList<>(this.getEdges(block));

        // Excise outgoing edges
        for (FlowEdge<BasicBlock> edge : outgoingEdges) {
            this.exciseEdge(edge);
        }

        for (Stmt stmt : new ArrayList<>(block)) {
            this.exciseStmt(stmt);
        }

        this.removeVertex(block);
    }

    /**
     * Properly removes the edge, and cleans up phi uses in fe.dst of phi arguments from fe.src.
     *
     * @param fe Edge to excise phi uses.
     */
    public void exciseEdge(FlowEdge<BasicBlock> fe) {
        if (!this.containsEdge(fe))
            throw new IllegalArgumentException("Graph does not contain the specified edge");

        removeEdge(fe);
        for (Stmt stmt : fe.dst()) {
            if (stmt.getOpcode() == PHI_STORE) {
                CopyPhiStmt phs = (CopyPhiStmt) stmt;
                PhiExpr phi = phs.getExpression();

                BasicBlock pred = fe.src();

                var argExpr = phi.getArgument(pred);

                if (argExpr instanceof VarExpr) {
                    VarExpr arg = (VarExpr) argExpr;

                    locals.uses.get(arg.getLocal()).remove(arg);
                }

                phi.removeArgument(pred);
            } else {
                return;
            }
        }
    }

    /**
     * Excises uses of a removed statement.
     *
     * @param c Removed statement to update def/use information with respect to.
     */
    public void exciseStmt(Stmt c) {
        // delete uses
        for (Expr e : c.enumerateOnlyChildren()) {
            if (e.getOpcode() == Opcode.LOCAL_LOAD) {
                VarExpr v = (VarExpr) e;

                locals.uses.get(v.getLocal()).remove(v);
            }
        }

        c.getBlock().remove(c);
    }

    /**
     * Replaces an expression and updates def/use information accordingly.
     *
     * @param parent Statement containing expression to be replaced.
     * @param from   Statement to be replaced.
     * @param to     Statement to replace old statement with.
     */
    public void writeAt(CodeUnit parent, Expr from, Expr to) {
        // remove uses in from
        for (Expr e : from.enumerateWithSelf()) {
            if (e.getOpcode() == Opcode.LOCAL_LOAD) {
                locals.uses.get(((VarExpr) e).getLocal()).remove(e);
            }
        }

        // add uses in to
        for (Expr e : to.enumerateWithSelf()) {
            if (e.getOpcode() == Opcode.LOCAL_LOAD) {
                VarExpr var = (VarExpr) e;
                locals.uses.getNonNull(var.getLocal()).add(var);
            }
        }

        parent.writeAt(to, parent.indexOf(from));
    }

    @Override
    public String toString() {
        TabbedStringWriter sw = new TabbedStringWriter();

        int insn = 0;

        for (BasicBlock b : verticesInOrder()) {
            CFGUtils.blockToString(sw, this, b, insn);
        }
        return sw.toString();
    }

    public LocalsPool getLocals() {
        return locals;
    }

    @Override
    public ControlFlowGraph copy() {
        return new ControlFlowGraph(this);
    }

    @Override
    public FlowEdge<BasicBlock> clone(FlowEdge<BasicBlock> edge, BasicBlock src, BasicBlock dst) {
        return edge.clone(src, dst);
    }

    public Iterable<Stmt> stmts() {
        return () -> new ChainIterator.CollectionChainIterator<>(vertices());
    }

    public void relabel(List<BasicBlock> order) {
        if (order.size() != size())
            throw new IllegalArgumentException("order is wrong length");
        // copy edge sets
        Map<BasicBlock, Set<FlowEdge<BasicBlock>>> edges = new HashMap<>();
        for (BasicBlock b : order) {
            if (!containsVertex(b))
                throw new IllegalArgumentException("order has missing vertex " + b);
            edges.put(b, getEdges(b));
        }
        // clean graph
        clear();

        // rename and add blocks
        blockCounter = 1;
        for (BasicBlock b : order) {
            b.setId(makeBlockId());
            addVertex(b);
        }

        for (Map.Entry<BasicBlock, Set<FlowEdge<BasicBlock>>> e : edges.entrySet()) {
            BasicBlock b = e.getKey();
            for (FlowEdge<BasicBlock> fe : e.getValue()) {
                addEdge(fe);
            }
        }
    }

    public BasicBlock getEntry() {
        var it = this.getEntries().iterator();
        var entry = it.next();

        if (it.hasNext())
            throw new IllegalStateException("CFG has more than one entries?");

        return entry;
    }

    /**
     * Runs sanity checking on this graph, useful for debugging purposes.
     */
    public void verify() {
        if (getEntries().size() != 1)
            throw new IllegalStateException("Wrong number of entries: " + getEntries());

        int maxId = 0;
        Set<Integer> usedIds = new HashSet<>();

        for (BasicBlock b : vertices()) {
            if (!usedIds.add(b.getNumericId()))
                throw new IllegalStateException("Id collision: " + b);
            if (b.getNumericId() > maxId)
                maxId = b.getNumericId();

            if (getReverseEdges(b).size() == 0 && !getEntries().contains(b)) {
                throw new IllegalStateException("dead incoming: " + b);
            }

            for (FlowEdge<BasicBlock> fe : getEdges(b)) {
                if (fe.src() != b) {
                    throw new RuntimeException(fe + " from " + b);
                }

                BasicBlock dst = fe.dst();

                if (!containsVertex(dst) || !containsReverseVertex(dst)) {
                    throw new RuntimeException(
                            fe + "; dst invalid: " + containsVertex(dst) + " : " + containsReverseVertex(dst));
                }

                boolean found = getReverseEdges(dst).contains(fe);

                if (!found) {
                    throw new RuntimeException("no reverse: " + fe);
                }
            }

            b.checkConsistency();
        }
//        if (maxId != size())
//            throw new IllegalStateException("Bad id numbering: " + size() + " vertices total, but max id is " + maxId);
    }

    public Set<FlowEdge<BasicBlock>> getPredecessors(Predicate<? super FlowEdge<BasicBlock>> e, BasicBlock b) {
        Stream<FlowEdge<BasicBlock>> s = getReverseEdges(b).stream();
        s = s.filter(e);
        return s.collect(Collectors.toSet());
    }

    public Set<FlowEdge<BasicBlock>> getSuccessors(Predicate<? super FlowEdge<BasicBlock>> e, BasicBlock b) {
        Stream<FlowEdge<BasicBlock>> s = getEdges(b).stream();
        s = s.filter(e);
        return s.collect(Collectors.toSet());
    }

    public ImmType getReturnType() {
        return returnType;
    }

    public ImmType[] getArgumentTypes() {
        return argumentTypes;
    }

    public CompilerMethod getCompilerMethod() {
        return compilerMethod;
    }
}
