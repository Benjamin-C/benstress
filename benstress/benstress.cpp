/* BenStress - A simple stress test to stress your computer.
 *
 * This program does some arbitrary math a bunch of times to stress your CPU and see what it does.
 * Tuning parameters can be passed as command line inputs
 * The average time for each stress test is printed at the end, and optionally the time each
 * 		test took to run as well as the CPU speed at the end can be printed to the console.
 *
 * This only works on Linux. If you don't use Linux, start (or just use someone else's tester)
 */

#include <stdio.h>
#include <thread>
#include <atomic>
#include <chrono>
#include <string.h>
#include <signal.h>
#include <iostream>
#include <fstream>
#include <string>

using namespace std;


// Number of running threads
static atomic<int> runningThreads;
// If the threads should keep going or not, used to stop early
static atomic<bool> go;
// The time the test was started at
static atomic<long> starttime;
// The amount of time taken to run the tests
static atomic<long> runtime;
// The letters used for magnitude prefixes
const char* PREFIX_LETTERS = " kMGTPE";

// A variable to store test results so that the compiler doesn't remove the test
static int nopleaseactuallydoit;

/** Actually does the stressing. It's just some randomish algorithm I came up with.
 * The parameter affects how long each test takes. Test (should) be comparable between different
 * runs with the same parameter
 *
 * param: The long parameter to control the length of the test
 */
void stress(long param) {
	int x = 1;
	int y = 1;
	for(long i = 0; i < param; i++) {
		x += y + 1;
		y += x - ((i+1) * y);
	}
	nopleaseactuallydoit = x + y;
}

/** Gets the speed of the CPU core this runs on
 *
 * returns the current average CPU speed in Hz
 */
double getCPUspeed() {
	// Read the CPU speeds from /proc/cpuinfo. Only works in Linux, too bad Windows uers
	string line;
	ifstream myfile ("/proc/cpuinfo");
	if (myfile.is_open()) {
		// Find the lines that denote which core the next data is for
		while(getline(myfile,line)) {
			if(line.rfind("processor", 0) == 0) {
				// Find the actual ID
				const char* r = line.c_str();
				while(*r++ != ':') { }
				int cpuid;
				sscanf(++r, "%d", &cpuid);
				// Check if it is the ID we want
				if(cpuid == sched_getcpu()) {
					// If so, find the speed line
					while(getline(myfile,line)) {
						if (line.rfind("cpu MHz", 0) == 0) { // pos=0 limits the search to the prefix
							// Read the speed
							r = line.c_str();
							while(*r++ != ':') { }
							double speed;
							sscanf(++r, "%lf", &speed);
							speed *= 1e6;
							return speed;
						}
					}
				}
			}
		}
		myfile.close();
	}
	return 0;
}

/** Gets the current system time in milliseconds
 *
 * Returns the long time in ms
 */
long millis() {
	return chrono::duration_cast<chrono::milliseconds>(chrono::system_clock::now().time_since_epoch()).count();
}

/** The task that controls and times the stresses
 *
 * id:			The int ID of the thread, used only for tabulating results from each thread
 * runs:		The int number of times to run the stress test, 0 means infinite
 * stressparam:	The long parameter passed to the stress test
 * print:		The boolean to print verbose output or not
 */
void stressTask(int id, int runs, long stressparam, bool print) {
	// Mark that the thread has started
	runningThreads++;
	// Say hi
	if(print) {
		printf("[%d] I stress you!\n", id);
	}
	// The number of runs
	int num = 0;
	// The total time spent in runs
	long sum = 0;
	// If we should run forever
	bool forever = false;
	if(runs == 0) {
		forever = true;
	}
	// Stress!
	while(go && (runs-- >= 0 || forever)) {
		// Time how long each stress takes
		auto start = chrono::high_resolution_clock::now();
		stress(stressparam);
		auto elapsed = chrono::high_resolution_clock::now() - start;
		long microseconds = chrono::duration_cast<chrono::microseconds>(elapsed).count();
		sum += microseconds;
		num++;
		// Print the time the stress took, as well as the CPU freq at the end
		if(print) {
			long stime = millis() - starttime;
			printf("%ld\t%2d\t%.6lf\t%.0lf\n", stime, id, microseconds / 1e6, getCPUspeed());
		}
	}
	// Save the average stress time
	long dur = sum / num;
	runtime += dur;
	runningThreads--;
	// Say goodby
	if(print) {
		printf("[% 2d] All done! %.6lf\n", id, (dur / 1e6));
	}
}

//char prefix(long num) {
//	int ct = 0;
//	long tn = num;
//	while(tn >= 1000) {
//		tn /= 1000;
//		ct++;
//	}
//
////	printf("%ld %d\n", tn, ct);
//	return PREFIX_LETTERS[ct];
//}

/** Writes a number as 2-3 significant figures and a prefix
 * E.G. 2.5M -> 2.5 mega, 259k -> 259 kilo
 *
 * param:	The number to write
 * str: 	The address of the string to write the result to
 */
void makePrefix(long num, char* str) {
	int ct = 0;
	double tn = num;
	// Find which prefix to use
	while(tn >= 1000) {
		tn /= 1000;
		ct++;
	}
	// Get the numeric part
	if(tn < 10) {
		sprintf(str, "%.1f", tn);
	} else {
		sprintf(str, "%.0f", tn);
	}
	// Add the prefix
	char* h = str;
	while(*++h != 0) { }
	*h++ = PREFIX_LETTERS[ct];
	*h = 0;
}

/**
 *  Politly ask the program to stop if ctl+c is pressed
 */
void signal_callback_handler(int signum) {
   printf("Stopping ... \n");
   go = false;
}

int main(int argc, char* argv[]) {
	// Tuning parameters
	int threadCount = 1;	// Number of threads to run
	long param = 4.1234e8;	// Stress parameter controlling length of each test
	int runs = 0;			// The number of test runs to do 0=infinite
	bool verbose = false;	// To print verbose output or not

	// Parse args
	for(int i = 1; i < argc; i++) {
		if(argv[i][0] == '-') {
			if(i+1 < argc){ // Flags that has a parameter
				switch(argv[i][1]) {
				case 't': {
					sscanf(argv[i+1], "%d", &threadCount);
				} break;
				case 'p': {
					double temp;
					sscanf(argv[i+1], "%lf", &temp);
					param = (long) temp;
				} break;
				case 'r': {
					sscanf(argv[i+1], "%d", &runs);
				} break;
				default: break; // Do nothing if we don't know what to do
				}
			}
			switch(argv[i][1]) { // Flags without a parameter
			case 'v': {
				verbose = true;
			} break;
			case 'h': {
				printf("BenStress (C++) - Stress your computer!\n");
				printf("Usage: benstress -t 4 -c 8 -p 2e8 -v\n");
				printf("\tRun the stress test with 4 threads, 8 itterations per thread,\n");
				printf("\ta stress paramater of 2x10^8 and verbose output\n");
				printf("Options:\n");
				printf("\tt: The number of threads to spawn. Defaults to 4\n");
				printf("\tc: The number of times to run the test. Use 0 to run forever and\n");
				printf("\t   ctl+c to stop. Defaults to 5\n");
				printf("\tp: The stress paramater. Defaults to 4.123e8\n");
				printf("\tv: Print each result to sysout rather than just the final average\n");
				return(0);
			}
			default: break; // Do nothing if we don't know what to do
			}
		}
	}
	// Listen for ctl+c to cleanly shut down
	signal(SIGINT, signal_callback_handler);

	// Print the start time of the test
	starttime = millis();
	time_t end_time = chrono::system_clock::to_time_t(chrono::system_clock::now());
	printf("Started at %s", ctime(&end_time));

	// Print the parameters for the test
	char rcts[9];
	makePrefix(param, rcts);
	printf("Testing in C++ on %d thread%s", threadCount, (threadCount > 1) ? "s " : " ");
	if(runs > 0) {
		printf("for %d run%s", runs, (runs > 0) ? "s " : " ");
	}
	printf("with %s iterations per run\n", rcts);

	// You may test now
	go = true;
	runningThreads = 0;
	runtime = 0;
	// Keep track of all the threads so we can see if they stop later
	std::thread threads[threadCount];
	// Make as many threads as requested
	for(int i = 0; i < threadCount; i++) {
		threads[i] = std::thread(stressTask, i, runs, param, verbose);
	}

	// Wait for them all to start
	while(runningThreads < threadCount) { }

	printf("Stressing ... !\n");

	// Wait for them all to stop
	for(int i = 0; i < threadCount; i++) {
		threads[i].join();
	}

	// Calculate the average time each run took
	double avgt = (runtime / threadCount) / 1e6 ;

	// Print results
	printf("Average time: %.6lf seconds\n", avgt);
	printf("Your magic number is %d\n", nopleaseactuallydoit);
	printf("All cleaned up\n");

	return 0;
}
