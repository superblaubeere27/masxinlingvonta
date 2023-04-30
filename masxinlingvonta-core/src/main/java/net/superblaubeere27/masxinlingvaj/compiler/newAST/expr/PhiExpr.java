package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.*;
import java.util.stream.Collectors;

public class PhiExpr extends Expr {

    private final Map<BasicBlock, Expr> arguments;
    private ImmType type;

    public PhiExpr(Map<BasicBlock, Expr> arguments, ImmType type) {
        super(PHI);

        this.arguments = arguments;
        this.type = type;

        this.arguments.forEach((b, x) -> CodeUnit.linkPhi(this, Objects.requireNonNull(x)));
    }

    public int getArgumentCount() {
        return arguments.size();
    }

    public Set<BasicBlock> getSources() {
        return arguments.keySet();
    }

    public Map<BasicBlock, Expr> getArguments() {
        return Collections.unmodifiableMap(arguments);
    }

    public Expr getArgument(BasicBlock b) {
        return arguments.get(b);
    }

    public void setArgument(BasicBlock b, Expr e) {
        Objects.requireNonNull(e);

        CodeUnit.linkPhi(this, e);

        CodeUnit.unlinkPhi(this, arguments.put(b, e));
    }

    @Override
    public void setBlock(BasicBlock block) {
        super.setBlock(block);

        for (Map.Entry<BasicBlock, Expr> basicBlockExprEntry : this.arguments.entrySet()) {
            basicBlockExprEntry.getValue().setBlock(block);
        }
    }

    public Expr removeArgument(BasicBlock b) {
        var prev = arguments.remove(b);

        CodeUnit.unlinkPhi(this, prev);

        return prev;
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public PhiExpr copy() {
        Map<BasicBlock, Expr> map = new HashMap<>();

        for (Map.Entry<BasicBlock, Expr> e : arguments.entrySet()) {
            map.put(e.getKey(), e.getValue().copy());
        }

        return new PhiExpr(map, this.type);
    }

    @Override
    public ImmType getType() {
        return type;
    }

    public void setType(ImmType type) {
        this.type = type;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("phi " + this.arguments.entrySet().stream().map(entry -> "[" + entry.getKey().getDisplayName() + ", " + entry.getValue() + "]").collect(Collectors.joining(", ")));
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (s instanceof PhiExpr) {
            PhiExpr phi = (PhiExpr) s;

            Set<BasicBlock> sources = new HashSet<>();
            sources.addAll(arguments.keySet());
            sources.addAll(phi.arguments.keySet());

            if (sources.size() != arguments.size()) {
                return false;
            }

            for (BasicBlock b : sources) {
                Expr e1 = arguments.get(b);
                Expr e2 = phi.arguments.get(b);
                if (e1 == null || e2 == null) {
                    return false;
                }
                if (!e1.equivalent(e2)) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var phi = LLVM.LLVMBuildPhi(ctx.getBuilder(), this.type.toNativeType().getLLVMType(), "phi");

        this.arguments.forEach((block, expr) -> {
            LLVM.LLVMAddIncoming(phi, expr.compile(ctx), ctx.getLLVMBlock(block), 1);
        });

        return phi;
    }

    public void refactorBasicBlocks(HashMap<BasicBlock, BasicBlock> basicBlockMap) {
        var sources = new ArrayList<>(this.getSources());

        for (BasicBlock source : sources) {
            BasicBlock replacement = basicBlockMap.get(source);

            if (replacement != null) {
                var expr = this.removeArgument(source);

                this.setArgument(replacement, expr);
            }
        }
    }
}