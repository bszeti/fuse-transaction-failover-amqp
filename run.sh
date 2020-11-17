export "MAVEN_OPTS=-Dorg.slf4j.simpleLogger.log.bszeti.artemis.test=debug -Dorg.slf4j.simpleLogger.log.org.apache.qpid.jms=trace -Dorg.slf4j.simpleLogger.log.org.springframework.jms.connection=info"
export "MAVEN_OPTS=$MAVEN_OPTS -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.showThreadName=true -Dorg.slf4j.simpleLogger.showShortLogName=true"
export "MAVEN_OPTS=$MAVEN_OPTS -Dorg.slf4j.simpleLogger.logFile=out.log"
mvn clean install -Dactivemq.basedir=${ARTEMIS_HOME}

grep "Message count" out.log
