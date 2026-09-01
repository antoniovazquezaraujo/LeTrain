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
                ;

goCommand : GO NUMBER COMMA NUMBER ;

newCommand : NEW (STATION | SENSOR | FORK | SEMAPHORE | SIGNAL) ;

delCommand : DEL (STATION | SENSOR | FORK | SEMAPHORE | TRAIN | SIGNAL) ;

turtleCommand : (WRITE | MOVE | DEL | CLEAR) turtleSequence? ;

turtleSequence : turtleStep (COMMA turtleStep)* ;

turtleStep : NUMBER | L | R | STRING ;

saveCommand : SAVE STRING? ;
loadCommand : LOAD STRING? ;
