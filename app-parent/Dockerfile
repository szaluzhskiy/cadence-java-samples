FROM openjdk:11
RUN mkdir -p /opt/app-parent/
COPY ./build/libs/app-parent-*.jar /opt/app-parent/
WORKDIR /opt/app-parent/
CMD java -jar app-parent-*.jar