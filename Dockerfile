from clojure:latest

RUN apt-get update && \
   apt-get install -y wget

#ENV MAVEN_VERSION="3.6.0" \
#    MAVEN_HOME=/opt/maven

#RUN cd /tmp && \
#    wget "http://ftp.unicamp.br/pub/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" && \
#    tar xzf /tmp/apache-maven-$MAVEN_VERSION-bin.tar.gz -C /opt/ && \
#    ln -s /opt/apache-maven-$MAVEN_VERSION /opt/maven && \
#    ln -s /opt/maven/bin/mvn /usr/local/bin && \
#    rm -f /tmp/apache-maven-$MAVEN_VERSION.tar.gz

# FROM openjdk:12-jdk-alpine

RUN apt-get install curl tar bash procps

ARG MAVEN_VERSION=3.6.0
ARG USER_HOME_DIR="/root"
ARG SHA=fae9c12b570c3ba18116a4e26ea524b29f7279c17cbaadc3326ca72927368924d9131d11b9e851b8dc9162228b6fdea955446be41207a5cfc61283dd8a561d2f
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

RUN mkdir -p $USER_HOME_DIR
WORKDIR $USER_HOME_DIR

COPY . $USER_HOME_DIR
RUN rm -rf $USER_HOME_DIR/target

ENV HOME $USER_HOME_DIR
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"
ENV SURFER_CREATE_DB 1
ENV HTTP_PORT 8080

RUN mvn clean install

# ENTRYPOINT mvn install exec:java
EXPOSE 8080

