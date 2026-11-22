#!/bin/sh
# Render gives the database as DATABASE_URL=postgresql://user:password@host[:port]/database.
# Spring needs a JDBC URL plus separate credentials, so split it here.
if [ -n "$DATABASE_URL" ]; then
  rest="${DATABASE_URL#*://}"
  creds="${rest%%@*}"
  export DB_USERNAME="${creds%%:*}"
  export DB_PASSWORD="${creds#*:}"
  export DB_URL="jdbc:postgresql://${rest#*@}"
fi
exec java $JAVA_OPTS -jar app.jar
