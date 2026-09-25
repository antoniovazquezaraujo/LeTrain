parser grammar ScriptLogicParser;
options { tokenVocab=LeTrainLexer; }

scriptStart : statement+ EOF;

// Block-terminated statements take an OPTIONAL trailing ';' after the closing '}': the console
// funnels every typed line through PlayerCommandExecutor, which appends one when the text does not
// end with ';', and users may type it explicitly. Without SEMI? the console could never create an
// itinerary ("extraneous input ';'").
statement : trigger commandBlock SEMI?    // event-driven automation
          | createItinerary SEMI?         // } terminates the block; the ; after it is optional
          | directCommand SEMI            // other immediate commands need ;
          ;

directCommand : assignItinerary
              | setAutopilot
              | setNameCommand
              | directTrainCommand
              | directForkCommand
              | directSemaphoreCommand
              | directSignalCommand
              | directStationCommand
              | directSensorCommand
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

waypoint : ADD STATION stationRef direction? waypointPlan? SEMI?
         | ADD SENSOR  sensorRef  direction? waypointPlan? SEMI?
         ;

/**
 * ADR-022 timetable attributes. Commas are mandatory between the plan items, but the waypoint
 * reference and its direction take no comma. The order is mandatory: `arrival` first, then the
 * actions in execution order, `departure` last. A single `departure` needs no comma either.
 */
waypointPlan : departureAttr
             | (arrivalAttr | action) (COMMA action)* (COMMA departureAttr)?
             ;

arrivalAttr   : ARRIVAL TIME ;
departureAttr : DEPARTURE TIME ;

stationRef : STRING | NUMBER ;
sensorRef  : STRING | NUMBER ;

direction : dir ;

/**
 * ADR-022 phase 2f: waypoint actions are the same train orders as scripts, executed in order on
 * arrival. Movement orders (`stop at …`, `stop when blocked …`) are missions that must complete
 * before the next action; fork actions force or prepare switches; `couple`/`uncouple` leave or
 * pick up vehicles. Commas are mandatory between actions.
 */
action : LOAD | UNLOAD | REVERSE | STOP | PARK
       | WAIT NUMBER
       | SPEED NUMBER
       | coupleAction
       | uncoupleAction
       | stopOrder
       | forkSelector forkAction
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

trainEvent   : (ENTER | EXIT | COUPLE | UNCOUPLE) (sense)?;

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

semaphoreAction : OPEN | CLOSED | CLOSE | SET semaphoreStatus | INVERT ;
forkAction      : SET forkDirection | FLIP ;
engineAction    : SET ENGINE (ON | OFF);
trainAction     : SET trainSense | ACCELERATE | DECELERATE | SET SPEED? trainSpeed | INVERT | coupleAction | uncoupleAction | SET NAME STRING | LOAD | UNLOAD | engineAction | stopOrder;
/**
 * Issue #619: one-shot "advance until X and stop" order. The destination is a station or sensor
 * (by number or quoted name), the end of the track, the first block that stops the train, or the
 * vehicle ahead (issue #645: {@code stop on contact}, the coupling approach). The optional speed
 * belongs to the order: it is set when the mission starts and the train ends stopped; without it
 * the train's current target speed is used.
 */
stopOrder       : STOP stopTarget missionSpeed?;
stopTarget      : AT (STATION stationRef | SENSOR sensorRef | END) | WHEN BLOCKED | ON CONTACT;
missionSpeed    : SPEED trainSpeed;
coupleAction    : COUPLE sense vehicleCount?;
uncoupleAction  : UNCOUPLE sense vehicleCount?;
vehicleCount    : NUMBER | ALL;


semaphoreStatus : OPEN | CLOSED;
forkDirection   : dir | STRAIGHT | CURVED | FLIP;
trainSense      : FORWARD | BACKWARD;
trainSpeed      : NUMBER;

sense : FORWARD | BACKWARD;
dir   : DIR_E| DIR_NE | DIR_N | DIR_NW | DIR_W | DIR_SW | DIR_S | DIR_SE;


directForkCommand : forkSelector forkAction ;
directSemaphoreCommand : semaphoreSelector semaphoreAction ;

directSignalCommand : signalSelector signalAction ;
signalAction        : SET LIMIT NUMBER
                    | SET MODE (MAX | MIN)
                    | INVERT
                    ;
signalSelector      : SIGNAL NUMBER ;

directStationCommand : STATION NUMBER INVERT ;
directSensorCommand  : SENSOR NUMBER INVERT ;
