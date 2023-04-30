package net.superblaubeere27.masxinlingvaj.compiler.newAST.utils;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;

import java.util.Iterator;

public class CFGUtils {

    public static void blockToString(TabbedStringWriter sw, ControlFlowGraph cfg, BasicBlock b, int insn) {
        // sw.print("===#Block " + b.getId() + "(size=" + (b.size()) + ")===");
//        sw.print(String.format("===#Block %s(size=%d, flags=%s)===", b.getDisplayName(), b.size(), Integer.toBinaryString(b.getFlags())));
        sw.print(b.getDisplayName() + ":");
        sw.tab();

        Iterator<Stmt> it = b.iterator();
        if (!it.hasNext()) {
            sw.untab();
        } else {
            sw.print("\n");
        }
        while (it.hasNext()) {
            Stmt stmt = it.next();
//			sw.print(stmt.getId() + ". ");
//            sw.print(insn++ + ". ");
            stmt.toString(sw);

            if (!it.hasNext()) {
                sw.untab();
            } else {
                sw.print("\n");
            }
        }

//        sw.tab();
//        sw.tab();
//
//        if(cfg.containsVertex(b)) {
//            for(FlowEdge<BasicBlock> e : cfg.getEdges(b)) {
////				if(e.getType() != FlowEdges.TRYCATCH) {
//                sw.print("\n-> " + e.toString());
////				}
//            }
//
//            for(FlowEdge<BasicBlock> p : cfg.getReverseEdges(b)) {
////				if(p.getType() != FlowEdges.TRYCATCH) {
//                sw.print("\n<- " + p.toString());
////				}
//            }
//        }
//
//        sw.untab();
//        sw.untab();

        sw.print("\n");
    }

}
