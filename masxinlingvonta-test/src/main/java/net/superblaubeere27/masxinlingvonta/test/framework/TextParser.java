package net.superblaubeere27.masxinlingvonta.test.framework;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.FloatingPointCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling.CatchExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.CreateLocalRefExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.AllocArrayExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLengthExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLoadExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.InstanceOfExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.SwitchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.*;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirBaseVisitor;
import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirParser;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static net.superblaubeere27.masxinlingvaj.utils.TypeUtils.getEffectiveArgumentTypes;

public class TextParser extends mlvirBaseVisitor<Object> {
    private final mlvirParser parser;
    private final CompilerClass compilerClass;
    ControlFlowGraph cfg;
    private HashMap<String, Local> localMap;
    private HashMap<String, BasicBlock> blockMap;

    public TextParser(mlvirParser parser, CompilerClass compilerClass) {
        this.parser = parser;
        this.compilerClass = compilerClass;
    }

    private static FloatingPointArithmeticsExpr.Operator getFPArithmeticsOpcode(String operator) {
        return switch (operator) {
            case "add" -> FloatingPointArithmeticsExpr.Operator.ADD;
            case "sub" -> FloatingPointArithmeticsExpr.Operator.SUB;
            case "mul" -> FloatingPointArithmeticsExpr.Operator.MUL;
            case "div" -> FloatingPointArithmeticsExpr.Operator.DIV;
            case "rem" -> FloatingPointArithmeticsExpr.Operator.REM;
            default -> throw new IllegalStateException("Unexpected value: " + operator);
        };
    }

    private static IntegerArithmeticsExpr.Operator getIntegerArithmeticsOpcode(String operator) {
        return switch (operator) {
            case "add" -> IntegerArithmeticsExpr.Operator.ADD;
            case "sub" -> IntegerArithmeticsExpr.Operator.SUB;
            case "mul" -> IntegerArithmeticsExpr.Operator.MUL;
            case "div" -> IntegerArithmeticsExpr.Operator.DIV;
            case "rem" -> IntegerArithmeticsExpr.Operator.REM;
            case "shl" -> IntegerArithmeticsExpr.Operator.SHL;
            case "shr" -> IntegerArithmeticsExpr.Operator.SHR;
            case "ushr" -> IntegerArithmeticsExpr.Operator.USHR;
            case "or" -> IntegerArithmeticsExpr.Operator.OR;
            case "and" -> IntegerArithmeticsExpr.Operator.AND;
            case "xor" -> IntegerArithmeticsExpr.Operator.XOR;
            default -> throw new IllegalStateException("Unexpected value: " + operator);
        };
    }

    /**
     * Extracts the type from variable names (i.e. '%5A' -> OBJECT)
     */
    private static ImmType getTypeForLocalName(String x) {
        return switch (x.charAt(x.length() - 1)) {
            case 'Z' -> ImmType.BOOL;
            case 'I' -> ImmType.INT;
            case 'J' -> ImmType.LONG;
            case 'F' -> ImmType.FLOAT;
            case 'D' -> ImmType.DOUBLE;
            case 'A' -> ImmType.OBJECT;
            default ->
                    throw new IllegalStateException("Local name ended with unexpected type: " + x.charAt(x.length() - 1));
        };
    }

    @Override
    public Stmt visitTerminator(mlvirParser.TerminatorContext ctx) {
        return (Stmt) super.visitTerminator(ctx);
    }

    @Override
    public List<ControlFlowGraph> visitFile(mlvirParser.FileContext ctx) {
        return ctx.method().stream().map(this::visitMethod).collect(Collectors.toList());
    }

    @Override
    public ControlFlowGraph visitMethod(mlvirParser.MethodContext ctx) {
        boolean isStatic = ctx.STATIC() != null;

        var cn = new ClassNode();
        var mn = new MethodNode();

        cn.name = ctx.className().getText();
        mn.access |= isStatic ? Opcodes.ACC_STATIC : 0;
        mn.name = ctx.methodName().getText();
        mn.desc = ctx.methodType().getText();

        var compilerMethod = new CompilerMethod(this.compilerClass, mn);

        this.blockMap = new HashMap<>();
        this.localMap = new HashMap<>();
        this.cfg = new ControlFlowGraph(new LocalsPool(), compilerMethod, Arrays.stream(getEffectiveArgumentTypes(compilerMethod)).map(ImmType::fromJVMType).toArray(ImmType[]::new), ImmType.fromJVMType(Type.getReturnType(compilerMethod.getNode().desc)));

        boolean first = true;

        for (mlvirParser.BlockContext blockContext : ctx.block()) {
            var block = this.visitBlock(blockContext);

            if (first) {
                cfg.getEntries().add(block);

                first = false;
            }

        }

        return cfg;
    }

    @Override
    public BasicBlock visitBlock(mlvirParser.BlockContext ctx) {
        var block = getBlockForName(ctx.IDENTIFIER().getText());

        for (mlvirParser.StatementContext statementContext : ctx.statement()) {
            block.add(visitStatement(statementContext));
        }

        block.add(visitTerminator(ctx.terminator()));

        return block;
    }

    @Override
    public Stmt visitStatement(mlvirParser.StatementContext ctx) {
        var stmt = super.visit(ctx.getChild(0));

        if (stmt == null) {
            throw new MLVIRParseError("Stmt null?", ctx, this.parser);
        }
        if (!(stmt instanceof Stmt stmt1)) {
            throw new MLVIRParseError("Stmt not stmt?", ctx, this.parser);
        }

        return stmt1;
    }

    @Override
    public Object visitClearExceptionStmt(mlvirParser.ClearExceptionStmtContext ctx) {
        return new ClearExceptionStateStmt();
    }

    @Override
    public Object visitMonitorStmt(mlvirParser.MonitorStmtContext ctx) {
        return new MonitorStmt(ctx.monitorOpcode().getText().equals("monitorenter") ? MonitorStmt.MonitorType.ENTER : MonitorStmt.MonitorType.EXIT, visitExpr(ctx.expr()));
    }

    @Override
    public Object visitThrowStmt(mlvirParser.ThrowStmtContext ctx) {
        return new ThrowStmt(visitExpr(ctx.expr()));
    }

    @Override
    public Object visitDeleteStmt(mlvirParser.DeleteStmtContext ctx) {
        return new DeleteRefStmt(visitExpr(ctx.expr()));
    }

    @Override
    public Object visitExprStmt(mlvirParser.ExprStmtContext ctx) {
        return new ExpressionStmt(visitExpr(ctx.expr()));
    }

    @Override
    public Object visitPutStaticStmt(mlvirParser.PutStaticStmtContext ctx) {
        return new PutStaticStmt(new MethodOrFieldIdentifier(ctx.className().getText(), ctx.IDENTIFIER().getText(), ctx.type().getText()), visitExpr(ctx.expr()));
    }

    @Override
    public Object visitPutFieldStmt(mlvirParser.PutFieldStmtContext ctx) {
        return new PutFieldStmt(new MethodOrFieldIdentifier(ctx.className().getText(), ctx.IDENTIFIER().getText(), ctx.type().getText()), visitExpr(ctx.expr(0)), visitExpr(ctx.expr(1)));
    }

    @Override
    public Object visitArrayStoreStmt(mlvirParser.ArrayStoreStmtContext ctx) {
        var exprs = ctx.expr();

        return new ArrayStoreStmt(getJNITypeFromTypeName(ctx.arrayTypeName().getText()), this.visitExpr(exprs.get(0)), this.visitExpr(exprs.get(1)), this.visitExpr(exprs.get(2)));
    }

    @Override
    public Object visitVarStmt(mlvirParser.VarStmtContext ctx) {
        return new CopyVarStmt(visitVarExpr(ctx.varExpr()), visitExpr(ctx.expr()));
    }

    @Override
    public Object visitPhiStmt(mlvirParser.PhiStmtContext ctx) {
        var edges = new HashMap<BasicBlock, Expr>();

        ImmType type = null;

        for (mlvirParser.PhiEdgeContext phiEdgeContext : ctx.phiEdge()) {
            var expr = visitExpr(phiEdgeContext.expr());

            edges.put(getBlockForName(phiEdgeContext.IDENTIFIER().getText()), expr);

            var currType = expr.getType();

            if (type != null && currType != type)
                throw new MLVIRParseError("Cannot merge phi types", phiEdgeContext, this.parser);

            type = currType;
        }

        return new CopyPhiStmt(visitVarExpr(ctx.varExpr()), new PhiExpr(edges, type));
    }

    @Override
    public UnconditionalBranch visitBr(mlvirParser.BrContext ctx) {
        return new UnconditionalBranch(getBlockForName(ctx.IDENTIFIER().getText()));
    }

    @Override
    public ConditionalBranch visitCondBr(mlvirParser.CondBrContext ctx) {
        return new ConditionalBranch(visitExpr(ctx.expr()), getBlockForName(ctx.IDENTIFIER().get(0).getText()), getBlockForName(ctx.IDENTIFIER().get(1).getText()));
    }

    @Override
    public ExceptionCheckStmt visitExceptionBr(mlvirParser.ExceptionBrContext ctx) {
        return new ExceptionCheckStmt(getBlockForName(ctx.IDENTIFIER().get(0).getText()), getBlockForName(ctx.IDENTIFIER().get(1).getText()));
    }

    @Override
    public Stmt visitRet(mlvirParser.RetContext ctx) {
        if (ctx.VOID() != null) {
            return new RetVoidStmt();
        }

        return new RetStmt(visitExpr(ctx.expr()));
    }

    @Override
    public SwitchStmt visitSwitch(mlvirParser.SwitchContext ctx) {
        var cases = ctx.switchCase();

        int[] keys = new int[cases.size()];
        BasicBlock[] targets = new BasicBlock[cases.size()];

        for (int i = 0; i < cases.size(); i++) {
            keys[i] = Integer.parseInt(cases.get(i).INTEGER().getText());
            targets[i] = getBlockForName(cases.get(i).IDENTIFIER().getText());
        }

        return new SwitchStmt(visitExpr(ctx.expr()), keys, targets, getBlockForName(ctx.IDENTIFIER().getText()));
    }

    @Override
    public ParamExpr visitParamExpr(mlvirParser.ParamExprContext ctx) {
        return new ParamExpr(this.cfg, Integer.parseInt(ctx.INTEGER().getText()));
    }

    @Override
    public VarExpr visitVarExpr(mlvirParser.VarExprContext ctx) {
        return new VarExpr(this.getLocalForName(ctx.VAR().getText().substring(1)));
    }

    @Override
    public ArrayLoadExpr visitArrayLoadExpr(mlvirParser.ArrayLoadExprContext ctx) {
        var exprs = ctx.lowerLevelExpr();

        return new ArrayLoadExpr(getJNITypeFromTypeName(ctx.arrayTypeName().getText()), this.visitLowerLevelExpr(exprs.get(0)), this.visitLowerLevelExpr(exprs.get(1)));
    }

    @Override
    public AllocArrayExpr visitAllocArrayExpr(mlvirParser.AllocArrayExprContext ctx) {
        return new AllocArrayExpr(visitType(ctx.type()), visitExpr(ctx.expr()));
    }

    @Override
    public PrimitiveCastExpr visitPrimitiveCastExpr(mlvirParser.PrimitiveCastExprContext ctx) {
        return new PrimitiveCastExpr(visitExpr(ctx.expr()), getCastTargetFromJNIType(visitPrimitiveTypeName(ctx.primitiveTypeName())));
    }

    @Override
    public ArrayLengthExpr visitArrayLenExpr(mlvirParser.ArrayLenExprContext ctx) {
        return new ArrayLengthExpr(visitExpr(ctx.expr()));
    }

    @Override
    public CreateLocalRefExpr visitCreateLocalRefExpr(mlvirParser.CreateLocalRefExprContext ctx) {
        return new CreateLocalRefExpr(visitExpr(ctx.expr()));
    }

    @Override
    public CheckCastExpr visitCheckcastExpr(mlvirParser.CheckcastExprContext ctx) {
        return new CheckCastExpr(ctx.className().getText(), visitExpr(ctx.expr()));
    }

    @Override
    public InstanceOfExpr visitInstanceOfExpr(mlvirParser.InstanceOfExprContext ctx) {
        return new InstanceOfExpr(ctx.className().getText(), visitExpr(ctx.expr()));
    }

    @Override
    public InvokeInstanceExpr visitInvokeInstanceExpr(mlvirParser.InvokeInstanceExprContext ctx) {
        return new InvokeInstanceExpr(new MethodOrFieldIdentifier(ctx.className().getText(), ctx.methodName().getText(), ctx.methodType().getText()), visitExpr(ctx.expr()), visitExprList(ctx.exprList()), visitCallType(ctx.callType()));
    }

    @Override
    public InvokeStaticExpr visitInvokeStaticExpr(mlvirParser.InvokeStaticExprContext ctx) {
        return new InvokeStaticExpr(new MethodOrFieldIdentifier(ctx.className().getText(), ctx.methodName().getText(), ctx.methodType().getText()), visitExprList(ctx.exprList()));
    }

    @Override
    public NegationExpr visitNegationExpr(mlvirParser.NegationExprContext ctx) {
        return new NegationExpr(visitExpr(ctx.expr()));
    }

    @Override
    public CatchExpr visitCatchExpr(mlvirParser.CatchExprContext ctx) {
        return new CatchExpr();
    }

    @Override
    public Object visitAllocObjectExpr(mlvirParser.AllocObjectExprContext ctx) {
        return new AllocObjectExpr(ctx.getText());
    }

    @Override
    public Object visitMathExpr(mlvirParser.MathExprContext ctx) {
        var lhs = visitExpr(ctx.expr(0));
        var rhs = visitExpr(ctx.expr(1));

        if (ctx.intMathOpcode() != null) {
            return new IntegerArithmeticsExpr(getIntegerArithmeticsOpcode(ctx.intMathOpcode().getText()), IntegerArithmeticsExpr.IntegerType.INT, lhs, rhs);
        }
        if (ctx.longMathOpcode() != null) {
            return new IntegerArithmeticsExpr(getIntegerArithmeticsOpcode(ctx.longMathOpcode().getText().substring(1)), IntegerArithmeticsExpr.IntegerType.LONG, lhs, rhs);
        }
        if (ctx.floatMathOpcode() != null) {
            return new FloatingPointArithmeticsExpr(getFPArithmeticsOpcode(ctx.floatMathOpcode().getText().substring(1)), FloatingPointArithmeticsExpr.FloatingPointType.FLOAT, lhs, rhs);
        }
        if (ctx.doubleMathOpcode() != null) {
            return new FloatingPointArithmeticsExpr(getFPArithmeticsOpcode(ctx.doubleMathOpcode().getText().substring(1)), FloatingPointArithmeticsExpr.FloatingPointType.DOUBLE, lhs, rhs);
        }

        throw new IllegalStateException("???");
    }

    @Override
    public Object visitCmpExpr(mlvirParser.CmpExprContext ctx) {
        if (ctx.fcmpExpr() != null) {
            return this.visitFcmpExpr(ctx.fcmpExpr());
        }

        var lhs = visitLowerLevelExpr(ctx.lowerLevelExpr(0));
        var rhs = visitLowerLevelExpr(ctx.lowerLevelExpr(1));

        if (ctx.acmpOpcode() != null) {
            return new ObjectCompareExpr(lhs, rhs);
        }

        return new IntegerCompareExpr(visitIcmpOpcode(ctx.icmpOpcode()), lhs, rhs);
    }

    @Override
    public Object visitFcmpExpr(mlvirParser.FcmpExprContext ctx) {
        return new FloatingPointCompareExpr(ctx.fcmpType().getText().equals("fcmp") ? FloatingPointArithmeticsExpr.FloatingPointType.FLOAT : FloatingPointArithmeticsExpr.FloatingPointType.DOUBLE, visitFcmpOpcode(ctx.fcmpOpcode()), visitExpr(ctx.expr(0)), visitExpr(ctx.expr(1)));
    }

    @Override
    public Object visitGetFieldExpr(mlvirParser.GetFieldExprContext ctx) {
        return new GetFieldExpr(new MethodOrFieldIdentifier(ctx.className().getText(), ctx.IDENTIFIER().getText(), ctx.type().getText()), visitExpr(ctx.expr()));
    }

    @Override
    public Object visitGetStaticExpr(mlvirParser.GetStaticExprContext ctx) {
        return new GetStaticExpr(new MethodOrFieldIdentifier(ctx.className().getText(), ctx.IDENTIFIER().getText(), ctx.type().getText()));
    }

    @Override
    public FloatingPointCompareExpr.Operator visitFcmpOpcode(mlvirParser.FcmpOpcodeContext ctx) {
        return FloatingPointCompareExpr.Operator.getByName(ctx.getText());
    }

    @Override
    public IntegerCompareExpr.Operator visitIcmpOpcode(mlvirParser.IcmpOpcodeContext ctx) {
        return IntegerCompareExpr.Operator.getByName(ctx.getText());
    }

    @Override
    public Object visitConstTypeExpr(mlvirParser.ConstTypeExprContext ctx) {
        return new ConstTypeExpr(visitType(ctx.type()));
    }

    @Override
    public Object visitConstStrExpr(mlvirParser.ConstStrExprContext ctx) {
        return new ConstStringExpr(ctx.getText().substring(1, ctx.getText().length() - 2));
    }

    @Override
    public Object visitConstNullExpr(mlvirParser.ConstNullExprContext ctx) {
        return new ConstNullExpr();
    }

    @Override
    public Object visitConstRealExpr(mlvirParser.ConstRealExprContext ctx) {
        if (ctx.doubleMarker() != null) {
            return new ConstDoubleExpr(Double.parseDouble(ctx.REAL().getText()));
        } else {
            return new ConstFloatExpr(Float.parseFloat(ctx.REAL().getText()));
        }
    }

    @Override
    public Object visitConstIntExpr(mlvirParser.ConstIntExprContext ctx) {
        if (ctx.longMarker() != null) {
            return new ConstLongExpr(Long.parseLong(ctx.INTEGER().getText()));
        } else {
            return new ConstIntExpr(Integer.parseInt(ctx.INTEGER().getText()));
        }
    }

    @Override
    public Expr[] visitExprList(mlvirParser.ExprListContext ctx) {
        return ctx.expr().stream().map(this::visitExpr).toArray(Expr[]::new);
    }

    @Override
    public InvokeInstanceExpr.InvokeInstanceType visitCallType(mlvirParser.CallTypeContext ctx) {
        return switch (ctx.getText()) {
            case "INVOKE_SPECIAL" -> InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL;
            case "INVOKE_VIRTUAL" -> InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL;
            case "INVOKE_INTERFACE" -> InvokeInstanceExpr.InvokeInstanceType.INVOKE_INTERFACE;
            default -> throw new IllegalStateException("Unexpected value: " + ctx.getText());
        };
    }

    @Override
    public Type visitType(mlvirParser.TypeContext ctx) {
        return Type.getType(ctx.getText());
    }

    @Override
    public Expr visitExpr(mlvirParser.ExprContext ctx) {
        var expr = super.visitExpr(ctx);

        if (expr == null) {
            throw new MLVIRParseError("Expr null?", ctx, this.parser);
        }
        if (!(expr instanceof Expr expr1)) {
            throw new MLVIRParseError("Expr not expr?", ctx, this.parser);
        }

        return expr1;
    }

    @Override
    public Expr visitLowerLevelExpr(mlvirParser.LowerLevelExprContext ctx) {
        var expr = super.visitLowerLevelExpr(ctx);

        if (expr == null) {
            throw new MLVIRParseError("Expr null?", ctx, this.parser);
        }
        if (!(expr instanceof Expr expr1)) {
            throw new MLVIRParseError("Expr not expr?", ctx, this.parser);
        }

        return expr1;
    }

    private Local getLocalForName(String name) {
        return this.localMap.computeIfAbsent(name, x -> this.cfg.getLocals().allocStatic(getTypeForLocalName(x)));
    }

    private BasicBlock getBlockForName(String name) {
        return this.blockMap.computeIfAbsent(name, x -> new BasicBlock(this.cfg));
    }

    @Override
    public JNIType visitPrimitiveTypeName(mlvirParser.PrimitiveTypeNameContext ctx) {
        return getJNITypeFromTypeName(ctx.getText());
    }

    private JNIType getJNITypeFromTypeName(String name) {
        return switch (name) {
            case "Object" -> JNIType.OBJECT;
            case "boolean" -> JNIType.BOOLEAN;
            case "byte" -> JNIType.BYTE;
            case "int" -> JNIType.INT;
            case "short" -> JNIType.SHORT;
            case "char" -> JNIType.CHAR;
            case "long" -> JNIType.LONG;
            case "float" -> JNIType.FLOAT;
            case "double" -> JNIType.DOUBLE;
            default -> throw new IllegalStateException("Unexpected type name: " + name);
        };
    }

    private PrimitiveCastExpr.CastTarget getCastTargetFromJNIType(JNIType jniType) {
        return switch (jniType) {
            case CHAR -> PrimitiveCastExpr.CastTarget.CHAR;
            case BYTE -> PrimitiveCastExpr.CastTarget.BYTE;
            case SHORT -> PrimitiveCastExpr.CastTarget.SHORT;
            case INT -> PrimitiveCastExpr.CastTarget.INT;
            case LONG -> PrimitiveCastExpr.CastTarget.LONG;
            case FLOAT -> PrimitiveCastExpr.CastTarget.FLOAT;
            case DOUBLE -> PrimitiveCastExpr.CastTarget.DOUBLE;
            default -> throw new IllegalStateException("Invalid cast target: " + jniType);
        };
    }
}
