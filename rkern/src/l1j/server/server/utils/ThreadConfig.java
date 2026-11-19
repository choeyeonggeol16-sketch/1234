package l1j.server.server.utils;


/**
 * This class loads all the threadpool related configurations.
 */
public class ThreadConfig
{
	// File
	private static final String THREADS_CONFIG_FILE = "./config/Threads.ini";
	
	// Constants
	public static int SCHEDULED_THREAD_POOL_SIZE;
	public static int HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE;
	public static int INSTANT_THREAD_POOL_SIZE;
	public static boolean THREADS_FOR_LOADING;
	
	public static void load() {
		final ConfigReader config = new ConfigReader(THREADS_CONFIG_FILE);
		
		SCHEDULED_THREAD_POOL_SIZE = config.getInt("ScheduledThreadPoolSize", -1);
		if (SCHEDULED_THREAD_POOL_SIZE == -1) {
			SCHEDULED_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 4;
		}
		
		INSTANT_THREAD_POOL_SIZE = config.getInt("InstantThreadPoolSize", -1);
		if (INSTANT_THREAD_POOL_SIZE == -1) {
			INSTANT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
		}
		
		if ((SCHEDULED_THREAD_POOL_SIZE > 2) && (INSTANT_THREAD_POOL_SIZE > 2)) {
			HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE = Math.max(2, SCHEDULED_THREAD_POOL_SIZE / 4);
		}
		else {
			HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE = 0;
		}
		
		if (config.containsKey("ThreadsForLoading")) {
			THREADS_FOR_LOADING = config.getBoolean("ThreadsForLoading", false);
		} else {
			THREADS_FOR_LOADING = false;
		}
	}
}
