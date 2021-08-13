package letrain.visitor;

import javafx.scene.paint.Color;
import letrain.map.*;
import letrain.mvp.Model;
import letrain.mvp.Model.GameMode;
import letrain.mvp.View;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

import java.text.DecimalFormat;

public class InfoVisitor implements Visitor {
    private static final Color RAIL_TRACK_COLOR = Color.grayRgb(80);
    public static final Color FORK_COLOR = Color.grayRgb(180);
    String infoBarText = "";
    String helpBarText = "";
    private final View view;

    public InfoVisitor(View view) {
        this.view = view;
    }

    @Override
    public void visitModel(Model model) {
        infoBarText = "";
        switch(model.getMode()){
            case TRACKS:
                Point pos = model.getCursor().getPosition();
                RailTrack track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());
                if(track != null) {
                    visitRailTrack(track);
                }
                break;
            case TRAINS:
                Train train = model.getSelectedTrain();
                if(train!= null){
                    visitTrain(train);
                }
                break;
            case FORKS:
                ForkRailTrack fork = model.getSelectedFork();
                if(fork !=null){
                    visitForkRailTrack(fork);
                }
                break;
            case CREATE_LOAD_PLATFORM:
                break;
            case LOAD_TRAINS:
                break;
            case MAKE_TRAINS:
                break;
        }
        visitCursor(model.getCursor());
        view.setInfoBarText(infoBarText);
        view.setHelpBarText(getModeHelp(model.getMode()));
    }

    private String getModeHelp(GameMode mode) {
        String ret="F1:tracks. F2:trains. F3:forks. F4:load train. F5:make train.\n";
        switch(mode){
            case TRACKS:
                ret+= "LEFT/RIGHT:rotate cursor. UP/DOWN:forward/backward. SHIFT+UP create rail. CTRL+UP: delete rail";
                break;
            case TRAINS:
                ret+= "HOME/END:invert motor. SPACE:toggle brakes. UP:inc brakes/speed. DOWN:dec brakes/speed. LEFT:prev train. RIGHT:next train. PAGE-U/D:map up/down. CTRL+PAGE-U/D:map left/right." ;
                break;
            case FORKS:
                ret+= "UP/DOWN:toggle fork. LEFT:previous fork. RIGHT:next fork.";
                break;
            case CREATE_LOAD_PLATFORM:
                ret+= "UP:create platform";
                break;
            case LOAD_TRAINS:
                ret+= "UP:load. DOWN:unload. LEFT:prev platform. RIGHT:next platform.";
                break;
            case MAKE_TRAINS:
                ret+= "[A-Z]:create locomotive. [a-z]:create wagon.";
                break;
        }
        return ret;
    }

    @Override
    public void visitMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        infoBarText+="Track:["+track.getPosition().getX()+"," + track.getPosition().getY()+"]"+ getRouterAspect(track.getRouter())+ " "+
                getTrackConnectionsAspect(track)+ " ";
    }

    private String getRouterAspect(Router router) {
        StringBuffer ret = new StringBuffer();
        router.forEach(t->{
            ret.append("("+t.getKey()+ "<->"+ t.getValue()+") ");
        });
        return ret.toString();
    }
    private String getTrackConnectionsAspect(RailTrack track){
        StringBuffer ret = new StringBuffer();
        for(Dir d: Dir.values()){
            Track connected = track.getConnectedTrack(d);
            if(connected != null) {
                ret.append("(" + d + "->" + connected + ")");
            }
        }
        return ret.toString();
    }

    @Override
    public void visitStopRailTrack(StopRailTrack track) {
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        infoBarText+="Track:["+track.getPosition().getX()+"," + track.getPosition().getY()+"] "+ getDynamicRouterAspect((DynamicRouter) track.getRouter())+ " "+getTrackConnectionsAspect(track)+" ";
    }


    private String getDynamicRouterAspect(DynamicRouter router) {
            StringBuffer ret = new StringBuffer();
            router.forEach(t->{
                if(t.getValue()!=null) {
                    ret.append("(" + t.getKey() + "<->" + t.getValue() + ") ");
                }
            });
            ret.append(" Norm:"+ router.getOriginalRoute()+ " Alt:"+ router.getAlternativeRoute()+" Using Alt:"+ (router.isUsingAlternativeRoute()?"TRUE":"FALSE"));
            return ret.toString();
    }

    @Override
    public void visitTrainFactoryRailTrack(TrainFactoryRailTrack track) {
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        infoBarText+="Track:["+track.getPosition().getX()+"," + track.getPosition().getY()+"]"+ getRouterAspect(track.getRouter())+ "\n";
        infoBarText+="Connect:...";
    }


    @Override
    public void visitTrain(Train train) {
        DecimalFormat df = new DecimalFormat("0000.0000");
        infoBarText+=
                " F:" + df.format(train.getForce())+
                " EF:"+ df.format(train.getExternalForce())+
                " VE:"+ df.format(train.getVelocity())+
                " DT:"+ df.format(train.getDistanceTraveled())+
                " BR:"+ df.format(train.getBrakes())+
                " RE:"+ train.isReversed();
    }

    @Override
    public void visitLinker(Linker linker) {

    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        infoBarText+= "Locomotive. Accel:"+ locomotive.getAcceleration()+ " Force:"+locomotive.getForce()+ " Mass:"+ locomotive.getMass()+ " Dir"+ locomotive.getDir()+"\n";

    }

    @Override
    public void visitWagon(Wagon wagon) {
        infoBarText+= "Wagon. Accel:"+ wagon.getAcceleration()+ " Mass:"+ wagon.getMass()+ " Dir"+ wagon.getDir()+"\n";
    }

    @Override
    public void visitCursor(Cursor cursor) {
        infoBarText+="Cursor:["+cursor.getPosition().getX()+"," + cursor.getPosition().getY()+"]"+ "\n";
    }

}
