package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.locals.StackLocal;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.locals.SyntheticLocal;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.AbstractCopyStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LocalsPool implements ValueCreator<GenericBitSet<Local>> {

    public final Map<Local, AbstractCopyStmt> defs;
    public final NullPermeableHashMap<Local, Set<VarExpr>> uses;
    // A little cache to ensure that every equal variable is using the same object
    private final HashMap<Local, Local> cache;
    private final BitSetIndexer<Local> indexer = new IncrementalBitSetIndexer<>();

    private final int[] syntheticIndices = new int[ImmType.values().length];
    private final int[] staticVarIndices = new int[ImmType.values().length];

    public LocalsPool() {
        cache = new HashMap<>();

        defs = new HashMap<>();
        uses = new NullPermeableHashMap<>(HashSet::new);
    }

    /**
     * Ensures that equivalent variables are using the same objects
     */
    private Local ensureSameObject(Local l) {
        return this.cache.computeIfAbsent(l, local -> local);
    }

    public Local getStackLocal(int stackIdx, ImmType type) {
        return ensureSameObject(new StackLocal(stackIdx, false, type));
    }

    public Local getLocal(int localIdx, ImmType type) {
        return ensureSameObject(new StackLocal(localIdx, true, type));
    }

    /**
     * Allocates a unique synthetic local with the given type
     */
    public Local allocSynthetic(ImmType immType) {
        return new SyntheticLocal(this.syntheticIndices[immType.ordinal()]++, immType);
    }

    /**
     * Allocates a unique synthetic local with the given type
     */
    public StaticLocal allocStatic(ImmType immType) {
        return new StaticLocal(this.staticVarIndices[immType.ordinal()]++, immType);
    }

    public GenericBitSet<Local> createBitSet() {
        return new GenericBitSet<>(indexer);
    }

    @Override
    public GenericBitSet<Local> create() {
        return createBitSet();
    }

}
