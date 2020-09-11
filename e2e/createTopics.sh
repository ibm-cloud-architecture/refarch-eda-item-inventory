### Variables
ALL="items
inventory
itemSoldSum"

KAFKA_INTERNAL_PATH="/opt/kafka/"
KAFKA_DOCKER_NAME=$(docker ps | grep kafka_1 | awk '{print $NF}')
if [ -z "$KAFKA_DOCKER_NAME" ]
then
    echo "\e[31m [ERROR] - Kafka docker is not running.\e[0m"
    exit 1
fi

### Functions

createTopic(){
    if [ ! -z "$1" ]
    then
        docker exec -ti $KAFKA_DOCKER_NAME  /bin/bash -c "$KAFKA_INTERNAL_PATH/bin/kafka-topics.sh --create  --zookeeper zookeeper:2181 --replication-factor 1 --partitions 3 --topic $1"
    else
        echo -e "\e[31m[ERROR] - Topic $1 is incorrect.\e[0m"
    fi
}



# The kafka-topics.sh --list command returns a list of the Kafka topics already created. However, this list comes with an ASCII Control Code carriage return (^M, ctrl+v or '\r')
# which, despite being a non-printable character, is taken into account by the uniq command to remove duplicates later on.
# This character can be removed by executing the dos2unix command. However, this utility might not be available in every laptop.
# Therefore we are going to use tr -d '\r' instead
ALREADY_CREATED=$(docker exec  -ti $KAFKA_DOCKER_NAME /bin/bash -c "$KAFKA_INTERNAL_PATH/bin/kafka-topics.sh --list --zookeeper zookeeper:2181" | tr -d '\r')
# There are other alternative such as sed. Using sed 's/.$//g' we are removing the last character of every line which should be that carriage return character.
# However, if for any reason there is no such character we would have a bug.
# Other option, that I could not get to work, is the one described here: https://www.cyberciti.biz/faq/unix-linux-sed-ascii-control-codes-nonprintable/

echo "These are the topics already created:"
echo "${ALREADY_CREATED}"
echo
# Calculate topics to be created:
# 1. Append all topics with topics already created
ALL_AND_ALREADY_CREATED="${ALL}
${ALREADY_CREATED}"

# 2. Select non-duplicate topics from the list above
TO_BE_CREATED=$(echo "${ALL_AND_ALREADY_CREATED}" | sort | uniq -u)

echo "These are the topics to be created:"
echo "${TO_BE_CREATED}"
echo

# Create non-existing topics
if [ -z "${TO_BE_CREATED}" ]
then
    echo "No new topics to be created"
else
    for topic in ${TO_BE_CREATED}
    do
        createTopic "${topic}"
    done
    echo "All topics created"
fi



