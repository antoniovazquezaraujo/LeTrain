package com.letrain.view;

 
 

/**
 * Usage:
 * <li>String msg = Ansi.Red.and(Ansi.BgYellow).format("Hello %s", name)</li>
 * <li>String msg = Ansi.Blink.colorize("BOOM!")</li>
 * 
 * Or, if you are adverse to that, you can use the constants directly:
 * <li>String msg = new Ansi(Ansi.ITALIC, Ansi.GREEN).format("Green money")</li>
 * Or, even:
 * <li>String msg = Ansi.BLUE + "scientific"</li>
 * 
 * NOTE: Nothing stops you from combining multiple FG colors or BG colors, 
 *       but only the last one will display.
 * 
 * @author dain
 *
 */
public class Term {
	public enum BackColor {
		BACKGROUND_BLACK	("\u001B[40m"),
		BACKGROUND_RED		("\u001B[41m"),
		BACKGROUND_GREEN	("\u001B[42m"),
		BACKGROUND_YELLOW	("\u001B[43m"),
		BACKGROUND_BLUE		("\u001B[44m"),
		BACKGROUND_MAGENTA	("\u001B[45m"),
		BACKGROUND_CYAN		("\u001B[46m"),
		BACKGROUND_WHITE	("\u001B[47m");
		private String value;
		BackColor(String value){
			this.value = value;
		}
		String getValue(){
			return value;
		}
		@Override
		public String toString(){
			return value;
		}
		public static BackColor random(){
			return values()[( (int)(Math.random()*(values().length)))];
		}
	}
	public enum Effect{
		SANE				("\u001B[0m"),
		HIGH_INTENSITY		("\u001B[1m"),
		LOW_INTENSITY		("\u001B[2m"),
		ITALIC				("\u001B[3m"),
		UNDERLINE			("\u001B[4m"),
		BLINK				("\u001B[5m"),
		RAPID_BLINK			("\u001B[6m"),
		REVERSE_VIDEO		("\u001B[7m"),
		INVISIBLE_TEXT		("\u001B[8m");
		private String value;
		Effect(String value){
			this.value = value;
		}
		String getValue(){
			return value;
		}
		@Override
		public String toString(){
			return value;
		}
		public static Effect random(){
			return values()[( (int)(Math.random()*(values().length)))];
		}
	}
	public enum ForeColor {

		BLACK				("\u001B[30m"),
		RED					("\u001B[31m"),
		GREEN				("\u001B[32m"),
		YELLOW				("\u001B[33m"),
		BLUE				("\u001B[34m"),
		MAGENTA				("\u001B[35m"),
		CYAN				("\u001B[36m"),
		WHITE				("\u001B[37m");

		private String value;
		ForeColor(String value){
			this.value = value;
		}
		String getValue(){
			return value;
		}
		@Override
		public String toString(){
			return value;
		}
		public static ForeColor random(){
			return values()[( (int)(Math.random()*(values().length)))];
		}

	}

	private static String HIDE_CURSOR = "\033[?25l";
	private static String SHOW_CURSOR = "\033[?25h";
	
	static StringBuffer content= new StringBuffer();
	public static void clear(){
		System.out.print("\u001B[2J");
	}
	public static void print(){
		System.out.print(content.toString());
		content.setLength(0);
	}
	public static void putAspect(int row, int col, Aspect  aspect ){
		content.append(at(row,col)).append(aspect.getAspectChar().getValue());
	}
	public static void putC(int row, int col, char c){
		content.append(at(row,col)).append(c);
	}
	public static void erase(int row, int col){
		putC(row, col, ' ');
	}
	public static void putStr(int row, int col, String s){
		content.append(at(row,col)).append(s);
	}
	public static void setForeColor(ForeColor color){
		content.append(color);
	}
	public static void setBackColor(BackColor color){
		content.append(color);
	}
	public static void setEffect(Effect color){
		content.append(color);
	}
	public static void hideCursor(){
		putStr(1,1,HIDE_CURSOR);
	}
	private static String  at(int row, int col){
		return "\u001B["+ row+ ";"+ col + "H";
	}

	public static void main(String[] args) {
		clear();
		putStr(1,1,HIDE_CURSOR);
		while(true){
			setForeColor(ForeColor.random());
			setBackColor(BackColor.random());
			putC((int)(Math.random()*20), (int)(Math.random()*80), '*');
			print();
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}