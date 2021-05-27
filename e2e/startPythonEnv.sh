#!/bin/bash

if [[ -z $1 ]]
then
  nname=$1
else
  nname=refarch-eda-item-inventory_default 
fi
docker run -ti --network $nname -v $(pwd):/home -e KAFKA_BROKERS=kafka:29092  quay.io/ibmcase/pythonitg bash