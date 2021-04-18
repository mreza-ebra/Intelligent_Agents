

//the list of imports
import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.DirectoryStream.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
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


/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedAgent implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private double p = 0.4;
    private List<Plan> FailSafe = new ArrayList<Plan>();
    private ArrayList<PlanDetails> FailSafe_objects = new ArrayList<PlanDetails>();

    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config" + File.separator + "settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);

        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }
    
    //Select the plans as action from details
    public List<Plan> GetPlansFromDetails(ArrayList<PlanDetails> Details) {
    	
    	List<Plan> Plans = new ArrayList<Plan>();
    	for (PlanDetails Detail : Details) {
    		Plans.add(Detail.ActionList);
    	}
    	return Plans;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks){
        long time_start = System.currentTimeMillis();       
        //Checking if the task are all smaller than the capacity of the car
        for (Vehicle vehicle : vehicles) {
        	for(Task task : tasks) {
        		if(vehicle.capacity() < task.weight) {
        			System.err.println("At least one task is too big for the biggest capacity's vehicle!");
        			System.exit(-1);
        		}
        	}
        }
        //Here we implement SLS Algorithm to find the optimal plan
        //Setting the LimitIteration
        int LimitIterations = 10000;
        if(vehicles.size() < 3 || tasks.size() < 30) {
        	LimitIterations -= Math.abs((vehicles.size()-3)*(tasks.size()-30)*100);
        }
        else {
        	LimitIterations += Math.abs((vehicles.size()-3)*(tasks.size()-30)*100);
        }
        
        //List of plan is defined as bellow it is defined as A
        ArrayList<PlanDetails> Details = initialListPlan(vehicles,tasks);
        boolean Cond = VerifyListPlan(Details, vehicles, tasks);
        List<Plan> A = GetPlansFromDetails(Details);
        FailSafe = A;
        int Iterations = 0;
        do {
        	//Checking time to come out of it when near to exception and return the fails safe value
        	if ((System.currentTimeMillis()-time_start) > timeout_plan - 500) {
        		A = FailSafe;
        		System.out.println("TimeOut");
        		break;
        	}
        	List<Plan> Aold = A;
        	ArrayList<PlanDetails> DetailsOld = Details;
        	HashMap<ArrayList<PlanDetails>, Double> N = ChooseNeighbors(Aold,vehicles,DetailsOld,tasks);
        	Details = LocalChoice(N,DetailsOld);
        	A = GetPlansFromDetails(Details);
        	Iterations++;
        }
        while(Iterations < LimitIterations); 
//		
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");
        
        return A;
    }
    
    ////SelectInitialSoluton
    private ArrayList<PlanDetails> initialListPlan(List<Vehicle> vehicles, TaskSet tasks){
    	
    	ArrayList<PlanDetails> InitialPlan = new ArrayList<PlanDetails>();
    	//This function produce an initialization for the first list of plans for vehicles
    	TaskSet TaskSetCopy = tasks.clone();
    	HashMap<Vehicle, ArrayList<Task>> TaskAssignment = new HashMap<Vehicle, ArrayList<Task>>();
    	for (Vehicle vehicle : vehicles) {
    		TaskAssignment.put(vehicle, new ArrayList<Task>());
    	}
    	//We assign tasks to the nearest vehicle relative to them
    	for (Task task : tasks) {
    		ArrayList<Double> distances = new ArrayList<Double>();
    		for (Vehicle vehicle : vehicles) {
    			distances.add(vehicle.homeCity().distanceTo(task.pickupCity));
    		}
    		Vehicle closest_vechicle = 
    				vehicles.get(distances.indexOf(Collections.min(distances)));
    		ArrayList<Task> Selected = TaskAssignment.get(closest_vechicle);
    		Selected.add(task);
    		TaskAssignment.put(closest_vechicle, Selected);
    	}
    	//Plan the assigned tasks for each vehicle
    	for (Vehicle vehicle : vehicles) {
    		//Plan
    		Plan VehiclePlan = new Plan(vehicle.getCurrentCity());
    		PlanDetails DetailsPlan = new PlanDetails(vehicle,TaskAssignment.get(vehicle));
    		DetailsPlan.InitialPlan();
    		InitialPlan.add(DetailsPlan);
    	}
        
        return InitialPlan;	
    }
    
    
    //Neighboring selection
    private HashMap<ArrayList<PlanDetails>, Double> ChooseNeighbors(List<Plan> Aold,
    			List<Vehicle> vehicles,ArrayList<PlanDetails> DetailsOld, TaskSet tasks){
    	
    	//This method generates new Leaf nodes
    	HashMap<ArrayList<PlanDetails>, Double> NWithCost = new HashMap<ArrayList<PlanDetails>, Double>();
    	ArrayList<ArrayList<PlanDetails>> N = new ArrayList<ArrayList<PlanDetails>>();
    	N.add(DetailsOld);
    	//Select a vehicle randomly for re ordering the task
    	PlanDetails SelectedVehicle = new PlanDetails(vehicles.get(0),new ArrayList<>(tasks));
    	int random = 0;
    	do {
    		random = (int)Math.floor(Math.random()*vehicles.size());
    		SelectedVehicle = DetailsOld.get(random);
    	}
    	while(SelectedVehicle.ActionInfoList.isEmpty());
    	//Now We do the ordering 
    	//First Neighbor Generator : Reordering the tasks
    	ArrayList<ArrayList<PlanDetails>> N_Reording = Reorder(DetailsOld,SelectedVehicle,tasks,vehicles);
    	N.addAll(N_Reording);
    	//Second Neighbor Generator : Exchange tasks between vehicle
    	ArrayList<ArrayList<PlanDetails>> N_Exchanging = new ArrayList<ArrayList<PlanDetails>>();
    	for(PlanDetails SinglePlan : DetailsOld) {
    		if(SinglePlan.vehicle != SelectedVehicle.vehicle) {
    			N_Exchanging.addAll(TaskExchanger(DetailsOld,tasks,SinglePlan,SelectedVehicle,vehicles));
    		}
    	}
    	N.addAll(N_Exchanging);
    	
    	for(ArrayList<PlanDetails> Plans : N) {
    		
    		NWithCost.put(Plans, CostPlan(Plans));
    	}
    	
    	
    	return NWithCost;
    }
      
    private ArrayList<ArrayList<PlanDetails>> Reorder(ArrayList<PlanDetails> Old,PlanDetails SelectedPlan,TaskSet tasks,List<Vehicle> vehicles){
    	
    	ArrayList<ArrayList<PlanDetails>> N_ordering = new ArrayList<ArrayList<PlanDetails>>();
    	
    	for (ActionInfo a1 : SelectedPlan.ActionInfoList) {
			for (ActionInfo a2 : SelectedPlan.ActionInfoList) {
				if (!a1.equals(a2)) {
					
					ArrayList<PlanDetails> N = (ArrayList<PlanDetails>) Old.clone();
					
					PlanDetails newPlanForSelectedVehicle = new PlanDetails(SelectedPlan.vehicle,SelectedPlan.AllAssignedTasks);
					
					ArrayList<ActionInfo> actionList = (ArrayList<ActionInfo>)SelectedPlan.ActionInfoList.clone();
					
					int indexT1 = actionList.indexOf(a1);
					int indexT2 = actionList.indexOf(a2);
					
					actionList.remove(a1);
					actionList.remove(a2);
					
					if (indexT1 < indexT2) {
						actionList.add(indexT1, a2);
						actionList.add(indexT2, a1);
					} else {
						actionList.add(indexT2, a1);
						actionList.add(indexT1, a2);
					}
					
					int index = N.indexOf(SelectedPlan);
					N.remove(index);
					newPlanForSelectedVehicle.ActionInfoList = actionList;
					newPlanForSelectedVehicle.planning();
					N.add(index,newPlanForSelectedVehicle);
					
					
					// We only keep the plan if it satisfies the constraints and is better than the current solution
					if (VerifyListPlan(N, vehicles, tasks) ) {
						N_ordering.add(N);
					}
			
				}
			}
    	}

    	return N_ordering;
    }
    
    private ArrayList<ArrayList<PlanDetails>> TaskExchanger(ArrayList<PlanDetails> Old,TaskSet tasks,PlanDetails VehicleJ,PlanDetails VehicleI_real,List<Vehicle> vehicles){
    	
    	ArrayList<ArrayList<PlanDetails>> N_exchanging = new ArrayList<ArrayList<PlanDetails>>();
    	ArrayList<ActionInfo> ActionVehicleI = (ArrayList<ActionInfo>) VehicleI_real.ActionInfoList.clone();
    	//Only the first task should be changed
    	ActionInfo FirstTaskPickUp = ActionVehicleI.get(0);
    	ActionInfo FirstTaskDelivery = new ActionInfo(FirstTaskPickUp.task, "delivery");
    	ActionVehicleI.remove(FirstTaskDelivery);
    	ActionVehicleI.remove(FirstTaskPickUp);
    	ArrayList<Task> Modified_vi = (ArrayList<Task>) VehicleI_real.AllAssignedTasks.clone();
		Modified_vi.remove(FirstTaskPickUp.task);
    	PlanDetails VehicleI = new PlanDetails(VehicleI_real.vehicle,Modified_vi);
    	VehicleI.ActionInfoList = ActionVehicleI;
    	VehicleI.planning();
    		
    	if(!VehicleJ.ActionInfoList.isEmpty()) {
	    	for(int actionIndex1 = 0; actionIndex1 < VehicleJ.ActionInfoList.size(); actionIndex1++) {
				
	    		for(int actionIndex2 = 0; actionIndex2 < VehicleJ.ActionInfoList.size(); actionIndex2++) {
	    			
	    			if(actionIndex1 != actionIndex2) {
	    				ArrayList<PlanDetails> N = (ArrayList<PlanDetails>) Old.clone();
	    				ArrayList<Task> Modified = (ArrayList<Task>) VehicleJ.AllAssignedTasks.clone();
	    				Modified.add(FirstTaskPickUp.task);
	    				PlanDetails newPlanForSelectedVehicle = new PlanDetails(VehicleJ.vehicle,Modified);
	    				ArrayList<ActionInfo> actionList = (ArrayList<ActionInfo>)VehicleJ.ActionInfoList.clone();
	    				
	    				if (actionIndex1 < actionIndex2) {
							actionList.add(actionIndex1, FirstTaskPickUp);
							actionList.add(actionIndex2, FirstTaskDelivery);
						} else {
							actionList.add(actionIndex2, FirstTaskPickUp);
							actionList.add(actionIndex1, FirstTaskDelivery);
						}
						
	    				int indexVi = N.indexOf(VehicleI_real);
						//N.remove(indexVi);
						//N.add(indexVi,VehicleI);
	    				N.set(indexVi, VehicleI);
						
						int indexVj = N.indexOf(VehicleJ);
						//N.remove(indexVj);
						newPlanForSelectedVehicle.ActionInfoList = actionList;
						newPlanForSelectedVehicle.planning();
						N.set(indexVj, newPlanForSelectedVehicle);
						//N.add(indexVj,newPlanForSelectedVehicle);
						
						
						// We only keep the plan if it satisfies the constraints and is better than the current solution
						if (VerifyListPlan(N, vehicles, tasks) ) {
							N_exchanging.add(N);
						}			
	    			}			
	    		}		
			}
	    	return N_exchanging;
    	}
    	else {
    		return new ArrayList<ArrayList<PlanDetails>>();
    	}
    }
      
    private boolean VerifyListPlan(ArrayList<PlanDetails> NewPlan, List<Vehicle> vehicles, TaskSet tasks) {


		 //We only accept if the vehicle can carry the tasks, at any moment		
    	
    	for(PlanDetails PlanVehicle : NewPlan) {
    		double carriedWeight = 0;
    		for (ActionInfo action: PlanVehicle.ActionInfoList) {
				
				if (action.actionType.equals("pickup")) {
					carriedWeight += action.task.weight;
				} else {
					carriedWeight -= action.task.weight;
				}
	
				if (carriedWeight > PlanVehicle.capacity) {
					return false;
				}
			}
    	}
		
		
    	ArrayList<Task> AvailableTasks = new ArrayList<Task>(tasks);
		 //Pickups actions of a task must be before corresponding deliveries, all picked up tasks must be delivered and all tasks available must be picked up.
		for(PlanDetails PlanVehicle : NewPlan) {
			ArrayList<Task> stack = new ArrayList<Task>();
			
			for(ActionInfo action : PlanVehicle.ActionInfoList) {
				
				if (action.actionType.equals("pickup")) {
					stack.add(action.task);
					AvailableTasks.remove(action.task);
				} else if (action.actionType.equals("delivery")) {
					if (!stack.remove(action.task)) return false;
				} else {
					System.err.println("[Error] verifyConstraints(): some action is neither a pickup nor a delivery action.");
				}
				
			}
			
			// All picked up tasks must be delivered
			if (!stack.isEmpty()) return false;
			
		}
		
		if(!AvailableTasks.isEmpty()) return false;
		
		return true;
			
	}
    
    private ArrayList<PlanDetails> LocalChoice(HashMap<ArrayList<PlanDetails>, Double> N,ArrayList<PlanDetails> DetailsOld){
    	ArrayList<Double> Costs = new ArrayList<Double>(N.values());
		ArrayList<ArrayList<PlanDetails>> N_dumm = new ArrayList<ArrayList<PlanDetails>>();
		N_dumm.addAll(N.keySet());
		
    	if(Math.random() < p) {
    		
    		double minCost = Collections.min(Costs);
    		int size_dumm = N_dumm.size();
    		for(int i=0;i<size_dumm;i++) {
    			if(N.get(N_dumm.get(i)) != minCost) {
    				N_dumm.remove(i);
    				size_dumm -=1;
    				i--;
    			}
    			
    		}
    		
    		if(N_dumm.size() == 1) {
    			return FailSafeHandler(N_dumm.get(0),DetailsOld);
    		}
    		else {
    			int Random = (int) Math.floor(Math.random()*N_dumm.size());
    			return FailSafeHandler(N_dumm.get(Random),DetailsOld);
    		}	
    	}
    	else {
    		return DetailsOld;
    	}
    }
    
    
    private double CostPlan(ArrayList<PlanDetails> Plans) {
    	double OverallCost = 0;
    	for(PlanDetails SinglePlan : Plans) {
    		OverallCost += SinglePlan.cost;
    	}
    	return OverallCost;
    }
    
    //FailsafeHandler
    public ArrayList<PlanDetails> FailSafeHandler(ArrayList<PlanDetails> ANew,ArrayList<PlanDetails> Aold){
    	
    	if(CostPlan(ANew) < CostPlan(Aold)) {
    		FailSafe = GetPlansFromDetails(ANew);
    		FailSafe_objects = ANew;
    	}
    	return ANew;
    	
    }


}

class ActionInfo {
	
	public Task task;
	public  String actionType;
	public City city;
	
	public ActionInfo(Task task, String type) {
		this.task = task;
		actionType = type;
		if (actionType.equals("pickup")) {
			city = this.task.pickupCity;
		} else if (actionType.equals("delivery")) {
			city = this.task.deliveryCity;
		} else {
			System.err.println("[Error] Attempt to create an action that is not a pickup nor a delivery action.");
		}
	}
	
	@Override
	public String toString() {
		return actionType + " Task" + task.id + " in " + city;
	}
	
	public boolean equals(Object obj) {
		ActionInfo action = (ActionInfo) obj;
		return this.task.equals(action.task) && this.actionType.equals(action.actionType);
	}
}


class PlanDetails{
	  
	  public int capacity;
	  public Vehicle vehicle;
	  private City InitialCity;
	  public ArrayList<Task> LoadedTasks = new ArrayList<Task>();
	  public ArrayList<Task> RemainingTasks;
	  public ArrayList<Task> AllAssignedTasks;
	  public ArrayList<Task> DeliveredTasks = new ArrayList<Task>();
	  public ArrayList<Action> Actions = new ArrayList<Action>();
	  public ArrayList<City> MovingPattern = new ArrayList<Topology.City>();
	  public ArrayList<ActionInfo> ActionInfoList = new ArrayList<ActionInfo>();
	  
	  public ArrayList<Task> pickedTasks = new ArrayList<Task>();
	  
	  public Plan ActionList;
	  public int load = 0;
	  int checkFinish = 0;
	  public double cost = 0;
	  // a hashmap that 
	  public HashMap<Task, Boolean> taskMap = new HashMap<Task, Boolean>();
	  public HashMap<Task, Boolean> pickMap = new HashMap<Task, Boolean>();
	  
	  
	  
	  public PlanDetails(Vehicle _vehicle,ArrayList<Task> _assignedTasks) {
	    capacity = _vehicle.capacity();
	    InitialCity = _vehicle.getCurrentCity();
	    vehicle = _vehicle;
	    RemainingTasks = _assignedTasks;
	    AllAssignedTasks = _assignedTasks;
	    checkFinish = _assignedTasks.size();
	    for (Task task:_assignedTasks) {
	      this.taskMap.put(task, false);
	      this.pickMap.put(task, false);
	    }
	  }
	  
	  public void planning() {
	    City current = InitialCity;
	    MovingPattern = new ArrayList<Topology.City>();
	    ActionList = new Plan(InitialCity);
	    Actions = new ArrayList<Action>();
	  
	    for (ActionInfo action : ActionInfoList) {
	      
	      if(action.actionType == "pickup") {
	        if(current != action.task.pickupCity) {
	          for(City city : current.pathTo(action.task.pickupCity)) {
	            Actions.add(new Move(city));
	            MovingPattern.add(city);
	            current = city;
	          }
	        }
	        Actions.add(new Pickup(action.task));
	        current = action.task.pickupCity;
	      }
	      else {
	        if(current != action.task.deliveryCity) {
	          for(City city : current.pathTo(action.task.deliveryCity)) {
	            Actions.add(new Move(city));
	            MovingPattern.add(city);
	            current = city;
	          }
	        }
	        Actions.add(new Delivery(action.task));
	        current = action.task.deliveryCity;
	      }
	    }
	    ConvertArrayToPlan();
	    CostNew();
	  }
	  
	  public void TaskHandler(Task newTask, boolean IsDelivery) {
	    
	    if (!IsDelivery) {
	      //AllAssignedTasks.add(newTask);
	      LoadedTasks.add(newTask);
	      load += newTask.weight;
	      pickMap.replace(newTask, true);
	    }
	    else {
	      DeliveredTasks.add(newTask);
	      load -= newTask.weight;
	      taskMap.replace(newTask, true);
	    }
	  }
	  
	  // implements the intended initial solution
	  public void ConvertArrayToPlan() {
	    ActionList = new Plan(InitialCity);
	    for(Action action : Actions) {
	      ActionList.append(action);
	    }
	  }
	  
	  public void InitialPlan() {
	    City current = InitialCity;
	    while(!RemainingTasks.isEmpty()) {
	      current = assignAllPossible(RemainingTasks,current);
	      current = deliverAllTasks(LoadedTasks, current);
	    }  
	    ConvertArrayToPlan();
	    CostNew();
	    
	  }
	  
	  // this method assigns all the tasks that do not exceed the capacity
	  private City assignAllPossible(ArrayList<Task> RemainingTasks, City current) {
	    for (Task task : RemainingTasks) {
	      if(IsFeasible(vehicle, task) && !pickMap.get(task)) {
	        isPicked(RemainingTasks, vehicle, current);
	        if (IsFeasible(vehicle, task)&& !pickMap.get(task)) {
	          if(current != task.pickupCity) {
	            for (City city : current.pathTo(task.pickupCity)) {
	              Actions.add(new Move(city));
	              MovingPattern.add(city);
	            }
	            
	          }
	          Actions.add(new Pickup(task));
	          ActionInfoList.add(new ActionInfo(task, "pickup"));
	          TaskHandler(task, false);
	          current  = task.pickupCity;
	        
	        }
	      }
	    }
	    RemainingTasks.removeAll(LoadedTasks);
	    
	    
	    return current;
	  }
	// this method delivers all the loaded tasks to make room for the remaining ones
	  private City deliverAllTasks(ArrayList<Task> LoadedTasks, City current) {
	    for (Task task:LoadedTasks) { //l_task is LoadedTasks iterator
	      isDelivered(LoadedTasks, vehicle,current);
	      if (!taskMap.get(task)) {
	        if(current != task.deliveryCity) {
	          for(City city : current.pathTo(task.deliveryCity)) {
	            Actions.add(new Move(city));
	            MovingPattern.add(city);
	          }
	        }
	        Actions.add(new Delivery(task));
	        ActionInfoList.add(new ActionInfo(task, "delivery"));
	        TaskHandler(task, true);
	        current = task.deliveryCity;    
	      }
	    }
	    LoadedTasks.removeAll(DeliveredTasks);
	    
	    return current;
	  }
	  
	  // THIS METHOD SHOULD BE MODIFIED
	  private void isDelivered(ArrayList<Task> LoadedTasks, Vehicle vehicle,City current) {
	    for (Task task:LoadedTasks){
	      if (current == task.deliveryCity && !taskMap.get(task))
	      {
	        Actions.add(new Delivery(task));
	        ActionInfoList.add(new ActionInfo(task, "delivery"));
	        TaskHandler(task, true);
	      }
	    }
	  }
	    
	  // THIS METHOD SHOULD BE MODIFIED
	  private void isPicked(ArrayList<Task> RemainingTasks, Vehicle vehicle,City current) {
	    for (Task task:RemainingTasks){
	      if (current == task.pickupCity && !pickMap.get(task) && IsFeasible(vehicle, task))
	      {
	        Actions.add(new Pickup(task));
	        ActionInfoList.add(new ActionInfo(task, "pickup"));
	        TaskHandler(task, false);
	      }
	    }
	  }
	    
	  // checks the feasibility of picking up a new task
	  private boolean IsFeasible(Vehicle vehicle, Task task) {
	      if ((load+task.weight) <= capacity) {
	        return true;
	      }
	      else {
	        return false;
	      }
	    }
	  
	  //Cost calculator
	  public double Cost() {
	    cost  = 0;
	    City current = InitialCity;
	    for(City move: MovingPattern) {
	      cost += (double)current.distanceTo(move)*vehicle.costPerKm();  
	      current = move;
	    }

	    return cost;
	  }
	  
	  public double CostNew() {
	    cost = 0;
	    City current = InitialCity;
	    
	    for(ActionInfo action : ActionInfoList) {
	      for(City city : current.pathTo(action.city)) {
	        cost += (double)current.distanceTo(city)*vehicle.costPerKm();
	        current = city;
	      }
	    }
	    
	    if(!ActionInfoList.isEmpty()) {
	      ActionInfo FirstTask = ActionInfoList.get(0);
	      ActionInfo CurrentTask = FirstTask;
	      cost += CurrentTask.city.distanceTo(CurrentTask.task.deliveryCity)*vehicle.costPerKm();
	      for (ActionInfo action : ActionInfoList) {
	        if(FirstTask != action) {
	          if(action.actionType == "pickup") {
	            cost += (double)(CurrentTask.city.distanceTo(action.city)
	                +action.city.distanceTo(action.task.deliveryCity))*vehicle.costPerKm();
	            CurrentTask = action;
	          }
	          else {
	            cost += (double)CurrentTask.city.distanceTo(action.city)*vehicle.costPerKm();
	            CurrentTask = action;
	          }
	        }
	      }
	    }
	    
	    return cost;
	  }
	  
	  
	}

