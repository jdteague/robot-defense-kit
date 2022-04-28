import jig.misc.rd.ai.AgentFactory;
import jig.misc.rd.ai.RobotDefenseAgent;




public class WilliamTeamFactory implements AgentFactory {

	public RobotDefenseAgent createAgent(String name, String agentResource) {
		return new WilliamTeam();
	}

}
