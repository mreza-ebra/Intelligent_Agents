/**
 * Class that implements the simulation space of the rabbits grass simulation.
 * @author MohammadReza Ebrahimi, Salar Rahimi 
 */

import uchicago.src.sim.space.Object2DGrid;


public class RabbitsGrassSimulationSpace {
	
	private Object2DGrid FieldSpace;
	private Object2DGrid agentSpace;
	private int GridSize;
	public RabbitsGrassSimulationSpace(int _GridSize) {
		GridSize = _GridSize;
		FieldSpace = new Object2DGrid(_GridSize,_GridSize);
		agentSpace = new Object2DGrid(_GridSize, _GridSize);
		for(int i = 0; i < _GridSize; i++){
		      for(int j = 0; j < _GridSize; j++){
		        FieldSpace.putObjectAt(i,j,new Integer(0));
		  }
	    }	
	}
	
	
	  @SuppressWarnings("deprecation")
	public void spreadGrass(int grass){
		    // Randomly place money in moneySpace
		    for(int i = 0; i < grass; i++){

		      // Choose coordinates
		      int x = (int)(Math.random()*(FieldSpace.getSizeX()));
		      int y = (int)(Math.random()*(FieldSpace.getSizeY()));

		      // Get the value of the object at those coordinates
		      // Get the value of the object at those coordinates
		      int currentValue = getGrassAt(x, y);
		      // Replace the Integer object with another one with the new value
		      FieldSpace.putObjectAt(x,y,new Integer(currentValue + 1));
		    }
      } 
	  
	  public int getGrassAt(int x, int y){
		    int i;
		    if(FieldSpace.getObjectAt(x,y)!= null){
		      i = ((Integer)FieldSpace.getObjectAt(x,y)).intValue();
		    }
		    else{
		      i = 0;
		    }
		    return i;
	}
	  public Object2DGrid getCurrentFieldSpace(){
		    return FieldSpace;
      }
	  
	  public Object2DGrid getCurrentAgentSpace(){
		    return agentSpace;
      }
	  
	  public boolean isCellOccupied(int x, int y){
		    boolean retVal = false;
		    if(agentSpace.getObjectAt(x, y)!=null) retVal = true;
		    return retVal;
	 }
	  
	  public boolean addAgent(RabbitsGrassSimulationAgent agent){
		    boolean retVal = false;
		    int count = 0;
		    int countLimit = 10 * agentSpace.getSizeX() * agentSpace.getSizeY();

		    while((retVal==false) && (count < countLimit)){
		      int x = (int)(Math.random()*(agentSpace.getSizeX()));
		      int y = (int)(Math.random()*(agentSpace.getSizeY()));
		      if(isCellOccupied(x,y) == false){
		        agentSpace.putObjectAt(x,y,agent);
		        agent.setXY(x,y);
		        agent.setSpace(this);
		        retVal = true;
		      }
		      count++;
		    }

		    return retVal;
		 }
	  
	  public void removeAgentAt(int x, int y){
		    agentSpace.putObjectAt(x, y, null);
	  }
	  
	  public int takeGrassAt(int x, int y){
		    int Grass = getGrassAt(x, y);
		    FieldSpace.putObjectAt(x, y, new Integer(0));
		    return Grass;
		  }
	  public boolean moveAgentAt(int x,int y,int vX,int vY) {
		  boolean retVal = false;
		  int newX = x+vX;
		  int newY = y+vY;
		  if (newX > GridSize-1) {
			  newX = 0;
		  }
		  else if(newX < 0) {
			  newX = GridSize-1;
		  }
		  if (newY > GridSize-1) {
			  newY = 0;
		  }
		  else if(newY < 0) {
			  newY = GridSize-1;
		  }
		  if(!isCellOccupied(newX,newY)) {
			  RabbitsGrassSimulationAgent rga = (RabbitsGrassSimulationAgent)agentSpace.getObjectAt(x,y);
			  removeAgentAt(x, y);
			  rga.setXY(newX, newY);
			  agentSpace.putObjectAt(newX, newY, rga);
		      retVal = true;
		  }
		  return retVal;
	  }
	  
	  public int getTotalGrass(){
		    int totalGrass = 0;
		    for(int i = 0; i < FieldSpace.getSizeX(); i++){
		      for(int j = 0; j < FieldSpace.getSizeY(); j++){
		    	  if(getGrassAt(i, j) != 0) {
		    		  totalGrass += 1;
		    	  }
		      }
		    }
		    return totalGrass;
		  }
}
