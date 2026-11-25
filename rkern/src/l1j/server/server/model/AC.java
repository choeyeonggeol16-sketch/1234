package l1j.server.server.model;

/**
 * Legacy typed access to creature defense during the full call-site migration.
 * L1Character implements it directly; no per-creature AC object is allocated.
 */
public interface AC {
	int getAc();
	void addAc(int i);
	void setAc(int i);
	void setSkillAc(int value, boolean notify);
}
