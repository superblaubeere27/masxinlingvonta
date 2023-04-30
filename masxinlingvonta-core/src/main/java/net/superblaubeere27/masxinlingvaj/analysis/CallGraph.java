package net.superblaubeere27.masxinlingvaj.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.graph.FastDirectedGraph;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FastGraphEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FastGraphVertex;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.HashMap;

public final class CallGraph extends FastDirectedGraph<CallGraph.CallGraphMethod, CallGraph.CallGraphMethodCall> {
    private final HashMap<MethodOrFieldIdentifier, CallGraphMethod> methods = new HashMap<>();

    private int idCounter;

    private int createId() {
        return this.idCounter++;
    }

    public CallGraphMethod getMethodVertex(MethodOrFieldIdentifier identifier) {
        return this.methods.computeIfAbsent(identifier, i -> {
            CallGraphMethod method = new CallGraphMethod(this, i);

            addVertex(method);

            return method;
        });
    }

    public static class CallGraphMethod implements FastGraphVertex {
        private final int id;
        private final MethodOrFieldIdentifier method;

        public CallGraphMethod(CallGraph callGraph, MethodOrFieldIdentifier method) {
            this.id = callGraph.createId();
            this.method = method;
        }

        @Override
        public int getNumericId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.method.toString();
        }

        public MethodOrFieldIdentifier getMethod() {
            return method;
        }
    }

    public static class CallGraphMethodCall implements FastGraphEdge<CallGraphMethod> {
        private final CallGraphMethod src;
        private final CallGraphMethod dst;
        private final int count;

        public CallGraphMethodCall(CallGraphMethod src, CallGraphMethod dst, int count) {
            this.src = src;
            this.dst = dst;
            this.count = count;
        }

        @Override
        public final CallGraphMethod src() {
            return this.src;
        }

        @Override
        public final CallGraphMethod dst() {
            return this.dst;
        }

        public int getCount() {
            return count;
        }
    }
}
