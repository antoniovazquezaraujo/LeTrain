grammar LeTrainProgram;

start : statement+;

statement : trigger commandBlock;

trigger : 
      sensorSelector    'on' trainSelector trainEvent  
    | forkSelector      'on' trainSelector trainEvent 
    | semaphoreSelector 'on' trainSelector trainEvent 
    | stationSelector   'on' (trainSelector trainEvent | trainEvent trainSelector) 
    | trainSelector     ( 'on' ('crash' | 'contact') | trainEvent )
    ;

sensorSelector    : 'sensor' NUMBER;
forkSelector      : 'fork' NUMBER;
semaphoreSelector : 'semaphore' NUMBER;
stationSelector   : 'station' NUMBER;
trainSelector     : 'train' (NUMBER)?;

trainEvent   : ('enter' | 'exit' | 'link' | 'unlink') (sense)?;

commandBlock : '{' commandItem* '}';

commandItem : (
      semaphoreSelector  semaphoreAction
    | forkSelector       forkAction 
    | (trainSelector|trainExtractor) trainAction     
    )
    ';'
    ;

trainExtractor : 'train at' placeSelector;
placeSelector  : forkSelector | semaphoreSelector | stationSelector | sensorSelector;

semaphoreAction : 'set' semaphoreStatus;
forkAction      : 'set' forkDirection;
trainAction     : 'set' trainSense | 'accelerate' | 'decelerate' | 'set' 'speed'? trainSpeed | 'stop' | 'invert' | linkAction | unlinkAction | 'load' | 'unload';
linkAction      : 'link' sense (NUMBER)?;
unlinkAction    : 'unlink' sense (NUMBER)?;

semaphoreStatus : 'open' | 'closed';
forkDirection   : dir | 'straight' | 'curved' | 'flip';
trainSense      : 'forward' | 'backward';
trainSpeed      : NUMBER;

sense : 'forward' | 'backward';
dir   : 'E'| 'NE' | 'N' | 'NW' | 'W' | 'SW' | 'S' | 'SE'; 
NUMBER : [0-9]+;
WS : [ \t\r\n]+ -> skip;
