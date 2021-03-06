/* BenStress - A simple stress test to stress your computer.
 * 
 * This program does some arbitrary math a bunch of times to stress your CPU and see what it does.
 * Tuning parameters can be passed as command line inputs
 * The average time for each stress test is printed at the end, and optionally the time each
 * 		test took to run as well as the CPU speed at the end can be printed to the console.
 * 
 * This only works on Linux. If you don't use Linux, start (or just use someone else's tester)
 */

package dev.orangeben;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class BenStress {
	
	// Number of running threads
	private static volatile int runningThreads = 0;
	// If the threads should keep going or not, used to stop early
	private static volatile boolean go;
	// The time the test was started at
	private static volatile long starttime;
	// The amount of time taken to run the tests
	private static volatile long runtime;
	// Indicates if the program finished before the shutdown signal was sent
	private static volatile boolean doneSignal = false;
	// Lets the shutdown thread wait until the main thread is done before shuttind down
	private static volatile Object shutdownWaiter;
	// The letters used for magnitude prefixes
	public static final String PREFIX_LETTERS = " kMGTPE";
	
	public static void main(String[] args) {
		// Tuning parameters
		int threadCount = 4; 			// Number of threads to run
		long param = (long) 4.1234e8;	// Stress parameter controlling length of each test
		int runs = 0;					// The number of test runs to do 0=infinite
		boolean verbose = false;		// To print verbose output or not
		String stressorName = "";
		Stressor stressor = (stressparam) -> {
			int x = 1;
			int y = 1;
			// Some math to make the CPU work. Hopefully the Java compiler won't just remove it ...
			for(long i = 0; i < stressparam; i++) {
				x += y + 1;
				y += x - ((i+1) * y);
			}
		};;
		
		// Parse args
		for(int i = 0; i < args.length; i++) {
			if(args[i].charAt(0) == '-') {
				if(i+1 < args.length){ // Flags that has a parameter
					System.out.println("c: " + args[i].charAt(1));
					switch(args[i].charAt(1)) {
					case 't': {
						threadCount = Integer.parseInt(args[i+1]);
					} break;
					case 'p': {
						param = (long) Double.parseDouble(args[i+1]);
					} break;
					case 'r': {
						runs = Integer.parseInt(args[i+1]);
					} break;
					case 's': {
						switch(args[i+1].charAt(0)) {
						case 'p':
							stressor = (sp) -> {
								int a = 0;
								for(int j = 0; j >= 0; j++) {
									a++;
									a%=255;
								}
							};
							param = 0;
							stressorName = "PeterStressor";
						break;
						}
					}
					default: break; // Do nothing if we don't know what to do with this flag
					}
				}
				switch(args[i].charAt(1)) { // Flags without a parameter
				case 'v': {
					verbose = true;
				} break;
				case 'h': {
					System.out.println("BenStress (Java) V1.1 - Stress your computer!");
					System.out.println("Usage: java -jar benstress.jar -t 4 -c 8 -p 2e8 -v");
					System.out.println("\tRun the stress test with 4 threads, 8 itterations per thread,");
					System.out.println("\ta stress paramater of 2x10^8 and verbose output");
					System.out.println("Options:");
					System.out.println("\tt: The number of threads to spawn. Defaults to 4");
					System.out.println("\tc: The number of times to run the test. Use 0 to run forever and");
					System.out.println("\t   ctl+c to stop. Defaults to 5");
					System.out.println("\tp: The stress paramater. Defaults to 4.123e8");
					System.out.println("\tv: Print each result to sysout rather than just the final average");
					System.out.println("\ts: Selects the stressing algorithm to use. Currently only 'p' is supported");
					return;
				}
				default: break; // Do nothing if we don't know what to do
				}
			}
		}
		// Listen for ctl+c to cleanly shut down
		shutdownWaiter = new Object();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					if(!doneSignal) { // If this was called before the program finished ...
						System.out.println("Stopping ...");
						// Ask the program to stop
						go = false;
						doneSignal = true;
						// and wait for it to finish
						synchronized(shutdownWaiter) {
							shutdownWaiter.wait();
						}
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					e.printStackTrace();
				}
			}
		});
		
		// Print the start time of the test
		starttime = System.currentTimeMillis();
		System.out.printf("Started at %s\n", java.time.LocalDateTime.now());

		// Print the parameters for the test
		System.out.printf("Testing in Java on %d thread%s", threadCount, (threadCount > 1) ? "s " : " ");
		if(runs > 0) {
			System.out.printf("for %d run%s", runs, (runs > 0) ? "s " : " ");
		}
		if(param != 0) {
			System.out.printf("with stress paramater %s ", makePrefix(param));
		}
		if(!stressorName.equals("")) {
			System.out.println("using " + stressorName);
		}
		System.out.println();

		// You may test now
		go = true;
		// Keep track of all the threads so we can see if they stop later
		Thread threads[] = new Thread[threadCount];
		// Make as many threads as requested
		for(int i = 0; i < threadCount; i++) {
			final int id = i;
			final int r = runs;
			final long p = param;
			final boolean v = verbose;
			final Stressor s = stressor;
			threads[i] = new Thread() {
				@Override
				public void run() {
					stressTask(id, r, p, v, s);
				}
			};
			threads[i].start();
		}
		
		// Wait for them all to start without using too much CPU time ourselves
		while(runningThreads < threadCount) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		};

		System.out.printf("Stressing ... !\n");
		
		// Wait for them all to stop
		for(int i = 0; i < threadCount; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Calculate the average time each run took
		double avgt = (runtime / threadCount) / 1e6 ;
		
		// Print results
		System.out.printf("Average time: %.6f seconds\n", avgt);
		System.out.printf("All cleaned up\n");
		
		// If we didn't finish before the shutdown signal is sent ...
		if(doneSignal) {
			synchronized (shutdownWaiter) {
				shutdownWaiter.notify();
			}
		} else { // If we did finish before the shutdown signal was sent
			doneSignal = true;
		}
	}
	
	/** Gets the current average CPU speed
	 * 
	 * @return the current average CPU speed in Hz
	 */
	public static double getCPUspeed() {
		return getCPUspeed(-1);
	}
	/** Gets the current speed of the CPU. If cid is specified >= 0, the speed of that core will be returned if that core exists.
	 * Otherwise, the average speed will be returned.
	 * 
	 * @param cid the int ID of the core to measure. Use -1 for average speed
	 * @return The double speed of the core / average in Hz, or 0 if something went wrong
	 */
	public static double getCPUspeed(int cid) {
		try {
			// Read the CPU speeds from /proc/cpuinfo. Only works in Linux, too bad Windows uers
			File cpuinfo = new File("/proc/cpuinfo");
			// Read the file
			Scanner myReader = new Scanner(cpuinfo);
			while (myReader.hasNextLine()) {
				String data = myReader.nextLine();
				if(cid >= 0) { // If we're looking for the speed of a specific core ...
					if(data.contains("processor	:")) { // Check if this is the core we want
						try {
							// Read the next speed which should belong to that core
							int cpuid = Integer.parseInt(data.split(": ")[1]);
							if(cpuid == cid) {
								while (myReader.hasNextLine()) {
									data = myReader.nextLine();
									if(data.contains("cpu MHz		:")) {
										double hz = Double.parseDouble(data.split(": ")[1]) * 1e6;
										myReader.close();
										return hz;
									}
								}
							}
						} catch(Exception e) {
							e.printStackTrace();
						}
					}
				} else { // Otherwise, average all cores
					double spd = 0;
					int ct = 0;
					while (myReader.hasNextLine()) {
						data = myReader.nextLine();
						// Find the lines that have CPU speeds
						if(data.contains("cpu MHz		:")) {
							double hz = Double.parseDouble(data.split(": ")[1]) * 1e6;
							spd += hz;
							ct++;
						}
					}
					myReader.close();
					// and average them
					return spd / ct;
				}
			}
			myReader.close();
			return 0;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	/** The task that controls and times the stresses
	 * 
	 * @param id			The int ID of the thread, used only for tabulating results from each thread
	 * @param runs			The int number of times to run the stress test, 0 means infinite
	 * @param stressparam	The long parameter passed to the stress test
	 * @param print			The boolean to print verbose output or not
	 */
	private static void stressTask(int id, int runs, long stressparam, boolean print, Stressor stressor) {
		// Mark that the thread has started
		runningThreads++;
		// Say hi
		if(print) {
			System.out.printf("[%d] I stress you!\n", id);
		}
		// The number of runs
		int num = 0;
		// The total time spent in runs
		long sum = 0;
		// If we should run forever
		boolean forever = false;
		if(runs == 0) {
			forever = true;
		}
		// Stress!
		while(go && (runs-- >= 0 || forever)) {
			// Time how long each stress takes
			long start = System.nanoTime();
			stressor.run(stressparam);
			long end = System.nanoTime();
			long microseconds = (end - start) / 1000;
			sum += microseconds;
			num++;
			// Print the time the stress took, as well as the CPU freq at the end
			if(print) {
				long stime = System.currentTimeMillis() - starttime;
				System.out.printf("%d\t%d\t%.6f\t%.0f\n", stime, id, microseconds / 1e6, getCPUspeed());
			}
		}
		// Save the average stress time
		long dur = sum / num;
		runtime += dur;
		runningThreads--;
		// Say goodby
		if(print) {
			System.out.printf("[% 2d] All done! %.6f\n", id, (dur / 1e6));
		}
	}
	
	/** Writes a number as 2-3 significant figures and a prefix
	 * E.G. 2.5M -> 2.5 mega, 259k -> 259 kilo
	 * 
	 * @param param The number to write
	 * @return		The number written
	 */
	private static String makePrefix(long param) {
		int ct = 0;
		double tn = param;
		// Find which prefix to use
		while(tn >= 1000) {
			tn /= 1000;
			ct++;
		}
		// Get the numeric part
		String str = "";
		if(tn < 10) {
			str = Double.toString(tn).substring(0, 3);
		} else {
			str = Double.toString(tn);
			str = str.substring(0, str.indexOf('.'));
		}
		// Add the prefix
		str += PREFIX_LETTERS.charAt(ct);

		return str;
	}
}
