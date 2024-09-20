package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.LocalsPool;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

class StatementTransactionTest {

    private static ControlFlowGraph createCfg() {
        return new ControlFlowGraph(new LocalsPool(), new CompilerMethod(new CompilerClass(new ClassNode(), false), new MethodNode()), new ImmType[0], ImmType.VOID);
    }

    @Test
    void testReachableRemoval() {
        var cfg = createCfg();

        var block1 = new BasicBlock(cfg);
        var block2 = new BasicBlock(cfg);

        block1.add(new UnconditionalBranch(block2));

        block2.add(new RetVoidStmt());


        cfg.addVertex(block1);
        cfg.addVertex(block2);

        cfg.getEntries().add(block1);

        var transaction = new StatementTransaction();

        transaction.exciseBlockIfUnreferenced(block1);

        assertFalse(transaction.apply());

        assertEquals(2, cfg.vertices().size());
    }

    @Test
    void testUnreachableRemoval() {
        var cfg = createCfg();

        var block1 = new BasicBlock(cfg);
        var block3 = new BasicBlock(cfg);
        var block2 = new BasicBlock(cfg);

        block1.add(new UnconditionalBranch(block2));

        block2.add(new RetVoidStmt());

        block3.add(new RetVoidStmt());


        cfg.addVertex(block1);
        cfg.addVertex(block2);
        cfg.addVertex(block3);

        cfg.getEntries().add(block1);

        var transaction = new StatementTransaction();

        transaction.exciseBlockIfUnreferenced(block3);

        assertTrue(transaction.apply());

        assertEquals(2, cfg.vertices().size());
    }

    @Test
    void testUnreachableCircularRemoval() {
        var cfg = createCfg();

        var block1 = new BasicBlock(cfg);
        var block3 = new BasicBlock(cfg);
        var block2 = new BasicBlock(cfg);

        block1.add(new UnconditionalBranch(block2));

        block2.add(new RetVoidStmt());

        block3.add(new UnconditionalBranch(block3));


        cfg.addVertex(block1);
        cfg.addVertex(block2);
        cfg.addVertex(block3);

        cfg.getEntries().add(block1);

        var transaction = new StatementTransaction();

        transaction.exciseBlockIfUnreferenced(block3);

        assertTrue(transaction.apply());

        assertEquals(2, cfg.vertices().size());
    }

    @Test
    void testUnreachableCircularRemoval1() {
        var cfg = createCfg();

        var block1 = new BasicBlock(cfg);
        var block3 = new BasicBlock(cfg);
        var block4 = new BasicBlock(cfg);
        var block2 = new BasicBlock(cfg);

        block1.add(new UnconditionalBranch(block2));

        block2.add(new RetVoidStmt());

        block4.add(new UnconditionalBranch(block3));

        block3.add(new UnconditionalBranch(block4));


        cfg.addVertex(block1);
        cfg.addVertex(block2);
        cfg.addVertex(block3);
        cfg.addVertex(block4);

        cfg.getEntries().add(block1);

        var transaction = new StatementTransaction();

        transaction.exciseBlockIfUnreferenced(block3);

        assertTrue(transaction.apply());

        assertEquals(2, cfg.vertices().size());
    }

}