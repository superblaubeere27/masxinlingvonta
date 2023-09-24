grammar mlvir;

codeUnit: (file | expr | codeSnippet | blockSnippet) EOF;

file: method*;
blockSnippet: block*;
codeSnippet: statement*;

method: STATIC? className '.' methodName methodType ':' block*;

methodName: IDENTIFIER | SPECIAL_METHOD_NAME;

methodType: METHOD_TYPE;

type: ARRAY_TYPE_DECL | primitiveType | OBJECT_TYPE_DECL;
primitiveType: IDENTIFIER | CLASS_NAME;

className: CLASS_NAME | IDENTIFIER;

block: IDENTIFIER ':' statement* terminator;

terminator: br | condBr | exceptionBr | switch | ret;

br: 'br' IDENTIFIER;
exceptionBr: 'br' IDENTIFIER ',' 'on_exception' IDENTIFIER;
condBr: 'br' expr 'if' IDENTIFIER ',' 'else' IDENTIFIER;
switch: 'switch' expr switchCase (',' switchCase)* ',' 'default' ':' IDENTIFIER;
ret: 'ret' (VOID | expr);

switchCase: '[' INTEGER ':' IDENTIFIER ']';

statement:  (clearExceptionStmt | monitorStmt | arrayStoreStmt | exprStmt | putStaticStmt
            | putFieldStmt | varStmt | phiStmt | throwStmt | deleteStmt) ';';

clearExceptionStmt: 'clear_exception';
monitorStmt: monitorOpcode expr;
throwStmt: 'throw' expr;
deleteStmt: 'delete' expr;
exprStmt: expr;
putStaticStmt: className '::' IDENTIFIER '@' type '=' expr;
putFieldStmt: '(' '(' className ')' expr ')' '.' IDENTIFIER '@' type '=' expr;
arrayStoreStmt: expr '[' expr ']' '=' expr '(' arrayTypeName ')';
phiStmt: varExpr '=' 'phi' phiEdge (',' phiEdge)*;
varStmt: varExpr '=' expr;

monitorOpcode: 'monitorenter' | 'monitorexit';

arrayTypeName: primitiveTypeName | 'Object';
primitiveTypeName: 'boolean' | 'byte' | 'int' | 'short' | 'char' | 'long' | 'float' | 'double' | 'Object';
phiEdge: '[' IDENTIFIER ',' expr ']';

expr:   cmpExpr | arrayLoadExpr | lowerLevelExpr
//
//
//
        ;

lowerLevelExpr:   mathExpr | paramExpr | invokeInstanceExpr | instanceOfExpr | allocObjectExpr
                    | arrayLenExpr | constStrExpr | constNullExpr | constIntExpr | constRealExpr | varExpr
                    | catchExpr | constTypeExpr | invokeStaticExpr | checkcastExpr | primitiveCastExpr
                    | negationExpr | createLocalRefExpr | allocArrayExpr | getFieldExpr | getStaticExpr
                    ;

paramExpr: 'params' '[' INTEGER ']';
constTypeExpr: type;
constStrExpr: STRING;
constNullExpr: NULL;
constIntExpr: INTEGER longMarker?;
constRealExpr: REAL doubleMarker?;

longMarker: 'L';
doubleMarker: 'D';

varExpr: VAR;
allocArrayExpr: 'alloc_array' '<' type '>' '(' expr ')';
primitiveCastExpr: 'cast' '<' primitiveTypeName '>' '(' expr ')';
arrayLenExpr: 'arraylen' '(' expr ')';
createLocalRefExpr: 'copy_ref' '(' expr ')';
checkcastExpr: 'checkcast' '<' className '>' '(' expr ')';
instanceOfExpr: 'instanceof' '<' className '>' '(' expr ')';
invokeInstanceExpr: '(' '(' className ')' expr ')' '.' methodName methodType '<' callType '>' '(' exprList ')';
invokeStaticExpr: className '::' methodName methodType '(' exprList ')';
allocObjectExpr: 'alloc' '<' className '>' '(' ')';
mathExpr: (intMathOpcode | longMathOpcode | floatMathOpcode | doubleMathOpcode) expr ',' expr;
cmpExpr: lowerLevelExpr (icmpOpcode | acmpOpcode) lowerLevelExpr | fcmpExpr;
fcmpExpr: fcmpType '(' fcmpOpcode ',' expr ',' expr ')';
fcmpType: 'fcmp' | 'dcmp';
getFieldExpr: '(' '(' className ')' expr ')' '.' IDENTIFIER '@' type;
getStaticExpr: className '::' IDENTIFIER '@' type;
arrayLoadExpr: lowerLevelExpr '[' lowerLevelExpr ']' '(' arrayTypeName ')';
negationExpr: '-' '(' expr ')';
catchExpr: 'catch' '(' ')';

fcmpOpcode: icmpOpcode | 'isnan';
icmpOpcode: '==' | '!=' | '<' | '>' | '<=' | '>=';
acmpOpcode: '===';
intMathOpcode: 'add' | 'sub' | 'mul' | 'div' | 'rem' | 'shl' | 'shr' | 'ushr' | 'or' | 'and' | 'xor';
longMathOpcode: 'ladd' | 'lsub' | 'lmul' | 'ldiv' | 'lrem' | 'lshl' | 'lshr' | 'lushr' | 'lor' | 'land' | 'lxor';
floatMathOpcode: 'fadd' | 'fsub' | 'frem' | 'fmul' | 'fdiv';
doubleMathOpcode: 'dadd' | 'dsub' | 'drem' | 'dmul' | 'ddiv';

exprList: (expr (',' expr)*)?;

callType: 'INVOKE_SPECIAL' | 'INVOKE_VIRTUAL' | 'INVOKE_INTERFACE';
//array_load_expr:

fragment PRIMITIVE_TYPE: [ZBCSIJDF];
fragment METHOD_RETURN_TYPE: TYPE | 'V';
fragment TYPE: PRIMITIVE_TYPE | OBJECT_TYPE_DECL | ARRAY_TYPE_DECL;
STATIC: 'STATIC';
METHOD_TYPE: '(' TYPE* ')' METHOD_RETURN_TYPE;
STRING: '"' ~["]* '"';
NULL: 'null';
OBJECT_TYPE_DECL: 'L' (CLASS_NAME | IDENTIFIER) ';';
ARRAY_TYPE_DECL: '[' TYPE;
VOID: 'void';
SPECIAL_METHOD_NAME: '<init>' | '<clinit>';

INTEGER: [+-]?[0-9]+;
REAL: [+-]?[0-9]+ '.' [0-9]*;
VAR: '%' [A-Za-z_0-9]+;
IDENTIFIER: [A-Za-z$_][A-Za-z$_0-9]*;
CLASS_NAME: IDENTIFIER ('/' IDENTIFIER)*;
SLASH: '/';

WHITESPACE: [ \n\r\t]+ -> skip;