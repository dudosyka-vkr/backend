#!/bin/bash
/home/wait-for-it.sh postgres:5432 -s -t 60
java -jar -Xmx7g /opt/application/app.jar