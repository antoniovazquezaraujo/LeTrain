import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.map.RailMap;
import com.letrain.rail.CrossRail;
import com.letrain.rail.CurveRail;
import com.letrain.rail.ForkRail;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;
import com.letrain.view.Term;
import com.letrain.view.Term.BackColor;
import com.letrain.view.Term.ForeColor;

public class Letrain {
	public static void main(String[] args) {
		RailMap map = new RailMap();
		Term.setForeColor(ForeColor.YELLOW);
		Term.setBackColor(BackColor.BACKGROUND_BLACK);
		Term.hideCursor();
		while (true) {
			int row = (int) (Math.random() * 25);
			int col = (int) (Math.random() * 80);
			Rail rail = makeRandomRail();
			map.addRail(row,col, rail);
			Term.putAspect(row, col, rail.getAspect());
			Term.print();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	static Rail makeRandomRail() {
		int rand = (int) (Math.random() * 4);
		switch (rand) {
		case 0:
			DirEnv env = new DirEnv();
			Dir random = Dir.random();
			Dir oposite = random.inverse();
			env.addPath(random, oposite);
			
			return new StraightRail(env);
		case 1:
			return new CurveRail();
		case 2:
			return new ForkRail();
		case 3:
			return new CrossRail();
		}
		return null;
	}
}
