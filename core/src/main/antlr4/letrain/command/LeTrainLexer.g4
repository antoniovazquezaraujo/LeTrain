lexer grammar LeTrainLexer;

// Keywords
TRAIN: 'train';
CREATE: 'create';
ITINERARY: 'itinerary';
ASSIGN: 'assign';
TO: 'to';
SET: 'set';
AUTOPILOT: 'autopilot';
STATION: 'station';
SENSOR: 'sensor';
NAME: 'name';
TRUE: 'true';
FALSE: 'false';
ADD: 'add';
LOAD: 'load';
UNLOAD: 'unload';
REVERSE: 'reverse';
STOP: 'stop';
WAIT: 'wait';
SPEED: 'speed';
ON: 'on';
CRASH: 'crash';
CONTACT: 'contact';
ENTER: 'enter';
EXIT: 'exit';
LINK: 'link';
UNLINK: 'unlink';
FORK: 'fork';
SEMAPHORE: 'semaphore';
TRAIN_AT: 'train at';
OPEN: 'open';
CLOSED: 'closed';
STRAIGHT: 'straight';
CURVED: 'curved';
FLIP: 'flip';
FORWARD: 'forward';
BACKWARD: 'backward';
ACCELERATE: 'accelerate';
DECELERATE: 'decelerate';
INVERT: 'invert';

// New Keywords for PlayerCommands
NEW: 'new';
DEL: 'del';
GO: 'go';
MODE: 'mode';
WRITE: 'write';
MOVE: 'move';
CLEAR: 'clear';
FACE: 'face';
STEP: 'step';
SAVE: 'save';
QUIT: 'quit';
WQ: 'wq';
MARK: 'mark';
KEEP_MAP: 'keep-map';
MAP: 'map';

// Directions
DIR_E: 'e';
DIR_NE: 'ne';
DIR_N: 'n';
DIR_NW: 'nw';
DIR_W: 'w';
DIR_SW: 'sw';
DIR_S: 's';
DIR_SE: 'se';

// Symbols
LBRACE: '{';
RBRACE: '}';
SEMI: ';';
COMMA: ',';
EQUALS: '=';

// Data types
NUMBER : [0-9]+;
STRING : '"' ~["]* '"' ;

// Whitespace
WS : [ \t\r\n]+ -> skip;
