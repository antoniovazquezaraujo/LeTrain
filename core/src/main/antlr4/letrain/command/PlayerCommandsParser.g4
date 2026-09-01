parser grammar PlayerCommandsParser;
options { tokenVocab=LeTrainLexer; }
import ScriptLogicParser;

playerStart : playerStatement+ EOF;

playerStatement : statement
                | goCommand SEMI
                | newCommand SEMI
                | delCommand SEMI
                | modeCommand SEMI
                | saveCommand SEMI
                | loadCommand SEMI
                ;

goCommand : GO NUMBER COMMA NUMBER ;

newCommand : NEW (STATION | SENSOR | FORK | SEMAPHORE) ;

delCommand : DEL (STATION | SENSOR | FORK | SEMAPHORE | TRAIN) ;

modeCommand : MODE (WRITE | MOVE | DEL) ;

saveCommand : SAVE STRING? ;
loadCommand : LOAD STRING? ;
