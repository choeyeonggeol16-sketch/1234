package l1j.server.server.model;

public class L1TaxCalculator {
	/**
	 * 전쟁세는15% 고정
	 */
	//private static final int WAR_TAX_RATES = 15;
	private static final int WAR_TAX_RATES = 0;

	/**
	 * 국세는10% 고정(지역세에 대한 비율)
	 */
//	private static final int NATIONAL_TAX_RATES = 10;
	private static final int NATIONAL_TAX_RATES = 10;

	/**
	 * 디아드세는10% 고정(전쟁세에 대한 비율)
	 */
//	private static final int DIAD_TAX_RATES = 10;
	private static final int DIAD_TAX_RATES = 10;

	private final int _taxRatesCastle;
	private final int _taxRatesTown;
	private final int _taxRatesWar = WAR_TAX_RATES;

	/**
	 * @param merchantNpcId
	 *            계산 대상 상점의 NPCID
	 */
	public L1TaxCalculator(int merchantNpcId) {
		_taxRatesCastle = L1CastleLocation.getCastleTaxRateByNpcId(merchantNpcId);
		_taxRatesTown = L1TownLocation.getTownTaxRateByNpcid(merchantNpcId);
	}

	public int calcTotalTaxPrice(int price) {
		return Math.toIntExact(totalTaxLong(price));
	}

	private long totalTaxLong(int price) {
		return ((long) price * _taxRatesCastle + (long) price * _taxRatesTown
				+ (long) price * WAR_TAX_RATES) / 100L;
	}

	// Preserve the original separate-tax rounding policy.
	public int calcCastleTaxPrice(int price) {
		return Math.toIntExact((long) price * _taxRatesCastle / 100L - calcNationalTaxPrice(price));
	}

	public int calcNationalTaxPrice(int price) {
		return Math.toIntExact((long) price * _taxRatesCastle / 100L / (100 / NATIONAL_TAX_RATES));
	}

	public int calcTownTaxPrice(int price) {
		return Math.toIntExact((long) price * _taxRatesTown / 100L);
	}

	public int calcWarTaxPrice(int price) {
		return Math.toIntExact((long) price * _taxRatesWar / 100L);
	}

	public int calcDiadTaxPrice(int price) {
		return Math.toIntExact((long) price * _taxRatesWar / 100L / (100 / DIAD_TAX_RATES));
	}

	/**
	 * 과세 후의 가격을 요구한다.
	 * 
	 * @param price
	 *            과세전의 가격
	 * @return 과세 후의 가격
	 */
	public int layTax(int price) {
		final long taxed = layTaxLong(price);
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, taxed));
	}

	public long layTaxLong(int price) {
		return (long) price + totalTaxLong(price);
	}
}
