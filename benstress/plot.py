import csv
import numpy as np
import matplotlib.pyplot as plt

with open('t.csv', newline='') as csvfile:
    spamreader = csv.reader(csvfile, delimiter='\t', quotechar='|')
    threads = 0;
    time = []
    dur = []
    freq = []
    for row in spamreader:
        if(len(row) > 0):
            if row[0].startswith("Testing on "):
                threads = int(row[0].split(' ')[2])
                for i in range(threads):
                    time.append([])
                    dur.append([])
                    freq.append([])
            if len(row) == 4 :
                if row[0].isnumeric():
                    i = int(row[1])
                    time[i].append(int(row[0]) / 1e3)
                    dur[i].append(float(row[2]))
                    freq[i].append(int(row[3]) / 1e9)
    # End for
    fig, ax1 = plt.subplots()

    color = 'tab:red'
    ax1.set_xlabel('time (s)')
    ax1.set_ylabel('Duration (s)', color=color)

    for i in range(threads):
        ax1.plot(time[i], dur[i], color=color)
    ax1.tick_params(axis='y', labelcolor=color)

    ax2 = ax1.twinx()  # instantiate a second axes that shares the same x-axis

    color = 'tab:blue'
    ax2.set_ylabel('Freq (GHz)', color=color)  # we already handled the x-label with ax1
    for i in range(threads):
        ax2.plot(time[i], freq[i], color=color)
    ax2.tick_params(axis='y', labelcolor=color)

    fig.tight_layout()  # otherwise the right y-label is slightly clipped
    plt.show()
        # for q in row:
            # print(q)
        # if row[0].isnumeric():
            # print(', '.join(row))
