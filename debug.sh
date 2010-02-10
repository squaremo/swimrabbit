#!/bin/bash
FILE=
[ -n "$1" ] && FILE="-f $1"
java -cp .:js.jar:rabbitmq-client.jar:commons-io-1.2.jar org.mozilla.javascript.tools.shell.Main $FILE
