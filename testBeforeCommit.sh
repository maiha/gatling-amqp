#!/bin/sh
sbt publish && rm -rf stats/ && ./run p publish && ./run c consume && ./run r rpc && less stats/*/*/log

