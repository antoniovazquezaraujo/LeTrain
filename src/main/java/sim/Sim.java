package sim;

import java.util.ArrayList;
import java.util.List;

import com.letrain.map.Point;
import com.letrain.map.RailMap;
import com.letrain.rail.ForkRail;
import com.letrain.vehicle.Bulldozer;
import com.letrain.vehicle.Train;

public class Sim {

	Bulldozer bulldozer;
	List<Train> trains;
	List<ForkRail> forks;
	public RailMap railMap;
	private int selectedTrainIndex;
	private int selectedForkIndex;

	public Sim() {
		trains = new ArrayList<>();
		forks = new ArrayList<>();
		railMap = new RailMap();
		bulldozer = new Bulldozer(railMap, new Point(0, 0));
	}

	public void selectNextTrain() {
		selectedTrainIndex++;
		if (trains.isEmpty()) {
			selectedTrainIndex = -1;
		} else {
			if (selectedTrainIndex >= trains.size()) {
				selectedTrainIndex = 0;
			}
		}
	}

	public void selectPrevTrain() {
		selectedTrainIndex--;
		if (trains.isEmpty()) {
			selectedTrainIndex = -1;
		} else {
			if (selectedTrainIndex == 0) {
				selectedTrainIndex = trains.size() - 1;
			}
		}
	}

	public Train getSelectedTrain() {
		if (selectedTrainIndex == -1){
			return null;
		}
		return trains.get(selectedTrainIndex);
	}

	public void selectNextFork() {
		selectedForkIndex++;
		if (forks.isEmpty()) {
			selectedForkIndex = -1;
		} else {
			if (selectedForkIndex >= forks.size()) {
				selectedForkIndex = 0;
			}
		}
	}

	public void selectPrevFork() {
		selectedForkIndex--;
		if (forks.isEmpty()) {
			selectedForkIndex = -1;
		} else {
			if (selectedForkIndex == 0) {
				selectedForkIndex = forks.size() - 1;
			}
		}
	}

	public ForkRail getSelectedFork() {
		if (selectedForkIndex == -1){
			return null;
		}
		return forks.get(selectedForkIndex);
	}

	public Bulldozer getBulldozer() {
		return bulldozer;
	}
}
