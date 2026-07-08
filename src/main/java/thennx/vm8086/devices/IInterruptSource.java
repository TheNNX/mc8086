package thennx.vm8086.devices;

public interface IInterruptSource {
	InterruptRequest consume();
	InterruptRequest peek();

	class InterruptRequest {
		public final IInterruptSource from;

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

		public InterruptRequest(IInterruptSource from) {
			this.from = from;
		}
	}
}
