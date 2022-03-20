#!/bin/sh

# JAVA_HOME="/usr/lib/jvm/jre"
# export JAVA_HOME

#
# Check for JAVA settings
#
# >printenv | grep JAVA_HOME
# >JAVA_HOME="...path to 1.8..."
# >export JAVA_HOME
#

if [ ! -x "$JAVA_HOME" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  exit 1
fi

JAVACMD="$JAVA_HOME/jre/bin/java"

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly."
  echo "  We cannot execute $JAVACMD"
  exit 1
fi

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

#
# Execute command
#
MAIN_CLASS="dcraft.hub.Ignite"

"$JAVACMD" -Xmx2048m -Xms2048m -XX:+UseG1GC -XX:NewRatio=1 -XX:-UseAdaptiveSizePolicy -XX:G1HeapRegionSize=32m -Djava.library.path=./lib -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HToolkit -classpath "$CLASSPATH" $MAIN_CLASS $@

exit $?
