grammar TrainCommand;

start : command+;

command : 'sensor' ID 'on' ('train' ID ('enter' | 'exit')) commandBlock;

commandBlock : '{' commandItem* '}';

commandItem : ('semaphore' ID ('open' | 'close') | 'fork' ID 'position' INT | 'train' 'max' 'speed' INT) ';';

ID : [a-zA-Z_][a-zA-Z0-9_]*;
INT : [0-9]+;
WS : [ \t\r\n]+ -> skip;
