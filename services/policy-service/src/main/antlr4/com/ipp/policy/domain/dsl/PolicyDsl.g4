// IPP Policy DSL grammar.
// A policy ("חוקה") is a small declarative rule: which permit type it applies to,
// which form fields are mandatory, the eligibility condition, and the outcome.
//
// Example:
//   RULE "work-permit-construction" VERSION 3 EFFECTIVE 2026-12-01
//   APPLIES TO permit.type == WORK_ENTRY
//   REQUIRE FIELDS idNumber, photo, magneticCard, employerId, workRegion
//   WHEN person.age >= 22 AND person.securityStatus == CLEAR
//    AND quota("CONSTRUCTION").remaining > 0 AND NOT sanctions.activeFor(person)
//   THEN APPROVE VALIDITY 90d
//   ELSE REFER_TO_CLERK REASON "תנאי זכאות לא מתקיימים"
//
// Security note: the grammar only allows comparisons, boolean logic, fact paths and a
// fixed whitelist of functions. There is no way to express arbitrary code, so evaluating
// a policy can never execute anything (no SpEL / script engines).
grammar PolicyDsl;

policy
    : RULE name=STRING VERSION version=NUMBER (EFFECTIVE effective=DATE)?
      appliesTo?
      requireFields?
      WHEN condition=expr
      THEN thenAction=action
      (ELSE elseAction=action)?
      EOF
    ;

appliesTo     : APPLIES TO expr ;
requireFields : REQUIRE FIELDS IDENT (COMMA IDENT)* ;

action
    : APPROVE (VALIDITY DURATION)?                 # approveAction
    | REJECT (REASON STRING)?                      # rejectAction
    | REFER_TO_CLERK (REASON STRING)?              # referAction
    ;

expr
    : NOT expr                                     # notExpr
    | expr AND expr                                # andExpr
    | expr OR expr                                 # orExpr
    | LPAREN expr RPAREN                           # parenExpr
    | operand IN LBRACK literal (COMMA literal)* RBRACK   # inExpr
    | operand compOp operand                       # compareExpr
    | operand                                      # truthyExpr
    ;

compOp : EQ | NEQ | GTE | LTE | GT | LT ;

operand
    : literal                                      # literalOperand
    | ref                                          # refOperand
    ;

ref     : segment (DOT segment)* ;
segment : IDENT (LPAREN (operand (COMMA operand)*)? RPAREN)? ;

literal
    : STRING                                       # stringLit
    | NUMBER                                       # numberLit
    | (TRUE | FALSE)                               # boolLit
    | DATE                                         # dateLit
    | DURATION                                     # durationLit
    | SYMBOL                                       # symbolLit
    ;

// ---- keywords (defined before SYMBOL so they win on equal length) ----
RULE      : 'RULE' ;
VERSION   : 'VERSION' ;
EFFECTIVE : 'EFFECTIVE' ;
APPLIES   : 'APPLIES' ;
TO        : 'TO' ;
REQUIRE   : 'REQUIRE' ;
FIELDS    : 'FIELDS' ;
WHEN      : 'WHEN' ;
THEN      : 'THEN' ;
ELSE      : 'ELSE' ;
APPROVE   : 'APPROVE' ;
REJECT    : 'REJECT' ;
REFER_TO_CLERK : 'REFER_TO_CLERK' ;
VALIDITY  : 'VALIDITY' ;
REASON    : 'REASON' ;
AND       : 'AND' ;
OR        : 'OR' ;
NOT       : 'NOT' ;
IN        : 'IN' ;
TRUE      : 'true' ;
FALSE     : 'false' ;

EQ  : '==' ;  NEQ : '!=' ;  GTE : '>=' ;  LTE : '<=' ;  GT : '>' ;  LT : '<' ;
LPAREN : '(' ; RPAREN : ')' ; LBRACK : '[' ; RBRACK : ']' ; COMMA : ',' ; DOT : '.' ;

DATE     : [0-9][0-9][0-9][0-9] '-' [0-9][0-9] '-' [0-9][0-9] ;
DURATION : [0-9]+ [dhm] ;
NUMBER   : [0-9]+ ('.' [0-9]+)? ;
STRING   : '"' ( ~["\\\r\n] | '\\' . )* '"' ;
SYMBOL   : [A-Z] [A-Z0-9_]* ;          // enum-like constants: CLEAR, CONSTRUCTION, WORK_ENTRY
IDENT    : [a-z] [a-zA-Z0-9_]* ;        // fact paths and field names: person.age, employerId

LINE_COMMENT : '#' ~[\r\n]* -> skip ;
WS           : [ \t\r\n]+ -> skip ;
