parser grammar PlayerCommandsParser;
options { tokenVocab=LeTrainLexer; }
import ScriptLogicParser;

playerStart : playerStatement+ EOF;

playerStatement : statement
                | goCommand SEMI
                | newCommand SEMI
                | delCommand SEMI
                | turtleCommand SEMI
                | saveCommand SEMI
                | loadCommand SEMI
                | markCommand SEMI
                | faceCommand SEMI
                ;

goCommand : GO MARK (STRING | NUMBER)
          | GO NUMBER COMMA NUMBER
          | GO STRING
          | GO NEXT entityType
          | GO PREV entityType
          | GO END
          | GO entityType (NUMBER | STRING)
          ;

entityType : STATION | SENSOR | FORK | SEMAPHORE | SIGNAL | TRAIN | RAIL ;

faceCommand : FACE (DIR_N | DIR_S | DIR_E | DIR_W | DIR_NE | DIR_NW | DIR_SE | DIR_SW)
            | FACE entityType (NUMBER | STRING)
            ;

newCommand : NEW (STATION | SENSOR | FORK | SEMAPHORE | SIGNAL) ;

delCommand : DEL entityType (NUMBER | STRING)? ;

turtleCommand : (WRITE | MOVE | DEL | CLEAR) turtleSequence? ;

turtleSequence : turtleStep (COMMA turtleStep)* ;

turtleStep : NUMBER | L | R | STRING ;

saveCommand : SAVE STRING? ;
loadCommand : LOAD STRING? ;

markCommand : MARK STRING | MARK NUMBER ;
