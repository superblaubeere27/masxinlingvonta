package net.superblaubeere27.masxinlingvonta.test.runner.cfgTests;

import com.google.gson.JsonElement;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;

public class ExpectEmptyCfgTest extends AbstractCfgTest {
    static ExpectEmptyCfgTest deserialize(JsonElement jsonElement) {
        return new ExpectEmptyCfgTest();
    }


    @Override
    public void run(ControlFlowGraph cfg) {
        var vertices = cfg.vertices();

        if (vertices.size() != 1) {
            throw new IllegalStateException("Expected 1 vertex, got " + vertices.size());
        }
        if (vertices.iterator().next().size() != 1) {
            throw new IllegalStateException("Expected block to be empty");
        }
    }
}
