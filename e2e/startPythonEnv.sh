if [[ -z $1 ]]
then
  nname=$1
else
  nname=item-inventory_default
fi
docker run --network $nname --name pythonenv -v $(pwd):/home -it -e KAFKA_BROKERS=kafka:9092 --rm  quay.io/ibmcase/pythonitg bash