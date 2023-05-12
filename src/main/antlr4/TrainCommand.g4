grammar TrainCommand;
@header {
    package letrain.parser;
}

start : command+;
command : 
    'sensor' NUM 'on'
        ('train' (NUM)? ('enter' | 'exit') (DIR)?) 
    commandBlock
    ;
commandBlock : '{' commandItem* '}';
commandItem : (
    'semaphore' NUM ('open' | 'close') 
    | 'fork' NUM DIR 
    | 'train' ('max'|'min')? 'speed' NUM     
    )
    ';'
    ;
NUM : [0-9]+;
DIR: 'E'| 'NE' | 'N' | 'NW' | 'W' | 'SW' | 'S' | 'SE'; 
WS : [ \t\r\n]+ -> skip;
