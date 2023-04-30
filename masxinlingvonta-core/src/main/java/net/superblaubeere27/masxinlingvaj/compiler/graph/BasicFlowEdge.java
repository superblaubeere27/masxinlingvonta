package net.superblaubeere27.masxinlingvaj.compiler.graph;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;

import java.util.Objects;

public class BasicFlowEdge implements FlowEdge<BasicBlock> {
    private final BasicBlock src;
    private final BasicBlock dst;

    public BasicFlowEdge(BasicBlock src, BasicBlock dst) {
        this.src = src;
        this.dst = dst;
    }


    @Override
    public BasicBlock src() {
        return this.src;
    }

    @Override
    public BasicBlock dst() {
        return this.dst;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public String toGraphString() {
        return this.src + " -> " + this.dst;
    }

    @Override
    public String toInverseString() {
        return this.src + " <- " + this.dst;
    }

    @Override
    public FlowEdge<BasicBlock> clone(BasicBlock src, BasicBlock dst) {
        return new BasicFlowEdge(src, dst);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicFlowEdge that = (BasicFlowEdge) o;
        return Objects.equals(src, that.src) && Objects.equals(dst, that.dst);
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, dst);
    }
}
