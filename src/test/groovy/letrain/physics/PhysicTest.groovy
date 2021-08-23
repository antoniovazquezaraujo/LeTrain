package letrain.physics


import spock.lang.Specification

class PhysicTest extends Specification {
    PhysicLabSpace physic ;
    char[][] screen = new char[25][80];
    Map<Body2D, Character> aspects = new HashMap<>();
    def setup(){
        physic = new PhysicLabSpace();
        clearScreen();

    }
    def "xxx"(){
        given:
        Body2D a = new Body2D()
        a.position = new Vector2D(10,10)
        a.setMotorForce(1)
        Body2D b = new Body2D()
        b.mass = 1000000;
        b.position = new Vector2D(12,10)
        Body2D c = new Body2D()
        c.mass = 1;
        c.position = new Vector2D(20,10)
        c.setMotorForce(1)

        physic.addBody(a)
        physic.addBody(b)
        physic.addBody(c)
        loadAspects();

        when:
        100.times {
            paint(physic)
            physic.calc()
        }
        then:
        assert true

    }
    public void loadAspects(){
        char name = 'a';
        for(Body2D body: physic.bodies){
            aspects.put(body, name++);
        }
    }
    void paint(PhysicLabSpace physic){
        clearScreen();
        for(Body2D body: physic.bodies){
            screen[((int)body.position.y)%25][((int)body.position.x)%80] = aspects.get(body);
        }
        println()
        println("               1         2         3         4         5         6         7         ")
        println("     01234567890123456789012345678901234567890123456789012345678901234567890123456789")
        for(int n=0; n<25; n++){
            System.out.print(String.format("%3d",n)+": ")
            for(int m =0; m<80; m++){
                System.out.print(screen[n][m])
            }
            System.out.println()
        }
    }
    void clearScreen(){
        for(int n=0; n<25; n++){
            for(int m =0; m<80; m++){
                screen[n][m]= '_';
            }
        }
    }
}
