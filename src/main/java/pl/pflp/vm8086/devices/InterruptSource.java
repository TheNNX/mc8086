package pl.pflp.vm8086.devices;

public interface InterruptSource {
	public InterruptRequest consume();
	public InterruptRequest peek();
	
	public class InterruptRequest {
		public final InterruptSource from;
		
		private int vector;
		private boolean vectorAssigned;
		
		public boolean assignVector(int vector) {
			if (vectorAssigned)
				return false;
			
			this.vectorAssigned = true;
			this.vector = vector;
			return true;
		}
		
		public int getVector() {
			return this.vector;
		}
		
		public InterruptRequest(InterruptSource from) {
			this.from = from;
		}
	}
}
