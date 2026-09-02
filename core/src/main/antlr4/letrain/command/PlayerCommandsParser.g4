parser grammar PlayerCommandsParser;
options { tokenVocab=LeTrainLexer; }
import ScriptLogicParser;

playerStart : playerStatement+ EOF;

playerStatement : statement
                | goCommand SEMI
                | newCommand SEMI
                | delCommand SEMI
                | clearCommand SEMI
                | turtleCommand SEMI
                | saveCommand SEMI
                | loadCommand SEMI
                | markCommand SEMI
                | faceCommand SEMI
                | lsCommand SEMI
                | infoCommand SEMI
                | setNameCommand SEMI
                | quitCommand SEMI
                ;

goCmdToken : GO | G ;

goCommand : goCmdToken MARK (STRING | NUMBER)
          | goCmdToken NUMBER COMMA NUMBER
          | goCmdToken STRING
          | goCmdToken NEXT entityType
          | goCmdToken PREV entityType
          | goCmdToken END
          | goCmdToken entityType (NUMBER | STRING)
          | GN entityType
          | GP entityType
          ;

entityType : STATION | SENSOR | FORK | SEMAPHORE | SIGNAL | TRAIN | RAIL ;

faceCommand : FACE (DIR_N | DIR_S | DIR_E | DIR_W | DIR_NE | DIR_NW | DIR_SE | DIR_SW)
            | FACE entityType (NUMBER | STRING)
            ;

newCommand : NEW (STATION | SENSOR | FORK | SEMAPHORE | SIGNAL) ;

delCommand : DEL entityType (NUMBER | STRING)? ;

clearCommand : CLEAR entityType (NUMBER | STRING)? ;

turtleCommand : (WRITE | MOVE | DEL | CLEAR) turtleSequence? ;

turtleSequence : turtleStep (COMMA turtleStep)* ;

turtleStep : NUMBER | L | R | STRING ;

saveCommand : SAVE STRING? ;
loadCommand : LOAD STRING? ;

markCommand : MARK STRING | MARK NUMBER ;

lsCommand : LS entityType ;
infoCommand : INFO entityType (NUMBER | STRING)? ;
setNameCommand : SET NAME entityType (NUMBER | STRING) STRING ;
quitCommand : QUIT | Q | Q_BANG | WQ ;
