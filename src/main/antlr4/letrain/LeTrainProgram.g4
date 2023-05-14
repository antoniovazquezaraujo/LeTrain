grammar LeTrainProgram;

start : command+;
command : 
    'sensor' NUMBER 'on' 'train' (NUMBER)? (trainAction)? (dir)? 
    commandBlock
    ;
trainAction : 'enter' | 'exit' ;
commandBlock : '{' commandItem* '}';
commandItem : (
    'semaphore' NUMBER semaphoreAction
    | 'fork' NUMBER dir 
    | 'train' (speedLimit)? 'speed' NUMBER     
    )
    ';'
    ;
semaphoreAction: 'open' | 'close'; 
speedLimit: 'max' | 'min';   
dir: 'E'| 'NE' | 'N' | 'NW' | 'W' | 'SW' | 'S' | 'SE'; 
NUMBER : [0-9]+;
WS : [ \t\r\n]+ -> skip;
