// Generated from letrain/command/LeTrainProgram.g4 by ANTLR 4.13.2
package letrain.command;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link LeTrainProgramParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface LeTrainProgramVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#start}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStart(LeTrainProgramParser.StartContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(LeTrainProgramParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#directCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectCommand(LeTrainProgramParser.DirectCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#directTrainCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectTrainCommand(LeTrainProgramParser.DirectTrainCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#createItinerary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateItinerary(LeTrainProgramParser.CreateItineraryContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#assignItinerary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignItinerary(LeTrainProgramParser.AssignItineraryContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#setAutopilot}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSetAutopilot(LeTrainProgramParser.SetAutopilotContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#setNameCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSetNameCommand(LeTrainProgramParser.SetNameCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#bool}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool(LeTrainProgramParser.BoolContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainRef(LeTrainProgramParser.TrainRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#waypoint}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWaypoint(LeTrainProgramParser.WaypointContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#stationRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStationRef(LeTrainProgramParser.StationRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#sensorRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSensorRef(LeTrainProgramParser.SensorRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#direction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirection(LeTrainProgramParser.DirectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#action}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAction(LeTrainProgramParser.ActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trigger}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrigger(LeTrainProgramParser.TriggerContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#sensorSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSensorSelector(LeTrainProgramParser.SensorSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#forkSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForkSelector(LeTrainProgramParser.ForkSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#semaphoreSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSemaphoreSelector(LeTrainProgramParser.SemaphoreSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#stationSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStationSelector(LeTrainProgramParser.StationSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainSelector(LeTrainProgramParser.TrainSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainEvent}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainEvent(LeTrainProgramParser.TrainEventContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#commandBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommandBlock(LeTrainProgramParser.CommandBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#commandItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommandItem(LeTrainProgramParser.CommandItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainExtractor}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainExtractor(LeTrainProgramParser.TrainExtractorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#placeSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPlaceSelector(LeTrainProgramParser.PlaceSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#semaphoreAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSemaphoreAction(LeTrainProgramParser.SemaphoreActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#forkAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForkAction(LeTrainProgramParser.ForkActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainAction(LeTrainProgramParser.TrainActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#linkAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLinkAction(LeTrainProgramParser.LinkActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#unlinkAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnlinkAction(LeTrainProgramParser.UnlinkActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#semaphoreStatus}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSemaphoreStatus(LeTrainProgramParser.SemaphoreStatusContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#forkDirection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForkDirection(LeTrainProgramParser.ForkDirectionContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainSense}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainSense(LeTrainProgramParser.TrainSenseContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#trainSpeed}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTrainSpeed(LeTrainProgramParser.TrainSpeedContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#sense}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSense(LeTrainProgramParser.SenseContext ctx);
	/**
	 * Visit a parse tree produced by {@link LeTrainProgramParser#dir}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDir(LeTrainProgramParser.DirContext ctx);
}