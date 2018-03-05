package com.letrain.dir

import spock.lang.Specification

class DirSpec extends Specification {

    def "angular distance"(){
        expect :
        Dir.N.angularDistance(Dir.N)== 0 ;
        Dir.S.angularDistance(Dir.S)== 0 ;
        Dir.N.angularDistance(Dir.S)== 4 ;
        Dir.NW.angularDistance(Dir.S)== 3 ;
        Dir.NE.angularDistance(Dir.S)== -3 ;
        Dir.NE.angularDistance(Dir.NW)==2 ;
        Dir.NW.angularDistance(Dir.NE)==-2 ;
    }
    def "inverse"(){
        expect:
        Dir.N.inverse()== Dir.S ;
        Dir.S.inverse()== Dir.N ;
        Dir.E.inverse()== Dir.W ;
        Dir.W.inverse()== Dir.E ;
        Dir.NW.inverse()== Dir.SE ;
        Dir.NE.inverse()== Dir.SW ;
        Dir.SE.inverse()== Dir.NW ;
        Dir.SW.inverse()== Dir.NE ;
    }
    def "shortWay"(){
        expect:
        Dir.shortWay(4)== 4  ;
        Dir.shortWay(5)== -3 ;
        Dir.shortWay(6)== -2 ;
        Dir.shortWay(7)== -1 ;
        Dir.shortWay(8)== 0 ;
    }

    def "isCurve" (){
        expect:
        for (int n = 0; n < Dir.NUM_DIRS; n++) {
            Dir d = Dir.fromInt(n);
            Dir inverseLeft = Dir.invert(Dir.add(d ,-1));
            Dir inverseRight = Dir.invert(Dir.add(d ,1));
            d.isCurve(inverseRight) ;
            d.isCurve( inverseRight ) ;
        }
    }


    def "isStraight"(){
        expect:
        for (int n = 0; n < Dir.NUM_DIRS; n++) {
            Dir d = Dir.fromInt(n);
            int inverse = Dir.invert(d.getValue());
            d.isStraight(Dir.fromInt(inverse )) ;
        }
    }
}
