


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Random;

import jig.misc.rd.AirCurrentGenerator;
import jig.misc.rd.Direction;
import jig.misc.rd.RobotDefense;


/**
 *  A simple agent that uses reinforcement learning to direct the vacuum
 *  The agent has the following critical limitations:
 *  
 *  	- it only considers a small set of possible actions
 *  	- it does not consider turning the vacuum off
 *  	- it only reconsiders an action when the 'local' state changes  
 *         in some cases this may take a (really) long time
 *      - it uses a very simplisitic action selection mechanism
 *      - actions are based only on the cells immediately adjacent to a tower
 *      - action values are not dependent (at all) on the resulting state 
 */
public class WilliamTeam extends BaseLearningAgent {

	/**
	 * A Map of states to actions
	 * 
	 *  States are encoded in the StateVector objects
	 *  Actions are associated with a utility value and stored in the QMap
	 */
	HashMap<StateVector,QMap> actions = new HashMap<StateVector,QMap>();

	/**
	 * The agent's sensor system tracks /how many/ insects a particular generator
	 * captures, but here I want to know /when/ an air current generator just
	 * captured an insect so I can reward the last action. We use this captureCount
	 * to see when a new capture happens.
	 */
	HashMap<AirCurrentGenerator, Integer> captureCount;

	/**
	 * Keep track of the agent's last action so we can reward it
	 */
	HashMap<AirCurrentGenerator, AgentAction> lastAction;
	
	/**
	 * This stores the possible actions that an agent many take in any
	 * particular state.
	 */
	private static final AgentAction [] potentials;

	static {
		Direction [] dirs = Direction.values();
		potentials = new AgentAction[dirs.length*5];

		int i = 0;
		for(Direction d: dirs) {
			// creates a new directional action with the power set to full
			// power can range from 1 ... AirCurrentGenerator.POWER_SETTINGS
			potentials[i] = new AgentAction(0, d);
			potentials[i + 1] = new AgentAction(1, d);
			potentials[i + 2] = new AgentAction(2, d);
			potentials[i + 3] = new AgentAction(3, d);
			potentials[i + 4] = new AgentAction(4, d);
			i += 5;
		}

	}
	
	public WilliamTeam() {
		captureCount = new HashMap<AirCurrentGenerator,Integer>();
		lastAction = new HashMap<AirCurrentGenerator,AgentAction>();		
	}
	
	/**
	 * Step the agent by giving it a chance to manipulate the environment.
	 * 
	 * Here, the agent should look at information from its sensors and 
	 * decide what to do.
	 * 
	 * @param deltaMS the number of milliseconds since the last call to step
	 * 
	 */
	public void step(long deltaMS) {
		StateVector state;
		QMap qmap;
		QMap current_qmap;
		double lr = 0.2;
		double gamma = 0.9;
		double value = 2.0;
		// This must be called each step so that the performance log is 
		// updated.
		updatePerformanceLog();
		
		for (AirCurrentGenerator acg : sensors.generators.keySet()) {
			if (!stateChanged(acg)){
				//do last action
				AgentAction act = lastAction.get(acg);
				act.doAction(acg);
				lastAction.put(acg, act);
				continue;
			}

			
			// Check the current state, and make sure member variables are
			// initialized for this particular state...
			state = thisState.get(acg);
			if (actions.get(state) == null) {
				actions.put(state, new QMap(potentials));
			}
			if (captureCount.get(acg) == null){
				captureCount.put(acg, 0);
			}

			current_qmap = actions.get(state);
			// Check to see if an insect was just captured by comparing our
			// cached value of the insects captured by each ACG with the
			// most up-to-date value from the sensors
			boolean justCaptured;
			justCaptured = (captureCount.get(acg) < sensors.generators.get(acg));

			// if this ACG has been selected by the user, we'll do some verbose printing
			boolean verbose = (RobotDefense.getGame().getSelectedObject() == acg);

			// If we did something on the last 'turn', we need to reward it
			if (lastAction.get(acg) != null) {
				// print out the reward
				
				// get the action map associated with the previous state
				qmap = actions.get(lastState.get(acg));
				// AgentAction next_action = qmap.findBestAction(false);
				// int j;
				// int best_current_util;

				// for(j=0; j< qmap.utility.length; j++){
				// 	if(qmap.actions[j].equals(lastAction.get(acg))){
				// 		break;
				// 	}
				// }

				// if(j >= qmap.utility.length){
				// 	System.out.println("Error: action not found");
				// 	return;
				// }

				// for(best_current_util = 0; best_current_util < current_qmap.utility.length; best_current_util++){
				// 	if(next_action.equals(current_qmap.actions[best_current_util])){
				// 		break;
				// 	}
				// }

				// if(best_current_util >= current_qmap.utility.length){
				// 	System.out.println("Error: action not found");
				// 	return;
				// }
			
				// qmap.utility[j] = qmap.utility[j] + lr * (value + gamma * current_qmap.utility[best_current_util] - qmap.utility[j]);



				for(int power = 0; power < 5; power ++) {
					if (justCaptured) {
						// capturing insects is good				
						if(lastAction.get(acg).getPower() == power){
							System.out.println("Reward: " + 10.0);
							// step(deltaMS);
							qmap.rewardAction(lastAction.get(acg), (5-power)*10.0);
							captureCount.put(acg, sensors.generators.get(acg));
						}
						
					} else  {
						if (lastAction.get(acg).getPower() == power)
							qmap.rewardAction(lastAction.get(acg), -0.3*power);
						// step(deltaMS);
					}
					

					// qmap.rewardAction(lastAction.get(acg), -1.0);

					// if (verbose) {
					// 	System.out.println("Last State for " + acg.toString());
					// 	System.out.println(lastState.get(acg).representation());
					// 	System.out.println("Updated Last Action: " + qmap.getQRepresentation());
					// }

				}
			}

			// decide what to do now...
			// first, get the action map associated with the current state
			current_qmap = actions.get(state);

			if (verbose) {
				System.out.println("This State for Tower " + acg.toString());
				System.out.println(thisState.get(acg).representation());
			}
			// find the 'right' thing to do, and do it.
			AgentAction bestAction = current_qmap.findBestAction(verbose);
			bestAction.doAction(acg);

			// finally, store our action so we can reward it later.
			lastAction.put(acg, bestAction);

		}
	}


	/**
	 * This inner class simply helps to associate actions with utility values
	 */
	static class QMap {
		static Random RN = new Random();

		private double[] utility; 		// current utility estimate
		private int[] attempts;			// number of times action has been tried
		private AgentAction[] actions;  // potential actions to consider

		public QMap(AgentAction[] potential_actions) {

			actions = potential_actions.clone();
			int len = actions.length;

			utility = new double[len];
			attempts = new int[len];
			for(int i = 0; i < len; i++) {
				utility[i] = 0.0;
				attempts[i] = 0;
			}
		}

		/**
		 * Finds the 'best' action for the agent to take.
		 * 
		 * @param verbose
		 * @return
		 */
		public AgentAction findBestAction(boolean verbose) {
			int i,maxi,maxcount;
			maxi=0;
			maxcount = 1;
			
			if (verbose)
				System.out.print("Picking Best Actions: " + getQRepresentation());

				for (i = 1; i < utility.length; i++) {
					if (utility[i] > utility[maxi]) {
						maxi = i;
						maxcount = 1;
					}
					else if (utility[i] == utility[maxi]) {
						maxcount++;
					}
			}
			if (RN.nextDouble() > .2) {
				int whichMax = RN.nextInt(maxcount);

				if (verbose)
					System.out.println( " -- Doing Best! #" + whichMax);
				// tie breaking
				for (i = 0; i < utility.length; i++) {
					if (utility[i] == utility[maxi]) {
						if (whichMax == 0) return actions[i];
						whichMax--;
					}
				}
				return actions[maxi];
			}
			else {
				// instead of picking a completely random action all the time.
				// if the best score is high enough (atleast two or more successes) it will randomly pick from 
				// the related similar to best pool
				for (i = 1; i < utility.length; i++) 
					{
						if (utility[i] > utility[maxi]) 
						{
							maxi = i;
							maxcount = 1;
						}
						else if (utility[i] == utility[maxi]) 
						{
							maxcount++;
						}
					} 
					
				if(utility[maxi] > 30.0)// roughly one success
				{
					// this is a rudimentary form of directed reasoning in that, if what the agent is doing is successful
					// it will then try actions that are close to it, in this case it will try different power settings or change
					// to the orientation adjacent, which ever action is above or below it
					
					if (verbose)
					System.out.println( " -- Doing Near Best (" + maxi + ")!!");
				
					if(RN.nextInt() % 2 == 1)
					{	
						if((maxi + 1) > utility.length)// bounds protection
						{
							return actions[maxi - 1];
						}
						
						else
						{
							return actions[maxi + 1];
						}
					}
				
					else
					{	
						if((maxi - 1) < 0)// bounds protection
						{
							return actions[maxi + 1];
						}
						
						else
						{
							return actions[maxi - 1];
						}
					}
				}
				
				else
				{
					int which = RN.nextInt(actions.length);
					// performs a truly random action
				if (verbose)
					System.out.println( " -- Doing Random (" + which + ")!!");

				return actions[which];
				}
			}
		}

		/**
		 * Modifies an action value by associating a particular reward with it.
		 * 
		 * @param a the action performed 
		 * @param value the reward received
		 */
		public void rewardAction(AgentAction a, double value) {
			int i;
			for (i = 0; i < actions.length; i++) {
				if (a == actions[i]) break;
			}
			if (i >= actions.length) {
				System.err.println("ERROR: Tried to reward an action that doesn't exist in the QMap. (Ignoring reward)");
				return;
			}

			utility[i] = (utility[i] * attempts[i]) + value;
			attempts[i] = attempts[i] + 1;
			utility[i] = utility[i]/attempts[i];
		}
		/**
		 * Gets a string representation (for debugging).
		 * 
		 * @return a simple string representation of the action values
		 */
		public String getQRepresentation() {
			StringBuffer sb = new StringBuffer(80);

			for (int i = 0; i < utility.length; i++) {
				sb.append(String.format("%.2f  ", utility[i]));
			}
			return sb.toString();

		}

	}
}