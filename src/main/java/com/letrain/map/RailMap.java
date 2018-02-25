package com.letrain.map;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.letrain.rail.Rail;

public class RailMap {

	Map<Integer, Map<Integer, Rail>> rails;

	public RailMap() {
		rails = new HashMap<>();
	}
	public void forEach(Consumer<Rail> c){
		for(int row : rails.keySet()){
			for(Rail rail: rails.get(row).values()){
				c.accept(rail);
			}
		}
	}
	public Rail getRailAt(int row, int col) {
		Map<Integer, Rail> m = rails.get(row);
		if (m != null) {
			return m.get(col);
		}
		return null;
	}

	public void setRail(int row, int col, Rail rail) {
		if(!rails.containsKey(row)){
			rails.put(row, new HashMap<Integer, Rail>());
		}
		Map<Integer, Rail> cols = rails.get(row);
		if (rail != null) {
			cols.put(col, rail);
			rail.setPos(row, col);
		} else {
			cols.remove(col);
		}
 
	}

}
