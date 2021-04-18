/* import table */
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Action.Delivery;
import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeAgent implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR }
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;
	
	//Checking the carried taskset
	ArrayList<Task> CarryingTask = new ArrayList<Task>();

	/* the planning class */
	Algorithm algorithm;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "BFS");
		algorithmName = algorithmName.replace("-", "");
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;
		Date start = new Date();

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			System.out.println("Plan: A*");
			plan = AstarPlan(vehicle, new ArrayList<Task>(tasks));
			System.out.println(((new Date()).getTime() - start.getTime())/1000.0 + "s");
			System.out.println(vehicle.name()+" total cost is: " + (plan.totalDistance()+vehicle.getDistance()) * vehicle.costPerKm());
			break;
		case BFS:
			// ...
			System.out.println("Plan: BFS");
			plan = bfsPlan(vehicle,new ArrayList<Task>(tasks));
			System.out.println(((new Date()).getTime() - start.getTime())/1000.0 + "s");
			System.out.println(vehicle.name()+" total cost is: " + (plan.totalDistance()+vehicle.getDistance()) * vehicle.costPerKm());
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}
	
	private Plan AstarPlan(Vehicle _vehicle, ArrayList<Task> listTasks) {
		City initCity = _vehicle.getCurrentCity();
		
		//Software Bug
		ArrayList<Task> carryingTasks = new ArrayList<Task>(_vehicle.getCurrentTasks());
		
		//Initialization of variables for algorithm
		ArrayList<State> Q = new ArrayList<State>(); 
		ArrayList<State> C = new ArrayList<State>();
		ArrayList<State> Leafs = new ArrayList<State>();
		int load = 0 ;
		if(!carryingTasks.isEmpty()) {
			for (Task task : carryingTasks) {
				load += task.weight;
			}
		}
		
		int totCost = 0; //initial cost
			
		
		State initState = new State(initCity, listTasks, carryingTasks, 
				new ArrayList<Action>() , _vehicle, totCost, load);
		initState.computeHeuristic();
		State FinalState = new State(initCity, listTasks, carryingTasks, 
				new ArrayList<Action>() , _vehicle, totCost, load);
		Q.add(initState);
		
		do {
			//Sort the Q in increasing fn
			//Collections.sort(Q, new StateComparator());
			Q.sort(new StateComparator());
			//Q retrieve the first
			State currentState = Q.get(0); // Check the first State in the list
			Q.remove(0); // Remove the checked state
			//Is it goal if yes break
			if (currentState.isLeaf()) {
				FinalState = currentState;
				//Useless
				System.out.println("Astar");
				System.out.println(FinalState.totalCost);
				break;
			}
			else {//if not find successors
				//Checking if the state is already explored
				// add to Q and C
				if(!C.contains(currentState) || 
				   (C.contains(currentState) &&
					C.get(C.indexOf(currentState)).fn > currentState.fn)) {
					
					C.add(currentState);
					ArrayList<State> NewNodes = currentState.Successors();
					NewNodes.forEach(x -> x.computeHeuristic());
					Q.addAll(NewNodes);

				}
			}	
		}
		while(!Q.isEmpty());
		
		return new Plan(initCity, FinalState.actionList);
	}
	
	private Plan bfsPlan(Vehicle _vehicle, ArrayList<Task> listTasks) {
				
		City initCity = _vehicle.getCurrentCity();
		
		//Software Bug
		ArrayList<Task> carryingTasks = new ArrayList<Task>(_vehicle.getCurrentTasks());

		//Initialization of variables for algorithm
		ArrayList<State> Q = new ArrayList<State>(); 
		ArrayList<State> C = new ArrayList<State>();
		ArrayList<State> Leafs = new ArrayList<State>();
		int load = 0 ;
		if(!carryingTasks.isEmpty()) {
			for (Task task : carryingTasks) {
				load += task.weight;
			}
		}
		
		int totCost = 0; //initial cost
			
		
		State initState = new State(initCity, listTasks, carryingTasks, 
				new ArrayList<Action>() , _vehicle, totCost, load);
		Q.add(initState);
		
		while(!Q.isEmpty()) {
			State currentState = Q.get(0); // Check the first State in the list
			Q.remove(0); // Remove the checked state
			//Check if the state is goal
			if(currentState.isLeaf()) {
				Leafs.add(currentState);
			}
			else {
				//Checking if the state is already explored
				if(!C.contains(currentState)) {				
					C.add(currentState);
					Q.addAll(currentState.Successors());
				}
				else {
					if (C.get(C.indexOf(currentState)).totalCost > currentState.totalCost){
						//Operation X
						State RemoveState_nodes = C.get(C.indexOf(currentState));
						ArrayList<State> RemoveNodes = currentState.Successors();
						Q.removeAll(RemoveNodes);
						C.remove(currentState);
						//End of operation X
						C.add(currentState);
						Q.addAll(currentState.Successors());						
					}
				}
			}	
		}
		
		return findOptimalLeaf(Leafs,initCity);
		}
	
		
	private Plan findOptimalLeaf(ArrayList<State> Leafs,City initcity) {
		// I may change if it's not compatible
		State optLeaf =  Collections.min(Leafs, Comparator.comparing(Leaf -> Leaf.getCost()));
		ArrayList<Action> optActions = optLeaf.getActions();
		Plan OptimalPlan = new Plan(initcity);
		//Useless
		System.out.println("BFS");
		System.out.println(optLeaf.totalCost);
		for (Action action : optActions) {
			OptimalPlan.append(action);
		}
		return OptimalPlan;
		}
	
	
	@Override
	public void planCancelled(TaskSet carriedTasks) {
		if (!carriedTasks.isEmpty()) {
			CarryingTask = new ArrayList<Task>(carriedTasks);	
		}
	}
}

class State{
	
	public Vehicle vehicle;
	public City currentCity;
	public ArrayList<Task> remainingTasks; //do we need labels for tasks or int is enough?
	public ArrayList<Task> carryingTasks; // we should know the packs we are carrying
	public ArrayList<Action> actionList;
	public int totalCost;
	public int load; 	
	public int fn;
	
	public State(City _currentCity, ArrayList<Task> _remainingTasks,
			ArrayList<Task> _carryingTasks, ArrayList<Action> _actionList, Vehicle _vehicle, 
			int _totalCost, int _load) {

		this.currentCity = _currentCity;
		this.remainingTasks = _remainingTasks;
		this.carryingTasks = _carryingTasks;
		this.actionList = _actionList;
		this.vehicle = _vehicle;
		this.totalCost = _totalCost;
		this.load = _load; //if load == capacity just deliver/move. load +/-= task.weight()
	}
	
	public int getCost() {
        return totalCost; //needed to find optimal plan
    }
	
	public ArrayList<Action> getActions() {
        return actionList; //needed to find optimal plan
    }
	
	public boolean isLeaf() {
		return  remainingTasks.isEmpty() && carryingTasks.isEmpty(); //what if we are carrying the final packs?
	}
	
	@Override
	public boolean equals(Object obj) {
		State state = (State) obj;
		return currentCity.equals(state.currentCity)
			&& remainingTasks.equals(state.remainingTasks)
			&& carryingTasks.equals(state.carryingTasks);
	}
	
	public ArrayList<State> Successors() {
		
		ArrayList<State> appendedNodes = new ArrayList<State>();
		List<City> neighbors = currentCity.neighbors();
		
		// reading the attributes of the current state to use for children states
		// we once copy the current state to the next one
		// check whether there is a package to pickup in the current city (and pickup), 
		//conditioning we have enough space. this is one successor
		for (Task pickupTask : remainingTasks) {
			if (pickupTask.pickupCity == currentCity && 
					vehicle.capacity() >= pickupTask.weight + load) {
				
				ArrayList<Task> parentRemainingTasks=new ArrayList<Task>(remainingTasks);
				ArrayList<Task> parentCarryingTasks= new ArrayList<Task>(carryingTasks);
				ArrayList<Action> parentactionList =new ArrayList<Action>(actionList);
				parentCarryingTasks.add(pickupTask);
				parentRemainingTasks.remove(pickupTask);
				parentactionList.add(new Pickup(pickupTask));
				State NewState = new State(currentCity,parentRemainingTasks,
						parentCarryingTasks,parentactionList,
						vehicle,totalCost,load+pickupTask.weight		
						);
				appendedNodes.add(NewState);
			}
		}
		
		for (City neighbor:neighbors) { //loop over all the current city neighbors. 
			//if we move to a city and it's a delivery city, we drop the package automatically
			ArrayList<Task> parentRemainingTasks=new ArrayList<Task>(remainingTasks);
			ArrayList<Task> parentCarryingTasks= new ArrayList<Task>(carryingTasks);
			ArrayList<Action> parentactionList =new ArrayList<Action>(actionList);
			State dummyState = new State(currentCity, parentRemainingTasks, parentCarryingTasks, parentactionList,
					vehicle, totalCost, load); // every time the state should be initialized to parent to avoid accumulating changes
			dummyState.actionList.add(new Move(neighbor)); //update plan with move
			dummyState.totalCost += dummyState.currentCity.distanceTo(neighbor)*vehicle.costPerKm(); //add the cost of moving
			dummyState.currentCity = neighbor;
			ArrayList<Task> Dummy = new ArrayList<Task>(dummyState.carryingTasks);
			for (Task deliverTask : Dummy) { //we deliver all the packages related to this city at once
				if (deliverTask.deliveryCity == dummyState.currentCity) {
					dummyState.carryingTasks.remove(deliverTask);
					dummyState.load -= deliverTask.weight;
					dummyState.actionList.add(new Delivery(deliverTask));
				}
			}
			appendedNodes.add(dummyState);
		}
		
		return appendedNodes;
		}
	
	public void computeHeuristic() {
	    int fn_update = totalCost;
	    int PenaltyRemainingTask = 600;
	    int PenaltyCarryingTask = 300;

	    int predCost = remainingTasks.size()*PenaltyRemainingTask + carryingTasks.size()*PenaltyCarryingTask;
	    
	    fn_update += predCost; // we update the total cost to the
	    // currentState.totalCost = fn;
	    fn = fn_update;
	  }
	
}

class StateComparator implements Comparator<State> {
	
	@Override
	public int compare(State s1, State s2) {
		return s1.fn - s2.fn ;
	}

}


