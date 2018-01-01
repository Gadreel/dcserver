#!/bin/sh

# JAVA_HOME="/usr/lib/jvm/jre"
# export JAVA_HOME
# 
# DC_NAME="dcServer"
# export DC_NAME
# 
# DC_DEPLOYMENT="production"
# export DC_DEPLOYMENT
# 
# DC_NODE="01001"
# export DC_NODE

if [ ! -x "$JAVA_HOME" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  exit 1
fi

if [ ".$DC_DEPLOYMENT" = . ] ; then
  echo "Error: deployment name missing."
  exit 1
fi

if [ ".$DC_NAME" = . ] ; then
  echo "Error: node name missing."
  exit 1
fi

if [ ".$DC_NODE" = . ] ; then
  echo "Error: node id missing."
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

if [ ".$DC_USER" = . ] ; then
        DC_USER="ec2-user"
fi

DC_HOME=`cd "$DIRNAME" >/dev/null; pwd`
DC_BASE="$DC_HOME"
DC_MAIN="dcraft.hub.Daemon"
DC_OUT="$DC_BASE/logs/hub-daemon.out"
DC_ERR="$DC_BASE/logs/hub-daemon.err"
DC_TMP="$DC_BASE/temp"
DC_PID="$DC_BASE/logs/hub-daemon.pid"

JAVA_BIN="$JAVA_HOME/bin/java"
JSVC="$DC_BASE/jsvc64"

CMD=$1

#
# Build classpath
#

S=':'
CLASSPATH=''

for i in ./lib/*.jar; do
  CLASSPATH=$CLASSPATH$S$i
done

for i in ./ext/*.jar; do
  CLASSPATH=$CLASSPATH$S$i
done

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

# ----- Execute The Requested Command -----------------------------------------
case "$CMD" in
    run     )
      shift
      "$JSVC" $* \
      $JSVC_OPTS \
      -java-home "$JAVA_HOME" \
      -server \
      -procname "$DC_NAME" \
      -pidfile "$DC_PID" \
      -wait 10 \
      -nodetach \
      -outfile "&1" \
      -errfile "&2" \
      -classpath "$CLASSPATH" \
      -Djava.io.tmpdir="$DC_TMP" \
	  -Djava.library.path=./lib \
	  -Djava.awt.headless=true \
	  -Dawt.toolkit=sun.awt.HToolkit \
      $DC_MAIN $DC_DEPLOYMENT $DC_NODE
      exit $?
    ;;
    start   )
      "$JSVC" $JSVC_OPTS \
      -java-home "$JAVA_HOME" \
      -server \
      -procname "$DC_NAME" \
      -user $DC_USER \
      -pidfile "$DC_PID" \
      -wait 10 \
      -outfile "$DC_OUT" \
      -errfile "$DC_ERR" \
      -classpath "$CLASSPATH" \
      -Djava.io.tmpdir="$DC_TMP" \
	  -Djava.library.path=./lib \
	  -Djava.awt.headless=true \
	  -Dawt.toolkit=sun.awt.HToolkit \
      $DC_MAIN $DC_DEPLOYMENT $DC_NODE
      exit $?
    ;;
    stop    )
      "$JSVC" $JSVC_OPTS \
      -stop \
      -pidfile "$DC_PID" \
      -classpath "$CLASSPATH" \
      $DC_MAIN
      exit $?
    ;;
    version  )
      "$JSVC" \
      -java-home "$JAVA_HOME" \
      -pidfile "$DC_PID" \
      -classpath "$CLASSPATH" \
      -errfile "&2" \
      -version \
      -check \
      $DC_MAIN
      if [ "$?" = 0 ]; then
        "$JAVA_BIN" \
        -classpath "$DC_HOME/lib/ftc.core.jar" \
        ftc.core.HubInfo
      fi
      exit $?
    ;;
    *       )
      echo "Unkown command: \`$1'"
      echo "Usage: $PROGRAM ( commands ... )"
      echo "commands:"
      echo "  run               Start Hub without detaching from console"
      echo "  start             Start Hub"
      echo "  stop              Stop Run"
      echo "  version           What version of commons daemon and Hub (TODO)"
      echo "                    are you running?"
      exit 1
    ;;
esac
