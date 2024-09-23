package net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm;

import net.superblaubeere27.masxinlingvaj.compiler.graph.MockEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.MockGraph;
import org.junit.jupiter.api.Test;

class CycleDetectorOfTarjanTest {

    @Test
    public void testSCC() {
        MockGraph graph = new MockGraph();

        var nodeA = graph.createNode("A");
        var nodeB = graph.createNode("B");
        var nodeC = graph.createNode("C");
        var nodeD = graph.createNode("D");
        var nodeE = graph.createNode("E");
        var nodeF = graph.createNode("F");
        var nodeG = graph.createNode("G");
        var nodeH = graph.createNode("H");
        var nodeI = graph.createNode("I");
        var nodeJ = graph.createNode("J");

        graph.addEdge(new MockEdge(nodeA, nodeB));

        graph.addEdge(new MockEdge(nodeB, nodeC));

        graph.addEdge(new MockEdge(nodeC, nodeD));
        graph.addEdge(new MockEdge(nodeC, nodeE));

        graph.addEdge(new MockEdge(nodeD, nodeA));
        graph.addEdge(new MockEdge(nodeD, nodeE));

        graph.addEdge(new MockEdge(nodeE, nodeC));
        graph.addEdge(new MockEdge(nodeE, nodeF));

        graph.addEdge(new MockEdge(nodeF, nodeG));
        graph.addEdge(new MockEdge(nodeF, nodeI));

        graph.addEdge(new MockEdge(nodeG, nodeF));
        graph.addEdge(new MockEdge(nodeG, nodeH));

        graph.addEdge(new MockEdge(nodeH, nodeJ));

        graph.addEdge(new MockEdge(nodeI, nodeF));
        graph.addEdge(new MockEdge(nodeI, nodeG));

        graph.addEdge(new MockEdge(nodeJ, nodeI));

        var c = new CycleDetectorOfTarjan<>(graph, nodeA);

        System.out.println(c.getStronglyConnectedComponents());
    }

}