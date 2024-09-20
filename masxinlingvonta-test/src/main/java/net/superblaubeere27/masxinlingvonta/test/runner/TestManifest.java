package net.superblaubeere27.masxinlingvonta.test.runner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.superblaubeere27.masxinlingvonta.test.runner.cfgTests.AbstractCfgTest;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestManifest {
    static {
        AbstractCfgTest.initializeDictionary();
    }

    public final HashMap<String, List<AbstractCfgTest>> cfgTests;

    private TestManifest(HashMap<String, List<AbstractCfgTest>> cfgTests) {
        this.cfgTests = cfgTests;
    }

    public static TestManifest loadFrom(Reader is) {
        var parser = new JsonParser().parse(is);

        var rootObject = parser.getAsJsonObject();

        var cfgTests = loadCfgTests(rootObject.getAsJsonObject("cfgTests"));

        return new TestManifest(cfgTests);
    }

    private static HashMap<String, List<AbstractCfgTest>> loadCfgTests(JsonObject object) {
        var cfgTests = new HashMap<String, List<AbstractCfgTest>>();

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            cfgTests.put(entry.getKey(), loadCfgTestList(entry.getValue().getAsJsonArray()));
        }

        return cfgTests;
    }

    private static List<AbstractCfgTest> loadCfgTestList(JsonArray testArray) {
        var outputList = new ArrayList<AbstractCfgTest>();

        testArray.forEach(element -> {
            var testObject = element.getAsJsonObject();

            var testName = testObject.get("name").getAsString();

            outputList.add(AbstractCfgTest.CFG_TEST_CONSTRUCTORS.get(testName).apply(testObject));
        });

        return outputList;
    }

}
