FROM maven:3.6.3-jdk-11

COPY pom.xml /home/
COPY src /home/src/

WORKDIR /home

CMD ["mvn", "compile", "quarkus:dev"]