package com.letrain.map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.map.RailMap;
import com.letrain.rail.StraightRail;

public class RailMapTest {
	RailMap map;
	@Before
	public void setUp() throws Exception {
		map = new RailMap();

	}

	@After
	public void tearDown() throws Exception {
		map = null;
	}
 

	@Test
	public void testForEach() {
		for(int n= 0; n<10; n++){
			map.setRail(n+1, 0, new StraightRail());
		}
		map.forEach(e -> assertTrue(e.getPos().getCol() == 0));
		map.forEach(e -> assertTrue(e.getPos().getRow() > 0));
	}

 

	@Test
	public void testGetAndSetRail() {
		for(int n= 0; n<10; n++){
			assertTrue(map.getRailAt(n, 0) == null);
		}
		for(int n= 0; n<10; n++){
			map.setRail(n, 0, new StraightRail());
		}
		for(int n= 0; n<10; n++){
			assertTrue(map.getRailAt(n, 0) != null);
		}
	}

}
