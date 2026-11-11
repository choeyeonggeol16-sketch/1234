package l1j.server.server.random;

public abstract interface RandomGenerator {
	public abstract int nextInt();

	public abstract int nextInt(int paramInt);

	public abstract int range(int paramInt1, int paramInt2);

	public abstract int choice(int[] paramArrayOfInt);
}