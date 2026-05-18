grammar LeTrainProgram;

start : statement+;

statement : trigger commandBlock          // event-driven automation
          | createItinerary               // } is the terminator, no ; needed
          | directCommand ';'             // other immediate commands need ;
          ;

// ── Direct commands (execute once when Apply is pressed) ──

directCommand : assignItinerary
              | setAutopilot
              | setNameCommand
              ;

createItinerary : 'create' 'itinerary' STRING '{' waypoint* '}' ;

assignItinerary : 'assign' 'itinerary' STRING 'to' 'train' trainRef ;

setAutopilot : 'train' trainRef 'set' 'autopilot' bool ;

setNameCommand : 'station' NUMBER 'set' 'name' STRING
               | 'sensor'  NUMBER 'set' 'name' STRING
               | 'train'   NUMBER 'set' 'name' STRING
               ;

bool : 'true' | 'false' ;

trainRef : NUMBER | STRING ;

waypoint : 'add' 'station' stationRef direction? action*
         | 'add' 'sensor'  sensorRef  direction? action*
         ;

stationRef : STRING | NUMBER ;
sensorRef  : STRING | NUMBER ;

direction : dir ;

action : 'PASO' | 'PARADA' | 'CARGA' | 'DESCARGA' | 'REVERSE'
       | 'LOAD' | 'UNLOAD'
       | 'WAIT' NUMBER
       | 'SPEED' NUMBER
       ;

// ── Trigger-based automation (existing) ──

trigger :
      sensorSelector    'on' trainSelector trainEvent
    | forkSelector      'on' trainSelector trainEvent
    | semaphoreSelector 'on' trainSelector trainEvent
    | stationSelector   'on' (trainSelector trainEvent | trainEvent trainSelector)
    | trainSelector     'on' ('crash' | 'contact') (sense)?
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
STRING : '"' ~["]* '"' ;
WS : [ \t\r\n]+ -> skip;
