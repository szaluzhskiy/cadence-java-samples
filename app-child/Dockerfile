FROM openjdk:11
RUN mkdir -p /opt/app-child/
COPY ./build/libs/app-child-*.jar /opt/app-child/
WORKDIR /opt/app-child/
CMD java -jar app-child-*.jar