package com.letrain.dir;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;

public class DirEnvTest {
	DirEnv env;
	@Before
	public void setUp() throws Exception {
		env = new DirEnv();
	}

	@After
	public void tearDown() throws Exception {
		env = null;
	}

	@Test
	public void testDirEnv() {
		for(Dir d: Dir.values()){
			assertTrue(env.getValue(d) == null);
		}
	}

	@Test
	public void testDirEnvDirEnv() {
		DirEnv x = new DirEnv();
		x.addPath(Dir.N, Dir.S);
		DirEnv y = new DirEnv(x);
		assertTrue(y.getPath(Dir.N) == Dir.S);
		assertTrue(y.getPath(Dir.S) == Dir.N);
	}

	@Test
	public void testgetPathInt() {
		env.addPath(Dir.E, Dir.W);
		assertTrue(env.getPath(Dir.E.getValue()).getValue() == Dir.W.getValue());
		assertTrue(env.getPath(Dir.W.getValue()).getValue() == Dir.E.getValue());
	}

	@Test
	public void testgetPathDir() {
		env.addPath(Dir.E, Dir.W);
		assertTrue(env.getPath(Dir.E ).getValue() == Dir.W.getValue());
		assertTrue(env.getPath(Dir.W ).getValue() == Dir.E.getValue());

	}

	@Test
	public void testGetFirstOpenOut() {
		env.addPath(Dir.S, Dir.N);
		assertTrue(env.getFirstOpenOut() == Dir.N);
		env.addPath(Dir.E, Dir.W);
		assertTrue(env.getFirstOpenOut() == Dir.E);
		 
	}
	@Test
	public void testGetFirstOpenIn() {
		env.addPath(Dir.S, Dir.N);
		assertTrue(env.getFirstOpenIn() == Dir.S);
		env.addPath(Dir.E, Dir.W);
		assertTrue(env.getFirstOpenIn() == Dir.W);
		 
	}
	@Test
	public void testGetNumOpenOuts() {
		assertTrue(env.getNumOpenOuts() == 0);
		env.addPath(Dir.S, Dir.N);
		assertTrue(env.getNumOpenOuts() == 2);
		env.addPath(Dir.SW, Dir.NE);
		assertTrue(env.getNumOpenOuts() == 4);
		
	}
	@Test
	public void testGetIn() {
		env.addPath(Dir.S, Dir.N);
		assertTrue(env.getPath(Dir.S) == Dir.N);
		assertTrue(env.getPath(Dir.N) == Dir.S);
;
	}
	@Test
	public void testgetPath() {
		env.addPath(Dir.S, Dir.NE);
		assertTrue(env.getPath(Dir.S) == Dir.NE);
		assertTrue(env.getPath(Dir.NE) == Dir.S);
	}

 
	@Test
	public void testRemovePath() {
		env.addPath(Dir.S, Dir.NE);
		assertTrue(env.getPath(Dir.S) == Dir.NE);
		assertTrue(env.getPath(Dir.NE) == Dir.S);
		assertTrue(env.getPath(Dir.S) == Dir.NE);
		assertTrue(env.getPath(Dir.NE) == Dir.S);
		env.removePath(Dir.NE, Dir.S);
		assertTrue(env.getNumOpenOuts() == 0);
	}

	@Test
	public void testIsStraight() {
		env.addPath(Dir.S, Dir.N);
		assertTrue(env.isStraight());
		env.addPath(Dir.S, Dir.NE);
		assertTrue(!env.isStraight());
 
	}

	@Test
	public void testIsCurve() {
		env.addPath(Dir.S, Dir.NE);
		assertTrue(env.isCurve());
		env.addPath(Dir.S, Dir.NW);
		assertTrue(!env.isCurve());
	}

	@Test
	public void testIsCross() {
		assertTrue(!env.isCross());
		env.addPath(Dir.S, Dir.N);
		assertTrue(!env.isCross());
		env.addPath(Dir.SE, Dir.NW);
		assertTrue(env.isCross());
	}

	@Test
	public void testIsFork() {
		assertTrue(!env.isCross());
		env.addPath(Dir.S, Dir.N);
		assertTrue(!env.isCross());
		env.addPath(Dir.SE, Dir.NW);
		assertTrue(env.isCross());
	}

	@Test
	public void testCanBeAForkInput() {
		for(Dir d: Dir.values()){
			assertTrue(!env.canBeAForkInput(d));
			Dir left = Dir.add(d, 1);
			Dir right = Dir.add(d, -1);
			env.addPath(d, left);
			env.addPath(d, right);
			assertTrue(env.canBeAForkInput(d));
			env.removePath(d, left);
			env.removePath(d, right);
		}
	}
}
