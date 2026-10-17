parser grammar ScriptLogicParser;
options { tokenVocab=LeTrainLexer; }

scriptStart : statement+ EOF;

// Block-terminated statements take an OPTIONAL trailing ';' after the closing '}': the console
// funnels every typed line through PlayerCommandExecutor, which appends one when the text does not
// end with ';', and users may type it explicitly. Without SEMI? the console could never create an
// itinerary ("extraneous input ';'").
statement : trigger commandBlock SEMI?         // event-driven automation
          | temporalTrigger commandBlock SEMI? // time-driven automation (ADR-022 phase 3, D1-D7)
          | createItinerary SEMI?              // } terminates the block; the ; after it is optional
          | directCommand SEMI                 // other immediate commands need ;
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

/**
 * A waypoint with a plan separates the reference (and its optional direction) from the first plan
 * item with a comma (U7): `add station 1 ne, arrival 9:00, load, departure 10:30;`. A waypoint
 * without a plan keeps the bare form: `add station 1;`. There is no legacy comma-less form.
 */
waypoint : ADD STATION stationRef direction? (COMMA waypointPlan)? SEMI?
         | ADD SENSOR  sensorRef  direction? (COMMA waypointPlan)? SEMI?
         ;

/**
 * ADR-022 timetable attributes. Commas are mandatory between the plan items (the reference block
 * is the first item). The order is mandatory: `arrival` first, then the actions in execution
 * order, `departure` last.
 */
waypointPlan : departureAttr
             | (arrivalAttr | action) (COMMA action)* (COMMA departureAttr)?
             ;

/** U8: `arrival 9` is 09:00; `arrival 9:20` (TIME) keeps the full form. */
arrivalAttr   : ARRIVAL waypointTime ;
departureAttr : DEPARTURE waypointTime ;

waypointTime : TIME | NUMBER ;

stationRef : STRING | NUMBER ;
sensorRef  : STRING | NUMBER ;

direction : dir ;

/**
 * ADR-022 phase 2f: waypoint actions are the same train orders as scripts, executed in order on
 * arrival. Movement orders (`stop at …`, `stop when blocked …`) are missions that must complete
 * before the next action; fork actions force or prepare switches; `couple`/`uncouple` leave or
 * pick up vehicles. Commas are mandatory between actions.
 */
action : LOAD | UNLOAD | INVERT | STOP | PARK
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
    | trainSelector     ON trainEvent
    | trainSelector     ON (CRASH | CONTACT) (sense)?
    ;

/**
 * ADR-022 phase 3 (contract D1-D7, frozen): time-driven automation. `at` fires every day at that
 * time of the game clock (D1/D2); `every` fires periodically on the fixed grid anchored at 00:00
 * of the game clock with an optional `from` (D7). The block is the event-trigger action block
 * (D6: same actions, `;`-terminated, no nesting).
 *
 * <p>
 * A period glues number and unit into one alphanumeric token (`30m` is a single ID), so both the
 * glued (`ID`) and the spaced (`NUMBER unit`) forms are accepted and the command manager validates
 * amount and unit at parse time: an unknown unit (`30x`) is a visible warning instead of a syntax
 * error.
 */
temporalTrigger : atTrigger | everyTrigger ;
atTrigger       : AT triggerTime ;
everyTrigger    : EVERY period (FROM triggerTime)? ;
period          : ID | NUMBER unit ;
unit            : M | ID ;
triggerTime     : TIME | NUMBER ;

/**
 * U4: sensor and station references accept a number or an exact quoted name (strict case). Forks,
 * semaphores and signals have no name and stay numeric. The name is resolved when the trigger is
 * registered / the `train at` order runs; an unknown name warns and the trigger is not installed.
 */
sensorSelector    : SENSOR  (NUMBER | STRING);
forkSelector      : FORK NUMBER;
semaphoreSelector : SEMAPHORE NUMBER;
stationSelector   : STATION (NUMBER | STRING);
trainSelector     : TRAIN (NUMBER)?;

trainEvent   : (ENTER | EXIT) (sense)?;

commandBlock : LBRACE commandItem* RBRACE;

commandItem : (
      semaphoreSelector  semaphoreAction
    | forkSelector       forkAction
    | (trainSelector|trainExtractor) trainAction
    )
    SEMI
    ;

/**
 * `train at <place>` is two tokens (`TRAIN AT`), not a single literal with an exact space: the old
 * `'train at'` token made `train  at` (two spaces/tab) a syntax error (D3).
 */
trainExtractor : TRAIN AT placeSelector;
placeSelector  : forkSelector | semaphoreSelector | stationSelector | sensorSelector;

semaphoreAction : OPEN | CLOSED | CLOSE | SET semaphoreStatus | INVERT | TOGGLE ;
forkAction      : SET forkDirection | FLIP ;
engineAction    : SET ENGINE (ON | OFF);
trainAction     : SET trainSense | ACCELERATE | DECELERATE | SET SPEED trainSpeed | INVERT | PARK | STOP | coupleAction | uncoupleAction | SET NAME STRING | LOAD | UNLOAD | engineAction | stopOrder;
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
