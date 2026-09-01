parser grammar ScriptLogicParser;
options { tokenVocab=LeTrainLexer; }

scriptStart : statement+ EOF;

statement : trigger commandBlock          // event-driven automation
          | createItinerary               // } is the terminator, no ; needed
          | directCommand SEMI            // other immediate commands need ;
          ;

directCommand : assignItinerary
              | setAutopilot
              | setNameCommand
              | directTrainCommand
              ;

directTrainCommand : TRAIN trainRef trainAction ;

createItinerary : CREATE ITINERARY STRING LBRACE waypoint* RBRACE ;

assignItinerary : ASSIGN ITINERARY STRING TO TRAIN trainRef ;

setAutopilot : TRAIN trainRef SET AUTOPILOT bool ;

setNameCommand : STATION NUMBER SET NAME STRING
               | SENSOR  NUMBER SET NAME STRING
               | TRAIN   NUMBER SET NAME STRING
               ;

bool : TRUE | FALSE ;

trainRef : NUMBER | STRING ;

waypoint : ADD STATION stationRef direction? action*
         | ADD SENSOR  sensorRef  direction? action*
         ;

stationRef : STRING | NUMBER ;
sensorRef  : STRING | NUMBER ;

direction : dir ;

action : LOAD | UNLOAD | REVERSE | STOP
       | WAIT NUMBER
       | SPEED NUMBER
       ;

trigger :
      sensorSelector    ON trainSelector trainEvent
    | forkSelector      ON trainSelector trainEvent
    | semaphoreSelector ON trainSelector trainEvent
    | stationSelector   ON (trainSelector trainEvent | trainEvent trainSelector)
    | trainSelector     ON (CRASH | CONTACT) (sense)?
    ;

sensorSelector    : SENSOR NUMBER;
forkSelector      : FORK NUMBER;
semaphoreSelector : SEMAPHORE NUMBER;
stationSelector   : STATION NUMBER;
trainSelector     : TRAIN (NUMBER)?;

trainEvent   : (ENTER | EXIT | LINK | UNLINK) (sense)?;

commandBlock : LBRACE commandItem* RBRACE;

commandItem : (
      semaphoreSelector  semaphoreAction
    | forkSelector       forkAction
    | (trainSelector|trainExtractor) trainAction
    )
    SEMI
    ;

trainExtractor : TRAIN_AT placeSelector;
placeSelector  : forkSelector | semaphoreSelector | stationSelector | sensorSelector;

semaphoreAction : SET semaphoreStatus;
forkAction      : SET forkDirection;
trainAction     : SET trainSense | ACCELERATE | DECELERATE | SET SPEED? trainSpeed | STOP | INVERT | linkAction | unlinkAction | LOAD | UNLOAD;
linkAction      : LINK sense (NUMBER)?;
unlinkAction    : UNLINK sense (NUMBER)?;

semaphoreStatus : OPEN | CLOSED;
forkDirection   : dir | STRAIGHT | CURVED | FLIP;
trainSense      : FORWARD | BACKWARD;
trainSpeed      : NUMBER;

sense : FORWARD | BACKWARD;
dir   : DIR_E| DIR_NE | DIR_N | DIR_NW | DIR_W | DIR_SW | DIR_S | DIR_SE;

