import java.awt.Color;
import java.awt.Image;
import java.awt.Toolkit;

import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;
import uchicago.src.sim.space.Object2DGrid;


/**
 * Class that implements the simulation agent for the rabbits grass simulation.

 * @author MohammadReza Ebrahimi, Salar Rahimi 
 */

public class RabbitsGrassSimulationAgent implements Drawable {
	private int x;
	private int y;
	private int vX;
	private int vY;
	public int Energy;
	private int BirthThreshold;
	private static int IDNumber = 0;
	private int ID;
	
	//Subtle Attributes
	private int ReducedEnergyPerMove;
	private int EnergyGainedFromGrass;
	
	private Image rabbit = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/rabbit.png"));
		
	private RabbitsGrassSimulationSpace Space;
	
	public RabbitsGrassSimulationAgent(int InitialEnergy, int _BirthThreshold, 
					int _ReducedEnergyPerMove, int _EnergyGainedFromGrass){
		Energy = InitialEnergy;
		BirthThreshold = _BirthThreshold;
		ReducedEnergyPerMove = _ReducedEnergyPerMove;
		EnergyGainedFromGrass = _EnergyGainedFromGrass;
		setVxVy();
		x = -1;
		y = -1;
		IDNumber++;
	    ID = IDNumber;
	}
	
	private void setVxVy() {
		
		int i = (int)Math.floor(Math.random() * 4);
		
		switch(i) {
			case 0: 
				vX = 1;
				vY = 0;
				break;
			case 1: 
				vX = 0;
				vY = 1;
				break;
			case 2: 
				vX = -1;
				vY = 0;
				break;
			case 3: 
				vX = 0;
				vY = -1;
				break;
		}
	}
	
	public void setSpace(RabbitsGrassSimulationSpace rgs){
	    Space = rgs;
	  }
	
	public void setXY(int newX, int newY){
	    x = newX;
	    y = newY;
	}
	 
	public String getID(){
	    return "A-" + ID;
	  }
	
	public int getEnergy(){
	    return Energy;
	  }
	
	public void draw(SimGraphics arg0) {
		arg0.drawFastRoundRect(Color.BLACK);
		arg0.drawImageToFit(rabbit);	
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}
	
	public void step(){
		
		if(tryMove()) {
			Energy += Space.takeGrassAt(x,y)*EnergyGainedFromGrass;
		}
		else {
			setVxVy();
		}
	    Energy -= ReducedEnergyPerMove; //Reduce energy per step for the rabbit
	  }
	
	private boolean tryMove() {
		return Space.moveAgentAt(x,y,vX,vY);
	}


}
