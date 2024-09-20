package net.superblaubeere27.masxinlingvonta.test.cli;

import net.superblaubeere27.masxinlingvaj.compiler.OptimizerSettings;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.cfg.CfgPruning;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.Callable;

import static net.superblaubeere27.masxinlingvonta.test.framework.TextToCfg.loadCFGs;

@CommandLine.Command(name = "run-cfg-opt", mixinStandardHelpOptions = true)
public class RunCfgTest implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "name of the cfg file")
    private String name;

    @Override
    public Integer call() throws Exception {
        var path = new File("D:\\Projects\\IntelliJ\\masxinlingvaj\\masxinlingvonta-test\\src\\test\\resources", name);

        String input;

        try (var stream = new FileInputStream(path)) {
            input = new String(stream.readAllBytes());
        }

        var inputFile = new File("testScrap/test.jar");
        var mlv = RunCommand.loadMLV(inputFile);

        mlv.preprocess(new OptimizerSettings(true));

        var index = mlv.getCompiler().getIndex();


        var cfgs = loadCFGs(index, input);

        var mainCfg = cfgs.get(0);

        var cfgPruningPass = new CfgPruning(mlv.getCompiler());

        cfgPruningPass.apply(mainCfg);

        var string = mainCfg.toString();

        if (!string.contains("17")) {
            System.err.println(string);
        } else {
            System.out.println(string);
        }

        return 0;
    }

}
