package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.FlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.graph.SimpleDfs;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.CycleDetectorOfTarjan;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.BranchStmt;
import net.superblaubeere27.masxinlingvaj.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Traverses the cfg in a topological order.
 */
class LocalVariableAnalysisExecutor {
    private final LocalVariableAnalyzer analyzer;
    /**
     * General priority queue. Important when we don't have anything left in the current cycleIndex
     */
    private final TreeSet<AnalyzedBlock> generalPriorityQueue = new TreeSet<>();
    /**
     * We try to visit strongly connected components first to reduce the amount of unnecessary backtracking
     */
    private final HashMap<Integer, TreeSet<AnalyzedBlock>> priorityQueueByCycleIndex = new HashMap<>();
    private final HashMap<BasicBlock, AnalyzedBlock> blockToAnalyzedObject = new HashMap<>();

    private final HashMap<FlowEdge<BasicBlock>, LocalInfoSnapshot> nextSnapshots = new HashMap<>();
    private final HashMap<BasicBlock, LocalInfoSnapshot> specialSnapshots = new HashMap<>();

    public int count = 0;

    public LocalVariableAnalysisExecutor(LocalVariableAnalyzer analyzer) {
        this.analyzer = analyzer;

        this.calculateBlockMetadata();
    }

    void analysisLoop(LocalInfoSnapshot entrySnapshot) {
        this.specialSnapshots.put(this.analyzer.cfg.getEntry(), entrySnapshot);

        var entryBlock = this.blockToAnalyzedObject.get(this.analyzer.cfg.getEntry());

        enqueueBlock(entryBlock);

        AnalyzedBlock currentBlock = getNextBlockInQueue(-1);

        while (currentBlock != null) {
            var currentBlockSnapshot = this.getLatestBlockSnapshot(currentBlock.basicBlock);

            var lastBlockSnapshot = this.analyzer.basicBlockSnapshots.put(currentBlock.basicBlock, currentBlockSnapshot);

            // If the last snapshot is null, we know that we did not analyze it yet.
            if (lastBlockSnapshot != null && currentBlockSnapshot.isEquivalent(lastBlockSnapshot) && currentBlock.wasVisited()) {
                currentBlock = getNextBlockInQueue(currentBlock.stronglyConnectedGroupIndex);
                count++;

                continue;
            }

            var snapshotAtTerminator = processBlockStatements(currentBlockSnapshot, currentBlock.basicBlock);

            // Update outgoing edges
            if (currentBlock.basicBlock.getTerminator() instanceof BranchStmt branchStmt) {
                updateOutgoingEdges(currentBlock.basicBlock, snapshotAtTerminator, branchStmt);
            }

            currentBlock.markAsVisited();

            currentBlock = getNextBlockInQueue(currentBlock.stronglyConnectedGroupIndex);
            count++;
        }
    }

    private void updateOutgoingEdges(BasicBlock currentBlock, LocalInfoSnapshot snapshotAtTerminator, BranchStmt branchStmt) {
        Pair<BasicBlock, LocalInfoSnapshot>[] successiveBlockSnapshots = LocalVariableAnalyzer.getSuccessiveBlockSnapshots(snapshotAtTerminator, branchStmt);

        for (Pair<BasicBlock, LocalInfoSnapshot> successiveBlockSnapshot : successiveBlockSnapshots) {
            var edgeIdentifier = new BasicFlowEdge(currentBlock, successiveBlockSnapshot.getFirst());
            var currentSnapshot = successiveBlockSnapshot.getSecond();
            var lastSnapshot = this.nextSnapshots.put(edgeIdentifier, currentSnapshot);

            // Process/Reprocess if the edge wasn't processed yet or if it changed.
            if (lastSnapshot == null || currentSnapshot.isEquivalent(lastSnapshot)) {
                enqueueBlock(this.blockToAnalyzedObject.get(successiveBlockSnapshot.getFirst()));
            }
        }
    }

    private LocalInfoSnapshot processBlockStatements(LocalInfoSnapshot currentSnapshot, BasicBlock block) {
        for (Stmt stmt : block) {
            currentSnapshot = this.analyzer.processStatement(stmt, currentSnapshot);

            this.analyzer.snapshots.put(stmt, currentSnapshot);
        }

        return currentSnapshot;
    }

    /**
     * Merges all incoming edges to block into a single snapshot.
     */
    private LocalInfoSnapshot getLatestBlockSnapshot(BasicBlock block) {
        // Mostly for the entry block
        var specialSnapshot = this.specialSnapshots.get(block);
        var incomingSnapshots = this.analyzer.cfg.getReverseEdges(block).stream().map(this.nextSnapshots::get).filter(Objects::nonNull).toList();

        if (specialSnapshot != null) {
            return specialSnapshot.merge(incomingSnapshots);
        }

        if (incomingSnapshots.isEmpty()) {
            throw new IllegalStateException("This situation should be impossible. Maybe an entry snapshot is missing?");
        }

        return incomingSnapshots.get(0).merge(incomingSnapshots.subList(1, incomingSnapshots.size()));
    }

    private AnalyzedBlock getNextBlockInQueue(int currentConnectedGroup) {
        var priorityQueueOfConnectedGroup = this.priorityQueueByCycleIndex.get(currentConnectedGroup);

        if (priorityQueueOfConnectedGroup != null) {
            var firstInConnectedGroup = priorityQueueOfConnectedGroup.pollFirst();

            if (firstInConnectedGroup != null) {
                generalPriorityQueue.remove(firstInConnectedGroup);

                return firstInConnectedGroup;
            }
        }

        var firstInGeneralGroup = generalPriorityQueue.pollFirst();

        if (firstInGeneralGroup != null) {
            var groupOf = this.priorityQueueByCycleIndex.get(firstInGeneralGroup.stronglyConnectedGroupIndex);

            if (groupOf != null) {
                groupOf.remove(firstInGeneralGroup);
            }
        }

        return firstInGeneralGroup;
    }

    private void calculateBlockMetadata() {
        var cycleDetector = new CycleDetectorOfTarjan<>(this.analyzer.cfg, this.analyzer.cfg.getEntry());

        int sccIndex = 0;

        for (ArrayList<BasicBlock> stronglyConnectedComponent : cycleDetector.getStronglyConnectedComponents()) {
            for (BasicBlock basicBlock : stronglyConnectedComponent) {
                this.blockToAnalyzedObject.put(basicBlock, new AnalyzedBlock(basicBlock, sccIndex));
            }

            if (stronglyConnectedComponent.size() > 1) {
                sccIndex++;
            }
        }

        int topologicalIndex = 0;

        for (BasicBlock block : SimpleDfs.topoorder(this.analyzer.cfg, this.analyzer.cfg.getEntry())) {
            this.blockToAnalyzedObject.get(block).topologicalDepth = topologicalIndex++;
        }
    }

    private void enqueueBlock(AnalyzedBlock analyzedBlock) {
        var priorityQueueByCycle = this.priorityQueueByCycleIndex.computeIfAbsent(analyzedBlock.stronglyConnectedGroupIndex, idx -> new TreeSet<>());

        this.generalPriorityQueue.add(analyzedBlock);
        priorityQueueByCycle.add(analyzedBlock);
    }

    static class AnalyzedBlock implements Comparable<AnalyzedBlock> {
        private final BasicBlock basicBlock;
        /**
         * First analyze in the loop
         */
        private final int stronglyConnectedGroupIndex;
        /**
         * Depth in topological sorting (minimize the amount of backtracking)
         */
        private int topologicalDepth;

        private boolean visited = false;

        public AnalyzedBlock(BasicBlock basicBlock, int stronglyConnectedGroupIndex) {
            this.basicBlock = basicBlock;
            this.stronglyConnectedGroupIndex = stronglyConnectedGroupIndex;
        }

        @Override
        public int compareTo(AnalyzedBlock o) {
            return Integer.compare(this.topologicalDepth, o.topologicalDepth);
        }

        public void markAsVisited() {
            this.visited = true;
        }

        public boolean wasVisited() {
            return this.visited;
        }

        @Override
        public String toString() {
            return "AnalyzedBlock{" +
                    "basicBlock=" + basicBlock +
                    ", stronglyConnectedGroupIndex=" + stronglyConnectedGroupIndex +
                    ", topologicalDepth=" + topologicalDepth +
                    ", visited=" + visited +
                    '}';
        }
    }
}
