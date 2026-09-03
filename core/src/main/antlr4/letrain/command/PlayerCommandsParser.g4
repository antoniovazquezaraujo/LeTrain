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

goCommand : goCmdToken (MARK | M) (STRING | NUMBER)
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

newCommand : NEW (STATION | SENSOR | FORK | SEMAPHORE | SIGNAL)
           | NEW (LOCOMOTIVE | LOCO) aspectId color?
           | NEW WAGON aspectId cargoType?
           ;

delCommand : DEL entityType (NUMBER | STRING)? ;

clearCommand : CLEAR entityType (NUMBER | STRING)? ;

turtleCommand : (WRITE | MOVE | DEL | CLEAR) turtleSequence? ;

turtleSequence : turtleStep (COMMA turtleStep)* ;

turtleStep : NUMBER | L | R | STRING | M STRING | MARK STRING ;

saveCommand : SAVE STRING? ;
loadCommand : LOAD STRING? ;

markCommand : (MARK | M) (STRING | NUMBER) ;

lsCommand : LS entityType ;
infoCommand : INFO entityType (NUMBER | STRING)? ;
setNameCommand : entityType (NUMBER | STRING) SET NAME STRING ;
quitCommand : QUIT | Q | Q_BANG | WQ ;


color : RED | GREEN | BLUE | YELLOW | BLACK | WHITE | ORANGE | PURPLE | GRAY | BROWN ;
cargoType : COAL | GOLD | RUBY ;


aspectId : ID | STRING | L | R | GO | G | Q | INFO | LS | DIR_N | DIR_S | DIR_E | DIR_W | DIR_NE | DIR_NW | DIR_SE | DIR_SW ;
