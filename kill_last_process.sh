#!/bin/bash
# A simple script to kill the last process.

# Get the process ID (PID) of the last command, excluding this script and basic shell tools.
PID=$(ps -ax -o pid,etime,comm | grep -v $$ | grep -v "ps" | grep -v "grep" | grep -v "bash" | tail -1 | awk '{print $1}')

if [ -z "$PID" ]; then
    echo "Could not find a process to kill."
    exit 1
fi

echo "The last process was: $(ps -p $PID -o comm=)"
echo "PID: $PID"
echo "Killing process $PID"
kill -9 $PID
echo "Process killed."