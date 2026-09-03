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

goCommand : goCmdToken (MARK | M) (identifier | NUMBER)
          | goCmdToken NUMBER COMMA NUMBER
          | goCmdToken identifier
          | goCmdToken NEXT entityType
          | goCmdToken PREV entityType
          | goCmdToken END
          | goCmdToken entityType (NUMBER | identifier)
          | GN entityType
          | GP entityType
          ;

entityType : STATION | SENSOR | FORK | SEMAPHORE | SIGNAL | TRAIN | RAIL ;

faceCommand : FACE (DIR_N | DIR_S | DIR_E | DIR_W | DIR_NE | DIR_NW | DIR_SE | DIR_SW)
            | FACE entityType (NUMBER | identifier)
            ;

newCommand : NEW (STATION | SENSOR | FORK | SEMAPHORE | SIGNAL)
           | NEW (LOCOMOTIVE | LOCO) aspectId color?
           | NEW WAGON aspectId cargoType?
           ;

delCommand : DEL entityType (NUMBER | identifier)? ;

clearCommand : CLEAR entityType (NUMBER | identifier)? ;

turtleCommand : (WRITE | MOVE | DEL | CLEAR) turtleSequence? ;

turtleSequence : turtleStep (COMMA turtleStep)* ;

turtleStep : NUMBER | L | R | identifier | M identifier | MARK identifier ;

saveCommand : SAVE identifier? ;
loadCommand : LOAD identifier? ;

markCommand : (MARK | M) (identifier | NUMBER) ;

lsCommand : LS entityType ;
infoCommand : INFO entityType (NUMBER | identifier)? ;
setNameCommand : entityType (NUMBER | STRING) SET NAME STRING ;
quitCommand : QUIT | Q | Q_BANG | WQ ;


color : RED | GREEN | BLUE | YELLOW | BLACK | WHITE | ORANGE | PURPLE | GRAY | BROWN ;
cargoType : COAL | GOLD | RUBY ;


aspectId : ID | STRING | L | R | GO | G | Q | INFO | LS | DIR_N | DIR_S | DIR_E | DIR_W | DIR_NE | DIR_NW | DIR_SE | DIR_SW ;

identifier : STRING | ID ;
