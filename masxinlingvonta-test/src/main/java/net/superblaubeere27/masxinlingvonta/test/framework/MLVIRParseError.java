package net.superblaubeere27.masxinlingvonta.test.framework;

import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;

public class MLVIRParseError extends RecognitionException {
    public MLVIRParseError(String message, ParserRuleContext ctx, mlvirParser parser) {
        super(message, parser, parser.getInputStream(), ctx);

        this.setOffendingToken(ctx.getStart());
    }
}
