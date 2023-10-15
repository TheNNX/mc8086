package pl.pflp.vm8086;

import java.util.ArrayList;
import java.util.List;

public class Bus<T> {
	public static interface BusDevice<T> {
		public boolean isWritingToBus();
		public boolean isWaitingForBusData();
		public T read();
		public void write(T data);
	}

	public static interface And<T> {
		T and(T a, T b);
		T defaultT();
	}
	
	private And<T> and;
	
	public Bus(And<T> and) {
		this.and = and;
	}
	
	private List<BusDevice<T> > busDevices = new ArrayList<>();
	
	public T read() {
		T result = and.defaultT();
		
		for (BusDevice<T> device : busDevices) {
			if (device.isWritingToBus())
				result = and.and(result, device.read());
		}
		
		return result;
	}
	
	public void write(T data) {
		for (BusDevice<T> device : busDevices) {
			if (device.isWaitingForBusData())
				device.write(data);
		}
	}
}
