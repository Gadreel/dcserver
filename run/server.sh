#!/bin/sh

# export JAVA_HOME="/usr/lib/jvm/jre"
# export DC_DEPLOYMENT="production"
# export DC_NODE="01001"
# export DC_DEV="true"   - only if developer

if [ ! -x "$JAVA_HOME" ] ; then
  echo "Error: JAVA_HOME is not defined."
  exit 1
fi

JAVACMD="$JAVA_HOME/jre/bin/java"

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo "  We cannot execute $JAVACMD"
  exit 1
fi


# resolve links - $0 may be a softlink
ARG0="$0"

while [ -h "$ARG0" ]; do
  ls=`ls -ld "$ARG0"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    ARG0="$link"
  else
    ARG0="`dirname $ARG0`/$link"
  fi
done

DIRNAME="`dirname $ARG0`"
PROGRAM="`basename $ARG0`"

CMD=$1

#
# Copy binary updates
#

if [ "$CMD" = "startup" ] ; then
	mv ./ext/* ./lib
  CMD="start"
fi

# Increase the maximum file descriptors if we can
MAX_FD=`ulimit -H -n`

if [ "$?" -eq 0 ]; then
	ulimit -n $MAX_FD

	if [ "$?" -ne 0 ]; then
	    echo "$PROGRAM: Could not set maximum file descriptor limit: $MAX_FD"
	fi
else
	echo "$PROGRAM: Could not query system maximum file descriptor limit: $MAX_FD_LIMIT"
fi

# run the command

if [ "$DC_DEV" = "true" ] ; then
  $JAVACMD -jar ext/dcraft.third.jar "$CMD"
else
  $JAVACMD -jar lib/dcraft.third.jar "$CMD"
fi
