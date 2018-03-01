package com.letrain.vehicle;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.rail.CurveRail;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;
import com.letrain.vehicle.Train;
import com.letrain.vehicle.Train.TrainSide;
import com.letrain.vehicle.Wagon;

public class TrainTest {
	Rail r1;
	Rail r2;
	Rail r3;
	Rail r4;
	Train train;
	@Before
	public void setUp() throws Exception {
		train = new Train();
		r1 = new StraightRail();
		r1.getEnv().addPath(Dir.W, Dir.E);
		r2 = new StraightRail();
		r2.getEnv().addPath(Dir.W, Dir.E);
		r3 = new StraightRail();
		r3.getEnv().addPath(Dir.W, Dir.E);
		r4 = new StraightRail();
		r4.getEnv().addPath(Dir.W, Dir.E);
		r1.linkRailAt(Dir.E, r2);
		r2.linkRailAt(Dir.E, r3);
		r3.linkRailAt(Dir.E, r4);
	}

	@After
	public void tearDown() throws Exception {
		train =null;
	}
 
	//Agregamos vagones por ambos extremos y están en el tren
	@Test
	public void testAddWagon() {
		Wagon w1 = new Wagon();
		w1.setDir(Dir.E);
		w1.gotoRail(r2);
		train.addVehicle(TrainSide.BACK, w1);
		Wagon w2 = new Wagon();
		w2.setDir(Dir.E);
		w2.gotoRail(r1);
		train.addVehicle(TrainSide.BACK, w2);
		Wagon w3 = new Wagon();
		w3.setDir(Dir.E);
		w3.gotoRail(r3);
		train.addVehicle(TrainSide.FRONT, w3);
		
	}

	//Movemos el tren y aparece en el rail correcto
	@Test
	public void testMoveTrain(){
		Wagon w1 = new Wagon();
		w1.setDir(Dir.E);
		w1.gotoRail(r2);
		train.addVehicle(TrainSide.BACK, w1);
		Wagon w2 = new Wagon();
		w2.setDir(Dir.E);
		w2.gotoRail(r1);
		train.addVehicle(TrainSide.BACK, w2);
		Wagon w3 = new Wagon();
		w3.setDir(Dir.E);
		w3.gotoRail(r3);
		train.addVehicle(TrainSide.FRONT, w3);

//		train.moveTrain();
	}
	//Movemos en ambos sentidos y el rail que hay delante es el correcto
	@Test
	public void testGetRailAheadMoveAndInvert(){
		Wagon w1 = new Wagon();
		w1.setDir(Dir.E);
		w1.gotoRail(r2);
		train.addVehicle(TrainSide.BACK, w1);
		Wagon w2 = new Wagon();
		w2.setDir(Dir.E);
		w2.gotoRail(r1);
		train.addVehicle(TrainSide.BACK, w2);
		Wagon w3 = new Wagon();
		w3.setDir(Dir.E);
		w3.gotoRail(r3);
		train.addVehicle(TrainSide.FRONT, w3);
		assertEquals(train.nextRail(), r4);
//		train.moveTrain();
//		assertEquals(train.railAhead(), null);
//		train.invert();
//		assertEquals(train.railAhead(), r1);
//		train.moveTrain();
//		assertEquals(train.railAhead(), null);
	}

	//Movemos el tren y la dir del primer vagon cambia según las curvas
	@Test
	public void testGetDirFromFirst() {
		Rail r5;
		r5 = new CurveRail();
		r5.getEnv().addPath(Dir.W, Dir.NE);
		r4.linkRailAt(Dir.E, r5);

		Wagon w1 = new Wagon();
		w1.setDir(Dir.E);
		w1.gotoRail(r2);
		train.addVehicle(TrainSide.BACK, w1);
		Wagon w2 = new Wagon();
		w2.setDir(Dir.E);
		w2.gotoRail(r1);
		train.addVehicle(TrainSide.BACK, w2);
		Wagon w3 = new Wagon();
		w3.setDir(Dir.E);
		w3.gotoRail(r3);
		train.addVehicle(TrainSide.FRONT, w3);
		assertEquals(train.nextRail(), r4);
//		train.moveTrain();
//		assertEquals(train.railAhead(), r5);
//		train.moveTrain();
//		assertEquals(train.railAhead(), null);
//		assertEquals(train.getDirFromFirst(), Dir.NE);
//		train.invert();
//		train.moveTrain();
//		assertEquals(train.getDirFromFirst(), Dir.W);
	}
 

}
