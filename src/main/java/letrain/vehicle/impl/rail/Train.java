package letrain.vehicle.impl.rail;

import letrain.track.rail.RailTrack;
import letrain.vehicle.Linkable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Trailer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Train extends Trailer<RailTrack> {

    final List<Linker<RailTrack>> vehiclesAtFront;
    final List<Linker<RailTrack>> vehiclesAtBack;

    public Train() {
        this.vehiclesAtFront = new ArrayList<>();
        this.vehiclesAtBack = new ArrayList<>();
    }

    public void linkVehicle(Linkable.LinkSide side, Linker<RailTrack> v) {

    }

    public Linker<RailTrack> unlinkVehicle(Linkable.LinkSide side) {
        return null;
    }

    public Stream<Linker<RailTrack>> getVehicles(Linkable.LinkSide side) {
        return side == Linkable.LinkSide.FRONT ? vehiclesAtFront.stream() : vehiclesAtBack.stream();
    }


//	List<RailVehicle> vehiclesAtBack;
//	List<RailVehicle> vehiclesAtFront;

///	int numStoppedTurns;

///	TrainSide trainSide;

//	public Train() {
//		vehiclesAtFront = new ArrayList<>();
//		vehiclesAtBack = new ArrayList<>();
//		trainSide = TrainSide.FRONT;
//	}

//	public int getSize() {
//		return vehiclesAtFront.size() + 1 + vehiclesAtBack.size();
//	}
//
//	public List<Transportable> getVehiclesAtFront() {
//		return vehiclesAtFront;
//	}
//
//	public List<Transportable> getVehiclesAtBack() {
//		return vehiclesAtBack;
//	}
//
//	public void setMoved(boolean moved) {
//		this.moved = moved;
//	}
//
////	public void enterLinker(letrain.linker.impl.rail.RailVehicle v) {
////		letrain.map.Router railOfVehicle = v.getRail();
////		if (railOfVehicle != null) {
////			if (railOfVehicle.equals(nextRail(TrainSide.FRONT))) {
////				enterLinker(TrainSide.FRONT, v);
////				return;
////			}
////			if (railOfVehicle.equals(nextRail(TrainSide.BACK))) {
////				enterLinker(TrainSide.BACK, v);
////				return;
////			}
////		}
////	}
//
//	public void enterLinker(TrainSide p, RailVehicle w) {
//		Transportable vehicleToLink = null;
//		if (p == TrainSide.FRONT) {
//			vehiclesAtFront.enterLinker(w);
//		} else {
//			vehiclesAtBack.enterLinker(w);
//		}
//		w.setTrain(this);
//	}
//
//	// public letrain.map.Dir getDirFromFirst() {
//	// return ((vehiclesAtFront.peek())).getDir();
//	// }
//	//
//	// public letrain.map.Dir getDirFromBack() {
//	// return ((vehiclesAtBack.peek())).getDir();
//	// }
//
//	public void reverse() {
//		// vehiclesAtFront.iterator().forEachRemaining(v -> {
//		// v.getRail().reverseVehicle();
//		// });
//		// getRail().reverseVehicle();
//		// vehiclesAtBack.iterator().forEachRemaining(v -> {
//		// v.getRail().reverseVehicle();
//		// });
//		reversed = !reversed;
//	}
//
//    @Override
//    public Train getTrain() {
//        return this;
//    }
//
//    public void forEachVehicle(Consumer<Transportable> c) {
//		forEachFrontVehicle(c);
//		c.accept(this);
//		forEachBackVehicle(c);
//	}
//
//	public void forEachBackVehicle(Consumer<Transportable> c) {
//		Iterator<Transportable> iterator;
//		iterator = vehiclesAtFront.iterator();
//		iterator.forEachRemaining(v -> {
//			c.accept(v);
//		});
//	}
//
//	public void forEachFrontVehicle(Consumer<Transportable> c) {
//		Iterator<Transportable> iterator;
//		iterator = vehiclesAtBack.iterator();
//		iterator.forEachRemaining(v -> {
//			c.accept(v);
//		});
//	}
//
//	public void turn() {
//		if (moved) {
//			return;
//		} else {
//			moved = true;
//		}
//
//		if (numStoppedTurns == 0) {
//			moveTrain();
//			numStoppedTurns = 100;
//		} else {
//			numStoppedTurns--;
//		}
//
//	}
//
//	// public letrain.map.Router nextRail() {
//	// return nextRail(reversed?TrainSide.BACK:TrainSide.FRONT);
//	// }
//	// public letrain.map.Router nextRailAtFront() {
//	// return nextRail(TrainSide.FRONT);
//	// }
//	// public letrain.map.Router nextRailAtBack() {
//	// return nextRail(TrainSide.BACK);
//	// }
//	// public letrain.map.Router nextRail(TrainSide trainSide) {
//	// letrain.linker.impl.rail.RailVehicle topVehicle = null;
//	// letrain.linker.impl.rail.RailVehicle linker = null;
//	// letrain.map.Router nextRail = null;
//	// if (trainSide.equals(TrainSide.BACK)) {
//	// topVehicle = getLastBackVehicle();
//	// linker = getBackAntecessor(topVehicle);
//	// } else {
//	// topVehicle = getLastFrontVehicle();
//	// linker = getFrontAntecessor(topVehicle);
//	// }
//	// if(linker==null) {
//	// linker = this;
//	// }
//	//
//	//
//	// nextRail = topVehicle.getRail().getConnected(topVehicle.getDir());
//	// return nextRail;
//	// }
//
//	public Transportable getBackAntecessor(Transportable v) {
//		if (vehiclesAtBack.isEmpty()) {
//			return null;
//		}
//		int index = vehiclesAtBack.indexOf(v);
//		if (index <= 0) {
//			return null;
//		}
//		return vehiclesAtBack.getLinker(index - 1);
//	}
//
//	public Transportable getFrontAntecessor(Transportable v) {
//		if (vehiclesAtFront.isEmpty()) {
//			return null;
//		}
//		int index = vehiclesAtFront.indexOf(v);
//		if (index <= 0) {
//			return null;
//		}
//		return vehiclesAtFront.getLinker(index - 1);
//	}
//
//	public Transportable getLastFrontVehicle() {
//		return vehiclesAtFront.isEmpty() ? null : vehiclesAtFront.getLinker(vehiclesAtFront.size() - 1);
//	}
//
//	public Transportable getLastBackVehicle() {
//		return vehiclesAtBack.isEmpty() ? null : vehiclesAtBack.getLinker(vehiclesAtBack.size() - 1);
//	}
//
//	public void moveTrain() {
//		Dir d = this.dir;
//		Transportable v = this;
//		for (RailVehicle next : vehiclesAtFront) {
//			d = next.push(v);
//			next.setDir(d);
//			v = next;
//		}
//		for (Transportable next : vehiclesAtFront) {
//			next.exitRail();
//		}
//		v = this;
//        this.exitRail();
//        for (RailVehicle next : vehiclesAtBack) {
//            d = next.pull(v);
//            next.setDir(d);
//            v = next;
//        }
//        for (Transportable next : vehiclesAtBack) {
//            next.exitRail();
//        }
//    }
//
//	public void incSpeed() {
//		// TODO Auto-generated method stub
//
//	}
//
//	public void decSpeed() {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public boolean gotoRail(RailTrack r) {
//        super.gotoRail(r);
//        Dir dir = r.getAnyDir();
//        RailVehicle nextVehicle = this;
//        RailTrack nextRail = null;
//        for(RailVehicle v : vehiclesAtFront){
//            nextRail= nextVehicle.getRail().getConnected(dir);
//            v.gotoRail(nextRail);
//            nextVehicle = v;
//        }
//		dir = r.getDir(dir);
//		nextVehicle = this;
//		for(RailVehicle v : vehiclesAtBack){
//			nextRail= nextVehicle.getRail().getConnected(dir);
//			v.gotoRail(nextRail);
//			nextVehicle = v;
//		}
//		return true; //agregar excepciones aquí
//	}
}
