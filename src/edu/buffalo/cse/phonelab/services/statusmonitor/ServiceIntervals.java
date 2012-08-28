package edu.buffalo.cse.phonelab.services.statusmonitor;

import java.util.ArrayList;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;

public class ServiceIntervals {
	
	@ElementListUnion({
		@ElementList(entry="activeLocation",inline = true,type = ActiveLocation.class),
		@ElementList(entry="passiveLocation",inline = true,type = PassiveLocation.class),
	})
	private ArrayList<Intervals> serviceIntervals;
	
	public ServiceIntervals(){
		serviceIntervals = new ArrayList<Intervals>();
	}

}

class Intervals {

	public long getRunInterval(long value, String units) {
		long runInterval = 0;

		if (units.equals("hour")) {
			runInterval = value * 60 * 60 * 1000;
		} else if (units.equals("min")) {
			runInterval = value * 60 * 1000;
		} else if (units.equals("sec")) {
			runInterval = value * 1000;
		} else if (units.equals("millisec")) {
			runInterval = value;
		}

		return runInterval;
	}
}
class ActiveLocation extends Intervals{
	
	public ActiveLocation(){
		
	}
}
class PassiveLocation extends Intervals{
	
	public PassiveLocation(){
		
	}
}
