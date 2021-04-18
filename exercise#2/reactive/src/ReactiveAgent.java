import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;




public class ReactiveAgent implements ReactiveBehavior {

	private Random random;
	private double Discount;
	private int numActions;
	private Agent myAgent;
	
	private Topology Map;
	private TaskDistribution TD;
	
	public HashMap<State, Boolean> BestAction;


	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);
		
		Map = topology;
		TD = td;

		this.random = new Random();
		this.Discount = discount;
		this.numActions = 0;
		this.myAgent = agent;
		
		BestAction = ComputeRLA();
	}
	
	//This part we implement RLA
	
	@SuppressWarnings("unchecked")
	public HashMap<State, Boolean> ComputeRLA() {
		//Generating all the possible States 
		ArrayList<State> AllStates = new ArrayList<State>();
		for(City cityI : Map.cities()) {
			for(City cityJ : Map.cities()) {
				if(cityI != cityJ) {
					AllStates.add(new State(cityI,cityJ));
				}
			}
		}
		//Defining the elements and initializing them
		HashMap<State, Boolean> bestAction = new HashMap<State, Boolean>();
		HashMap<State, Double> V = new HashMap<State, Double>();
		HashMap<State, Double> PreviousV = new HashMap<State, Double>();
		HashMap<State,HashMap<Boolean,Double>> Q = new HashMap<State,HashMap<Boolean,Double>>();
		
		
		for(State state : AllStates) {
			V.put(state, 0.0);
			bestAction.put(state, false); //false is for move and true is for pickup
			HashMap<Boolean, Double> Tempo = new HashMap<Boolean, Double>();
			Tempo.put(false, 0.0);
			Tempo.put(true, 0.0);
			Q.put(state, Tempo); //why Tempo?
		}
		//start of the iteration
		ArrayList<String> actions = new ArrayList<String>();
		actions.add("move");
		actions.add("pick");
		
		do {
			
			PreviousV = (HashMap<State, Double>) V.clone();
			for (State state : AllStates) {
				HashMap<Boolean,Double> ValueForAction = new HashMap<Boolean, Double>();
				for (String action : actions) {
					boolean BoolAction = false;
					double Q_Value = 0.0;
					// just call QCalculator
					if(action == "pick") { // no need to call Q inside
						Q_Value = QCalculator(state, action, V);
						BoolAction = true;		
					}
					else {
						Q_Value = QCalculator(state, action, V);
						BoolAction = false;
					}
					ValueForAction.put(BoolAction, Q_Value);
				}
				//Final Assignments
				Q.get(state).putAll(ValueForAction);
				V.put(state,Math.max(ValueForAction.get(false), ValueForAction.get(true))); //max?
				bestAction.put(state, WhatIsBestMove(ValueForAction));
			}
		} 
		while(!Stopping(V,PreviousV));
		
		
		return bestAction;
	
	}
	
	//finding the best move
	public boolean WhatIsBestMove(HashMap<Boolean,Double> PossibleMoves) {
		
		double False_value = PossibleMoves.get(false);
		double True_value = PossibleMoves.get(true);
		
		if(True_value >= False_value) {
			return true;
		}
		else {
			return false;
		}
	}
	
	//Good enough function 
	public boolean Stopping(HashMap<State, Double> V, HashMap<State, Double> PreviousV) {
		double epsilon = 1E-20;
		double diff = 0.0;
		
		Double[] v = V.values().toArray(new Double[V.values().size()]);
		Double[] oldv = PreviousV.values().toArray(new Double[PreviousV.values().size()]);
		
		for (int i = 0; i < V.values().size(); i++) {
			diff += v[i] - oldv[i];
		}
		
		return diff <= epsilon;
	}
	
	
	//Q calculator
	public double QCalculator(State state, String action, HashMap<State, Double> V) {
		double Q = 0.0;
		double reward = 0.0;
		double probability_sum = 0.0;
		Vehicle vehicle = myAgent.vehicles().get(0); //vehicle is the first object
		
		if(action == "pick") {
			//Reward is calculated
			reward = TD.reward(state.cityI, state.cityJ) 
					- state.cityI.distanceTo(state.cityJ)*vehicle.costPerKm();
			//Calculation for the new state needs to be done.
			//First Step is to create state primes
			ArrayList<State> SPrimes = new ArrayList<State>(); //maybe better to add it to top
			//The possible options after agent moved to new city
			for (City city : Map.cities()) {
				if(state.cityJ != city) {
					SPrimes.add(new State(state.cityJ,city));
				}
			}
			//Now the probability element should be defined
			for (State Sprime : SPrimes) {
				probability_sum += TD.probability(Sprime.cityI, Sprime.cityJ)*V.get(Sprime);
			}
			Q = reward + Discount*probability_sum;	
		}
		else { //Move condition
			//Reward is calculated
			//No reward for delivery, but punishment for move
			//Because next move is selected randomly, we consider same probability
			//and average the possible cities
			List<City> Neighbors = state.cityI.neighbors();
			for (City neighbor : Neighbors) {
				reward += - state.cityI.distanceTo(neighbor)*vehicle.costPerKm();
			}
			reward = reward / Neighbors.size(); //average
			//Create State
			ArrayList<State> SPrimes = new ArrayList<State>();
			for (City neighbor : Neighbors) {
				for (City cityJ:Map.cities()) {
					if(neighbor != cityJ) {
						SPrimes.add(new State(neighbor,cityJ));
					}
				}
			}
			//Now the probability element should be defined
			for (State Sprime : SPrimes) {
				probability_sum += TD.probability(Sprime.cityI, Sprime.cityJ)*V.get(Sprime);
			}
			probability_sum = probability_sum/Neighbors.size();
			
			Q = reward + Discount*probability_sum;
		}
		
		
		return Q;
	}
	

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		boolean bestaction = false; //move
		if(availableTask != null) {
			State state = new State(availableTask.pickupCity,availableTask.deliveryCity);
			bestaction = BestAction.get(state);
		}

		if (availableTask == null || !bestaction) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
<<<<<<< HEAD
			if (availableTask != null) {
//				System.out.println("The Task between "+availableTask.pickupCity+" and  "+availableTask.deliveryCity+" is not picked up");
			}
			else {
//				System.out.println("No Task");
			}
		} else {
			action = new Pickup(availableTask);
//			System.out.println("The Task between "+availableTask.pickupCity+" and  "+availableTask.deliveryCity+" is picked up");
=======
		} else {
			action = new Pickup(availableTask);
			//System.out.println("The Task between "+availableTask.pickupCity+" and  "+availableTask.deliveryCity+" is picked up");
>>>>>>> 64b4a7ece3e0a622621bf08baf35c66aa0cff9f0
		}
		
		if (numActions >= 1) {
//			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
}


class State{
	public City cityI;
	public City cityJ;
	
	public State(City _cityI,City _cityJ) {
		cityI = _cityI;
		cityJ = _cityJ;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null) return false;
		State s = (State) obj;
		if (s == this) return true;
		//return this.currentCity.equals(s.currentCity) && this.packetDestination.equals(s.packetDestination);
		return s.toString().equals(this.toString());
		
	}
	
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
	
	@Override
	public String toString() {
		return "(" + this.cityI + " → " + this.cityJ + ")";
	}
	

}
