package net.superblaubeere27.masxinlingvonta.test.runner.cfgTests;

import com.google.gson.JsonElement;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;

import java.util.HashMap;
import java.util.function.Function;

public abstract class AbstractCfgTest {
    public static final HashMap<String, Function<JsonElement, AbstractCfgTest>> CFG_TEST_CONSTRUCTORS = new HashMap<>();

    public static void initializeDictionary() {
        CFG_TEST_CONSTRUCTORS.put("expectEmpty", ExpectEmptyCfgTest::deserialize);
    }

    public abstract void run(ControlFlowGraph cfg);
}
