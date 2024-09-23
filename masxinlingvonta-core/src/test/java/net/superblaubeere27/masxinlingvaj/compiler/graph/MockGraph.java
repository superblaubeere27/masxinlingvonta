package net.superblaubeere27.masxinlingvaj.compiler.graph;

public class MockGraph extends FastDirectedGraph<MockNode, MockEdge> {
    private int nodeCounter;

    public MockNode createNode(String displayName) {
        return new MockNode(this.nodeCounter++, displayName);
    }

}
