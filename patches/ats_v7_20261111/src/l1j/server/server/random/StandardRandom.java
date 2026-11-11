package l1j.server.server.random;

import java.util.Random;

class StandardRandom implements RandomGenerator {
	private Random _random = new Random();

	public int nextInt() {
		return this._random.nextInt();
	}

	public int nextInt(int n) {
		if (n <= 0) {
			return 0;
		}
		return this._random.nextInt(n);
	}

	public int range(int min, int max) {
		return min + this._random.nextInt(max - min + 1);
	}

	public int choice(int[] numbers) {
		int[] list = (int[]) (int[]) numbers.clone();
		return list[this._random.nextInt(numbers.length)];
	}
}