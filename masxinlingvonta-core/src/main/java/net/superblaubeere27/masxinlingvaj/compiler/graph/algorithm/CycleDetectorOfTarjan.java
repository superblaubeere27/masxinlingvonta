package net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FastDirectedGraph;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FastGraphEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FastGraphVertex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class CycleDetectorOfTarjan<G extends FastDirectedGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> {
    private final G graph;
    private final HashMap<N, TarjanParams> visitedNodes = new HashMap<>();
    private int dfsIndex = 0;
    private final ArrayList<ArrayList<N>> stronglyConnectedComponents = new ArrayList<>();

    public CycleDetectorOfTarjan(G graph, N root) {
        this.graph = graph;

        tarjan(new ArrayDeque<>(), root);
    }

    private TarjanParams tarjan(ArrayDeque<N> stack, N node) {
        var currParams = new TarjanParams(this.dfsIndex++);

        this.visitedNodes.put(node, currParams);

        stack.push(node);
        currParams.pushToStack();

        this.graph.getSuccessors(node).forEach(successor -> {
            var successorParams = this.visitedNodes.get(successor);

            // Is this edge new?
            if (successorParams == null) {
                successorParams = tarjan(stack, successor);

                currParams.lowLink = Math.min(currParams.lowLink, successorParams.lowLink);
            } else if (successorParams.isInStack) {
                currParams.lowLink = Math.min(currParams.lowLink, successorParams.searchIndex);
            }
        });

        if (currParams.lowLink == currParams.searchIndex) {
            var scc = new ArrayList<N>();

            N topOfStack;

            do {
                topOfStack = stack.pop();

                this.visitedNodes.get(topOfStack).popFromStack();

                scc.add(topOfStack);
            } while (topOfStack != node);

            Collections.reverse(scc);

            this.stronglyConnectedComponents.add(scc);
        }

        return currParams;
    }

    public ArrayList<ArrayList<N>> getStronglyConnectedComponents() {
        return stronglyConnectedComponents;
    }

    private static class TarjanParams {
        private final int searchIndex;
        private int lowLink;
        private boolean isInStack = false;

        public TarjanParams(int searchIndex) {
            this.searchIndex = searchIndex;
            this.lowLink = searchIndex;
        }

        public void pushToStack() {
            if (this.isInStack) {
                throw new IllegalStateException();
            }

            this.isInStack = true;
        }

        public void popFromStack() {
            if (!this.isInStack) {
                throw new IllegalStateException();
            }

            this.isInStack = false;
        }
    }
}
