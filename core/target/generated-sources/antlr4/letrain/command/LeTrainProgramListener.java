// Generated from letrain/command/LeTrainProgram.g4 by ANTLR 4.13.2
package letrain.command;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link LeTrainProgramParser}.
 */
public interface LeTrainProgramListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#start}.
	 * @param ctx the parse tree
	 */
	void enterStart(LeTrainProgramParser.StartContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#start}.
	 * @param ctx the parse tree
	 */
	void exitStart(LeTrainProgramParser.StartContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(LeTrainProgramParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(LeTrainProgramParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#directCommand}.
	 * @param ctx the parse tree
	 */
	void enterDirectCommand(LeTrainProgramParser.DirectCommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#directCommand}.
	 * @param ctx the parse tree
	 */
	void exitDirectCommand(LeTrainProgramParser.DirectCommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#directTrainCommand}.
	 * @param ctx the parse tree
	 */
	void enterDirectTrainCommand(LeTrainProgramParser.DirectTrainCommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#directTrainCommand}.
	 * @param ctx the parse tree
	 */
	void exitDirectTrainCommand(LeTrainProgramParser.DirectTrainCommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#createItinerary}.
	 * @param ctx the parse tree
	 */
	void enterCreateItinerary(LeTrainProgramParser.CreateItineraryContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#createItinerary}.
	 * @param ctx the parse tree
	 */
	void exitCreateItinerary(LeTrainProgramParser.CreateItineraryContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#assignItinerary}.
	 * @param ctx the parse tree
	 */
	void enterAssignItinerary(LeTrainProgramParser.AssignItineraryContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#assignItinerary}.
	 * @param ctx the parse tree
	 */
	void exitAssignItinerary(LeTrainProgramParser.AssignItineraryContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#setAutopilot}.
	 * @param ctx the parse tree
	 */
	void enterSetAutopilot(LeTrainProgramParser.SetAutopilotContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#setAutopilot}.
	 * @param ctx the parse tree
	 */
	void exitSetAutopilot(LeTrainProgramParser.SetAutopilotContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#setNameCommand}.
	 * @param ctx the parse tree
	 */
	void enterSetNameCommand(LeTrainProgramParser.SetNameCommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#setNameCommand}.
	 * @param ctx the parse tree
	 */
	void exitSetNameCommand(LeTrainProgramParser.SetNameCommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#bool}.
	 * @param ctx the parse tree
	 */
	void enterBool(LeTrainProgramParser.BoolContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#bool}.
	 * @param ctx the parse tree
	 */
	void exitBool(LeTrainProgramParser.BoolContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainRef}.
	 * @param ctx the parse tree
	 */
	void enterTrainRef(LeTrainProgramParser.TrainRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainRef}.
	 * @param ctx the parse tree
	 */
	void exitTrainRef(LeTrainProgramParser.TrainRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#waypoint}.
	 * @param ctx the parse tree
	 */
	void enterWaypoint(LeTrainProgramParser.WaypointContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#waypoint}.
	 * @param ctx the parse tree
	 */
	void exitWaypoint(LeTrainProgramParser.WaypointContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#stationRef}.
	 * @param ctx the parse tree
	 */
	void enterStationRef(LeTrainProgramParser.StationRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#stationRef}.
	 * @param ctx the parse tree
	 */
	void exitStationRef(LeTrainProgramParser.StationRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#sensorRef}.
	 * @param ctx the parse tree
	 */
	void enterSensorRef(LeTrainProgramParser.SensorRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#sensorRef}.
	 * @param ctx the parse tree
	 */
	void exitSensorRef(LeTrainProgramParser.SensorRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#direction}.
	 * @param ctx the parse tree
	 */
	void enterDirection(LeTrainProgramParser.DirectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#direction}.
	 * @param ctx the parse tree
	 */
	void exitDirection(LeTrainProgramParser.DirectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#action}.
	 * @param ctx the parse tree
	 */
	void enterAction(LeTrainProgramParser.ActionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#action}.
	 * @param ctx the parse tree
	 */
	void exitAction(LeTrainProgramParser.ActionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trigger}.
	 * @param ctx the parse tree
	 */
	void enterTrigger(LeTrainProgramParser.TriggerContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trigger}.
	 * @param ctx the parse tree
	 */
	void exitTrigger(LeTrainProgramParser.TriggerContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#sensorSelector}.
	 * @param ctx the parse tree
	 */
	void enterSensorSelector(LeTrainProgramParser.SensorSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#sensorSelector}.
	 * @param ctx the parse tree
	 */
	void exitSensorSelector(LeTrainProgramParser.SensorSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#forkSelector}.
	 * @param ctx the parse tree
	 */
	void enterForkSelector(LeTrainProgramParser.ForkSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#forkSelector}.
	 * @param ctx the parse tree
	 */
	void exitForkSelector(LeTrainProgramParser.ForkSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#semaphoreSelector}.
	 * @param ctx the parse tree
	 */
	void enterSemaphoreSelector(LeTrainProgramParser.SemaphoreSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#semaphoreSelector}.
	 * @param ctx the parse tree
	 */
	void exitSemaphoreSelector(LeTrainProgramParser.SemaphoreSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#stationSelector}.
	 * @param ctx the parse tree
	 */
	void enterStationSelector(LeTrainProgramParser.StationSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#stationSelector}.
	 * @param ctx the parse tree
	 */
	void exitStationSelector(LeTrainProgramParser.StationSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainSelector}.
	 * @param ctx the parse tree
	 */
	void enterTrainSelector(LeTrainProgramParser.TrainSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainSelector}.
	 * @param ctx the parse tree
	 */
	void exitTrainSelector(LeTrainProgramParser.TrainSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainEvent}.
	 * @param ctx the parse tree
	 */
	void enterTrainEvent(LeTrainProgramParser.TrainEventContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainEvent}.
	 * @param ctx the parse tree
	 */
	void exitTrainEvent(LeTrainProgramParser.TrainEventContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#commandBlock}.
	 * @param ctx the parse tree
	 */
	void enterCommandBlock(LeTrainProgramParser.CommandBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#commandBlock}.
	 * @param ctx the parse tree
	 */
	void exitCommandBlock(LeTrainProgramParser.CommandBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#commandItem}.
	 * @param ctx the parse tree
	 */
	void enterCommandItem(LeTrainProgramParser.CommandItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#commandItem}.
	 * @param ctx the parse tree
	 */
	void exitCommandItem(LeTrainProgramParser.CommandItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainExtractor}.
	 * @param ctx the parse tree
	 */
	void enterTrainExtractor(LeTrainProgramParser.TrainExtractorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainExtractor}.
	 * @param ctx the parse tree
	 */
	void exitTrainExtractor(LeTrainProgramParser.TrainExtractorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#placeSelector}.
	 * @param ctx the parse tree
	 */
	void enterPlaceSelector(LeTrainProgramParser.PlaceSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#placeSelector}.
	 * @param ctx the parse tree
	 */
	void exitPlaceSelector(LeTrainProgramParser.PlaceSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#semaphoreAction}.
	 * @param ctx the parse tree
	 */
	void enterSemaphoreAction(LeTrainProgramParser.SemaphoreActionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#semaphoreAction}.
	 * @param ctx the parse tree
	 */
	void exitSemaphoreAction(LeTrainProgramParser.SemaphoreActionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#forkAction}.
	 * @param ctx the parse tree
	 */
	void enterForkAction(LeTrainProgramParser.ForkActionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#forkAction}.
	 * @param ctx the parse tree
	 */
	void exitForkAction(LeTrainProgramParser.ForkActionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainAction}.
	 * @param ctx the parse tree
	 */
	void enterTrainAction(LeTrainProgramParser.TrainActionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainAction}.
	 * @param ctx the parse tree
	 */
	void exitTrainAction(LeTrainProgramParser.TrainActionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#linkAction}.
	 * @param ctx the parse tree
	 */
	void enterLinkAction(LeTrainProgramParser.LinkActionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#linkAction}.
	 * @param ctx the parse tree
	 */
	void exitLinkAction(LeTrainProgramParser.LinkActionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#unlinkAction}.
	 * @param ctx the parse tree
	 */
	void enterUnlinkAction(LeTrainProgramParser.UnlinkActionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#unlinkAction}.
	 * @param ctx the parse tree
	 */
	void exitUnlinkAction(LeTrainProgramParser.UnlinkActionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#semaphoreStatus}.
	 * @param ctx the parse tree
	 */
	void enterSemaphoreStatus(LeTrainProgramParser.SemaphoreStatusContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#semaphoreStatus}.
	 * @param ctx the parse tree
	 */
	void exitSemaphoreStatus(LeTrainProgramParser.SemaphoreStatusContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#forkDirection}.
	 * @param ctx the parse tree
	 */
	void enterForkDirection(LeTrainProgramParser.ForkDirectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#forkDirection}.
	 * @param ctx the parse tree
	 */
	void exitForkDirection(LeTrainProgramParser.ForkDirectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainSense}.
	 * @param ctx the parse tree
	 */
	void enterTrainSense(LeTrainProgramParser.TrainSenseContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainSense}.
	 * @param ctx the parse tree
	 */
	void exitTrainSense(LeTrainProgramParser.TrainSenseContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#trainSpeed}.
	 * @param ctx the parse tree
	 */
	void enterTrainSpeed(LeTrainProgramParser.TrainSpeedContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#trainSpeed}.
	 * @param ctx the parse tree
	 */
	void exitTrainSpeed(LeTrainProgramParser.TrainSpeedContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#sense}.
	 * @param ctx the parse tree
	 */
	void enterSense(LeTrainProgramParser.SenseContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#sense}.
	 * @param ctx the parse tree
	 */
	void exitSense(LeTrainProgramParser.SenseContext ctx);
	/**
	 * Enter a parse tree produced by {@link LeTrainProgramParser#dir}.
	 * @param ctx the parse tree
	 */
	void enterDir(LeTrainProgramParser.DirContext ctx);
	/**
	 * Exit a parse tree produced by {@link LeTrainProgramParser#dir}.
	 * @param ctx the parse tree
	 */
	void exitDir(LeTrainProgramParser.DirContext ctx);
}