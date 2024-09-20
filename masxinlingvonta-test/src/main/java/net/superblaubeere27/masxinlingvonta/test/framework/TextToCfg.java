package net.superblaubeere27.masxinlingvonta.test.framework;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;
import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirLexer;
import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

public class TextToCfg {

    public static List<ControlFlowGraph> loadCFGs(CompilerIndex index, String input) {
        mlvirLexer lexer = new mlvirLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        mlvirParser parser = new mlvirParser(tokens);

        var lines = input.lines().collect(Collectors.toList());

        parser.addErrorListener(new ANTLRErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                System.out.println("file:" + line + ":" + charPositionInLine + ": error: " + msg);
                System.out.println(lines.get(line - 1));

                System.out.println(" ".repeat(charPositionInLine) + "^");
            }

            @Override
            public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {

            }

            @Override
            public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {

            }

            @Override
            public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {

            }
        });

        TextParser textParser = new TextParser(parser, index);

        List<ControlFlowGraph> cfgs;

        try {
            cfgs = textParser.visitFile(parser.file());
        } catch (RecognitionException e) {
            parser.notifyErrorListeners(e.getOffendingToken(), e.getMessage(), e);

            throw e;
        }

        return cfgs;
    }

}
