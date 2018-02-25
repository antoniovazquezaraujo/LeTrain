package com.letrain.map;

import com.letrain.dir.Dir;

public class Point {
	private int col;
	private int row;
	public Point(int row, int col) {
		super();
		this.row = row;
		this.col = col;
	}
	@Override
	public boolean equals(Object p) {
		Point q = (Point)p;
		return q.row == row && q.col == col;
	}
	public Point(Point pos) {
		this(pos.row, pos.col);
	}
	public int getCol() {
		return col;
	}
	public void setCol(int col) {
		this.col = col;
	}
	public int getRow() {
		return row;
	}
	public void setRow(int row) {
		this.row = row;
	}
	public Dir  locate (Point p){
		if(row > p.row){
			if(col > p.col){
				return Dir.NW;
			}else if(col < p.col){
				return Dir.NE;
			}else{
				return Dir.N;
			}
		}else if(row < p.row){
			if(col > p.col){
				return Dir.SW;
			}else if(col < p.col){
				return Dir.SE;
			}else{
				return Dir.S;
			}
		}else{//row == p.row
			if(col > p.col){
				return Dir.W;
			}else if(col < p.col){
				return Dir.E;
			}else{
				return null;
			}
		}
	}
	public void  move(Dir dir){
		move(dir,1);
	}
	public void  move(Dir dir, int distance){
		 
		switch(dir ) {
		case N: 
			row-=distance; 
			break;
		case NE: 
			row-=distance; 
			col+=distance; 
			break;
		case E: 
			col+=distance; 
			break;
		case SE: 
			row+=distance; 
			col+=distance; 
			break;
		case S: 
			row+=distance; 
			break;
		case SW: 
			row+=distance; 
			col-=distance; 
			break;
		case W: 
			col-=distance; 
			break;
		case NW: 
			row-=distance; 
			col-=distance; 
			break;
		default:
			assert(false);	
		}
	}

}
