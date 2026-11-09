package fr.brickcraft.world;
public final class PreciseManageRulesSelfTest {
    public static void main(String[] args){
        int checks=0;
        for(int x=-200;x<=200;x+=5)for(int z=-200;z<=200;z+=5){
            double dx=x+.5,dz=z+.5;
            boolean expected=dx*dx+.25+dz*dz<=PreciseManageRules.RADIUS*PreciseManageRules.RADIUS;
            boolean actual=PreciseManageRules.inRange(0,0,0,x,0,z);
            if(expected!=actual)throw new AssertionError("Range mismatch: "+x+","+z);
            checks++;
        }
        if(PreciseManageRules.inRange(Double.NaN,0,0,0,0,0))throw new AssertionError("NaN accepted");
        if(PreciseManageRules.inRange(0,0,0,0,500,0))throw new AssertionError("Too far accepted");
        System.out.println("BrickCraft 0.9.7 management rules OK: "+checks+" samples");
    }
}
