package edu.buffalo.cse.phonelab.services.statusmonitor;

import java.util.ArrayList;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;

import edu.buffalo.cse.phonelab.services.platform.PlatformImage;
import edu.buffalo.cse.phonelab.services.platform.PlatformParameters;

public class ServiceIntervals {
	
	@ElementListUnion({
		@ElementList(entry="activeLocation",inline = true,type = ActiveLocation.class),
		@ElementList(entry="passiveLocation",inline = true,type = PassiveLocation.class),
	})
	private ArrayList<Intervals> serviceIntervals;
	
	public ServiceIntervals(){
		serviceIntervals = new ArrayList<Intervals>();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ServiceIntervals)) {
			return false;
		}
		ServiceIntervals lhs = (ServiceIntervals) o;
		
		for (Intervals interval : serviceIntervals) {
			if (!(lhs.serviceIntervals.contains(interval))) {
				return false;
				}			
		}
	
		return logTag.equals(lhs.logTag) &&
				failedRetryDelay.equals(lhs.failedRetryDelay) &&
				platforms.size() == lhs.platforms.size();
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
	
	@Element
	public Integer value;
	
	@Element
	public String units;
	
	@Element
	public Boolean wakelock;
	
	public ActiveLocation(){
		
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ActiveLocation)) {
			return false;
		}
		ActiveLocation lhs = (ActiveLocation) o;
		
		return value.equals(lhs.value) &&
				units.equals(lhs.units) &&
				wakelock.equals(lhs.wakelock);
	}
}
class PassiveLocation extends Intervals{
	@Element
	public Integer value;
	
	@Element
	public String units;
	
	@Element
	public Boolean wakelock;
	
	public PassiveLocation(){
		
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PassiveLocation)) {
			return false;
		}
		PassiveLocation lhs = (PassiveLocation) o;
		
		return value.equals(lhs.value) &&
				units.equals(lhs.units) &&
				wakelock.equals(lhs.wakelock);
	}
}
