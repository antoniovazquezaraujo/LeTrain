package com.letrain;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.letrain.dir.DirEnvTest;
import com.letrain.dir.DirTest;
import com.letrain.map.RailMapTest;
import com.letrain.rail.RailTest;
import com.letrain.vehicle.RailPenTest;
import com.letrain.vehicle.RailVehicleTest;
import com.letrain.vehicle.TrainTest;

@RunWith(Suite.class)
@SuiteClasses({ 
	DirTest.class , 
	DirEnvTest.class, 
	RailTest.class,
	RailVehicleTest.class,
	TrainTest.class,
	RailMapTest.class,
	RailPenTest.class})
public class AllTests {

}
