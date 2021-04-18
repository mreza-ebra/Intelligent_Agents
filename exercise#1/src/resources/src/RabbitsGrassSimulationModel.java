import java.awt.Color;
import java.util.ArrayList;

import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimModelImpl;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.gui.Value2DDisplay;
import uchicago.src.sim.util.SimUtilities;

//Adding Chart
import uchicago.src.sim.analysis.DataSource;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.analysis.Sequence;





/**
 * Class that implements the simulation model for the rabbits grass
 * simulation.  This is the first class which needs to be setup in
 * order to run Repast simulation. It manages the entire RePast
 * environment and the simulation.
 *
 * @author MohammadReza Ebrahimi, Salar Rahimi 
 */


public class RabbitsGrassSimulationModel extends SimModelImpl {		

		public static void main(String[] args) {
			
			System.out.println("Rabbit skeleton");

			SimInit init = new SimInit();
			RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
			// Do "not" modify the following lines of parsing arguments
			if (args.length == 0 || args.length == 1) // by default, you don't use parameter file nor batch mode 
				init.loadModel(model, "", false);
			else
				init.loadModel(model, args[0], Boolean.parseBoolean(args[1]));
		}
		
		private RabbitsGrassSimulationSpace Space;
		
		private DisplaySurface displaySurf;
		
		private OpenSequenceGraph StatInSpace;
		
		class GrassInSpace implements DataSource, Sequence {

		    public Object execute() {
		      return new Double(getSValue());
		    }

		    public double getSValue() {
		      return (double)Space.getTotalGrass();
		    }
		  }
		
		class RabbitInSpace implements DataSource, Sequence {

		    public Object execute() {
		      return new Double(getSValue());
		    }

		    public double getSValue() {
		      return (double)countLivingAgents();
		    }
		  }
		
		private ArrayList<RabbitsGrassSimulationAgent> agentList; 
		
		public void setup() {
			System.out.println("Running setup");	
			Space = null;
			agentList = new ArrayList();
			schedule = new Schedule(1);
			// Tear down Displays
			if (displaySurf != null){
			      displaySurf.dispose();
		    }
		    displaySurf = null;
		    if (StatInSpace != null){
		        StatInSpace.dispose();
		      }
		    StatInSpace = null;

		      // Create Displays
		    displaySurf = new DisplaySurface(this, "Rabbit Grass Model Window");
		    StatInSpace = new OpenSequenceGraph("Amount Of Grass and Rabbit In Space",this);

		    registerDisplaySurface("Rabbit Grass Model Window", displaySurf);
		    this.registerMediaProducer("Plot", StatInSpace);
		}

		public void begin() {
			buildModel();
			buildSchedule();
			buildDisplay();
			displaySurf.display();
			StatInSpace.display();
		}
	
		
		public void buildModel(){
			System.out.println("Running BuildModel");
			Space = new RabbitsGrassSimulationSpace(GridSize);
			Space.spreadGrass(NumInitGrass);
			
			for(int i = 0; i < NumInitRabbits; i++){
			      addNewAgent();
			    }
			
			
			for(int i = 0; i < agentList.size(); i++){
				RabbitsGrassSimulationAgent cda = (RabbitsGrassSimulationAgent)agentList.get(i);
			    }
		}
			
		private void addNewAgent(){
			RabbitsGrassSimulationAgent a = new RabbitsGrassSimulationAgent(InitialEnergy, BirthThreshold,ReducedEnergyPerMove
												,EnergyGainedFromGrass);
		    agentList.add(a);
		    Space.addAgent(a);
		  }

	    public void buildSchedule(){
	    	System.out.println("Running BuildSchedule");
	    	
	    	class RabbitStep extends BasicAction {
	    	      public void execute() {
	    	        SimUtilities.shuffle(agentList);
	    	        for(int i =0; i < agentList.size(); i++){
	    	          RabbitsGrassSimulationAgent rga = (RabbitsGrassSimulationAgent)agentList.get(i);
	    	          rga.step();
	    	        }
	    	        
	    	        reapDeadAgents();
	    	        int NewAgents = Reproduction(); //Checks the energy of each agent for reproducing
	    	        for(int i=0; i < NewAgents; i++) {
	    	        	addNewAgent();
	    	        }
	    	        
	    	        Space.spreadGrass(GrassGrowthRate);
	    	        displaySurf.updateDisplay();
	    	        
	    	      }
	    	    }

	    	schedule.scheduleActionBeginning(0, new RabbitStep());
	    	
	    	class UpdateStatInSpace extends BasicAction {
	    	      public void execute(){
	    	        StatInSpace.step();
	    	      }
	    	    }

	    	schedule.scheduleActionAtInterval(5, new UpdateStatInSpace());
	    	
	    	
	    	  
	      }
	    
	    private void reapDeadAgents(){
	    	
	        for(int i = (agentList.size() - 1); i >= 0 ; i--){
	          RabbitsGrassSimulationAgent rga = (RabbitsGrassSimulationAgent)agentList.get(i);
	          if(rga.Energy <= 0){
	            Space.removeAgentAt(rga.getX(), rga.getY());
	            agentList.remove(i);
	          }
	        }
	      }
	    
	    private int Reproduction() {
	    	int count = 0;
	    	
	    	for(RabbitsGrassSimulationAgent rabbit : agentList) {
	    		if(rabbit.Energy >= BirthThreshold) {
	    			count++;
	    			rabbit.Energy-=ReducedEnergyPerReproduce;
	    		}
	    	}
	    	return count;
	    }
	    
	    private int countLivingAgents(){
	        int livingAgents = 0;
	        for(int i = 0; i < agentList.size(); i++){
	          RabbitsGrassSimulationAgent rga = (RabbitsGrassSimulationAgent)agentList.get(i);
	          if(rga.Energy > 0) livingAgents++;
	        }

	        return livingAgents;
	      }

	    public void buildDisplay(){
	    	System.out.println("Running BuildDisplay");
	    	ColorMap map = new ColorMap();

	        for(int i = 1; i<16; i++){
	          map.mapColor(i, new Color(0,(int)(i * 8 + 127), 0));
	        }
	        map.mapColor(0, Color.black);

	        Value2DDisplay displayGrass =
	            new Value2DDisplay(Space.getCurrentFieldSpace(), map);
	        
	        Object2DDisplay displayAgents = new Object2DDisplay(Space.getCurrentAgentSpace());
	        displayAgents.setObjectList(agentList);
	        displaySurf.addDisplayable(displayGrass, "Grass");
	        displaySurf.addDisplayable(displayAgents, "Rabbits");
	        StatInSpace.addSequence("Grass In Space", new GrassInSpace());
	        StatInSpace.addSequence("Rabbits in Space", new RabbitInSpace());
		  }
	    //Parameters
	    private int NumInitGrass = 20;
	    public int getNumInitGrass() {
	    	return NumInitGrass;
	    }
	    public void setNumInitGrass(int _NumInitGrass) {
	    	NumInitGrass = _NumInitGrass;
	    }
	    
	    private int NumInitRabbits = 20;
	    public int getNumInitRabbits() {
	    	return NumInitRabbits;
	    }
	    public void setNumInitRabbits(int _NumInitRabbits) {

	    	NumInitRabbits = _NumInitRabbits;
	    }
	    
	    private int InitialEnergy = 100;
	    public int getInitialEnergy() {
	    	return InitialEnergy;
	    }
	    public void setInitialEnergy(int _InitialEnergy) {

	    	InitialEnergy = _InitialEnergy;
	    }
	    
	    private int BirthThreshold = 120;
	    public int getBirthThreshold() {
	    	return BirthThreshold;
	    }
	    public void setBirthThreshold(int _BirthThreshold) {

	    	BirthThreshold = _BirthThreshold;
	    }
	    
	    private int ReducedEnergyPerMove = 5;
	    public int getReducedEnergyPerMove() {
	    	return ReducedEnergyPerMove;
	    }
	    public void setReducedEnergyPerMove(int _ReducedEnergyPerMove) {

	    	ReducedEnergyPerMove = _ReducedEnergyPerMove;
	    }
	    
	    private int ReducedEnergyPerReproduce = 40;
	    public int getReducedEnergyPerReproduce() {
	    	return ReducedEnergyPerReproduce;
	    }
	    public void setReducedEnergyPerReproduce(int _ReducedEnergyPerReproduce) {

	    	ReducedEnergyPerReproduce = _ReducedEnergyPerReproduce;
	    }
	    
	    private int EnergyGainedFromGrass = 20;
	    public int getEnergyGainedFromGrass() {
	    	return EnergyGainedFromGrass;
	    }
	    public void setEnergyGainedFromGrass(int _EnergyGainedFromGrass) {
	    	EnergyGainedFromGrass = _EnergyGainedFromGrass;
	    }
	    
	    private int GrassGrowthRate = 5; 
	    public int getGrassGrowthRate() {
	    	return GrassGrowthRate;
	    }
	    public void setGrassGrowthRate(int _GrassGrowthRate) {
	    	GrassGrowthRate = _GrassGrowthRate;
	    }
	    
	    private int GridSize = 20;   
	    public int getGridSize() {
	    	return GridSize;
	    }
	    public void setGridSize(int _GridSize) {
	    	GridSize = _GridSize;
	    }
	    
	    
		public String[] getInitParam() {
			// TODO Auto-generated method stub
			// Parameters to be set by users via the Repast UI slider bar
			// Do "not" modify the parameters names provided in the skeleton code, you can add more if you want 
			String[] params = { "GridSize", "NumInitRabbits", "NumInitGrass", "GrassGrowthRate",
					"BirthThreshold", "EnergyGainedFromGrass", "ReducedEnergyPerReproduce", "ReducedEnergyPerMove",
					"InitialEnergy"};
			return params;
		}
		
		//endParameters

		public String getName() {
			// TODO Auto-generated method stub
			String NameOfSimulation = "A Rabbits Grass Simulation";
			return NameOfSimulation;
		}
		
		
		private Schedule schedule;
		
		public Schedule getSchedule() {
			// TODO Auto-generated method stub	
			return schedule;
		}


}
